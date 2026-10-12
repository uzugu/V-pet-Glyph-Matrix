package com.digimon.glyph.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimePacerTest {
    private fun repay(pacer: RealtimePacer, core: E0C6200, now: Long) {
        while (pacer.debtCycles(now) > 0) pacer.advance(core.clock())
    }

    @Test fun oneHourOfJitterAndSuspendPreservesEveryFirmwareSecond() {
        val core = TimingRom.core()
        val start = 123_456_789_000_000L
        val pacer = RealtimePacer(core.clockFrequency, start)
        var now = start
        val gaps = longArrayOf(1_000_000, 17_000_000, 25_000_000, 150_000_000, 2_500_000_000)
        var i = 0
        val end = start + 3_600_000_000_000L
        while (now < end) {
            now = (now + gaps[i++ % gaps.size]).coerceAtMost(end)
            repay(pacer, core, now)
            assertTrue("Debt may overshoot by only one slow-clock instruction", kotlin.math.abs(pacer.debtNs(now)) < 700_000)
        }
        assertEquals(3600, core.getState()["DBG_INT_COUNT"])
        assertEquals(3600 % 16, core.getRAM()[0])
        assertEquals(3600.0, pacer.virtualNs / 1e9, 0.0007)
    }

    @Test fun clockRateRemainsOneToOneAtAnyFrameCadence() {
        for (fps in listOf(4, 15, 30)) {
            val core = TimingRom.core()
            val pacer = RealtimePacer(core.clockFrequency, 0)
            for (frame in 1..(fps * 120)) repay(pacer, core, frame * 1_000_000_000L / fps)
            assertEquals("$fps FPS", 120, core.getState()["DBG_INT_COUNT"])
        }
    }

    @Test fun bothCpuOscillatorsKeepTheSameOneSecondTimer() {
        for (oscillator in listOf(0, E0C6200.IO_CLKCHG)) {
            val core = TimingRom.core().apply { restoreState(getState() + ("CTRL_OSC" to oscillator)) }
            val pacer = RealtimePacer(core.clockFrequency, 0)
            repay(pacer, core, 12_500_000_000)
            assertEquals(12, core.getState()["DBG_INT_COUNT"])
            assertEquals(12.5, pacer.virtualNs / 1e9, 0.0007)
        }
    }

    @Test fun firmwareSecondsFollowSixRealSecondsDespiteAThreadStall() {
        val core = TimingRom.core()
        val start = System.nanoTime()
        val pacer = RealtimePacer(core.clockFrequency, start)
        var stalled = false
        var now = start
        while (now - start < 6_000_000_000L) {
            if (!stalled && now - start > 2_000_000_000L) {
                Thread.sleep(500)
                stalled = true
            }
            repay(pacer, core, System.nanoTime())
            Thread.sleep(3)
            now = System.nanoTime()
        }
        repay(pacer, core, now)
        assertEquals(6, core.getState()["DBG_INT_COUNT"])
        assertEquals((now - start) / 1e9, pacer.virtualNs / 1e9, 0.0007)
    }

    @Test fun explicitStepPauseRebasesWithoutReplayingPausedTime() {
        val core = TimingRom.core()
        val pacer = RealtimePacer(core.clockFrequency, 0)
        repay(pacer, core, 1_000_000_000)
        pacer.rebase(60_000_000_000)
        repay(pacer, core, 61_000_000_000)
        assertEquals(2, core.getState()["DBG_INT_COUNT"])
    }

    @Test fun repeatedSaveRestoresPreserveFractionalTimers() {
        var restored = TimingRom.core()
        val uninterrupted = TimingRom.core()
        repeat(600) {
            // Exactly the same instruction sequence/cycle budget on both paths.
            val budget = 1_060_000 * 0.071234567
            restored.runForCycles(budget)
            uninterrupted.runForCycles(budget)
            restored = TimingRom.core().apply { restoreState(restored.getState()) }
            for (key in listOf("TM", "OSC1_COUNTER", "TIMER_COUNTER", "STOPWATCH_COUNTER", "PTIMER_COUNTER")) {
                assertEquals(key, uninterrupted.getState()[key], restored.getState()[key])
            }
            assertEquals(uninterrupted.getRAM()[0], restored.getRAM()[0])
        }
    }
}
