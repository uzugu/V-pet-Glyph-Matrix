package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EmulatorStatusTest {
    @Before fun reset() {
        EmulatorStatus::class.java.getDeclaredField("current").apply { isAccessible = true }
            .set(null, EmulatorStatus.Snapshot())
    }
    @Test fun runningStateDoesNotDependOnFramesOrGlyphConnection() {
        EmulatorStatus.runtime(EmulatorStatus.Run.RUNNING)
        EmulatorStatus.glyph(EmulatorStatus.Glyph.DISCONNECTED)
        assertEquals(EmulatorStatus.Run.RUNNING, EmulatorStatus.snapshot().run)
        EmulatorStatus.runtime(EmulatorStatus.Run.FROZEN)
        assertEquals(EmulatorStatus.Run.FROZEN, EmulatorStatus.snapshot().run)
    }
    @Test fun duplicateAndLateImportResultsCannotReplaceCurrentSelection() {
        EmulatorStatus.device("classic", "old")
        val first = EmulatorStatus.beginImport()!!
        assertNull(EmulatorStatus.beginImport())
        EmulatorStatus.importResult(first, EmulatorStatus.Import.CANCELLED, "cancelled")
        val second = EmulatorStatus.beginImport()!!
        EmulatorStatus.importResult(first, EmulatorStatus.Import.COMPLETE, "late")
        assertEquals(EmulatorStatus.Import.SCANNING, EmulatorStatus.snapshot().importing)
        assertEquals("classic", EmulatorStatus.snapshot().name)
        EmulatorStatus.importResult(second, EmulatorStatus.Import.FAILED, "invalid ROM")
        assertFalse(EmulatorStatus.snapshot().busy)
        assertEquals("old", EmulatorStatus.snapshot().hash)
    }
    @Test fun staleInitialDiskReadCannotReplaceImportedDevice() {
        val revision = EmulatorStatus.snapshot().revision
        EmulatorStatus.device("DPowerDigiviceEU101", RomCatalog.DPOWER_HASH)
        EmulatorStatus.initialDevice("old", "old", revision)
        assertTrue(EmulatorStatus.snapshot().isDPower)
        assertFalse(EmulatorStatus.snapshot().hasClassicMetadata)
    }
}
