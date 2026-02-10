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
import kotlin.math.abs

/**
 * Maps motion + Glyph Button to Digimon A/B/C buttons.
 *
 * Flick mode:
 * - detect a short acceleration impulse and opposite rebound (A/C)
 * - works in portrait/landscape by taking dominant X or Y device axis
 */
class InputController(context: Context) : SensorEventListener {

    private enum class FlickAxis { NONE, X, Y }

    companion object {
        private const val TAG = "DigimonInput"

        private const val FLICK_START_THRESHOLD = 4.2f
        private const val FLICK_REBOUND_THRESHOLD = 2.2f
        private const val FLICK_WINDOW_MS = 260L
        private const val FLICK_COOLDOWN_MS = 180L
        private const val FLICK_PRESS_MS = 85L
        private const val ACCEL_SMOOTH_ALPHA = 0.35f
        private const val ACCEL_GRAVITY_ALPHA = 0.82f

        // Sign mapping per dominant axis.
        private const val AXIS_X_POSITIVE_IS_C = true
        private const val AXIS_Y_POSITIVE_IS_C = true

        private const val DEBUG_PUSH_INTERVAL_MS = 80L
        private const val B_HOLD_ARM_MS = 200L

        private const val FLICK_VIBRATE_MS = 160L
        private const val FLICK_VIBRATE_AMPLITUDE = 255

        // K0 port pins for Digimon buttons (active-low: 0=pressed)
        private const val BUTTON_A_PIN = 2
        private const val BUTTON_B_PIN = 1
        private const val BUTTON_C_PIN = 0
    }

    private val appContext = context.applicationContext
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometerFallback =
        if (linearAccelerationSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator? =
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator

    private var emulator: E0C6200? = null

    // Button state
    private var buttonAActive = false
    private var buttonBActive = false
    private var buttonCActive = false
    private var buttonALatchedByB = false
    private var buttonCLatchedByB = false
    private var glyphPhysicalDown = false

    // Orientation debug values (from rotation vector)
    private var latestPitchDeg = 0f
    private var latestRollDeg = 0f

    // Acceleration values
    private var linearX = 0f
    private var linearY = 0f
    private var linearZ = 0f
    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f

    // Flick state
    private var pendingAxis = FlickAxis.NONE
    private var pendingDirection = 0
    private var pendingSinceMs = 0L
    private var lastFlickMs = 0L
    private var lastTriggerButton = "NONE"
    private var lastTriggerAtMs = 0L

    private var lastDebugPushMs = 0L

    private val armBHold = Runnable {
        if (glyphPhysicalDown && !buttonBActive) {
            buttonBActive = true
            emulator?.pinSet("K0", BUTTON_B_PIN, 0)
            lastTriggerButton = "B"
            lastTriggerAtMs = System.currentTimeMillis()
            publishDebugSnapshot(force = true)
        }
    }

    private val releaseATap = Runnable {
        if (buttonAActive) {
            releaseA()
        }
    }

    private val releaseCTap = Runnable {
        if (buttonCActive) {
            releaseC()
        }
    }

    fun attach(emu: E0C6200) {
        emulator = emu
    }

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        linearAccelerationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometerFallback?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        releaseAll()
    }

    fun onGlyphButtonDown() {
        glyphPhysicalDown = true
        mainHandler.removeCallbacks(armBHold)
        mainHandler.postDelayed(armBHold, B_HOLD_ARM_MS)
        publishDebugSnapshot(force = true)
    }

    fun onGlyphButtonUp() {
        glyphPhysicalDown = false
        mainHandler.removeCallbacks(armBHold)
        if (buttonBActive) {
            buttonBActive = false
            emulator?.pinRelease("K0", BUTTON_B_PIN)
        }
        if (buttonALatchedByB) {
            releaseA()
        }
        if (buttonCLatchedByB) {
            releaseC()
        }
        publishDebugSnapshot(force = true)
    }

