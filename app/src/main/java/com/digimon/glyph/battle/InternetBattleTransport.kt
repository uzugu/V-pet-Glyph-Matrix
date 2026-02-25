package com.digimon.glyph.battle

import android.content.Context
import android.util.Log
import com.digimon.glyph.battle.BattleStateStore.Role
import com.digimon.glyph.battle.BattleStateStore.Status

/**
 * Placeholder transport for future relay-backed online battles.
 *
 * This class intentionally reports a clear status instead of silently failing,
 * so users know internet mode is selected but not yet available.
 */
class InternetBattleTransport(
    context: Context,
    @Suppress("unused") private val listener: BattleTransport.Listener? = null
) : BattleTransport {

    companion object {
        private const val TAG = "InternetBattleTransport"
    }

    private val appContext = context.applicationContext
    private var role: Role = Role.NONE

    override fun startHost(): String {
        role = Role.HOST
        val relayUrl = BattleTransportSettings.getRelayUrl()
        if (relayUrl.isBlank()) {
            updateState(Status.ERROR, "Internet relay URL is empty")
            return "battle failed: internet relay URL is empty"
        }
        Log.i(TAG, "Internet host requested relayUrl=$relayUrl")
        updateState(Status.ERROR, "Internet relay transport not implemented yet")
        return "battle failed: internet relay transport not implemented yet"
    }

    override fun startJoin(): String {
        role = Role.JOIN
        val relayUrl = BattleTransportSettings.getRelayUrl()
        if (relayUrl.isBlank()) {
            updateState(Status.ERROR, "Internet relay URL is empty")
            return "battle failed: internet relay URL is empty"
        }
        Log.i(TAG, "Internet join requested relayUrl=$relayUrl")
        updateState(Status.ERROR, "Internet relay transport not implemented yet")
        return "battle failed: internet relay transport not implemented yet"
    }

    override fun stop(): String {
        role = Role.NONE
        BattleStateStore.setIdle(appContext, "Battle idle")
        return "battle link stopped"
    }

    override fun sendPing(): String = "battle ping failed: not connected"

    override fun sendWaveStart(stepMs: Int, totalMs: Int): Boolean = false

    override fun sendSerialByte(value: Int): Boolean = false

    override fun sendVpetPacket(packet: Int): Boolean = false

    override fun sendPinDrive(port: String, value: Int?): Boolean = false

    override fun sendPinEdge(port: String, value: Int?, seq: Long, sourceUptimeMs: Long): Boolean = false

    override fun parsePinEdge(body: String?): BattleTransport.PinEdge? {
        if (body.isNullOrBlank()) return null
        val parts = body.split(',')
        if (parts.size != 4) return null
        val port = parts[0]
        if (port.isBlank()) return null
        val valueToken = parts[1].toIntOrNull() ?: return null
        val seq = parts[2].toLongOrNull() ?: return null
        val sourceUptimeMs = parts[3].toLongOrNull() ?: return null
        val normalizedValue = if (valueToken < 0) null else (valueToken and 0xF)
        return BattleTransport.PinEdge(
            port = port,
            value = normalizedValue,
            seq = seq,
            sourceUptimeMs = sourceUptimeMs
        )
    }

    private fun updateState(status: Status, message: String) {
        BattleStateStore.update(
            context = appContext,
            status = status,
            role = role,
            peerName = null,
            message = message
        )
    }
}
