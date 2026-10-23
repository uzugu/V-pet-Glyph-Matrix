package com.digimon.glyph.input

import org.junit.Assert.*
import org.junit.Test

class AutoStepperTest {
    @Test fun eachRateProducesExpectedStepsWithoutChangingClock() {
        for (rate in 1..8) {
            val auto = AutoStepper(); val pulses = StepPulses(); var presses = 0
            repeat(10000) {
                if (auto.advance(2200.0, true, rate) && pulses.pending == 0 && pulses.phase != 1) pulses.enqueue()
                pulses.advance(2200) { if (it) presses++ }
            }
            assertEquals("half-steps rate=$rate over 10 seconds", 5 * rate, presses)
            assertEquals(0, pulses.pending)
        }
    }
    @Test fun disabledResetAndRateChangeDoNotCatchUp() {
        val auto = AutoStepper()
        assertFalse(auto.advance(1_000_000.0, true, 2))
        assertFalse(auto.advance(100_000_000.0, false, 2))
        assertFalse(auto.advance(1_000_000.0, true, 2))
        auto.reset()
        assertFalse(auto.advance(1_000_000.0, true, 2))
        assertFalse(auto.advance(100.0, true, 8))
        assertTrue(auto.advance(550_000.0, true, 8))
    }
    @Test fun largeBatchProducesOneRequestRatherThanBurst() {
        val auto = AutoStepper()
        assertTrue(auto.advance(22_000_000.0, true, 2))
        assertFalse(auto.advance(2200.0, true, 2))
    }
}
