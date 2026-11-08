package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

/** Exercises the real worker with an elapsed clock that includes deep sleep. */
class StandbyCatchUpTest {
    @Test fun eightHoursSuspendServicesEveryFirmwareInterruptWithoutRebase() {
        val now=AtomicLong(0)
        val core=TimingRom.core()
        val reference=TimingRom.core().apply {idleSkippingEnabled=false}
        val parked=CountDownLatch(1)
        val loop=EmulatorLoop(core,{},now::get,parkNs={parked.countDown();LockSupport.parkNanos(it)})
        loop.realTimeCatchUpEnabled=true;loop.setBackgroundIdle(true);loop.start()
        try {
            assertTrue(parked.await(2,TimeUnit.SECONDS))
            now.set(TimeUnit.HOURS.toNanos(8))
            val expectedCycles=reference.runForCycles(8*3600.0*reference.clockFrequency)
            val actual=loop.executeSync(20_000){it.getState()}!!
            ClassicFirmwareClockTest.assertUnchanged(reference.getState(),actual,skipDebug=true)
            assertEquals(reference.getState()["DBG_IT1_SET"],actual["DBG_IT1_SET"])
            assertEquals(reference.getState()["DBG_INT_COUNT"],actual["DBG_INT_COUNT"])
            assertTrue(expectedCycles>=8*3600.0*core.clockFrequency)
        } finally {loop.stop()}
    }
    @Test fun periodicCatchUpsPreserveTimestampedInputOrderAndSleepBetweenAlarms() {
        val now=AtomicLong(0);val core=TimingRom.core();val parked=CountDownLatch(1)
        val loop=EmulatorLoop(core,{},now::get,parkNs={parked.countDown();LockSupport.parkNanos(it)})
        val order=mutableListOf<Int>()
        loop.realTimeCatchUpEnabled=true;loop.setBackgroundIdle(true);loop.start()
        try {
            assertTrue(parked.await(2,TimeUnit.SECONDS))
            // Deliberately receive batched sensor callbacks out of timestamp order.
            loop.enqueueAtTime(TimeUnit.MINUTES.toNanos(12),wake=false){order.add(12)}
            loop.enqueueAtTime(TimeUnit.MINUTES.toNanos(3),wake=false){order.add(3)}
            now.set(TimeUnit.MINUTES.toNanos(15))
            val complete=CountDownLatch(1)
            loop.enqueueAtTime {order.add(15);complete.countDown()}
            assertTrue(complete.await(10,TimeUnit.SECONDS))
            assertEquals(listOf(3,12,15),order)
            val first=loop.executeSync(10_000){it.getState()}!!
            now.set(TimeUnit.MINUTES.toNanos(30))
            loop.requestCatchUp()
            val second=loop.executeSync(10_000){it.getState()}!!
            assertEquals(900,(second["DBG_IT1_SET"] as Int)-(first["DBG_IT1_SET"] as Int))
        } finally {loop.stop()}
    }
    @Test fun screenOffWorkerWakeupsDropFromCoalescedPacingToZeroPeriodicPolling() {
        fun waits(idle:Boolean):Int {
            val now=AtomicLong(0);val count=java.util.concurrent.atomic.AtomicInteger()
            val done=CountDownLatch(1)
            lateinit var loop:EmulatorLoop
            loop=EmulatorLoop(TimingRom.core(),{},{now.addAndGet(1000)},parkNs={ns ->
                count.incrementAndGet()
                if(ns==Long.MAX_VALUE) {done.countDown();LockSupport.parkNanos(ns)}
                else {now.addAndGet(ns);if(now.get()>=TimeUnit.SECONDS.toNanos(10)){done.countDown();LockSupport.parkNanos(Long.MAX_VALUE)}}
            })
            loop.realTimeCatchUpEnabled=true;loop.setBackgroundIdle(idle);loop.start()
            try {assertTrue(done.await(10,TimeUnit.SECONDS));return count.get()} finally {loop.stop()}
        }
        val before=waits(false);val after=waits(true)
        println("Fake-clock worker waits over 10 seconds: before=$before (${before/10.0}/s), after=$after (one indefinite standby park, zero periodic worker wakes)")
        assertTrue(before>500);assertEquals(1,after)
    }
}
