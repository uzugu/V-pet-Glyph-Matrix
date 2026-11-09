package com.digimon.glyph.emulator

import android.app.AlarmManager
import android.app.PendingIntent
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

class ClassicPredictiveStandbyTest {
    companion object {
        /** Original test ROM: toggle the care bit on each serviced 1 Hz interrupt. */
        fun callingCore(): E0C6200 {
            val rom = ByteArray(8192)
            fun op(pc: Int, value: Int) { rom[pc * 2] = (value shr 8).toByte(); rom[pc * 2 + 1] = value.toByte() }
            op(0x100, 0x020)
            intArrayOf(0xe0e, 0xe80, 0xb00, 0xd21, 0xe0f, 0xe80, 0xb00, 0xec2, 0xf48, 0xfdf)
                .forEachIndexed { i, value -> op(0x102 + i, value) }
            op(0x120, 0xff8); op(0x121, 0x020)
            return E0C6200(rom).apply {
                restoreState(getState() + mapOf("IF" to 1, "EIT" to E0C6200.IO_IT1, "SP" to 0xf0, "PC" to 0x120, "NPC" to 0x100))
            }
        }
        fun hash(core: E0C6200) = MessageDigest.getInstance("SHA-256").digest(Gson().toJson(core.getState()).toByteArray()).toList()
    }

    @Test fun lookaheadLeavesEveryLiveStateFieldAndCallbacksUntouchedAndSkipsExistingCall() {
        val live = callingCore()
        var callbacks = 0
        live.onBuzzerChange = { _, _, _ -> callbacks++ }
        live.onIoAccess = { _, _, _ -> callbacks++ }
        val before = hash(live)
        val first = ClassicAttentionPredictor.predict(live)!!
        assertEquals(1000.0, first.offsetMs.toDouble(), 30.0)
        assertTrue(first.onset)
        assertEquals(before, hash(live)); assertEquals(0, callbacks)
        live.runForCycles(live.clockFrequency * 1.2)
        assertTrue(live.careCallActive())
        val active = hash(live)
        val next = ClassicAttentionPredictor.predict(live)!!
        assertTrue("Wait through a clear and the following new onset", next.offsetMs in 1700..1900)
        assertEquals(active, hash(live))
        assertNull(ClassicAttentionPredictor.predict(live, cancelled = { true }))
        assertEquals(active, hash(live))
    }

