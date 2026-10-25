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
        val timestampMs: Long,
        val romHash: String? = null
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
    const val CMD_SET_FIRMWARE_CLOCK = "set_firmware_clock"
    const val CMD_SYNC_FIRMWARE_CLOCK = "sync_firmware_clock"
    const val CMD_REFRESH_SETTINGS = "refresh_settings"
    const val CMD_BUTTON_PRESS = "button_press"
    const val CMD_BATTLE_START_HOST = "battle_start_host"
    const val CMD_BATTLE_START_JOIN = "battle_start_join"
    const val CMD_BATTLE_STOP = "battle_stop"
    const val CMD_BATTLE_PING = "battle_ping"
    const val CMD_BATTLE_STEP_PULSE = "battle_step_pulse"

    const val COMBO_AB = 1
    const val COMBO_AC = 2
    const val COMBO_BC = 3

    const val BUTTON_A = 10
    const val BUTTON_B = 11
    const val BUTTON_C = 12

    private const val KEY_QUEUE = "pending_commands_v1"
    private val gson = com.google.gson.Gson()
    private fun queue(prefs: android.content.SharedPreferences): MutableList<Command> {
        val encoded = prefs.getString(KEY_QUEUE, null)
        if (encoded != null) return runCatching {
            gson.fromJson(encoded, Array<Command>::class.java).toMutableList()
        }.getOrDefault(mutableListOf())
        val id = prefs.getLong(KEY_CMD_ID, 0L)
        val type = prefs.getString(KEY_CMD_TYPE, null)
        return if (type != null && id > prefs.getLong(KEY_ACK_ID, 0L)) mutableListOf(
            Command(id, type, prefs.getInt(KEY_CMD_ARG, 0), prefs.getLong(KEY_CMD_TIME, 0L))
        ) else mutableListOf()
    }

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
        val pending = queue(prefs)
        require(pending.size < 64) { "Too many pending commands; wait for the current action" }
        val nextId = prefs.getLong(KEY_CMD_ID, 0L) + 1L
        pending += Command(nextId, type, arg, System.currentTimeMillis(),
            if (type == CMD_REFRESH_SETTINGS) null else EmulatorStatus.snapshot().hash)
        prefs.edit().putLong(KEY_CMD_ID, nextId).putString(KEY_QUEUE, gson.toJson(pending)).apply()
        return nextId
    }

    @Synchronized
    fun readPending(context: Context, lastHandledId: Long): Command? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seen = maxOf(lastHandledId, prefs.getLong(KEY_ACK_ID, 0L))
        return queue(prefs).firstOrNull { it.id > seen }
    }

    @Synchronized
    fun ack(context: Context, id: Long, status: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (id < prefs.getLong(KEY_ACK_ID, 0L)) return
        prefs.edit()
            .putString(KEY_QUEUE, gson.toJson(queue(prefs).filter { it.id > id }))
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
