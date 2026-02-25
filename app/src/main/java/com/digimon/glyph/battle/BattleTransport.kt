package com.digimon.glyph.battle

/**
 * Transport abstraction for battle link traffic.
 *
 * Nearby (local) and Internet relay transports should both implement this contract
 * so the emulator/service logic stays transport-agnostic.
 */
interface BattleTransport {

    interface Listener {
        fun onConnected(peerName: String)
        fun onDisconnected(reason: String)
        fun onMessage(type: String, body: String?)
    }

    data class PinEdge(
        val port: String,
        val value: Int?,
        val seq: Long,
        val sourceUptimeMs: Long
    )

    fun startHost(): String
    fun startJoin(): String
    fun stop(): String
    fun sendPing(): String
    fun sendWaveStart(stepMs: Int, totalMs: Int): Boolean
    fun sendSerialByte(value: Int): Boolean
    fun sendVpetPacket(packet: Int): Boolean
    fun sendPinDrive(port: String, value: Int?): Boolean
    fun sendPinEdge(port: String, value: Int?, seq: Long, sourceUptimeMs: Long): Boolean
    fun parsePinEdge(body: String?): PinEdge?
}

