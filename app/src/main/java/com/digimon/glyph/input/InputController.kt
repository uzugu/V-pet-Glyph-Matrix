package com.digimon.glyph.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.digimon.glyph.emulator.E0C6200

/**
 * Maps Nothing Phone 3 gyroscope tilt + Glyph Button to Digimon A/B/C buttons.
 *
 * Digimon V3 button mapping on K0 port:
 *   Pin 2 = Button A (top/left)    — Tilt left
 *   Pin 1 = Button B (center)      — Glyph Button tap
 *   Pin 0 = Button C (bottom/right) — Tilt right
 *
 * Buttons are active-low (level 0 = pressed).
 *
 * Tilt detection uses TYPE_GAME_ROTATION_VECTOR (gyro+accel fused, no mag drift).
 * Hysteresis: activate at 25 deg, release at 15 deg, 150ms debounce.
 */
class InputController(context: Context) : SensorEventListener {

    companion object {
        private const val ACTIVATE_DEG = 25f
        private const val RELEASE_DEG = 15f
        private const val DEBOUNCE_MS = 150L

        // K0 port pins for Digimon buttons (active-low: 0=pressed)
        private const val BUTTON_A_PIN = 2
        private const val BUTTON_B_PIN = 1
        private const val BUTTON_C_PIN = 0
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private var emulator: E0C6200? = null

    // Tilt state
    private var tiltLeftActive = false
    private var tiltRightActive = false
    private var lastTiltChangeA = 0L
    private var lastTiltChangeC = 0L

    // Glyph button state
    private var buttonBActive = false

    fun attach(emu: E0C6200) {
        emulator = emu
    }

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        // Release all buttons
        releaseAll()
    }

    /** Called when the Glyph Button is pressed (from service EVENT_ACTION_DOWN). */
    fun onGlyphButtonDown() {
        if (!buttonBActive) {
            buttonBActive = true
            emulator?.pinSet("K0", BUTTON_B_PIN, 0)
        }
    }

    /** Called when the Glyph Button is released (from service EVENT_ACTION_UP). */
    fun onGlyphButtonUp() {
        if (buttonBActive) {
            buttonBActive = false
            emulator?.pinRelease("K0", BUTTON_B_PIN)
        }
    }

    private fun releaseAll() {
        if (tiltLeftActive) {
            tiltLeftActive = false
            emulator?.pinRelease("K0", BUTTON_A_PIN)
        }
        if (tiltRightActive) {
            tiltRightActive = false
            emulator?.pinRelease("K0", BUTTON_C_PIN)
        }
        if (buttonBActive) {
            buttonBActive = false
            emulator?.pinRelease("K0", BUTTON_B_PIN)
        }
    }

    // ========== SensorEventListener ==========

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return

        // Convert rotation vector to orientation angles
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // Roll = orientation[2], in radians — positive = tilt right, negative = tilt left
        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
        val now = System.currentTimeMillis()

        // Button A: tilt left (negative roll)
        if (!tiltLeftActive && rollDeg < -ACTIVATE_DEG && now - lastTiltChangeA > DEBOUNCE_MS) {
            tiltLeftActive = true
            lastTiltChangeA = now
            emulator?.pinSet("K0", BUTTON_A_PIN, 0)
        } else if (tiltLeftActive && rollDeg > -RELEASE_DEG && now - lastTiltChangeA > DEBOUNCE_MS) {
            tiltLeftActive = false
            lastTiltChangeA = now
            emulator?.pinRelease("K0", BUTTON_A_PIN)
        }

        // Button C: tilt right (positive roll)
        if (!tiltRightActive && rollDeg > ACTIVATE_DEG && now - lastTiltChangeC > DEBOUNCE_MS) {
            tiltRightActive = true
            lastTiltChangeC = now
            emulator?.pinSet("K0", BUTTON_C_PIN, 0)
        } else if (tiltRightActive && rollDeg < RELEASE_DEG && now - lastTiltChangeC > DEBOUNCE_MS) {
            tiltRightActive = false
            lastTiltChangeC = now
            emulator?.pinRelease("K0", BUTTON_C_PIN)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