    private fun releaseAll() {
        mainHandler.removeCallbacks(armBHold)
        mainHandler.removeCallbacks(releaseATap)
        mainHandler.removeCallbacks(releaseCTap)
        glyphPhysicalDown = false
        buttonAActive = false
        buttonBActive = false
        buttonCActive = false
        buttonALatchedByB = false
        buttonCLatchedByB = false
        emulator?.pinRelease("K0", BUTTON_A_PIN)
        emulator?.pinRelease("K0", BUTTON_B_PIN)
        emulator?.pinRelease("K0", BUTTON_C_PIN)

        pendingAxis = FlickAxis.NONE
        pendingDirection = 0
        pendingSinceMs = 0L
        lastFlickMs = 0L
        lastTriggerButton = "NONE"
        lastTriggerAtMs = 0L
        lastDebugPushMs = 0L
        publishDebugSnapshot(force = true)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                updateOrientation(event.values)
                publishDebugSnapshot()
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                linearX = event.values[0]
                linearY = event.values[1]
                linearZ = event.values[2]
                updateFilteredLinear()
                processFlick()
                publishDebugSnapshot()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                // Fallback when TYPE_LINEAR_ACCELERATION is unavailable.
                gravityX = ACCEL_GRAVITY_ALPHA * gravityX + (1f - ACCEL_GRAVITY_ALPHA) * event.values[0]
                gravityY = ACCEL_GRAVITY_ALPHA * gravityY + (1f - ACCEL_GRAVITY_ALPHA) * event.values[1]
                gravityZ = ACCEL_GRAVITY_ALPHA * gravityZ + (1f - ACCEL_GRAVITY_ALPHA) * event.values[2]
                linearX = event.values[0] - gravityX
                linearY = event.values[1] - gravityY
                linearZ = event.values[2] - gravityZ
                updateFilteredLinear()
                processFlick()
                publishDebugSnapshot()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateOrientation(values: FloatArray) {
        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        latestPitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
        latestRollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
    }

    private fun updateFilteredLinear() {
        filteredX += (linearX - filteredX) * ACCEL_SMOOTH_ALPHA
        filteredY += (linearY - filteredY) * ACCEL_SMOOTH_ALPHA
        filteredZ += (linearZ - filteredZ) * ACCEL_SMOOTH_ALPHA
    }

    private fun processFlick() {
        val now = System.currentTimeMillis()

        if (pendingAxis == FlickAxis.NONE) {
            if (now - lastFlickMs < FLICK_COOLDOWN_MS) return

            val (axis, value) = dominantAxisAndValue(filteredX, filteredY)
            if (axis != FlickAxis.NONE && abs(value) >= FLICK_START_THRESHOLD) {
                pendingAxis = axis
                pendingDirection = if (value >= 0f) 1 else -1
                pendingSinceMs = now
            }
            return
        }

        if (now - pendingSinceMs > FLICK_WINDOW_MS) {
            clearPending()
            return
        }

        val value = when (pendingAxis) {
            FlickAxis.X -> filteredX
            FlickAxis.Y -> filteredY
            FlickAxis.NONE -> 0f
        }
        val direction = signedDirection(value)
        if (direction == -pendingDirection && abs(value) >= FLICK_REBOUND_THRESHOLD) {
            triggerFlick(pendingAxis, pendingDirection, now)
            clearPending()
        }
    }

    private fun dominantAxisAndValue(x: Float, y: Float): Pair<FlickAxis, Float> {
        return if (abs(x) >= abs(y)) {
            Pair(FlickAxis.X, x)
        } else {
            Pair(FlickAxis.Y, y)
        }
    }

    private fun signedDirection(value: Float): Int {
        return when {
            value > 0.15f -> 1
            value < -0.15f -> -1
            else -> 0
        }
    }

    private fun triggerFlick(axis: FlickAxis, direction: Int, now: Long) {
        val positiveIsC = when (axis) {
            FlickAxis.X -> AXIS_X_POSITIVE_IS_C
            FlickAxis.Y -> AXIS_Y_POSITIVE_IS_C
            FlickAxis.NONE -> true
        }
        val triggerC = (direction > 0) == positiveIsC
        if (triggerC) {
            pressC()
            lastTriggerButton = "C"
        } else {
            pressA()
            lastTriggerButton = "A"
        }
        lastTriggerAtMs = now
        lastFlickMs = now
        Log.d(TAG, "flick axis=$axis direction=$direction -> $lastTriggerButton")
        vibrateFlickConfirm()
        publishDebugSnapshot(force = true)
    }

    private fun pressA() {
        if (buttonCActive) releaseC()
        if (!buttonAActive) {
            buttonAActive = true
            emulator?.pinSet("K0", BUTTON_A_PIN, 0)
        }
        mainHandler.removeCallbacks(releaseATap)
        buttonALatchedByB = glyphPhysicalDown
        if (!buttonALatchedByB) {
            mainHandler.postDelayed(releaseATap, FLICK_PRESS_MS)
        }
    }

    private fun pressC() {
        if (buttonAActive) releaseA()
        if (!buttonCActive) {
            buttonCActive = true
            emulator?.pinSet("K0", BUTTON_C_PIN, 0)
        }
        mainHandler.removeCallbacks(releaseCTap)
        buttonCLatchedByB = glyphPhysicalDown
        if (!buttonCLatchedByB) {
            mainHandler.postDelayed(releaseCTap, FLICK_PRESS_MS)
        }
    }

    private fun clearPending() {
        pendingAxis = FlickAxis.NONE
        pendingDirection = 0
        pendingSinceMs = 0L
    }

    private fun releaseA() {
        if (!buttonAActive) return
        mainHandler.removeCallbacks(releaseATap)
        buttonAActive = false
        buttonALatchedByB = false
        emulator?.pinRelease("K0", BUTTON_A_PIN)
    }

    private fun releaseC() {
        if (!buttonCActive) return
        mainHandler.removeCallbacks(releaseCTap)
        buttonCActive = false
        buttonCLatchedByB = false
        emulator?.pinRelease("K0", BUTTON_C_PIN)
    }

    private fun vibrateFlickConfirm() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(FLICK_VIBRATE_MS, FLICK_VIBRATE_AMPLITUDE))
    }

    private fun publishDebugSnapshot(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastDebugPushMs < DEBUG_PUSH_INTERVAL_MS) return
        lastDebugPushMs = now
        val pendingAgeMs = if (pendingSinceMs == 0L) 0L else (now - pendingSinceMs)
        InputDebugState.write(
            appContext,
            InputDebugState.Snapshot(
                timestampMs = now,
                mode = "flick",
                pitchDeg = latestPitchDeg,
                rollDeg = latestRollDeg,
                linearX = linearX,
                linearY = linearY,
                linearZ = linearZ,
                filteredX = filteredX,
                filteredY = filteredY,
                filteredZ = filteredZ,
                pendingAxis = pendingAxis.name,
                pendingDir = pendingDirection,
                pendingAgeMs = pendingAgeMs,
                lastTriggerButton = lastTriggerButton,
                lastTriggerAtMs = lastTriggerAtMs,
                buttonAActive = buttonAActive,
                buttonALatchedByB = buttonALatchedByB,
                buttonBActive = buttonBActive,
                buttonCActive = buttonCActive,
                buttonCLatchedByB = buttonCLatchedByB,
                glyphPhysicalDown = glyphPhysicalDown
            )
        )
    }
}
