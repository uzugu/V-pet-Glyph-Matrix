package com.digimon.glyph.emulator

import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Opt-in private-ROM coverage; firmware must never be checked into the repository. */
@RunWith(Parameterized::class)
class ClassicRomIntegrationTest(private val device: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun devices() = listOf("DigimonV1JA", "DigimonV1JB", "DigimonV1ES", "DigimonV2J", "DigimonV3J", "DigimonV4J").map { arrayOf(it) }
    }
    @Test fun firmwareClockButtonsAndSnapshotContinuation() {
        val directory = System.getenv("CLASSIC_ROM_DIR")
        assumeTrue("Set CLASSIC_ROM_DIR for actual-ROM tests", directory != null)
        val rom = File(directory, "$device.bin").readBytes()
        assertEquals(device, RomCatalog.identify(rom).id)
        val core = E0C6200(rom)
        core.runForCycles(600.0 * core.clockFrequency)
        assertEquals(600, core.getState()["DBG_IT1_SET"])
        assertTrue(core.getVRAM().any { it != 0 })
        for (pin in listOf(2,1,0)) {
            core.pinSet("K0", pin, 0); core.runForCycles(0.1 * core.clockFrequency)
            core.pinRelease("K0", pin); core.runForCycles(0.1 * core.clockFrequency)
        }
        val restored = E0C6200(rom).apply { restoreState(core.getState()) }
        val budget = 2.0 * core.clockFrequency
        assertEquals(core.runForCycles(budget), restored.runForCycles(budget), 0.0)
        for ((key, expected) in core.getState()) {
            if (key.startsWith("DBG_")) continue
            val actual = restored.getState()[key]
            if (expected is IntArray) assertArrayEquals("$device $key", expected, actual as IntArray)
            else assertEquals("$device $key", expected, actual)
        }
    }
}
