package com.digimon.glyph.sync

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/** Private Android storage: sync bytes before publishing an immutable, non-overwriting backup. */
object LocalSnapshotBackups {
    fun write(directory: File, record: PortableSave.Record): File {
        check(directory.isDirectory || directory.mkdirs()) { "Could not create handoff backup directory" }
        val contents = PortableSave.encode(record)
        PortableSave.decode(contents)
        val target = File(directory, record.id + ".vpetsave")
        val partial = Files.createTempFile(directory.toPath(), record.id, ".partial").toFile()
        try {
            FileOutputStream(partial).use { stream ->
                stream.write(contents.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            check(partial.readText(Charsets.UTF_8) == contents) { "Backup verification failed" }
            // Atomic exclusive publication on app-private Linux storage. Existing
            // backups cannot be replaced, even if an identity is accidentally reused.
            Files.createLink(target.toPath(), partial.toPath())
            return target
        } finally {
            partial.delete()
        }
    }
}
