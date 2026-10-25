package com.digimon.glyph.sync

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class ExchangeFolderPublicationTest {
    private fun fixture(body: (ExchangeFolder, SharedPreferences.Editor, () -> ByteArray) -> Unit,
                        corrupt: Boolean = false, renameFailure: Boolean = false) {
        val context = mock(Context::class.java)
        val resolver = mock(ContentResolver::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
        val root = mock(Uri::class.java)
        val parent = mock(Uri::class.java)
        val partial = mock(Uri::class.java)
        val output = ByteArrayOutputStream()
        `when`(context.contentResolver).thenReturn(resolver)
        `when`(context.getSharedPreferences("save_exchange", 0)).thenReturn(prefs)
        `when`(prefs.getString("tree", null)).thenReturn("tree")
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.commit()).thenReturn(false)
        `when`(resolver.openOutputStream(partial, "w")).thenReturn(output)
        `when`(resolver.openInputStream(partial)).thenAnswer {
            ByteArrayInputStream(if (corrupt) byteArrayOf(0) else output.toByteArray())
        }
        mockStatic(Uri::class.java).use { uris ->
            uris.`when`<Uri> { Uri.parse("tree") }.thenReturn(root)
            mockStatic(DocumentsContract::class.java).use { documents ->
                documents.`when`<String> { DocumentsContract.getTreeDocumentId(root) }.thenReturn("root")
                documents.`when`<Uri> { DocumentsContract.buildDocumentUriUsingTree(root, "root") }.thenReturn(parent)
                documents.`when`<Uri> { DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name + ".partial") }.thenReturn(partial)
                if (renameFailure) {
                    documents.`when`<Uri> { DocumentsContract.renameDocument(resolver, partial, name) }
                        .thenThrow(IllegalStateException("Provider lost rename acknowledgement"))
                } else {
                    // ExternalStorageProvider may preserve its document identity on rename.
                    documents.`when`<Uri> { DocumentsContract.renameDocument(resolver, partial, name) }.thenReturn(partial)
                }
                body(ExchangeFolder(context), editor) { output.toByteArray() }
                documents.verify({ DocumentsContract.deleteDocument(resolver, partial) }, if (corrupt) times(1) else never())
                documents.verify({ DocumentsContract.renameDocument(resolver, partial, name) }, if (corrupt) never() else times(1))
            }
        }
    }

    @Test fun completedSnapshotSurvivesHistoryCommitFailureWithStableDocumentIdentity() {
        fixture({ folder, editor, bytes ->
            folder.publish(record)
            assertEquals(record, PortableSave.decode(bytes().toString(Charsets.UTF_8)))
            verify(editor, never()).commit()
            assertThrows(IllegalStateException::class.java) { folder.mark(record) }
        })
    }

    @Test fun verificationFailureDeletesOnlyTheUnpublishedPartial() {
        fixture({ folder, editor, _ ->
            assertThrows(IllegalArgumentException::class.java) { folder.publish(record) }
            verify(editor, never()).commit()
        }, corrupt = true)
    }

    @Test fun ambiguousRenameFailureNeverDeletesThroughThePossiblyPublishedDocumentId() {
        fixture({ folder, editor, bytes ->
            assertThrows(IllegalStateException::class.java) { folder.publish(record) }
            assertEquals(record, PortableSave.decode(bytes().toString(Charsets.UTF_8)))
            verify(editor, never()).commit()
        }, renameFailure = true)
    }

    companion object {
        private val payload = "{}"
        val record = PortableSave.Record(id = "12345678-1234-1234-1234-123456789abc", parent = null,
            origin = "phone_test", createdMs = 1, romHash = "a".repeat(64), profile = "DigimonV1JA",
            core = "E0C6200", payload = payload, payloadSha256 = PortableSave.digest(payload))
        val name = "${record.romHash}--${record.id}.vpetsave"
    }
}
