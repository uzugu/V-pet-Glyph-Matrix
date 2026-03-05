package com.digimon.glyph.battle

import android.os.Handler
import android.os.Looper

/**
 * A mock battle transport that simply echoes packets back to the sender.
 * This guarantees a perfectly deterministic battle because the local 
 * emulator fights a clone of itself with identical stats and identical RNG seed!
 */
class SimulationBattleTransport(
    private val preset: SimulationPreset = SimulationPreset.PURE_ECHO
) : BattleTransport {

    private var listener: BattleTransport.Listener? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isConnected = false
    private var packetIndex = 0

    private var runningChecksum = 0

    fun setListener(listener: BattleTransport.Listener) {
        this.listener = listener
        // Auto-start the simulation connection the moment it is selected and wired up.
        // This ensures the user doesn't have to manually hit "Host/Join" in the app menu, 
        // they can just start clicking the V-Pet buttons immediately.
        startHost()
    }

    override fun startHost(): String {
        connect()
        return "Started Simulation Host Mode"
    }

    override fun startJoin(): String {
        connect()
        return "Started Simulation Join Mode"
    }

    private fun connect() {
        if (isConnected) return
        isConnected = true
        runningChecksum = 0 // Reset checksum for the new session
        packetIndex = 0
        // Delay slightly to simulate connection time so the UI can catch up
        handler.postDelayed({
            listener?.onConnected("Echo Bot")
            listener?.onMessage("connect_sync", "true")
        }, 500)
    }

    override fun stop(): String {
        if (!isConnected) return "Already disconnected"
        isConnected = false
        runningChecksum = 0
        packetIndex = 0
        listener?.onDisconnected("Simulation Stopped")
        return "Stopped Simulation"
    }

    override fun sendPing(): String {
        if (isConnected) {
            listener?.onMessage("ping", null)
            return "Simulated Ping Sent"
        }
        return "Not connected"
    }

    override fun sendWaveStart(stepMs: Int, totalMs: Int): Boolean {
        if (!isConnected) return false
        // Echo the wave start back so the other "device" starts too
        listener?.onMessage("sync_start", "$stepMs,$totalMs")
        return true
    }

    override fun sendSerialByte(value: Int): Boolean {
        if (!isConnected) return false
        listener?.onMessage("serial", value.toString())
        return true
    }

    override fun sendVpetPacket(packet: Int): Boolean {
        if (!isConnected) return false
        
        val isStatPacket = (packetIndex == 1) // First packet is handshake, secondary packets are stats
        packetIndex++
        
        val finalPacket = if (isStatPacket) {
            when (preset) {
                SimulationPreset.PURE_ECHO -> packet
                SimulationPreset.XOR_CHECKSUM -> {
                    // V-Pets transmit bits LSB-first. VirtualDCom stores them as [D][C][B][A]
                    // We completely randomize A, B, and C to create a varied AI opponent
                    val a = (0..15).random()
                    val b = (0..15).random()
                    val c = (0..15).random()
                    val d = a xor b xor c // Simple bitwise XOR check digit
                    (d shl 12) or (c shl 8) or (b shl 4) or a
                }
                SimulationPreset.GLOBAL_CHECKSUM -> {
                    val a = (0..15).random()
                    val b = (0..15).random()
                    val c = (0..15).random()
                    
                    // Add A, B, and C to the global running checksum
                    runningChecksum = (runningChecksum + a + b + c) % 16
                    
                    // The 4th nibble, D, must drive the running checksum back to exactly 0.
                    val d = (16 - runningChecksum) % 16
                    
                    // Reset sum to 0 for the next packet.
                    runningChecksum = 0 
                    (d shl 12) or (c shl 8) or (b shl 4) or a
                }
            }
        } else {
            // It's a handshake or other non-stat packet, echo exactly to avoid corrupting protocol
            packet
        }

        handler.postDelayed({
            listener?.onMessage("vpet_packet", finalPacket.toString())
        }, 30) // Simulate a realistic 30ms network round-trip delay
        return true
    }

    override fun sendPinDrive(port: String, value: Int?): Boolean {
        return false // Pure V-Pet emulation doesn't rely on raw pin driving over network anymore
    }

    override fun sendPinEdge(port: String, value: Int?, seq: Long, sourceUptimeMs: Long): Boolean {
        return false // Same as above, V-Pet battles use `sendVpetPacket` instead
    }

    override fun parsePinEdge(body: String?): BattleTransport.PinEdge? {
        return null
    }
}
