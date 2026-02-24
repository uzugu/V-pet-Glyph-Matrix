package com.digimon.glyph.battle

import android.util.Log
import kotlin.math.abs

/**
 * Virtual D-Com (Digitial Communication) Bridge.
 * 
 * Decodes and encodes the strictly timed microsecond bit-banging protocol
 * used by Digimon virtual pets on the P2 pin (V-Pet / 2-prong timings).
 * 
 * Instead of transmitting raw pin edges with microsecond precision over 
 * a high-latency Android Nearby Connections network, this bridge:
 * 1. Decodes local P2 pulses into 16-bit payload packets.
 * 2. Transmits the payload packet.
 * 3. Encodes received payload packets back into perfectly timed P2 local pulses.
 */
class VirtualDCom(
    private val onPacketDecoded: (Int) -> Unit,
    private val onP2DriveChange: (Int?) -> Unit
) {
    companion object {
        private const val TAG = "VirtualDCom"
        
        // V-Pet ('V') Timings from dmcomm (in microseconds)
        private const val PRE_HIGH = 3000L
        private const val PRE_LOW = 59000L
        private const val START_HIGH = 2083L
        private const val START_LOW = 917L
        private const val BIT1_HIGH = 2667L
        private const val BIT1_LOW = 1667L
        private const val BIT0_HIGH = 1000L
        private const val BIT0_LOW = 3167L
        private const val BIT1_HIGH_MIN = 1833L
        private const val SEND_RECOVERY = 400L

        private const val MARGIN = 0.40f // 40% tolerance for pulse width checks
    }

    private var rxState = RxState.IDLE
    private var lastEdgeTimeUs = 0L
    private var currentPacket = 0
    private var bitCount = 0

    private var txState = TxState.IDLE
    private val txQueue = java.util.concurrent.ConcurrentLinkedQueue<Int>()
    private var txStartTimeUs = 0L
    private var txBitIndex = 0
    private var txActiveValue: Int? = null

    enum class RxState { IDLE, WAITING_START_LOW, WAITING_BITS_HIGH, RECEIVING_BITS }
    enum class TxState { IDLE, PRE_HIGH, PRE_LOW, START_HIGH, START_LOW, SEND_BIT_HIGH, SEND_BIT_LOW, RECOVERY }

    /**
     * Called when the local emulator changes its P2 pin drive.
     * [isLow] true if P2 was pulled low (drive value < 15)
     */
    fun onLocalP2Edge(isLow: Boolean, timeUs: Long) {
        if (txState != TxState.IDLE) return // Ignore echo from our own transmission

        val dt = timeUs - lastEdgeTimeUs
        lastEdgeTimeUs = timeUs
        
        Log.d(TAG, "onLocalP2Edge isLow=$isLow dtUs=$dt rxState=$rxState")

        if (isLow) { // P2 went LOW (meaning it was just HIGH for dt)
            when (rxState) {
                RxState.WAITING_START_LOW -> {
                    if (isRoughly(dt, START_HIGH)) {
                        rxState = RxState.WAITING_BITS_HIGH
                    } else {
                        Log.w(TAG, "Decode aborted at START_HIGH, dt=$dt")
                        rxState = RxState.IDLE
                    }
                }
                RxState.RECEIVING_BITS -> {
                    // Send bits are LSB first in the 16 bit packet, wait dmcomm says "sendBit(bits & 1); bits >>= 1"
                    // Wait, dmcomm rcvBit: "(*bits) >>= 1; if (bit0) { (*bits) |= 0x8000; }"
                    // That means the first bit received goes to the highest position eventually because it shifts down 16 times.
                    // Yes: `currentPacket = (currentPacket shr 1) or (bit shl 15)` perfectly matches!
                    val bit = if (dt > BIT1_HIGH_MIN) 1 else 0
                    currentPacket = (currentPacket ushr 1) or (bit shl 15)
                    bitCount++
                    if (bitCount >= 16) {
                        Log.i(TAG, "Decoded packet: 0x${currentPacket.toString(16).padStart(4, '0')}")
                        onPacketDecoded(currentPacket)
                        rxState = RxState.IDLE
                    }
                }
                else -> {}
            }
        } else { // P2 went HIGH (meaning it was just LOW for dt)
            when (rxState) {
                RxState.IDLE -> {
                    // Check if it was pulled low for the ~59ms init pulse
                    if (dt > PRE_LOW * (1f - MARGIN)) {
                        rxState = RxState.WAITING_START_LOW
                    }
                }
                RxState.WAITING_BITS_HIGH -> {
                    if (isRoughly(dt, START_LOW)) {
                        rxState = RxState.RECEIVING_BITS
                        bitCount = 0
                        currentPacket = 0
                    } else {
                        Log.w(TAG, "Decode aborted at START_LOW, dt=$dt")
                        rxState = RxState.IDLE
                    }
                }
                else -> {}
            }
        }
    }

    private fun isRoughly(actual: Long, target: Long): Boolean {
        val diff = abs(actual - target)
        return diff <= target * MARGIN
    }

    /**
     * Queues a 16-bit packet to be transmitted into the local emulator.
     */
    fun enqueuePacket(packet: Int) {
        txQueue.add(packet)
    }

    /**
     * Called continuously by the emulator loop to progress the transmission state machine.
     * Returns true if a transmission is actively locking the P2 pin.
     */
    fun updateVirtualTime(timeUs: Long): Boolean {
        if (txState == TxState.IDLE) {
            if (txQueue.isNotEmpty()) {
                Log.i(TAG, "Starting encoding for packet 0x${txQueue.peek()?.toString(16)?.padStart(4, '0')}")
                txState = TxState.PRE_HIGH
                txStartTimeUs = timeUs
                setTxDrive(0xF) // High
            } else {
                return false
            }
        }

        val dt = timeUs - txStartTimeUs
        val currentTxPacket = txQueue.peek() ?: return false

        when (txState) {
            TxState.PRE_HIGH -> {
                if (dt >= PRE_HIGH) {
                    txState = TxState.PRE_LOW
                    txStartTimeUs = timeUs
                    setTxDrive(0xE) // Pull low
                }
            }
            TxState.PRE_LOW -> {
                if (dt >= PRE_LOW) {
                    txState = TxState.START_HIGH
                    txStartTimeUs = timeUs
                    setTxDrive(0xF) // High
                }
            }
            TxState.START_HIGH -> {
                if (dt >= START_HIGH) {
                    txState = TxState.START_LOW
                    txStartTimeUs = timeUs
                    setTxDrive(0xE) // Low
                }
            }
            TxState.START_LOW -> {
                if (dt >= START_LOW) {
                    txState = TxState.SEND_BIT_HIGH
                    txStartTimeUs = timeUs
                    txBitIndex = 0
                    setTxDrive(0xF) // High
                }
            }
            TxState.SEND_BIT_HIGH -> {
                val bit = (currentTxPacket ushr txBitIndex) and 1
                val targetTime = if (bit == 1) BIT1_HIGH else BIT0_HIGH
                if (dt >= targetTime) {
                    txState = TxState.SEND_BIT_LOW
                    txStartTimeUs = timeUs
                    setTxDrive(0xE) // Low
                }
            }
            TxState.SEND_BIT_LOW -> {
                val bit = (currentTxPacket ushr txBitIndex) and 1
                val targetTime = if (bit == 1) BIT1_LOW else BIT0_LOW
                if (dt >= targetTime) {
                    txBitIndex++
                    if (txBitIndex >= 16) {
                        txState = TxState.RECOVERY
                    } else {
                        txState = TxState.SEND_BIT_HIGH
                    }
                    txStartTimeUs = timeUs
                    setTxDrive(0xF) // High
                }
            }
            TxState.RECOVERY -> {
                if (dt >= SEND_RECOVERY) {
                    txState = TxState.IDLE
                    txQueue.poll()
                    setTxDrive(null) // Release
                }
            }
            else -> {}
        }
        return true
    }
    
    private fun setTxDrive(value: Int?) {
        if (txActiveValue != value) {
            txActiveValue = value
            onP2DriveChange(value)
        }
    }
    
    fun reset() {
        rxState = RxState.IDLE
        txState = TxState.IDLE
        txQueue.clear()
        if (txActiveValue != null) {
            setTxDrive(null)
        }
    }
}
