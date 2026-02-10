package com.digimon.glyph.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.digimon.glyph.emulator.E0C6200

/**
 * Maps Nothing Phone 3 motion + Glyph Button to Digimon A/B/C buttons.
 *
 * Digimon V3 button mapping on K0 port:
 *   Pin 2 = Button A (top/left)    — Tilt left (hold)
 *   Pin 1 = Button B (center)      — Glyph Button hold
 *   Pin 0 = Button C (bottom/right) — Tilt right (hold)
 *
 * Buttons are active-low (level 0 = pressed).
 *
 * A/C use a small dwell + hysteresis:
 * - tilt past activate threshold for a short time -> press
 * - remain held while tilted
 * - release when returned near neutral
 */
class InputController(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "DigimonInput"
        private const val TILT_ACTIVATE_DEG = 24f
        private const val TILT_RELEASE_DEG = 14f
        private const val TILT_DWELL_MS = 120L
        private const val REARM_NEUTRAL_DEG = 10f
        private const val REARM_NEUTRAL_MS = 140L
        private const val ACTIVATION_COOLDOWN_MS = 220L
        // Phone posture with Glyph usage can rotate perceived left/right.
        // Use pitch axis here so side-tilt (for this posture) controls A/C.
        private const val USE_PITCH_AXIS = true
        private const val INVERT_TILT_AXIS = false
        private const val B_HOLD_ARM_MS = 200L

        // K0 port pins for Digimon buttons (active-low: 0=pressed)
        private const val BUTTON_A_PIN = 2
        private const val BUTTON_B_PIN = 1
        private const val BUTTON_C_PIN = 0
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator? =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator

    private var emulator: E0C6200? = null

    // Tilt-hold state
    private var buttonAActive = false
    private var buttonCActive = false
    private var pendingALeftSinceMs: Long = 0L
    private var pendingCRightSinceMs: Long = 0L
    private var neutralSinceMs: Long = 0L
    private var directionalArmed = true
    private var lastDirectionalActivationMs: Long = 0L

    // Glyph button state
    private var glyphPhysicalDown = false
    private var buttonBActive = false

    private val armBHold = Runnable {
        if (glyphPhysicalDown && !buttonBActive) {
            buttonBActive = true
            emulator?.pinSet("K0", BUTTON_B_PIN, 0)
        }
    }

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
        glyphPhysicalDown = true
        mainHandler.removeCallbacks(armBHold)
        mainHandler.postDelayed(armBHold, B_HOLD_ARM_MS)
    }

    /** Called when the Glyph Button is released (from service EVENT_ACTION_UP). */
    fun onGlyphButtonUp() {
        glyphPhysicalDown = false
        mainHandler.removeCallbacks(armBHold)
        if (buttonBActive) {
            buttonBActive = false
            emulator?.pinRelease("K0", BUTTON_B_PIN)
        }
    }

    private fun releaseAll() {
        mainHandler.removeCallbacks(armBHold)
        glyphPhysicalDown = false
        pendingALeftSinceMs = 0L
        pendingCRightSinceMs = 0L
        neutralSinceMs = 0L
        directionalArmed = true
        lastDirectionalActivationMs = 0L
        if (buttonAActive) {
            buttonAActive = false
            emulator?.pinRelease("K0", BUTTON_A_PIN)
        }
        if (buttonCActive) {
            buttonCActive = false
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

        // Convert rotation vector to orientation angles.
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // Pitch = orientation[1], Roll = orientation[2], in radians.
        val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
        val rawTiltDeg = if (USE_PITCH_AXIS) pitchDeg else rollDeg
        val tiltDeg = if (INVERT_TILT_AXIS) -rawTiltDeg else rawTiltDeg
        val now = System.currentTimeMillis()

        // Rearm only after the device settles near center for a short dwell.
        if (kotlin.math.abs(tiltDeg) <= REARM_NEUTRAL_DEG) {
            if (neutralSinceMs == 0L) neutralSinceMs = now
            if (!directionalArmed && now - neutralSinceMs >= REARM_NEUTRAL_MS) {
                directionalArmed = true
            }
        } else {
            neutralSinceMs = 0L
        }

        // Hysteresis release to neutral
        if (buttonAActive && tiltDeg > -TILT_RELEASE_DEG) {
            releaseA()
        }
        if (buttonCActive && tiltDeg < TILT_RELEASE_DEG) {
            releaseC()
        }

        // Left tilt -> A
        val inCooldown = now - lastDirectionalActivationMs < ACTIVATION_COOLDOWN_MS

        if (directionalArmed && !inCooldown && !buttonAActive && tiltDeg <= -TILT_ACTIVATE_DEG) {
            if (pendingALeftSinceMs == 0L) pendingALeftSinceMs = now
            if (now - pendingALeftSinceMs >= TILT_DWELL_MS) {
                activateA()
            }
        } else {
            pendingALeftSinceMs = 0L
        }

        // Right tilt -> C
        if (directionalArmed && !inCooldown && !buttonCActive && tiltDeg >= TILT_ACTIVATE_DEG) {
            if (pendingCRightSinceMs == 0L) pendingCRightSinceMs = now
            if (now - pendingCRightSinceMs >= TILT_DWELL_MS) {
                activateC()
            }
        } else {
            pendingCRightSinceMs = 0L
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun activateA() {
        if (buttonAActive) return
        if (buttonCActive) releaseC()
        buttonAActive = true
        directionalArmed = false
        neutralSinceMs = 0L
        lastDirectionalActivationMs = System.currentTimeMillis()
        Log.d(TAG, "activate A tilt")
        emulator?.pinSet("K0", BUTTON_A_PIN, 0)
        pendingALeftSinceMs = 0L
        pendingCRightSinceMs = 0L
        vibrateTiltConfirm()
    }

    private fun activateC() {
        if (buttonCActive) return
        if (buttonAActive) releaseA()
        buttonCActive = true
        directionalArmed = false
        neutralSinceMs = 0L
        lastDirectionalActivationMs = System.currentTimeMillis()
        Log.d(TAG, "activate C tilt")
        emulator?.pinSet("K0", BUTTON_C_PIN, 0)
        pendingALeftSinceMs = 0L
        pendingCRightSinceMs = 0L
        vibrateTiltConfirm()
    }

    private fun releaseA() {
        if (!buttonAActive) return
        buttonAActive = false
        emulator?.pinRelease("K0", BUTTON_A_PIN)
    }

    private fun releaseC() {
        if (!buttonCActive) return
        buttonCActive = false
        emulator?.pinRelease("K0", BUTTON_C_PIN)
    }

    private fun vibrateTiltConfirm() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(16L, 120))
    }
}
