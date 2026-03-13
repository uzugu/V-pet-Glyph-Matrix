package com.digimon.glyph.emulator

import android.content.Context

/**
 * User-facing settings for attention notifications and care-mistake visibility.
 */
object DigimonAttentionSettings {

    private const val PREFS_NAME = "digimon_attention_settings"
    private const val KEY_ATTENTION_NOTIFICATIONS_ENABLED = "attention_notifications_enabled"
    private const val KEY_SHOW_CARE_MISTAKES = "show_care_mistakes"

    @Volatile
    private var attentionNotificationsEnabled: Boolean = false

    @Volatile
    private var showCareMistakes: Boolean = false

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        attentionNotificationsEnabled = prefs.getBoolean(KEY_ATTENTION_NOTIFICATIONS_ENABLED, false)
        showCareMistakes = prefs.getBoolean(KEY_SHOW_CARE_MISTAKES, false)
    }

    fun isAttentionNotificationsEnabled(): Boolean = attentionNotificationsEnabled

    fun setAttentionNotificationsEnabled(context: Context, enabled: Boolean) {
        attentionNotificationsEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ATTENTION_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    fun isShowCareMistakesEnabled(): Boolean = showCareMistakes

    fun setShowCareMistakesEnabled(context: Context, enabled: Boolean) {
        showCareMistakes = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_CARE_MISTAKES, enabled)
            .apply()
    }
}
