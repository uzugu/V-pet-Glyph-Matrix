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
import com.digimon.glyph.emulator.EmulatorDebugSettings
import kotlin.math.abs

/**
 * Maps motion + Glyph Button to Digimon A/B/C buttons.
 *
 * Flick mode:
 * - detect a short acceleration impulse and opposite rebound
 * - left/right (X axis) => A/C
 * - toward/away (Z axis) => B
 */
class InputController(context: Context) : SensorEventListener {

    private enum class FlickAxis { NONE, X, Z }
    private enum class InputRateMode { IDLE, ACTIVE }

    companion object {
        private const val TAG = "DigimonInput"

        private const val FLICK_START_THRESHOLD = 4.6f
        private const val FLICK_REBOUND_THRESHOLD = 2.5f
        private const val FLICK_WINDOW_MS = 230L
        private const val FLICK_COOLDOWN_MS = 260L
        private const val FLICK_MIN_REBOUND_DELAY_MS = 26L
        private const val FLICK_AXIS_DOMINANCE_RATIO = 1.2f
        private const val FLICK_MAX_Y_ABS_AT_START = 3.4f
        private const val FLICK_REBOUND_RATIO_OF_START = 0.5f
        private const val FLICK_PRESS_MS = 85L
        private const val B_FLICK_PRESS_MS = 75L
        private const val COMBO_PRESS_MS = 95L
        private const val ACCEL_SMOOTH_ALPHA = 0.35f
        private const val ACCEL_GRAVITY_ALPHA = 0.82f

        // Sign mapping for horizontal flick axis.
        private const val AXIS_X_POSITIVE_IS_C = true

        private const val DEBUG_PUSH_INTERVAL_MS = 80L
        private const val FLICK_VIBRATE_MS = 160L
        private const val FLICK_VIBRATE_AMPLITUDE = 255

        private const val INPUT_IDLE_AFTER_MS = 60_000L

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
    private var pendingStartAbs = 0f
    private var pendingSinceMs = 0L
    private var lastFlickMs = 0L
    private var lastTriggerButton = "NONE"
    private var lastTriggerAtMs = 0L

    private var started = false
    private var sensorsRegistered = false
    private var inputRateMode = InputRateMode.IDLE
    private var lastInteractionMs = 0L

    private var lastDebugPushMs = 0L
    private var debugEnabled = EmulatorDebugSettings.isDebugEnabled()
    var onUserInteraction: (() -> Unit)? = null
    var onPinSet: ((port: String, pin: Int, level: Int) -> Unit)? = null
    var onPinRelease: ((port: String, pin: Int) -> Unit)? = null

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

    private val releaseBTap = Runnable {
        if (buttonBActive && !glyphPhysicalDown) {
            buttonBActive = false
            pinRelease("K0", BUTTON_B_PIN)
        }
    }

    fun attach(emu: E0C6200) {
        emulator = emu
    }

    private fun pinSet(port: String, pin: Int, level: Int) {
        onPinSet?.invoke(port, pin, level) ?: emulator?.pinSet(port, pin, level)
    }

    private fun pinRelease(port: String, pin: Int) {
        onPinRelease?.invoke(port, pin) ?: emulator?.pinRelease(port, pin)
    }

    fun setDebugEnabled(enabled: Boolean) {
        debugEnabled = enabled
        if (!enabled) {
            InputDebugState.clear(appContext)
            lastDebugPushMs = 0L
        } else {
            publishDebugSnapshot(force = true)
        }
    }

    fun start() {
        if (started) return
        started = true
        lastInteractionMs = System.currentTimeMillis()
        setInputRateMode(InputRateMode.IDLE)
    }

    fun stop() {
        started = false
        sensorManager.unregisterListener(this)
        sensorsRegistered = false
        releaseAll()
    }

    fun onGlyphButtonDown() {
        noteInteraction()
        glyphPhysicalDown = true
        if (!buttonBActive) {
            buttonBActive = true
            pinSet("K0", BUTTON_B_PIN, 0)
            lastTriggerButton = "B"
            lastTriggerAtMs = System.currentTimeMillis()
            if (debugEnabled) {
                Log.d(TAG, "glyph button -> B down")
            }
        }
        onUserInteraction?.invoke()
        publishDebugSnapshot(force = true)
    }

    fun onGlyphButtonUp() {
        noteInteraction()
        glyphPhysicalDown = false
        if (buttonBActive) {
            buttonBActive = false
            pinRelease("K0", BUTTON_B_PIN)
            if (debugEnabled) {
                Log.d(TAG, "glyph button -> B up")
            }
        }
        if (buttonALatchedByB) {
            releaseA()
        }
        if (buttonCLatchedByB) {
            releaseC()
        }
        onUserInteraction?.invoke()
        publishDebugSnapshot(force = true)
    }

    private fun releaseAll() {
        mainHandler.removeCallbacks(releaseBTap)
        mainHandler.removeCallbacks(releaseATap)
        mainHandler.removeCallbacks(releaseCTap)
        glyphPhysicalDown = false
        buttonAActive = false
        buttonBActive = false
        buttonCActive = false
        buttonALatchedByB = false
        buttonCLatchedByB = false
        pinRelease("K0", BUTTON_A_PIN)
        pinRelease("K0", BUTTON_B_PIN)
        pinRelease("K0", BUTTON_C_PIN)

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
        val now = System.currentTimeMillis()
        maybeEnterIdleMode(now)
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
                processMotion(now)
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
                processMotion(now)
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

    private fun processMotion(now: Long) {
        if (inputRateMode == InputRateMode.IDLE) {
            return
        }
        processFlick(now)
    }

    private fun maybeEnterIdleMode(now: Long) {
        if (inputRateMode != InputRateMode.ACTIVE) return
        if (glyphPhysicalDown) return
        if (now - lastInteractionMs <= INPUT_IDLE_AFTER_MS) return
        setInputRateMode(InputRateMode.IDLE)
        clearPending()
    }

    private fun noteInteraction() {
        lastInteractionMs = System.currentTimeMillis()
        if (inputRateMode != InputRateMode.ACTIVE) {
            setInputRateMode(InputRateMode.ACTIVE)
        }
    }

    private fun setInputRateMode(mode: InputRateMode) {
        if (inputRateMode == mode && started && sensorsRegistered) return
        inputRateMode = mode
        if (!started) return
        if (sensorsRegistered) {
            sensorManager.unregisterListener(this)
            sensorsRegistered = false
        }
        val delay = if (mode == InputRateMode.ACTIVE) {
            SensorManager.SENSOR_DELAY_GAME
        } else {
            SensorManager.SENSOR_DELAY_NORMAL
        }
        rotationSensor?.let { sensorManager.registerListener(this, it, delay) }
        linearAccelerationSensor?.let { sensorManager.registerListener(this, it, delay) }
        accelerometerFallback?.let { sensorManager.registerListener(this, it, delay) }
        sensorsRegistered = true
        if (debugEnabled) {
            Log.d(TAG, "Input polling mode=$mode delay=$delay")
        }
    }

    private fun processFlick(now: Long) {
        if (pendingAxis == FlickAxis.NONE) {
            if (now - lastFlickMs < FLICK_COOLDOWN_MS) return

            val (axis, value) = dominantAxisAndValue(filteredX, filteredZ)
            val startAbs = abs(value)
            if (
                axis != FlickAxis.NONE &&
                startAbs >= FLICK_START_THRESHOLD &&
                abs(filteredY) <= FLICK_MAX_Y_ABS_AT_START
            ) {
                pendingAxis = axis
                pendingDirection = if (value >= 0f) 1 else -1
                pendingStartAbs = startAbs
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
            FlickAxis.Z -> filteredZ
            FlickAxis.NONE -> 0f
        }
        val direction = signedDirection(value)
        val reboundAbs = abs(value)
        val orthogonalAbs = when (pendingAxis) {
            FlickAxis.X -> abs(filteredZ)
            FlickAxis.Z -> abs(filteredX)
            FlickAxis.NONE -> 0f
        }
        val enoughDelay = now - pendingSinceMs >= FLICK_MIN_REBOUND_DELAY_MS
        val reboundThreshold = maxOf(FLICK_REBOUND_THRESHOLD, pendingStartAbs * FLICK_REBOUND_RATIO_OF_START)
        val axisDominant = reboundAbs >= orthogonalAbs * FLICK_AXIS_DOMINANCE_RATIO
        if (direction == -pendingDirection && enoughDelay && axisDominant && reboundAbs >= reboundThreshold) {
            triggerFlick(pendingAxis, pendingDirection, now)
            clearPending()
        }
    }

    private fun dominantAxisAndValue(x: Float, z: Float): Pair<FlickAxis, Float> {
        val ax = abs(x)
        val az = abs(z)
        return if (ax >= az) {
            if (ax >= az * FLICK_AXIS_DOMINANCE_RATIO) Pair(FlickAxis.X, x) else Pair(FlickAxis.NONE, 0f)
        } else {
            if (az >= ax * FLICK_AXIS_DOMINANCE_RATIO) Pair(FlickAxis.Z, z) else Pair(FlickAxis.NONE, 0f)
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
        noteInteraction()
        when (axis) {
            FlickAxis.Z -> {
                pressBTap()
                lastTriggerButton = "B"
            }
            FlickAxis.X -> {
                val triggerC = (direction > 0) == AXIS_X_POSITIVE_IS_C
                if (triggerC) {
                    pressC()
                    lastTriggerButton = "C"
                } else {
                    pressA()
                    lastTriggerButton = "A"
                }
            }
            FlickAxis.NONE -> return
        }
        lastTriggerAtMs = now
        lastFlickMs = now
        if (debugEnabled) {
            Log.d(TAG, "flick axis=$axis direction=$direction -> $lastTriggerButton")
        }
        onUserInteraction?.invoke()
        vibrateFlickConfirm()
        publishDebugSnapshot(force = true)
    }

    private fun pressA() {
        if (buttonCActive) releaseC()
        if (!buttonAActive) {
            buttonAActive = true
            pinSet("K0", BUTTON_A_PIN, 0)
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
            pinSet("K0", BUTTON_C_PIN, 0)
        }
        mainHandler.removeCallbacks(releaseCTap)
        buttonCLatchedByB = glyphPhysicalDown
        if (!buttonCLatchedByB) {
            mainHandler.postDelayed(releaseCTap, FLICK_PRESS_MS)
        }
    }

    private fun pressBTap() {
        if (glyphPhysicalDown || buttonBActive) return
        buttonBActive = true
        pinSet("K0", BUTTON_B_PIN, 0)
        mainHandler.removeCallbacks(releaseBTap)
        mainHandler.postDelayed(releaseBTap, B_FLICK_PRESS_MS)
    }

    fun triggerComboTap(combo: Int): Boolean {
        val includeA: Boolean
        val includeB: Boolean
        val includeC: Boolean
        val triggerName: String
        when (combo) {
            1 -> {
                includeA = true
                includeB = true
                includeC = false
                triggerName = "A+B"
            }
            2 -> {
                includeA = true
                includeB = false
                includeC = true
                triggerName = "A+C"
            }
            3 -> {
                includeA = false
                includeB = true
                includeC = true
                triggerName = "B+C"
            }
            else -> return false
        }

        val pressA = includeA && !buttonAActive
        val pressB = includeB && !buttonBActive
        val pressC = includeC && !buttonCActive

        if (pressA) {
            buttonAActive = true
            pinSet("K0", BUTTON_A_PIN, 0)
            mainHandler.removeCallbacks(releaseATap)
        }
        if (pressB) {
            buttonBActive = true
            pinSet("K0", BUTTON_B_PIN, 0)
            mainHandler.removeCallbacks(releaseBTap)
        }
        if (pressC) {
            buttonCActive = true
            pinSet("K0", BUTTON_C_PIN, 0)
            mainHandler.removeCallbacks(releaseCTap)
        }

        if (pressA || pressB || pressC) {
            mainHandler.postDelayed({
                if (pressA && !buttonALatchedByB) {
                    releaseA()
                }
                if (pressB && !glyphPhysicalDown) {
                    buttonBActive = false
                    pinRelease("K0", BUTTON_B_PIN)
                }
                if (pressC && !buttonCLatchedByB) {
                    releaseC()
                }
                publishDebugSnapshot(force = true)
            }, COMBO_PRESS_MS)
        }

        lastTriggerButton = triggerName
        lastTriggerAtMs = System.currentTimeMillis()
        noteInteraction()
        onUserInteraction?.invoke()
        publishDebugSnapshot(force = true)
        return true
    }

    private fun clearPending() {
        pendingAxis = FlickAxis.NONE
        pendingDirection = 0
        pendingStartAbs = 0f
        pendingSinceMs = 0L
    }

    private fun releaseA() {
        if (!buttonAActive) return
        mainHandler.removeCallbacks(releaseATap)
        buttonAActive = false
        buttonALatchedByB = false
        pinRelease("K0", BUTTON_A_PIN)
    }

    private fun releaseC() {
        if (!buttonCActive) return
        mainHandler.removeCallbacks(releaseCTap)
        buttonCActive = false
        buttonCLatchedByB = false
        pinRelease("K0", BUTTON_C_PIN)
    }

    private fun vibrateFlickConfirm() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(FLICK_VIBRATE_MS, FLICK_VIBRATE_AMPLITUDE))
    }

    private fun publishDebugSnapshot(force: Boolean = false) {
        if (!debugEnabled) return
        val now = System.currentTimeMillis()
        if (!force && now - lastDebugPushMs < DEBUG_PUSH_INTERVAL_MS) return
        lastDebugPushMs = now
        val pendingAgeMs = if (pendingSinceMs == 0L) 0L else (now - pendingSinceMs)
        InputDebugState.write(
            appContext,
            InputDebugState.Snapshot(
                timestampMs = now,
                mode = if (inputRateMode == InputRateMode.ACTIVE) "flick-active" else "flick-idle",
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
