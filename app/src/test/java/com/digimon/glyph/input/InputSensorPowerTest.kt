package com.digimon.glyph.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.SensorEvent
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

/** Exercises the real controller against recorded Android registrations and a virtual main loop. */
class InputSensorPowerTest {
    private class Scheduler : Handler(Looper.getMainLooper()) {
        var now = 1000L
        private val pending = mutableListOf<Pair<Long, Runnable>>()
        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            pending.add(now + delayMillis to runnable)
            return true
        }
        override fun removeCallbacks(runnable: Runnable) { pending.removeAll { it.second === runnable } }
        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = pending.filter { it.first <= target }.minByOrNull { it.first } ?: break
                pending.remove(next)
                now = next.first
                next.second.run()
            }
            now = target
        }
    }
    private class Fixture(fallback: Boolean = false) {
        val scheduler = Scheduler()
        val sensors = mock(SensorManager::class.java)
        val rotation = mock(Sensor::class.java)
        val motion = mock(Sensor::class.java)
        val pins = mutableListOf<String>()
        val modes = mutableListOf<Boolean>()
        val controller: InputController
        init {
            val context = mock(Context::class.java)
            `when`(context.applicationContext).thenReturn(context)
            `when`(context.getSystemService(Context.SENSOR_SERVICE)).thenReturn(sensors)
            `when`(sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)).thenReturn(rotation)
            if (fallback) {
                `when`(sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)).thenReturn(motion)
            } else {
                `when`(sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)).thenReturn(motion)
            }
            `when`(motion.type).thenReturn(if (fallback) Sensor.TYPE_ACCELEROMETER else Sensor.TYPE_LINEAR_ACCELERATION)
            controller = InputController(context, scheduler) { scheduler.now }.apply {
                onPinSet = { port, pin, level -> pins.add("$port:$pin=$level") }
                onPinRelease = { port, pin -> pins.add("$port:$pin=release") }
                onInputRateModeChanged = { modes.add(it) }
            }
            clearInvocations(sensors)
        }
        fun registered(delay: Int, times: Int) {
            verify(sensors, times(times)).registerListener(controller, rotation, delay)
            verify(sensors, times(times)).registerListener(controller, motion, delay)
        }
    }

    @Test fun quietPowerModeStopsSensorsAndGlyphInputRestoresThemWithoutSensorCallbacks() {
        val f = Fixture()
        f.controller.setPowerSaveEnabled(true)
        f.controller.start()
        verifyNoInteractions(f.sensors)
        f.controller.onGlyphButtonDown()
        f.registered(SensorManager.SENSOR_DELAY_GAME, 1)
        assertEquals(listOf("K0:1=0"), f.pins)
        f.controller.onGlyphButtonUp()
        assertEquals("K0:1=release", f.pins.last())
        f.scheduler.advance(60_000)
        verify(f.sensors).unregisterListener(f.controller)
        assertEquals(listOf(true, false), f.modes)
        f.controller.onGlyphButtonDown()
        f.registered(SensorManager.SENSOR_DELAY_GAME, 2)
        assertEquals("K0:1=0", f.pins.last())
    }

    @Test fun changingPowerPreferenceInIdleAppliesImmediatelyAndRepeatedValuesDoNoWork() {
        val f = Fixture()
        f.controller.start()
        f.registered(SensorManager.SENSOR_DELAY_NORMAL, 1)
        f.controller.setPowerSaveEnabled(true)
        verify(f.sensors).unregisterListener(f.controller)
        clearInvocations(f.sensors)
        f.controller.setPowerSaveEnabled(true)
        verifyNoInteractions(f.sensors)
        f.controller.setPowerSaveEnabled(false)
        f.registered(SensorManager.SENSOR_DELAY_NORMAL, 1)
    }

    @Test fun heldGlyphButtonKeepsControlsArmedAndReleaseStartsANewIdleWindow() {
        val f = Fixture()
        f.controller.setPowerSaveEnabled(true)
        f.controller.start()
        f.controller.onGlyphButtonDown()
        f.scheduler.advance(120_000)
        verify(f.sensors, never()).unregisterListener(f.controller)
        f.controller.onGlyphButtonUp()
        f.scheduler.advance(59_999)
        verify(f.sensors, never()).unregisterListener(f.controller)
        f.scheduler.advance(1)
        verify(f.sensors).unregisterListener(f.controller)
        assertEquals(listOf("K0:1=0", "K0:1=release"), f.pins)
    }

    @Test fun normalModeRetainsNormalIdleSamplingAndStopCancelsItsTimeoutAndReleasesButtons() {
        val f = Fixture()
        f.controller.start()
        f.registered(SensorManager.SENSOR_DELAY_NORMAL, 1)
        f.controller.onGlyphButtonDown()
        f.registered(SensorManager.SENSOR_DELAY_GAME, 1)
        f.controller.onGlyphButtonUp()
        f.scheduler.advance(60_000)
        f.registered(SensorManager.SENSOR_DELAY_NORMAL, 2)
        f.controller.onGlyphButtonDown()
        f.controller.stop()
        assertTrue(f.pins.contains("K0:1=release"))
        clearInvocations(f.sensors)
        f.scheduler.advance(120_000)
        verifyNoInteractions(f.sensors)
    }
    @Test fun fallbackAccelerometerAlsoStopsInIdleAndReturnsOnGlyphInput() {
        val f = Fixture(fallback = true)
        f.controller.setPowerSaveEnabled(true)
        f.controller.start()
        verifyNoInteractions(f.sensors)
        f.controller.onGlyphButtonDown()
        f.registered(SensorManager.SENSOR_DELAY_GAME, 1)
        f.controller.onGlyphButtonUp()
        f.scheduler.advance(60_000)
        verify(f.sensors).unregisterListener(f.controller)
    }

    @Test fun resumedMotionStartsFreshAndStillRecognizesDeliberateFlicks() {
        val f = Fixture()
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        fun sample(x: Float) {
            val event = unsafeClass.getMethod("allocateInstance", Class::class.java)
                .invoke(unsafe, SensorEvent::class.java) as SensorEvent
            SensorEvent::class.java.getDeclaredField("values").apply { isAccessible = true }
                .set(event, floatArrayOf(x, 0f, 0f))
            event.sensor = f.motion
            f.controller.onSensorChanged(event)
        }
        f.controller.setPowerSaveEnabled(true)
        f.controller.start()
        f.controller.onGlyphButtonDown()
        f.controller.onGlyphButtonUp()
        sample(0f) // fresh baseline
        sample(10f)
        f.scheduler.advance(30)
        sample(10f)
        f.scheduler.advance(30)
        sample(-20f) // deliberate rebound -> C
        assertEquals(1, f.pins.count { it == "K0:0=0" })
        f.scheduler.advance(60_000)
        f.controller.onGlyphButtonDown()
        f.controller.onGlyphButtonUp()
        sample(50f) // first resumed sample must not start a gesture from old filters
        f.scheduler.advance(30)
        sample(-50f)
        assertEquals(1, f.pins.count { it == "K0:0=0" })
        assertEquals(0, f.pins.count { it == "K0:2=0" })
    }

    @Test fun cancellingInputInvalidatesOldComboReleaseWithoutReleasingANewerPress() {
        val f = Fixture()
        f.controller.start()
        assertTrue(f.controller.triggerComboTap(1)) // A+B
        f.controller.cancelPendingInput()
        f.scheduler.advance(20)
        assertTrue(f.controller.triggerComboTap(2)) // A+C
        f.pins.clear()
        f.scheduler.advance(75) // old combo deadline, new combo still held
        assertTrue(f.pins.isEmpty())
        f.scheduler.advance(20)
        assertTrue(f.pins.contains("K0:2=release"))
        assertTrue(f.pins.contains("K0:0=release"))
    }

}
