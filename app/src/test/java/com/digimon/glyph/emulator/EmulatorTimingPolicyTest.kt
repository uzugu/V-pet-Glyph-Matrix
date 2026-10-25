package com.digimon.glyph.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class EmulatorTimingPolicyTest {
    @Test fun powerSaveOverridesCosmeticSmoothingButNeverInputOrBattleTiming() {
        for (power in listOf(false, true)) for (smooth in listOf(false, true)) {
            assertEquals(EmulatorLoop.TimingMode.FULL_ACCURATE,
                EmulatorTimingPolicy.select(power, smooth, true, false))
            assertEquals(EmulatorLoop.TimingMode.FULL_ACCURATE,
                EmulatorTimingPolicy.select(power, smooth, false, true))
        }
        assertEquals(EmulatorLoop.TimingMode.POWER_SAVE, EmulatorTimingPolicy.select(true, true, false, false))
        assertEquals(EmulatorLoop.TimingMode.POWER_SAVE, EmulatorTimingPolicy.select(true, false, false, false))
        assertEquals(EmulatorLoop.TimingMode.FULL_ACCURATE, EmulatorTimingPolicy.select(false, true, false, false))
        assertEquals(EmulatorLoop.TimingMode.EXACT, EmulatorTimingPolicy.select(false, false, false, false))
    }
}
