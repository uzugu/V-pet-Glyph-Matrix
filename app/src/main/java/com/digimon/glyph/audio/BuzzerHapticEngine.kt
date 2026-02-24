package com.digimon.glyph.audio

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Maps emulator buzzer state to device haptics.
 */
class BuzzerHapticEngine(context: Context) {

    companion object {
        private const val MIN_PATTERN_RESTART_MS = 35L
    }

    private val vibrator: Vibrator? = context
        .getSystemService(VibratorManager::class.java)
        ?.defaultVibrator
        ?.takeIf { it.hasVibrator() }

    @Volatile private var enabled = false
    @Volatile private var toneOn = false
    @Volatile private var patternRunning = false
    @Volatile private var lastPatternStartMs = 0L
    @Volatile private var lastFreqBand = -1
    @Volatile private var lastAmpBucket = -1

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!enabled) {
            stopPattern()
            return
        }
        if (toneOn) {
            startOrRefreshPattern(frequencyHz = 1024, level = 1f, force = true)
        }
    }

    fun onBuzzerChange(on: Boolean, frequencyHz: Int, level: Float) {
        toneOn = on
        if (!enabled) {
            if (patternRunning) stopPattern()
            return
        }
        if (!on) {
            stopPattern()
            return
        }
        startOrRefreshPattern(frequencyHz, level, force = false)
    }

    fun stop() {
        toneOn = false
        stopPattern()
    }

    private fun startOrRefreshPattern(frequencyHz: Int, level: Float, force: Boolean) {
        val vib = vibrator ?: return
        val amp = (40 + (level.coerceIn(0f, 1f) * 180f)).toInt().coerceIn(1, 255)
        val freqBand = when {
            frequencyHz >= 2200 -> 2
            frequencyHz >= 1200 -> 1
            else -> 0
        }
        val ampBucket = amp / 32
        val now = SystemClock.elapsedRealtime()
        if (!force && patternRunning) {
            val samePattern = (freqBand == lastFreqBand) && (ampBucket == lastAmpBucket)
            if (samePattern) return
            if (now - lastPatternStartMs < MIN_PATTERN_RESTART_MS) return
        }

        val pulseMs = when (freqBand) {
            2 -> 8L
            1 -> 10L
            else -> 12L
        }
        val gapMs = when (freqBand) {
            2 -> 6L
            1 -> 8L
            else -> 10L
        }

        vib.cancel()
        val timings = longArrayOf(0L, pulseMs, gapMs)
        val amplitudes = intArrayOf(0, amp, 0)
        vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))

        patternRunning = true
        lastPatternStartMs = now
        lastFreqBand = freqBand
        lastAmpBucket = ampBucket
    }

    private fun stopPattern() {
        vibrator?.cancel()
        patternRunning = false
        lastPatternStartMs = 0L
        lastFreqBand = -1
        lastAmpBucket = -1
    }
}
