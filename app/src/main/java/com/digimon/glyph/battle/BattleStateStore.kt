package com.digimon.glyph.battle

import android.content.Context

/**
 * Shared battle-link state for service <-> launcher UI.
 */
object BattleStateStore {
    private const val STALE_LINK_MS = 30_000L

    enum class Status {
        IDLE,
        ADVERTISING,
        DISCOVERING,
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }

    enum class Role {
        NONE,
        HOST,
        JOIN
    }

    data class Snapshot(
        val status: Status,
        val role: Role,
        val peerName: String?,
        val message: String,
        val updatedAtMs: Long
    )

    private const val PREFS = "battle_state"
    private const val KEY_STATUS = "status"
    private const val KEY_ROLE = "role"
    private const val KEY_PEER = "peer"
    private const val KEY_MESSAGE = "message"
    private const val KEY_UPDATED_AT = "updated_at"

    fun init(context: Context) {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_STATUS)) {
            setIdle(context, "Battle idle")
            return
        }
        val updatedAt = prefs.getLong(KEY_UPDATED_AT, 0L)
        val stale = updatedAt <= 0L || (System.currentTimeMillis() - updatedAt) > STALE_LINK_MS
        if (stale) {
            val status = runCatching {
                Status.valueOf(prefs.getString(KEY_STATUS, Status.IDLE.name) ?: Status.IDLE.name)
            }.getOrDefault(Status.IDLE)
            if (status != Status.IDLE) {
                setIdle(context, "Battle idle")
            }
        }
    }

    fun setIdle(context: Context, message: String = "Battle idle") {
        update(context, Status.IDLE, Role.NONE, null, message)
    }

    fun update(
        context: Context,
        status: Status,
        role: Role,
        peerName: String?,
        message: String
    ) {
        prefs(context).edit()
            .putString(KEY_STATUS, status.name)
            .putString(KEY_ROLE, role.name)
            .putString(KEY_PEER, peerName)
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun read(context: Context): Snapshot {
        val prefs = prefs(context)
        val status = runCatching {
            Status.valueOf(prefs.getString(KEY_STATUS, Status.IDLE.name) ?: Status.IDLE.name)
        }.getOrDefault(Status.IDLE)
        val role = runCatching {
            Role.valueOf(prefs.getString(KEY_ROLE, Role.NONE.name) ?: Role.NONE.name)
        }.getOrDefault(Role.NONE)
        return Snapshot(
            status = status,
            role = role,
            peerName = prefs.getString(KEY_PEER, null),
            message = prefs.getString(KEY_MESSAGE, "Battle idle") ?: "Battle idle",
            updatedAtMs = prefs.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