    @Test fun sevenMinuteCallUsesExactElapsedWakeFiveSecondsEarlyAndDenialUsesPeriodicFallback() {
        val manager = mock(AlarmManager::class.java)
        val pending = mock(PendingIntent::class.java)
        val clock = AtomicLong(10_000)
        val alarms = ClassicWakeAlarm(manager, pending, 35)
        `when`(manager.canScheduleExactAlarms()).thenReturn(true)
        val prediction = ClassicAttentionPredictor.Prediction(7 * 60_000L, true)
        val exact = alarms.schedule(clock.get(), clock.get(), prediction, 15)
        assertEquals(425_000L, exact.atMs)
        verify(manager).setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, 425_000, pending)
        clock.set(exact.atMs)
        assertEquals(7 * 60_000 - 5000L, clock.get() - 10_000)
        `when`(manager.canScheduleExactAlarms()).thenReturn(false)
        val fallback = alarms.schedule(clock.get(), clock.get(), prediction, 15)
        assertFalse(fallback.exact)
        assertEquals(clock.get() + 15 * 60_000, fallback.atMs)
        verify(manager).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, fallback.atMs, pending)
        val horizon = StandbyWakePolicy.classic(0, 0, ClassicAttentionPredictor.Prediction(3600_000, false), true, 15)
        assertEquals(3600_000L, horizon.atMs)
    }

    @Test fun aQuietCoreSearchesToTheHorizonWithoutChangingLiveState() {
        val core = callingCore().apply {
            restoreState(getState() + mapOf("EIT" to 0))
            idleSkippingEnabled = false
        }
        val before = hash(core)
        assertEquals(ClassicAttentionPredictor.Prediction(60_000, false),
            ClassicAttentionPredictor.predict(core, horizonMinutes = 1))
        assertEquals(before, hash(core))
        assertFalse(core.idleSkippingEnabled)
    }

    @Test fun revokedExactPermissionFallsBackWithoutFailingTheWorker() {
        val manager = mock(AlarmManager::class.java)
        val pending = mock(PendingIntent::class.java)
        `when`(manager.canScheduleExactAlarms()).thenReturn(true)
        doThrow(SecurityException("revoked")).`when`(manager).setExactAndAllowWhileIdle(anyInt(), anyLong(), any())
        assertFalse(ClassicWakeAlarm(manager, pending, 35).schedule(0, 0,
            ClassicAttentionPredictor.Prediction(420_000, true), 15).exact)
        verify(manager).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, 900_000, pending)
    }

    @Test fun dpowerPeriodicSchedulingDoesNotBecomePredictiveOrExact() {
        for (interval in listOf(15, 30, 120)) {
            val wake = StandbyWakePolicy.periodic(12345, interval)
            assertFalse(wake.exact)
            assertEquals(12345 + interval * 60_000L, wake.atMs)
        }
    }

    @Test fun standbyInputCancelsAndReplansOnTheActualWorker() {
        val core = callingCore()
        val now = AtomicLong(0)
        val asleep = CountDownLatch(1)
        val replanned = CountDownLatch(1)
        val revision = AtomicLong()
        val manager = mock(AlarmManager::class.java)
        val pending = mock(PendingIntent::class.java)
        `when`(manager.canScheduleExactAlarms()).thenReturn(true)
        val alarm = ClassicWakeAlarm(manager, pending, 35)
        val loop = EmulatorLoop(core, {}, { now.get() }, parkNs = { ns ->
            if (ns == Long.MAX_VALUE) { asleep.countDown(); LockSupport.parkNanos(1_000_000) }
            else now.addAndGet(ns)
        })
        loop.realTimeCatchUpEnabled = true; loop.setBackgroundIdle(true)
        loop.onInputQueued = { revision.incrementAndGet(); alarm.cancel() }
        loop.onInputApplied = {
            alarm.schedule(now.get() / 1_000_000, loop.firmwareElapsedRealtimeNs() / 1_000_000,
                ClassicAttentionPredictor.predict(core)!!, 15)
            replanned.countDown()
        }
        loop.start()
        try {
            assertTrue(asleep.await(3, TimeUnit.SECONDS))
            val oldRevision = revision.get()
            now.set(30_000_000_000)
            assertTrue(loop.enqueueAtTime(input = true) { it.pinSet("K0", 2, 0); it.pinRelease("K0", 2) })
            assertTrue(replanned.await(3, TimeUnit.SECONDS))
            assertTrue(revision.get() > oldRevision)
            verify(manager, atLeastOnce()).cancel(pending)
            verify(manager).setExactAndAllowWhileIdle(eq(AlarmManager.ELAPSED_REALTIME_WAKEUP), anyLong(), eq(pending))
        } finally { loop.stop() }
    }

    @Test fun alarmDebtIsMutedThenAtLeastSixtyFirmwareSecondsRunLiveAtFineCadence() {
        val now = AtomicLong(0)
        val core = callingCore()
        val asleep = CountDownLatch(1)
        val done = CountDownLatch(1)
        var started = 0L
        var ended = 0L
        var liveCalls = 0
        var historicalCalls = 0
        var largestLiveWait = 0L
        lateinit var loop: EmulatorLoop
        loop = EmulatorLoop(core, {}, { now.get() }, parkNs = { ns ->
            if (ns == Long.MAX_VALUE) { asleep.countDown(); LockSupport.parkNanos(1_000_000) }
            else { if (loop.isLiveWindow) largestLiveWait = maxOf(largestLiveWait, ns); now.addAndGet(ns) }
        })
        loop.realTimeCatchUpEnabled = true; loop.trackHistoricalReplay = true
        loop.backgroundCoalescing = true; loop.setBackgroundIdle(true)
        loop.onHistoricalReplayChanged = { historical -> if (!historical && loop.isLiveWindow) started = loop.firmwareElapsedRealtimeNs() }
        loop.onClassicAttentionChanged = { current ->
            if (current) { if (loop.isReplayingHistory) historicalCalls++ else liveCalls++ }
        }
        loop.onLiveWindowChanged = { live -> if (!live) { ended = loop.firmwareElapsedRealtimeNs(); done.countDown() } }
        loop.start()
        try {
            assertTrue(asleep.await(3, TimeUnit.SECONDS))
            now.set(7 * 60_000_000_000L)
            loop.beginHistoricalReplay()
            loop.enqueueAtTime { loop.holdLiveForFirmwareSeconds(60.0) }
            assertTrue(done.await(8, TimeUnit.SECONDS))
            assertTrue(historicalCalls > 0)
            assertTrue(liveCalls >= 29)
            assertTrue("Full live interval follows historical replay", ended - started >= 60_000_000_000L)
            assertTrue("No 500 ms batching during call playback", largestLiveWait < 30_000_000)
        } finally { loop.stop() }
    }
}
