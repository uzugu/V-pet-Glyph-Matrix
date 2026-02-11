package com.digimon.glyph.emulator

import android.content.Context

/**
 * Simple prefs-backed command bus between launcher activity and toy service.
 */
object EmulatorCommandBus {

    data class Command(
        val id: Long,
        val type: String,
        val arg: Int,
        val timestampMs: Long
    )

    data class Ack(
        val id: Long,
        val status: String,
        val timestampMs: Long
    )

    const val CMD_SAVE_AUTOSAVE = "save_autosave"
    const val CMD_LOAD_AUTOSAVE = "load_autosave"
    const val CMD_SAVE_SLOT = "save_slot"
    const val CMD_LOAD_SLOT = "load_slot"
    const val CMD_RESTART = "restart"
    const val CMD_FULL_RESET = "full_reset"
    const val CMD_PRESS_COMBO = "press_combo"
    const val CMD_REFRESH_SETTINGS = "refresh_settings"

    const val COMBO_AB = 1
    const val COMBO_AC = 2
    const val COMBO_BC = 3

    private const val PREFS_NAME = "emulator_command_bus"
    private const val KEY_CMD_ID = "cmd_id"
    private const val KEY_CMD_TYPE = "cmd_type"
    private const val KEY_CMD_ARG = "cmd_arg"
    private const val KEY_CMD_TIME = "cmd_time"

    private const val KEY_ACK_ID = "ack_id"
    private const val KEY_ACK_STATUS = "ack_status"
    private const val KEY_ACK_TIME = "ack_time"

    @Synchronized
    fun post(context: Context, type: String, arg: Int = 0): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextId = prefs.getLong(KEY_CMD_ID, 0L) + 1L
        prefs.edit()
            .putLong(KEY_CMD_ID, nextId)
            .putString(KEY_CMD_TYPE, type)
            .putInt(KEY_CMD_ARG, arg)
            .putLong(KEY_CMD_TIME, System.currentTimeMillis())
            .apply()
        return nextId
    }

    fun readPending(context: Context, lastHandledId: Long): Command? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_CMD_ID, 0L)
        val ackedId = prefs.getLong(KEY_ACK_ID, 0L)
        val seenId = maxOf(lastHandledId, ackedId)
        if (id <= seenId) return null
        val type = prefs.getString(KEY_CMD_TYPE, null) ?: return null
        val arg = prefs.getInt(KEY_CMD_ARG, 0)
        val ts = prefs.getLong(KEY_CMD_TIME, 0L)
        return Command(id, type, arg, ts)
    }

    fun ack(context: Context, id: Long, status: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_ACK_ID, id)
            .putString(KEY_ACK_STATUS, status)
            .putLong(KEY_ACK_TIME, System.currentTimeMillis())
            .apply()
    }

    fun readAck(context: Context): Ack? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_ACK_ID, 0L)
        if (id <= 0L) return null
        val status = prefs.getString(KEY_ACK_STATUS, "") ?: ""
        val ts = prefs.getLong(KEY_ACK_TIME, 0L)
        return Ack(id, status, ts)
    }
}
