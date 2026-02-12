package com.digimon.glyph.emulator

import android.content.Context

/**
 * Shared timing mode settings for emulator pacing.
 */
object EmulatorTimingSettings {

    private const val PREFS_NAME = "emulator_timing_settings"
    private const val KEY_EXACT_TIMING_ENABLED = "exact_timing_enabled"

    @Volatile
    private var exactTimingEnabled: Boolean = true

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        exactTimingEnabled = prefs.getBoolean(KEY_EXACT_TIMING_ENABLED, true)
    }

    fun isExactTimingEnabled(): Boolean = exactTimingEnabled

    fun setExactTimingEnabled(context: Context, enabled: Boolean) {
        exactTimingEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXACT_TIMING_ENABLED, enabled)
            .apply()
    }
}
