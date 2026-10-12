package com.digimon.glyph.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockRegressionTest {
    @Test fun reproducesLostSecondsWhenOnlyTheOscillatorIsFastForwarded() {
        val faithful = TimingRom.core()
        faithful.runForCycles(5.0 * 1_060_000)
        val skipped = TimingRom.core()
        skipped.fastForwardOsc1(5L * E0C6200.OSC1_CLOCK)
        skipped.runForCycles(0.01 * 1_060_000)
        val serviced = faithful.getState()["DBG_INT_COUNT"] as Int
        val lost = skipped.getState()["DBG_INT_COUNT"] as Int
        assertEquals(5, serviced)
        assertEquals(1, lost)
        assertEquals(5, skipped.getState()["DBG_IT1_SET"] as Int)
        assertTrue("OSC1 diagnostics look accurate even though firmware lost seconds", (skipped.getDiagnostics()["osc1"] as Long) >= 5L * 32768)
    }
}
