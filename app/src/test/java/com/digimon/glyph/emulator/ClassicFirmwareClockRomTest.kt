package com.digimon.glyph.emulator

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.*

/** Uses private dumps only; no firmware bytes are distributed with these tests. */
@RunWith(Parameterized::class)
class ClassicFirmwareClockRomTest(private val device: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun devices() = ClassicRomIntegrationTest.devices()
    }
    private fun rom(): ByteArray {
        val directory = System.getenv("CLASSIC_ROM_DIR")
        assumeTrue("Set CLASSIC_ROM_DIR for private firmware-clock checks", directory != null)
        return File(directory, "$device.bin").readBytes().also { assertEquals(device, RomCatalog.identify(it).id) }
    }
    private class Firmware(rom: ByteArray) {
        val core = E0C6200(rom)
        private var debt = 0.0
        fun run(seconds: Double) {
            debt += seconds * core.clockFrequency
            if (debt > 0) debt -= core.runForCycles(debt)
        }
        fun press(pin: Int) {
            core.pinSet("K0", pin, 0); run(.2)
            core.pinRelease("K0", pin); run(.3)
        }
        fun openSetup() { run(2.0); press(1) }
        fun confirm() { press(0); awaitBoundary() }
        fun awaitBoundary() {
            repeat(100) { if (core.isFirmwareClockBoundary()) return; run(.001) }
            fail("Firmware did not reach its safe idle boundary")
        }
        fun untilClockChanges(from: ClassicFirmwareClock.Time): ClassicFirmwareClock.Time? {
            repeat(110) {
                run(.01)
                // Sampling partway through a decimal carry can observe a transient
                // mixed value; production mutations wait for this same boundary.
                if (!core.isFirmwareClockBoundary()) return@repeat
                val now = core.readFirmwareClock()
                if (now != from) return now
            }
            fail("Firmware clock did not tick"); return null
        }
    }

    @Test fun androidCoreIndependentlyConfirmsButtonTraceAndNaturalMidnightRollover() {
        val f = Firmware(rom())
        assertTrue(f.core.supportsFirmwareClockOverride())
        f.openSetup()
        assertFalse(f.core.isFirmwareClockBoundary())
        assertNull(f.core.readFirmwareClock())
        for (hour in 1..24) {
            f.press(2)
            assertEquals(ClassicFirmwareClock.Time(hour % 24, 0), f.core.readFirmwareClock())
        }
        for (minute in 1..60) {
            f.press(1)
            assertEquals(ClassicFirmwareClock.Time(0, minute % 60), f.core.readFirmwareClock())
        }
        repeat(23) { f.press(2) }
        repeat(59) { f.press(1) }
        f.confirm()
        f.run(59.0)
        assertEquals(ClassicFirmwareClock.Time(23, 59, 59), f.core.readFirmwareClock())
        f.run(1.0)
        assertEquals(ClassicFirmwareClock.Time(0, 0), f.core.readFirmwareClock())
        f.run(1.0)
        assertEquals(ClassicFirmwareClock.Time(0, 0, 1), f.core.readFirmwareClock())
    }

    @Test fun overrideTouchesOnlySixClockNibblesAndSurvivesActualJsonSaveRestore() {
        val bytes = rom()
        val f = Firmware(bytes)
        f.openSetup(); f.press(2); f.confirm()
        val before = f.core.getState()
        val target = ClassicFirmwareClock.Time(23, 59, 59)
        f.core.overrideFirmwareClock(target)
        val after = f.core.getState()
        val expectedRam = (before.getValue("RAM") as IntArray).copyOf()
        // Keep expected encoding independent of the production encoder.
        intArrayOf(9, 5, 9, 5, 11, 1).copyInto(expectedRam, 0x1a)
        ClassicFirmwareClockTest.assertUnchanged(before + ("RAM" to expectedRam), after)
        assertEquals(target, f.core.readFirmwareClock())

        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        val manager = StateManager(context)
        val encode = StateManager::class.java.getDeclaredMethod("serializeState", Map::class.java).apply { isAccessible = true }
        val decode = StateManager::class.java.getDeclaredMethod("deserializeState", String::class.java).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val decoded = decode.invoke(manager, Gson().toJson(encode.invoke(manager, after))) as Map<String, Any>
        val resumed = E0C6200(bytes).apply { restoreState(decoded) }
        ClassicFirmwareClockTest.assertUnchanged(after, resumed.getState(), skipDebug = true)
        val budget = 1.3 * f.core.clockFrequency
        assertEquals(f.core.runForCycles(budget), resumed.runForCycles(budget), 0.0)
        ClassicFirmwareClockTest.assertUnchanged(f.core.getState(), resumed.getState(), skipDebug = true)
        val timerTicks = (f.core.getState().getValue("DBG_IT1_SET") as Int) -
            (after.getValue("DBG_IT1_SET") as Int)
        assertEquals(0, (after.getValue("IT") as Int) and E0C6200.IO_IT1)
        assertTrue(timerTicks in 1..2)
        // Firmware seconds use their own half-second phase, not the peripheral's
        // diagnostic IT1 edge. 1.3 seconds crosses one or two firmware ticks.
        val rolled = resumed.readFirmwareClock()!!
        assertEquals(0, rolled.hour)
        assertEquals(0, rolled.minute)
        assertTrue(rolled.second in 0..1)
        val tenSeconds = 10.0 * f.core.clockFrequency
        assertEquals(f.core.runForCycles(tenSeconds), resumed.runForCycles(tenSeconds), 0.0)
        ClassicFirmwareClockTest.assertUnchanged(f.core.getState(), resumed.getState(), skipDebug = true)
        assertEquals(ClassicFirmwareClock.Time(0, 0, rolled.second + 10), resumed.readFirmwareClock())

        // Noon uses the same binary 12 nibble, with PM set; preserve the next tick's phase.
        f.awaitBoundary()
        f.core.overrideFirmwareClock(ClassicFirmwareClock.Time(11, 59, 59))
        assertEquals(ClassicFirmwareClock.Time(12, 0), f.untilClockChanges(ClassicFirmwareClock.Time(11, 59, 59)))
    }

    @Test fun clockDisplayAfterOverrideMatchesTheSameTimeEnteredUsingButtons() {
        val bytes = rom()
        val reference = Firmware(bytes)
        reference.openSetup()
        repeat(13) { reference.press(2) }
        repeat(12) { reference.press(1) }
        reference.confirm(); reference.run(1.0)
        val referencePixels = topClockPixels(reference.core)

        val f = Firmware(bytes)
        f.openSetup(); f.press(2); f.confirm(); f.run(1.0); f.awaitBoundary()
        val oldPixels = topClockPixels(f.core)
        f.core.overrideFirmwareClock(ClassicFirmwareClock.Time(13, 12))
        // Firmware owns the normal redraw. No button, VRAM or overlay patch is used.
        f.run(1.0)
        val changedPixels = topClockPixels(f.core)
        assertNotEquals("The visible clock digits must change", oldPixels, changedPixels)
        assertEquals("Clock digits must match manual firmware input", referencePixels, changedPixels)
    }

    private fun topClockPixels(core: E0C6200): String {
        val bridge = DisplayBridge()
        val extract = DisplayBridge::class.java.getDeclaredMethod("extractPixels", IntArray::class.java).apply { isAccessible = true }
        extract.invoke(bridge, core.getVRAM())
        @Suppress("UNCHECKED_CAST")
        val pixels = DisplayBridge::class.java.getDeclaredField("lcdPixels").apply { isAccessible = true }.get(bridge) as Array<BooleanArray>
        // Hours/minutes occupy the upper seven rows, columns 0..23. The tiny
        // seconds at the right and the colon can differ with sub-second phase.
        val digits = (0..6).joinToString("") { y -> (0..23).filter { it != 12 }.joinToString("") { x -> if (pixels[y][x]) "#" else "." } }
        val amPm = (8..15).joinToString("") { y -> (0..10).joinToString("") { x -> if (pixels[y][x]) "#" else "." } }
        return digits + amPm
    }

    @Test fun actualWorkerAcceptsClockCommandsInEveryDisplayTimingMode() {
        val f = Firmware(rom())
        f.openSetup(); f.press(2); f.confirm()
        val loop = EmulatorLoop(f.core, {}, elapsedNs = { System.nanoTime() })
        loop.start()
        try {
            for (mode in EmulatorLoop.TimingMode.values()) {
                loop.setTimingMode(mode)
                val requested = ClassicFirmwareClock.Time(6, 7, mode.ordinal + 8)
                val result = loop.executeSyncWhen(1200, { (it as E0C6200).isFirmwareClockBoundary() }) {
                    (it as E0C6200).overrideFirmwareClock(requested)
                    it.readFirmwareClock()
                }
                assertEquals("$device $mode", requested, result)
            }
        } finally { loop.stop() }
    }

    @Test fun busyFirmwareAndModifiedRomCannotBePatched() {
        val bytes = rom()
        val f = Firmware(bytes)
        f.openSetup(); f.press(2)
        val setting = f.core.getState()
        assertThrows(IllegalStateException::class.java) { f.core.overrideFirmwareClock(ClassicFirmwareClock.Time(12, 0)) }
        ClassicFirmwareClockTest.assertUnchanged(setting, f.core.getState())
        f.confirm()
        f.core.restoreState(f.core.getState() + ("HALT" to 0))
        val busy = f.core.getState()
        assertFalse(f.core.isFirmwareClockBoundary())
        assertThrows(IllegalStateException::class.java) { f.core.overrideFirmwareClock(ClassicFirmwareClock.Time(12, 0)) }
        ClassicFirmwareClockTest.assertUnchanged(busy, f.core.getState())
        val modified = bytes.copyOf().apply { this[1] = (this[1].toInt() xor 1).toByte() }
        val unknown = E0C6200(modified)
        unknown.restoreState(setting)
        assertFalse(unknown.supportsFirmwareClockOverride())
        assertThrows(IllegalStateException::class.java) { unknown.overrideFirmwareClock(ClassicFirmwareClock.Time(12, 0)) }
    }
}
