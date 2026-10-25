package com.digimon.glyph.sync

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalSnapshotBackupsTest {
    @get:Rule val folder = TemporaryFolder()
    private val record = ExchangeFolderPublicationTest.record

    @Test fun backupPublishesVerifiedBytesAndLeavesNoPartial() {
        val directory = File(folder.root, "backups")
        val backup = LocalSnapshotBackups.write(directory, record)
        assertEquals(record, PortableSave.decode(backup.readText()))
        assertEquals(listOf(record.id + ".vpetsave"), directory.list()!!.toList())
    }

    @Test fun reusedIdentityCannotReplaceAnExistingBackup() {
        val original = LocalSnapshotBackups.write(folder.root, record)
        val changed = record.copy(origin = "other_device")
        assertThrows(java.nio.file.FileAlreadyExistsException::class.java) {
            LocalSnapshotBackups.write(folder.root, changed)
        }
        assertEquals(record, PortableSave.decode(original.readText()))
        assertFalse(folder.root.list()!!.any { it.endsWith(".partial") })
    }

    @Test fun corruptRecordAndUnavailableDirectoryFailBeforePublishing() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalSnapshotBackups.write(folder.root, record.copy(payloadSha256 = "wrong"))
        }
        val occupied = folder.newFile("occupied")
        assertThrows(IllegalStateException::class.java) { LocalSnapshotBackups.write(occupied, record) }
        assertEquals(listOf("occupied"), folder.root.list()!!.toList())
    }
}
