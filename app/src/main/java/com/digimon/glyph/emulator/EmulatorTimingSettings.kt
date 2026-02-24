package com.digimon.glyph.emulator

import android.content.Context

/**
 * Shared timing mode settings for emulator pacing.
 */
object EmulatorTimingSettings {

    private const val PREFS_NAME = "emulator_timing_settings"
    private const val KEY_EXACT_TIMING_ENABLED = "exact_timing_enabled"
    private const val KEY_CLOCK_CORRECTION_ENABLED = "clock_correction_enabled"
    private const val KEY_CLOCK_CORRECTION_FACTOR = "clock_correction_factor"
    private const val KEY_BATTLE_STEP_MODE_ENABLED = "battle_step_mode_enabled"
    private const val KEY_BATTLE_STEP_SLICE_MS = "battle_step_slice_ms"

    /**
     * Default correction factor for Digimon V3 ROM.
     * The ROM's in-game clock runs at ~83% of real time (30s behind per 3 min).
     * Factor = realTime / inGameTime = 180 / 150 = 1.20
     * This speeds up emulation so in-game time matches wall clock.
     */
    const val DEFAULT_CLOCK_CORRECTION_FACTOR = 1.20f
    const val DEFAULT_BATTLE_STEP_SLICE_MS = 10

    @Volatile
    private var exactTimingEnabled: Boolean = true

    @Volatile
    private var clockCorrectionEnabled: Boolean = true

    @Volatile
    private var clockCorrectionFactor: Float = DEFAULT_CLOCK_CORRECTION_FACTOR

    @Volatile
    private var battleStepModeEnabled: Boolean = false

    @Volatile
    private var battleStepSliceMs: Int = DEFAULT_BATTLE_STEP_SLICE_MS

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        exactTimingEnabled = prefs.getBoolean(KEY_EXACT_TIMING_ENABLED, true)
        clockCorrectionEnabled = prefs.getBoolean(KEY_CLOCK_CORRECTION_ENABLED, true)
        clockCorrectionFactor = prefs.getFloat(KEY_CLOCK_CORRECTION_FACTOR, DEFAULT_CLOCK_CORRECTION_FACTOR)
        battleStepModeEnabled = prefs.getBoolean(KEY_BATTLE_STEP_MODE_ENABLED, false)
        battleStepSliceMs = prefs.getInt(KEY_BATTLE_STEP_SLICE_MS, DEFAULT_BATTLE_STEP_SLICE_MS)
            .coerceIn(4, 250)
    }

    fun isExactTimingEnabled(): Boolean = exactTimingEnabled

    fun setExactTimingEnabled(context: Context, enabled: Boolean) {
        exactTimingEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXACT_TIMING_ENABLED, enabled)
            .apply()
    }

    fun isClockCorrectionEnabled(): Boolean = clockCorrectionEnabled

    fun setClockCorrectionEnabled(context: Context, enabled: Boolean) {
        clockCorrectionEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CLOCK_CORRECTION_ENABLED, enabled)
            .apply()
    }

    fun getClockCorrectionFactor(): Float = clockCorrectionFactor

    fun setClockCorrectionFactor(context: Context, factor: Float) {
        clockCorrectionFactor = factor.coerceIn(0.5f, 3.0f)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_CLOCK_CORRECTION_FACTOR, clockCorrectionFactor)
            .apply()
    }

    fun isBattleStepModeEnabled(): Boolean = battleStepModeEnabled

    fun setBattleStepModeEnabled(context: Context, enabled: Boolean) {
        battleStepModeEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BATTLE_STEP_MODE_ENABLED, enabled)
            .apply()
    }

    fun getBattleStepSliceMs(): Int = battleStepSliceMs

    fun setBattleStepSliceMs(context: Context, ms: Int) {
        battleStepSliceMs = ms.coerceIn(4, 250)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BATTLE_STEP_SLICE_MS, battleStepSliceMs)
            .apply()
    }
}
