package com.digimon.glyph.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EmulatorLoopClockTest {
    @Test fun activeBattleClockRetainsIndividualFastOscillatorHaltSlots() {
        val now=java.util.concurrent.atomic.AtomicLong(0)
        val core=TimingRom.core().apply {restoreState(getState() + ("CTRL_OSC" to E0C6200.IO_CLKCHG))}
        val done=CountDownLatch(1)
        var previous=0.0
        var largest=0.0
        val loop=EmulatorLoop(core,{},{now.addAndGet(1000)},parkNs={now.addAndGet(it.coerceAtMost(1_000_000))})
        loop.virtualTickEnabled=true
        loop.onVirtualTick={tick ->
            largest=maxOf(largest,tick-previous);previous=tick
            if(tick>=20_000_000)done.countDown()
        }
        loop.start()
        try {assertTrue(done.await(2,TimeUnit.SECONDS))} finally {loop.stop()}
        assertTrue("Active link callbacks must retain seven-cycle HALT slots: $largest",largest<=7e9/core.clockFrequency+0.001)
    }

    @Test fun standbyStepsQueueWithoutWakingFirmwareAndDrainAfterInput() {
        val now = java.util.concurrent.atomic.AtomicLong(0)
        val sleeping = AtomicBoolean(true)
        val base = TimingRom.core()
        val pulses = com.digimon.glyph.input.StepPulses()
        var clocks = 0
        val edges = mutableListOf<Boolean>()
        val core = object : EmulatorCore by base {
            override fun clock(budgetCycles: Double): Double = clock()
            override val clockFrequency = 2_200_000
            override fun clock(): Double {
                clocks++
                pulses.advance(2200) { edges += it }
                return 2200.0
            }
            override fun isDisplaySleeping() = sleeping.get()
        }
        val paused = CountDownLatch(1)
        val resumed = CountDownLatch(1)
        val loop = EmulatorLoop(core, {}, now::get)
        loop.onPauseChanged = { if (it) paused.countDown() else resumed.countDown() }
        loop.start()
        try {
            assertTrue(paused.await(2, TimeUnit.SECONDS))
            now.set(TimeUnit.HOURS.toNanos(12))
            repeat(300) { assertTrue(loop.enqueue { pulses.enqueue() }) }
            assertEquals(300, loop.executeSync { pulses.pending })
            assertEquals(0, loop.executeSync { clocks })
            assertTrue(loop.isPaused)
            assertTrue(edges.isEmpty())
            sleeping.set(false)
            loop.wakeForInput()
            assertTrue(resumed.await(2, TimeUnit.SECONDS))
            now.addAndGet(TimeUnit.MILLISECONDS.toNanos(61_000))
            assertTrue(loop.executeSyncWhen(2000, { pulses.pending == 0 && pulses.phase == 0 }) { true } == true)
            assertEquals(List(300) { listOf(true, false) }.flatten(), loop.executeSync { edges.toList() })
        } finally { loop.stop() }
    }

    @Test fun backgroundPauseRunsNoFirmwareAndDoesNotCatchUpHiddenHours() {
        val now = java.util.concurrent.atomic.AtomicLong(0)
        val base = TimingRom.core()
        var clocks = 0
        var cycles = 0.0
        val core = object : EmulatorCore by base {
            override fun clock(budgetCycles: Double): Double = clock()
            override fun clock(): Double = base.clock().also { clocks++; cycles += it }
        }
        val parked = CountDownLatch(1)
        val resumed = CountDownLatch(1)
        val loop = EmulatorLoop(core, {}, now::get, parkNs = {
            parked.countDown()
            java.util.concurrent.locks.LockSupport.parkNanos(it)
        })
        loop.onPauseChanged = { if (!it) resumed.countDown() }
        loop.setBackgroundPaused(true)
        loop.start()
        try {
            assertTrue(parked.await(2, TimeUnit.SECONDS))
            now.set(TimeUnit.HOURS.toNanos(12))
            assertEquals(0, loop.executeSync { clocks })
            loop.setBackgroundPaused(false)
            assertTrue(resumed.await(2, TimeUnit.SECONDS))
            now.addAndGet(TimeUnit.MILLISECONDS.toNanos(100))
            assertTrue(loop.executeSyncWhen(2000, { cycles >= core.clockFrequency * 0.099 }) { true } == true)
            val virtualMs = loop.executeSync { cycles / core.clockFrequency * 1000 }!!
            assertTrue("Resumed firmware must owe only the visible 100ms: $virtualMs", virtualMs in 99.0..101.0)
        } finally { loop.stop() }
    }

    @Test fun sleepingLcdStaysParkedForMaintenanceAndRealInputWakesIt() {
        val now = java.util.concurrent.atomic.AtomicLong(0)
        val sleeping = AtomicBoolean(true)
        val base = TimingRom.core()
        var cycles = 0.0
        val edges = mutableListOf<Boolean>()
        val core = object : EmulatorCore by base {
            override fun clock(budgetCycles: Double): Double = clock()
            override fun clock(): Double = base.clock().also { cycles += it }
            override fun isDisplaySleeping() = sleeping.get()
            override fun pinSet(port: String, pin: Int, level: Int) { edges += true }
            override fun pinRelease(port: String, pin: Int) { edges += false }
        }
        val paused = CountDownLatch(1)
        val resumed = CountDownLatch(1)
        val loop = EmulatorLoop(core, {}, now::get)
        loop.onPauseChanged = { if (it) paused.countDown() else resumed.countDown() }
        loop.start()
        try {
            assertTrue(paused.await(2, TimeUnit.SECONDS))
            now.set(TimeUnit.HOURS.toNanos(12))
            assertEquals(0.0, loop.executeSync { cycles }!!, 0.0)
            assertTrue(loop.isPaused)
            sleeping.set(false)
            assertTrue(loop.tapButton("K0", 2, 0) { true })
            assertTrue(resumed.await(2, TimeUnit.SECONDS))
            now.addAndGet(TimeUnit.MILLISECONDS.toNanos(130))
            assertTrue(loop.executeSyncWhen(2000, { edges.size == 2 }) { true } == true)
            assertEquals(listOf(true, false), loop.executeSync { edges.toList() })
            val virtualMs = loop.executeSync { cycles / core.clockFrequency * 1000 }!!
            assertTrue("Wake must not catch up sleep time: $virtualMs", virtualMs in 120.0..131.0)
        } finally { loop.stop() }
    }

    @Test fun queuedWidgetTapsSurviveAStalledWorkerWithFirmwareHoldAndReleaseTimes() {
        val base = TimingRom.core()
        var cycles = 0.0
        val edges = mutableListOf<Pair<Boolean, Double>>()
        val finished = CountDownLatch(4)
        val core = object : EmulatorCore by base {
            override fun clock(budgetCycles: Double): Double = clock()
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
