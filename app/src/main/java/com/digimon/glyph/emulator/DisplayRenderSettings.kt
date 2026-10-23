package com.digimon.glyph.emulator

import android.content.Context

/**
 * Shared render settings for DisplayBridge.
 */
object DisplayRenderSettings {

    private const val PREFS_NAME = "display_render_settings"
    private const val KEY_TEXT_ZOOM_OUT = "text_zoom_out"

    @Volatile
    private var textZoomOutEnabled: Boolean = false

    @Volatile private var dpowerFitEnabled = false
    fun isDPowerFitEnabled() = dpowerFitEnabled
    fun setDPowerFitEnabled(context: Context, enabled: Boolean) {
        dpowerFitEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("dpower_fit", enabled).apply()
    }

    @Volatile private var dpowerClockEnabled = false
    fun isDPowerClockEnabled() = dpowerClockEnabled
    fun setDPowerClockEnabled(context: Context, enabled: Boolean) {
        dpowerClockEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("dpower_clock", enabled).apply()
    }

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        dpowerFitEnabled = prefs.getBoolean("dpower_fit", false)
        dpowerClockEnabled = prefs.getBoolean("dpower_clock", false)
        textZoomOutEnabled = prefs.getBoolean(KEY_TEXT_ZOOM_OUT, false)
    }

    fun isTextZoomOutEnabled(): Boolean = textZoomOutEnabled

    fun setTextZoomOutEnabled(context: Context, enabled: Boolean) {
        textZoomOutEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TEXT_ZOOM_OUT, enabled)
            .apply()
    }
}

