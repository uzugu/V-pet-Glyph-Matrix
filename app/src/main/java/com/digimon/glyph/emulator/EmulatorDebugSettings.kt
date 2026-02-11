package com.digimon.glyph.emulator

import android.content.Context

/**
 * Shared debug settings for emulator diagnostics and telemetry.
 */
object EmulatorDebugSettings {

    private const val PREFS_NAME = "emulator_debug_settings"
    private const val KEY_DEBUG_ENABLED = "debug_enabled"

    @Volatile
    private var debugEnabled: Boolean = false

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        debugEnabled = prefs.getBoolean(KEY_DEBUG_ENABLED, false)
    }

    fun isDebugEnabled(): Boolean = debugEnabled

    fun setDebugEnabled(context: Context, enabled: Boolean) {
        debugEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEBUG_ENABLED, enabled)
            .apply()
    }
}
