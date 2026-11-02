package com.digimon.glyph.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EmulatorLoopClockTest {
    @Test fun queuedWidgetTapsSurviveAStalledWorkerWithFirmwareHoldAndReleaseTimes() {
        val base = TimingRom.core()
        var cycles = 0.0
        val edges = mutableListOf<Pair<Boolean, Double>>()
        val finished = CountDownLatch(4)
        val core = object : EmulatorCore by base {
            override fun clock(): Double = base.clock().also { cycles += it }
            override fun pinSet(port: String, pin: Int, level: Int) {
                edges += true to cycles
                finished.countDown()
            }
            override fun pinRelease(port: String, pin: Int) {
                edges += false to cycles
                finished.countDown()
            }
        }
        val stalled = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val loop = EmulatorLoop(core, {}, { System.nanoTime() })
        loop.start()
        try {
            assertTrue(loop.enqueue { stalled.countDown(); resume.await(2, TimeUnit.SECONDS) })
            assertTrue(stalled.await(2, TimeUnit.SECONDS))
            repeat(2) { assertTrue(loop.tapButton("K0", 2, 0) { true }) }
            Thread.sleep(150) // Both host-time releases would already be due.
            resume.countDown()
            assertTrue(finished.await(3, TimeUnit.SECONDS))
            val recorded = loop.executeSync { edges.toList() }!!
            assertEquals(listOf(true, false, true, false), recorded.map { it.first })
            assertTrue(recorded[1].second - recorded[0].second >= core.clockFrequency * 0.120)
            assertTrue(recorded[2].second - recorded[1].second >= core.clockFrequency * 0.080)
            assertTrue(recorded[3].second - recorded[2].second >= core.clockFrequency * 0.120)
        } finally { resume.countDown(); loop.stop() }
    }

    @Test fun powerSaveBatchesWakeupsWithoutStoppingTheFirmwareClock() {
        val waits = java.util.concurrent.atomic.AtomicInteger()
        val core = TimingRom.core()
        val loop = EmulatorLoop(core, {}, { System.nanoTime() }, parkNs = { ns ->
            waits.incrementAndGet()
            java.util.concurrent.locks.LockSupport.parkNanos(ns)
        })
        loop.setTimingMode(EmulatorLoop.TimingMode.POWER_SAVE)
        loop.start()
        try {
            Thread.sleep(1200)
            assertTrue("Power saving should need fewer than 90 waits in 1.2 seconds: ${waits.get()}", waits.get() < 90)
            assertEquals(1, loop.executeSync(1000) { it.getState()["DBG_INT_COUNT"] })
        } finally { loop.stop() }
    }

    @Test fun normalPacingBatchesWakeupsAndCommandsWakeTheWorker() {
        val waits = java.util.concurrent.atomic.AtomicInteger()
        val parked = CountDownLatch(1)
        val loop = EmulatorLoop(TimingRom.core(), {}, { System.nanoTime() }, parkNs = { ns ->
            waits.incrementAndGet()
            parked.countDown()
            java.util.concurrent.locks.LockSupport.parkNanos(ns)
        })
        loop.start()
        try {
            assertTrue(parked.await(2, TimeUnit.SECONDS))
            Thread.sleep(1000)
            assertTrue("Idle batching should avoid thousands of wakeups per second: ${waits.get()}", waits.get() < 250)
            assertEquals(42, loop.executeSync(1000) { 42 })
        } finally { loop.stop() }
    }

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
            // Normal pacing may intentionally owe up to one 8ms batch at the
            // exact wall-second boundary. Cross that boundary before sampling.
            Thread.sleep(100)
            // Read on the actual worker, after the delayed renderer has finished and debt is repaid.
            val serviced = loop.executeSync { it.getState()["DBG_INT_COUNT"] }
            assertTrue(stalled.get())
            assertEquals(6, serviced)
        } finally { loop.stop() }
    }
}
