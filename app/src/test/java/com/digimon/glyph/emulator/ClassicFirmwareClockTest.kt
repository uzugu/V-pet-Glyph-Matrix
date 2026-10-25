package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Test

class ClassicFirmwareClockTest {
    @Test fun allTimesUseBinaryTwelveHourNibblesAndDecimalMinutesSeconds() {
        for (seconds in 0 until 86400) {
            val time = ClassicFirmwareClock.fromSecondsOfDay(seconds)
            assertEquals(seconds, time.secondsOfDay)
            assertEquals(time, ClassicFirmwareClock.read(time.nibbles()))
        }
        assertArrayEquals(intArrayOf(0, 0, 0, 0, 12, 0), ClassicFirmwareClock.Time(0, 0).nibbles())
        assertArrayEquals(intArrayOf(9, 5, 9, 5, 11, 1), ClassicFirmwareClock.Time(23, 59, 59).nibbles())
        assertArrayEquals(intArrayOf(0, 0, 0, 0, 12, 1), ClassicFirmwareClock.Time(12, 0).nibbles())
    }
    @Test fun unsetOrMalformedFieldsAndInvalidRequestsAreRejected() {
        assertNull(ClassicFirmwareClock.read(IntArray(6)))
        assertNull(ClassicFirmwareClock.read(intArrayOf(10, 0, 0, 0, 12, 0)))
        assertNull(ClassicFirmwareClock.read(intArrayOf(0, 6, 0, 0, 12, 0)))
        assertNull(ClassicFirmwareClock.read(intArrayOf(0, 0, 10, 0, 12, 0)))
        assertNull(ClassicFirmwareClock.read(intArrayOf(0, 0, 0, 6, 12, 0)))
        assertNull(ClassicFirmwareClock.read(intArrayOf(0, 0, 0, 0, 13, 0)))
        assertNull(ClassicFirmwareClock.read(intArrayOf(0, 0, 0, 0, 12, 2)))
        for (seconds in listOf(-1, 86400, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { ClassicFirmwareClock.fromSecondsOfDay(seconds) }
        }
    }
    @Test fun aForgedBoundaryCannotEnableAnUnknownRom() {
        val core = TimingRom.core()
        core.restoreState(core.getState() + mapOf("HALT" to 1, "PC" to 0x16D, "NPC" to 0x100))
        val before = core.getState()
        assertFalse(core.supportsFirmwareClockOverride())
        assertFalse(core.isFirmwareClockBoundary())
        assertNull(core.readFirmwareClock())
        assertThrows(IllegalStateException::class.java) { core.overrideFirmwareClock(ClassicFirmwareClock.Time(12, 0)) }
        assertUnchanged(before, core.getState())
    }

    companion object {
        fun assertUnchanged(expected: Map<String, Any>, actual: Map<String, Any>, skipDebug: Boolean = false) {
            for ((key, value) in expected) {
                if (skipDebug && key.startsWith("DBG_")) continue
                if (value is IntArray) assertArrayEquals(key, value, actual[key] as IntArray)
                else assertEquals(key, value, actual[key])
            }
        }
    }
}
