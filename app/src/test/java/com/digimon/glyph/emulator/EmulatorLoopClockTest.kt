package com.digimon.glyph.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EmulatorLoopClockTest {
    @Test fun actualWorkerPreservesFirmwareSecondsAcrossARenderStallAndModeChanges() {
        val core = TimingRom.core()
        val started = CountDownLatch(1)
        val stalled = AtomicBoolean(false)
        val loop = EmulatorLoop(core, onFrame = {
            if (stalled.compareAndSet(false, true)) Thread.sleep(2300)
        }, elapsedNs = { started.countDown(); System.nanoTime() })
        loop.start()
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            for (mode in listOf(EmulatorLoop.TimingMode.EXACT, EmulatorLoop.TimingMode.FULL_ACCURATE, EmulatorLoop.TimingMode.POWER_SAVE)) {
                loop.setTimingMode(mode)
                Thread.sleep(2000)
            }
            // Read on the actual worker, after the delayed renderer has finished and debt is repaid.
            val serviced = loop.executeSync { it.getState()["DBG_INT_COUNT"] }
            assertTrue(stalled.get())
            assertEquals(6, serviced)
        } finally { loop.stop() }
    }
}
