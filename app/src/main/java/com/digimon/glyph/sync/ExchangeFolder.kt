package com.digimon.glyph.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.UUID

class ExchangeFolder(private val context: Context) {
    private val prefs = context.getSharedPreferences("save_exchange", 0)
    val tree: Uri? get() = prefs.getString("tree", null)?.let(Uri::parse)
    fun setTree(uri: Uri) { prefs.edit().putString("tree", uri.toString()).commit() }
    val origin: String get() = prefs.getString("origin", null) ?: ("phone_" + UUID.randomUUID().toString().take(8)).also { prefs.edit().putString("origin", it).commit() }
    fun parent(hash: String): String? = prefs.getString("parent_$hash", null)
    fun mark(record: PortableSave.Record) { check(prefs.edit().putString("parent_${record.romHash}", record.id).commit()) { "Could not record handoff history" } }
    fun read(uri: Uri): String = context.contentResolver.openInputStream(uri)!!.use {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() <= PortableSave.MAX_BYTES) {
            val count = it.read(buffer, 0, minOf(buffer.size, PortableSave.MAX_BYTES + 1 - out.size()))
            if (count < 0) break
            out.write(buffer, 0, count)
        }
        val bytes = out.toByteArray()
        require(bytes.size <= PortableSave.MAX_BYTES) { "Save is too large" }
        bytes.toString(Charsets.UTF_8)
    }
    fun records(): List<Pair<Uri, PortableSave.Record>> {
        val root = tree ?: return emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
        val result = mutableListOf<Pair<Uri, PortableSave.Record>>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count++ < 1024) {
                if (!cursor.getString(1).endsWith(".vpetsave")) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(root, cursor.getString(0))
                try { result += uri to PortableSave.decode(read(uri)) } catch (_: Exception) { /* partial/corrupt files never replace a pet */ }
            }
        }
        return result.sortedByDescending { it.second.createdMs }
    }
    fun publish(record: PortableSave.Record) {
        val root = tree ?: error("Choose the Syncthing exchange folder first")
        val parent = DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
        val name = "${record.romHash}--${record.id}.vpetsave"
        val partial = DocumentsContract.createDocument(context.contentResolver, parent, "application/octet-stream", "$name.partial") ?: error("Could not create save")
        // A provider may keep the document ID across rename, or complete rename
        // before reporting an error. Once attempted, never delete through the old URI.
        var renameAttempted = false
        try {
            context.contentResolver.openOutputStream(partial, "w")!!.use { it.write(PortableSave.encode(record).toByteArray(Charsets.UTF_8)); it.flush() }
            require(read(partial) == PortableSave.encode(record)) { "Save verification failed" }
            renameAttempted = true
            check(DocumentsContract.renameDocument(context.contentResolver, partial, name) != null) { "Folder provider must support rename to publish completed saves" }
        } catch (e: Exception) {
            if (!renameAttempted) {
                try { DocumentsContract.deleteDocument(context.contentResolver, partial) } catch (_: Exception) {}
            }
            throw e
        }
    }
}
