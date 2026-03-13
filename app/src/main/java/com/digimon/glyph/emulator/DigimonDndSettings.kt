package com.digimon.glyph.emulator

import android.content.Context
import java.util.Calendar

object DigimonDndSettings {

    enum class Mode { FREEZE, SILENT }

    private const val PREFS_NAME = "digimon_dnd_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODE = "mode"
    private const val KEY_START_MINUTES = "start_minutes"
    private const val KEY_END_MINUTES = "end_minutes"
    private const val KEY_AUTO_RESUME = "auto_resume"
    private const val KEY_SUPPRESS_NOTIFICATIONS = "suppress_notifications"
    private const val KEY_FROZEN_NOW = "frozen_now"
    private const val KEY_RESUME_PENDING = "resume_pending"

    @Volatile private var enabled = false
    @Volatile private var mode = Mode.FREEZE
    @Volatile private var startMinutes = 23 * 60
    @Volatile private var endMinutes = 7 * 60
    @Volatile private var autoResume = true
    @Volatile private var suppressNotifications = true
    @Volatile private var frozenNow = false
    @Volatile private var resumePending = false

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        mode = runCatching { Mode.valueOf(prefs.getString(KEY_MODE, Mode.FREEZE.name) ?: Mode.FREEZE.name) }
            .getOrDefault(Mode.FREEZE)
        startMinutes = prefs.getInt(KEY_START_MINUTES, 23 * 60).coerceIn(0, 23 * 60 + 59)
        endMinutes = prefs.getInt(KEY_END_MINUTES, 7 * 60).coerceIn(0, 23 * 60 + 59)
        autoResume = prefs.getBoolean(KEY_AUTO_RESUME, true)
        suppressNotifications = prefs.getBoolean(KEY_SUPPRESS_NOTIFICATIONS, true)
        frozenNow = prefs.getBoolean(KEY_FROZEN_NOW, false)
        resumePending = prefs.getBoolean(KEY_RESUME_PENDING, false)
    }

    fun isEnabled(): Boolean = enabled
    fun getMode(): Mode = mode
    fun getStartMinutes(): Int = startMinutes
    fun getEndMinutes(): Int = endMinutes
    fun isAutoResumeEnabled(): Boolean = autoResume
    fun isSuppressNotificationsEnabled(): Boolean = suppressNotifications
    fun isFrozenNow(): Boolean = frozenNow
    fun isResumePending(): Boolean = resumePending

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
    }

    fun setMode(context: Context, value: Mode) {
        mode = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, value.name)
            .apply()
    }

    fun setStartMinutes(context: Context, value: Int) {
        startMinutes = value.coerceIn(0, 23 * 60 + 59)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_START_MINUTES, startMinutes)
            .apply()
    }

    fun setEndMinutes(context: Context, value: Int) {
        endMinutes = value.coerceIn(0, 23 * 60 + 59)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_END_MINUTES, endMinutes)
            .apply()
    }

    fun setAutoResumeEnabled(context: Context, value: Boolean) {
        autoResume = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_RESUME, value)
            .apply()
    }

    fun setSuppressNotificationsEnabled(context: Context, value: Boolean) {
        suppressNotifications = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUPPRESS_NOTIFICATIONS, value)
            .apply()
    }

    fun setFrozenState(context: Context, frozen: Boolean, resumeAfterDnd: Boolean = resumePending) {
        frozenNow = frozen
        resumePending = resumeAfterDnd
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FROZEN_NOW, frozen)
            .putBoolean(KEY_RESUME_PENDING, resumeAfterDnd)
            .apply()
    }

    fun clearFrozenState(context: Context) = setFrozenState(context, frozen = false, resumeAfterDnd = false)

    fun isFreezeMode(): Boolean = mode == Mode.FREEZE

    fun isDndActiveNow(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!enabled) return false
        val nowMinutes = getMinutesOfDay(nowMs)
        return isWithinWindow(nowMinutes, startMinutes, endMinutes)
    }

    fun shouldSuppressAttentionNotifications(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!enabled || !suppressNotifications) return false
        return isDndActiveNow(nowMs)
    }

    fun isWithinWindow(nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean {
        return if (startMinutes == endMinutes) {
            true
        } else if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    fun getMinutesOfDay(nowMs: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun formatTime(minutesOfDay: Int): String {
        val h = (minutesOfDay / 60) % 24
        val m = minutesOfDay % 60
        return "%02d:%02d".format(h, m)
    }
}
