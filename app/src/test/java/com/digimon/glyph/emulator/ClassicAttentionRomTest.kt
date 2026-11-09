package com.digimon.glyph.emulator

import android.app.AlarmManager
import android.app.PendingIntent
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mockito.Mockito.*
import java.io.File

class ClassicAttentionRomTest {
    private fun cpuNow(): Long {
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        return Class.forName("java.lang.management.ThreadMXBean").getMethod("getCurrentThreadCpuTime").invoke(bean) as Long
    }

    @Test fun everyCallAcrossSixRomsAndTwentyFourIdleHoursReceivesALiveWakeWithinTenSeconds() {
        val directory = System.getenv("CLASSIC_ROM_DIR")
        assumeTrue("Set CLASSIC_ROM_DIR for 24-hour care-call validation", directory != null)
        for (device in listOf("DigimonV1JA", "DigimonV1JB", "DigimonV1ES", "DigimonV2J", "DigimonV3J", "DigimonV4J")) {
            val bytes = File(directory, "$device.bin").readBytes()
            val seed = E0C6200(bytes)
            seed.runForCycles(seed.clockFrequency * 2.0)
            fun press(pin: Int) {
                seed.pinSet("K0", pin, 0); seed.runForCycles(seed.clockFrequency * .2)
                seed.pinRelease("K0", pin); seed.runForCycles(seed.clockFrequency * .3)
            }
            press(1); press(2); press(0)
            repeat(1000) { if (!seed.isFirmwareClockBoundary()) seed.runForCycles(seed.clockFrequency * .001) }
            assertTrue("$device normal firmware idle", seed.isFirmwareClockBoundary())
            seed.overrideFirmwareClock(ClassicFirmwareClock.Time(7, 0))
            val snapshot = seed.getState()
            val reference = E0C6200(bytes).apply { restoreState(snapshot); idleSkippingEnabled = false }
            val live = E0C6200(bytes).apply { restoreState(snapshot) }
            val dayMs = 24 * 60 * 60_000L
            val frequency = live.clockFrequency.toDouble()
            val expected = mutableListOf<Double>()
            var elapsed = 0.0
            var previous = reference.careCallActive()
            while (elapsed < dayMs * frequency / 1000) {
                elapsed += reference.clock()
                val current = reference.careCallActive()
                if (current && !previous) {
                    assertTrue(reference.getCurrentDigimonState(device)!!.needsAttention)
                    expected.add(elapsed * 1000 / frequency)
                }
                previous = current
            }
            assertTrue("$device must actually issue care calls", expected.isNotEmpty())

            val manager = mock(AlarmManager::class.java)
            val pending = mock(PendingIntent::class.java)
            `when`(manager.canScheduleExactAlarms()).thenReturn(true)
            var alarmAt = 0L
            doAnswer { invocation -> alarmAt = invocation.getArgument(1); null }.`when`(manager)
                .setExactAndAllowWhileIdle(anyInt(), anyLong(), eq(pending))
            val alarm = ClassicWakeAlarm(manager, pending, 35)
            val notifications = mutableListOf<Double>()
            val liveWindows = mutableListOf<Pair<Long, Long>>()
            var cycles = 0.0
            var notificationLevel = live.careCallActive()
            fun observe(audible: Boolean) {
                val current = live.careCallActive()
                if (!current) notificationLevel = false
                if (audible && current && !notificationLevel) {
                    notifications.add(cycles * 1000 / frequency)
                    notificationLevel = true
                }
            }
            fun advance(toMs: Long, audible: Boolean) {
                val target = toMs * frequency / 1000
                while (cycles < target) {
                    cycles += live.clock(minOf(target - cycles, frequency * .05))
                    observe(audible)
                }
            }
            var now = 0L
            var lookaheadCpu = 0L
            var horizonMs = 0L
            while (now < dayMs) {
                val before = ClassicPredictiveStandbyTest.hash(live)
                val start = cpuNow()
                val prediction = ClassicAttentionPredictor.predict(live)!!
                lookaheadCpu += cpuNow() - start
                horizonMs += prediction.offsetMs
                assertEquals("$device lookahead cannot change any live field", before, ClassicPredictiveStandbyTest.hash(live))
                alarm.schedule(now, (cycles * 1000 / frequency).toLong(), prediction, 15)
                if (alarmAt > dayMs) { advance(dayMs, false); break }
                now = alarmAt
                advance(now, false) // Historical audio/notifications are never replayed.
                observe(true)
                val until = minOf(dayMs, now + 60_000)
                liveWindows.add(now to until)
                advance(until, true)
                now = until
            }
            assertEquals("$device exactly one notification per new onset", expected.size, notifications.size)
            var worst = 0.0
            expected.zip(notifications).forEach { (onset, notified) ->
                val delay = notified - onset
                assertTrue("$device call $onset notified $notified", delay >= -2.0 && delay <= 10_000)
                assertTrue("$device onset must be in an actual live wake", liveWindows.any { notified >= it.first - 2 && notified <= it.second + 2 })
                worst = maxOf(worst, delay)
            }
            val cpuPerHour = lookaheadCpu / 1e9 * 3_600_000 / horizonMs
            println("$device 24h: ${expected.size} care onsets, ${notifications.size} timely notifications, ${liveWindows.size} live wakes; worst delay ${worst}ms; lookahead $cpuPerHour CPU seconds/hour of scanned horizon")
        }
    }
}
