package com.digimon.glyph.emulator

import android.content.Context

/**
 * Shared timing mode settings for emulator pacing.
 */
object EmulatorTimingSettings {

    private const val PREFS_NAME = "emulator_timing_settings"
    private const val KEY_POWER_SAVE_ENABLED = "power_save_enabled"
    private const val KEY_FULL_ACCURACY_ENABLED = "full_accuracy_enabled"
    private const val KEY_CLOCK_CORRECTION_ENABLED = "clock_correction_enabled"
    private const val KEY_CLOCK_CORRECTION_FACTOR = "clock_correction_factor"
    private const val KEY_BATTLE_STEP_MODE_ENABLED = "battle_step_mode_enabled"
    private const val KEY_BATTLE_STEP_SLICE_MS = "battle_step_slice_ms"

    // Legacy telemetry API: the clock now always runs at the phone's real-time rate.
    const val DEFAULT_CLOCK_CORRECTION_FACTOR = 1.0f
    const val DEFAULT_BATTLE_STEP_SLICE_MS = 10

    @Volatile
    private var powerSaveEnabled: Boolean = false

    @Volatile
    private var fullAccuracyEnabled: Boolean = false

    @Volatile
    private var clockCorrectionEnabled: Boolean = false

    @Volatile
    private var clockCorrectionFactor: Float = DEFAULT_CLOCK_CORRECTION_FACTOR

    @Volatile
    private var battleStepModeEnabled: Boolean = false

    @Volatile
    private var battleStepSliceMs: Int = DEFAULT_BATTLE_STEP_SLICE_MS

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        powerSaveEnabled = prefs.getBoolean(KEY_POWER_SAVE_ENABLED, false)
        fullAccuracyEnabled = prefs.getBoolean(KEY_FULL_ACCURACY_ENABLED, false)
        clockCorrectionEnabled = false
        clockCorrectionFactor = 1.0f
        // Retire stored workaround multipliers so upgrading cannot silently keep speeding up time.
        if (prefs.getBoolean(KEY_CLOCK_CORRECTION_ENABLED, false) ||
            prefs.getFloat(KEY_CLOCK_CORRECTION_FACTOR, 1.0f) != 1.0f) {
            prefs.edit().putBoolean(KEY_CLOCK_CORRECTION_ENABLED, false)
                .putFloat(KEY_CLOCK_CORRECTION_FACTOR, 1.0f).apply()
        }
        battleStepModeEnabled = prefs.getBoolean(KEY_BATTLE_STEP_MODE_ENABLED, false)
        battleStepSliceMs = prefs.getInt(KEY_BATTLE_STEP_SLICE_MS, DEFAULT_BATTLE_STEP_SLICE_MS)
            .coerceIn(4, 250)
    }

    fun isPowerSaveEnabled(): Boolean = powerSaveEnabled

    fun setPowerSaveEnabled(context: Context, enabled: Boolean) {
        powerSaveEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_POWER_SAVE_ENABLED, enabled).apply()
    }

    fun isFullAccuracyEnabled(): Boolean = fullAccuracyEnabled

    fun setFullAccuracyEnabled(context: Context, enabled: Boolean) {
        fullAccuracyEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FULL_ACCURACY_ENABLED, enabled)
            .apply()
    }

    fun isClockCorrectionEnabled(): Boolean = clockCorrectionEnabled

    fun getClockCorrectionFactor(): Float = clockCorrectionFactor

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
