package com.digimon.glyph.emulator

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class RomImportTest {
    @Test fun archiveReturnsAllSupportedMembersForSelection() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for (name in listOf("folder/first.bin", "second.BIN")) {
                zip.putNextEntry(ZipEntry(name)); zip.write(ByteArray(8192)); zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("bad.bin")); zip.write(ByteArray(8192) { 127 }); zip.closeEntry()
        }
        val candidates = RomImport.read(output.toByteArray().inputStream(), "pets.zip")
        assertEquals(listOf("folder/first.bin", "second.BIN"), candidates.map { it.name })
    }
    @Test fun rejectsUnverifiedLargeDumpAndBadEncoding() {
        for (bytes in listOf(ByteArray(98304), ByteArray(8192) { 127 }, ByteArray(98305))) {
            try { RomImport.read(bytes.inputStream(), "DPowerDigiviceEU101.bin"); fail("Must reject invalid data") }
            catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun filenameCannotSelectDifferentCpu() {
        val candidate = RomImport.read(ByteArray(8192).inputStream(), "DPowerDigiviceEU101.bin").single()
        assertFalse(candidate.device.isDPower)
        assertEquals("UnknownClassic", candidate.device.id)
    }
}
