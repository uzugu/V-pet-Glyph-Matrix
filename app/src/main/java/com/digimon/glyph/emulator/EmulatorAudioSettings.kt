package com.digimon.glyph.emulator

import android.content.Context

/**
 * Shared audio settings for emulator buzzer playback.
 */
object EmulatorAudioSettings {

    private const val PREFS_NAME = "emulator_audio_settings"
    private const val KEY_AUDIO_ENABLED = "audio_enabled"

    @Volatile
    private var audioEnabled: Boolean = false

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        audioEnabled = prefs.getBoolean(KEY_AUDIO_ENABLED, false)
    }

    fun isAudioEnabled(): Boolean = audioEnabled

    fun setAudioEnabled(context: Context, enabled: Boolean) {
        audioEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUDIO_ENABLED, enabled)
            .apply()
    }
}

