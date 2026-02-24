package com.digimon.glyph.emulator

/**
 * Seiko Epson E0C6200 4-bit microcontroller emulator.
 * Faithfully ported from BrickEmuPy by azya52.
 *
 * This single class encapsulates:
 * - CPU registers and execution
 * - 4096-entry instruction dispatch table
 * - Memory (RAM/VRAM) access with I/O register mapping
 * - Timer, stopwatch, programmable timer peripherals
 * - Button input (K0/K1 ports)
 * - Interrupt controller
 *
 * @param romData Raw ROM binary bytes
 * @param clock CPU clock frequency in Hz (1060000 for Digimon)
 * @param portPullupK0 Pull-up value for K0 port (15 for Digimon)
 * @param portPullupK1 Pull-up value for K1 port (15 for Digimon)
 */
class E0C6200(
    romData: ByteArray,
    private val clock: Int = 1060000,
    private val portPullupK0: Int = 15,
    private val portPullupK1: Int = 15,
    private val p3Dedicated: Boolean = false
) {
    data class DisplaySnapshot(
        val generation: Long,
        val vramData: IntArray
    )

    companion object {
        const val OSC1_CLOCK = 32768
        val TIMER_CLOCK_DIV = OSC1_CLOCK.toDouble() / 256
        val STOPWATCH_CLOCK_DIV = OSC1_CLOCK.toDouble() / 100
        val PTIMER_CLOCK_DIV = doubleArrayOf(0.0, 0.0,
            OSC1_CLOCK.toDouble() / 256, OSC1_CLOCK.toDouble() / 512,
            OSC1_CLOCK.toDouble() / 1024, OSC1_CLOCK.toDouble() / 2048,
            OSC1_CLOCK.toDouble() / 4096, OSC1_CLOCK.toDouble() / 8192)
        val BUZZER_FREQ_DIV = intArrayOf(8, 10, 12, 14, 16, 20, 24, 28)
        val ONE_SHOT_PULSE_WIDTH_DIV = intArrayOf(8 * 128, 16 * 128)
        val ENVELOPE_CYCLE_DIV = intArrayOf(16 * 128, 32 * 128)

        const val RAM_SIZE = 0x300
        const val VRAM_SIZE = 0x0A0
        const val VRAM_PART1_OFFSET = 0xE00
        const val VRAM_PART2_OFFSET = 0xE80
        const val VRAM_PART_SIZE = 0x050
        const val IORAM_OFFSET = 0xF00

        // I/O bit masks
        const val IO_CLKCHG = 8; const val IO_ALOFF = 8; const val IO_ALON = 4
        const val IO_SVDDT = 8
        const val IO_BZFQ = 7; const val IO_SHOTPW = 8
        const val IO_BZSHOT = 8; const val IO_ENVRST = 4; const val IO_ENVRT = 2; const val IO_ENVON = 1
        const val IO_TMRST = 2
        const val IO_SWRST = 2; const val IO_SWRUN = 1
        const val IO_PTRST = 2; const val IO_PTRUN = 1
        const val IO_PTCOUT = 8; const val IO_PTC = 7
        const val IO_IOC0 = 1; const val IO_IOC1 = 2; const val IO_IOC2 = 4; const val IO_IOC3 = 8
        const val IO_PUP0 = 1; const val IO_PUP1 = 2; const val IO_PUP2 = 4; const val IO_PUP3 = 8
        const val IO_R33 = 8; const val IO_R43 = 8
        const val IO_ISW0 = 2; const val IO_ISW1 = 1
        const val IO_IPT = 1; const val IO_IK0 = 1; const val IO_IK1 = 1
        const val IO_IT1 = 8; const val IO_IT2 = 4; const val IO_IT8 = 2; const val IO_IT32 = 1
        const val IO_TM2 = 4; const val IO_TM4 = 1; const val IO_TM6 = 4; const val IO_TM7 = 8
    }

    // ROM
    private val rom: ByteArray = romData.copyOf()
    private val romSize: Int = romData.size

    // CPU registers
    private var A = 0; private var B = 0
    private var IX = 0; private var IY = 0
    private var SP = 0
    private var PC = 0x100; private var NPC = 0x100
    private var CF = 0; private var ZF = 0; private var DF = 0; private var IF = 0
    private var HALT = 0; private var RESET = 0
    private var ifDelay = false
    private var instrCounter: Long = 0

    // Memory
    private val RAM = IntArray(RAM_SIZE)
    private val VRAM = IntArray(VRAM_SIZE)

    // I/O registers
    private var IT = 0; private var ISW = 0; private var IPT = 0; private var ISIO = 0
    private var IK0 = 0; private var IK1 = 0
    private var EIT = 0; private var EISW = 0; private var EIPT = 0; private var EISIO = 0
    private var EIK0 = 0; private var EIK1 = 0
    private var TM = 0; private var SWL = 0; private var SWH = 0
    private var PT = 0; private var RD = 0; private var SD = 0
    private var K0 = portPullupK0; private var DFK0 = 0xF; private var K1 = portPullupK1
    private var R0 = 0; private var R1 = 0; private var R2 = 0; private var R3 = 0; private var R4 = 0xF
    private var P0 = 0; private var P1 = 0; private var P2 = 0; private var P3 = 0
    private var P0_OUT = 0; private var P1_OUT = 0; private var P2_OUT = 0; private var P3_OUT = 0
    private var CTRL_OSC = 0; private var CTRL_LCD = IO_ALOFF; private var LC = 0
    private var CTRL_SVD = IO_SVDDT; private var CTRL_BZ1 = 0; private var CTRL_BZ2 = 0
    private var CTRL_SW = 0; private var CTRL_PT = 0; private var PTC = 0
    private var SC = 0; private var HZR = 0; private var IOC = 0; private var PUP = 0

    // Clock divider
    private val osc1ClockDiv = clock.toDouble() / OSC1_CLOCK
    private var osc1Counter = 0.0
    private var timerCounter = 0.0
    private var ptimerCounter = 0.0
    private var stopwatchCounter = 0.0

    // Buzzer state (ported from BrickEmuPy E0C6200sound timing model)
    private var buzzerSoundOn = false
    private var buzzerFreqHz = OSC1_CLOCK / BUZZER_FREQ_DIV[0]
    private var buzzerEnvelopeOn = false
    private var buzzerEnvelopeStep = 7
    private var buzzerEnvelopeCycle = ENVELOPE_CYCLE_DIV[0]
    private var buzzerEnvelopeCounter = 0
    private var buzzerOneShotCounter = 0
    private var buzzerOutputOn = false
    private var buzzerOutputGain = 1f
    private var lastBuzzerEmitOn = false
    private var lastBuzzerEmitFreq = -1
    private var lastBuzzerEmitGain = -1f

    // Debug counters for interrupt/timer diagnostics.
    private var dbgInterruptCount = 0
    private var dbgLastInterruptVector = 0
    private var dbgIt32SetCount = 0
    private var dbgIt8SetCount = 0
    private var dbgIt2SetCount = 0
    private var dbgIt1SetCount = 0

    // Timing chain diagnostic counters (read by EmulatorLoop for rate analysis)
    private var diagOsc1Ticks = 0L
    private var diagTimerTicks = 0L
    private var diagStopwatchTicks = 0L
    private var diagClockCalls = 0L
    private var diagHaltCycles = 0L
    private var diagTotalExecCycles = 0L
    private var diagTotalElapsedCycles = 0.0

    // Buzzer callback (optional): on/off, frequency in Hz, gain [0..1]
    var onBuzzerChange: ((on: Boolean, freqHz: Int, gain: Float) -> Unit)? = null
    // Serial callback (optional): emitted when firmware writes a TX byte via SD registers.
    var onSerialTx: ((value: Int) -> Unit)? = null
    // Linked-port callback (optional): value=null means released (not actively driven).
    var onPortDriveChange: ((port: String, value: Int?) -> Unit)? = null
    // Optional I/O trace callback for emulator integration diagnostics.
    var onIoAccess: ((isWrite: Boolean, addr: Int, value: Int) -> Unit)? = null
    private var displayGeneration: Long = 1L
    private var sdWriteMask = 0
    private val lastDrivenPortValues = intArrayOf(-2, -2, -2, -2)
    private val linkedDrivenPortValues = intArrayOf(-1, -1, -1, -1)

    // 4096-entry dispatch table
    private val execute: Array<(Int) -> Int>

    // Register access tables for r/q operand encoding (0=A, 1=B, 2=M(X), 3=M(Y))
    private val getReg: Array<() -> Int> = arrayOf(
        { A }, { B }, { getMem(IX) }, { getMem(IY) }
    )
    private val setReg: Array<(Int) -> Unit> = arrayOf(
        { v -> A = v and 0xF }, { v -> B = v and 0xF },
        { v -> setMem(IX, v) }, { v -> setMem(IY, v) }
    )

    init {
        execute = buildDispatchTable()
    }

    private fun emitBuzzerStateIfChanged(force: Boolean = false) {
        val on = buzzerOutputOn && buzzerFreqHz > 0
        if (!force &&
            on == lastBuzzerEmitOn &&
            buzzerFreqHz == lastBuzzerEmitFreq &&
            kotlin.math.abs(buzzerOutputGain - lastBuzzerEmitGain) < 0.001f
        ) {
            return
        }
        lastBuzzerEmitOn = on
        lastBuzzerEmitFreq = buzzerFreqHz
        lastBuzzerEmitGain = buzzerOutputGain
        onBuzzerChange?.invoke(on, buzzerFreqHz, buzzerOutputGain)
    }

    private fun markDisplayDirty() {
        displayGeneration++
    }

    private fun setBuzzerOutput(on: Boolean, gain: Float = buzzerOutputGain) {
        buzzerOutputOn = on
        buzzerOutputGain = gain.coerceIn(0f, 1f)
        emitBuzzerStateIfChanged()
    }

    private fun updateBuzzerFreqFromCtrl() {
        val idx = (CTRL_BZ1 and IO_BZFQ).coerceIn(0, BUZZER_FREQ_DIV.lastIndex)
        buzzerFreqHz = OSC1_CLOCK / BUZZER_FREQ_DIV[idx]
        emitBuzzerStateIfChanged()
    }

    private fun setBuzzerOn() {
        buzzerSoundOn = true
        buzzerOneShotCounter = 0
        setBuzzerOutput(true, buzzerEnvelopeStep / 7f)
        if (buzzerEnvelopeOn) {
            buzzerEnvelopeCounter = buzzerEnvelopeCycle
        }
    }

    private fun setBuzzerOff() {
        buzzerSoundOn = false
        buzzerEnvelopeCounter = 0
        buzzerOneShotCounter = 0
        setBuzzerOutput(false, 0f)
    }

    private fun setBuzzerEnvelopeOn() {
        buzzerEnvelopeOn = true
        buzzerEnvelopeStep = 7
    }

    private fun setBuzzerEnvelopeOff() {
        buzzerEnvelopeOn = false
        buzzerEnvelopeStep = 7
        buzzerEnvelopeCounter = 0
        setBuzzerOutput(false, 0f)
    }

    private fun setBuzzerEnvelopeCycle(cycle: Int) {
        val idx = cycle.coerceIn(0, ENVELOPE_CYCLE_DIV.lastIndex)
        buzzerEnvelopeCycle = ENVELOPE_CYCLE_DIV[idx]
    }

    private fun resetBuzzerEnvelope() {
        buzzerEnvelopeStep = 7
    }

    private fun triggerBuzzerOneShot(longPulse: Boolean) {
        if (buzzerOneShotCounter != 0) return
        buzzerOneShotCounter = ONE_SHOT_PULSE_WIDTH_DIV[if (longPulse) 1 else 0]
        if (!buzzerSoundOn) {
            setBuzzerOutput(true, buzzerEnvelopeStep / 7f)
        }
    }

    private fun isOneShotRinging(): Boolean = buzzerOneShotCounter > 0

    private fun clockBuzzer() {
        if (buzzerOneShotCounter > 0) {
            buzzerOneShotCounter--
            if (buzzerOneShotCounter <= 0 && !buzzerSoundOn) {
                setBuzzerOutput(false, 0f)
            }
        }
        if (buzzerEnvelopeCounter > 0) {
            buzzerEnvelopeCounter--
            if (buzzerEnvelopeCounter <= 0) {
                if (buzzerEnvelopeStep > 0) {
                    setBuzzerOutput(true, buzzerEnvelopeStep / 7f)
                    buzzerEnvelopeStep--
                }
                buzzerEnvelopeCounter = buzzerEnvelopeCycle
            }
        }
    }

    // ========== Memory access ==========

    fun getMem(addr: Int): Int {
        return when {
            addr < RAM_SIZE -> RAM[addr]
            addr >= VRAM_PART1_OFFSET && addr < VRAM_PART1_OFFSET + VRAM_PART_SIZE ->
                VRAM[addr - VRAM_PART1_OFFSET]
            addr >= VRAM_PART2_OFFSET && addr < VRAM_PART2_OFFSET + VRAM_PART_SIZE ->
                VRAM[addr - VRAM_PART2_OFFSET + VRAM_PART_SIZE]
            addr >= IORAM_OFFSET && addr < IORAM_OFFSET + 0x7F ->
                readIoReg(addr)
            else -> 0
        }
    }

    fun setMem(addr: Int, value: Int) {
        val nibble = value and 0xF
        when {
            addr < RAM_SIZE -> RAM[addr] = nibble
            addr >= VRAM_PART1_OFFSET && addr < VRAM_PART1_OFFSET + VRAM_PART_SIZE -> {
                val index = addr - VRAM_PART1_OFFSET
                if (VRAM[index] != nibble) {
                    VRAM[index] = nibble
                    markDisplayDirty()
                }
            }
            addr >= VRAM_PART2_OFFSET && addr < VRAM_PART2_OFFSET + VRAM_PART_SIZE -> {
                val index = addr - VRAM_PART2_OFFSET + VRAM_PART_SIZE
                if (VRAM[index] != nibble) {
                    VRAM[index] = nibble
                    markDisplayDirty()
                }
            }
            addr >= IORAM_OFFSET && addr < IORAM_OFFSET + 0x7F ->
                writeIoReg(addr, value)
        }
    }

    private fun romWord(addr: Int): Int {
        if (romSize == 0) return 0
        val a0 = addr % romSize
        val a1 = (addr + 1) % romSize
        return ((rom[a0].toInt() and 0xFF) shl 8) or (rom[a1].toInt() and 0xFF)
    }

    // ========== I/O register read/write ==========

    private fun readIoReg(addr: Int): Int {
        val result = when (addr) {
            0xF00 -> { val r = IT; IT = 0; r }
            0xF01 -> { val r = ISW; ISW = 0; r }
            0xF02 -> { val r = IPT; IPT = 0; r }
            0xF03 -> { val r = ISIO; ISIO = 0; r }
            0xF04 -> { val r = IK0; IK0 = 0; r }
            0xF05 -> { val r = IK1; IK1 = 0; r }
            0xF10 -> EIT; 0xF11 -> EISW; 0xF12 -> EIPT; 0xF13 -> EISIO; 0xF14 -> EIK0; 0xF15 -> EIK1
            0xF20 -> TM and 0xF; 0xF21 -> (TM shr 4) and 0xF
            0xF22 -> SWL and 0xF; 0xF23 -> SWH and 0xF
            0xF24 -> PT and 0xF; 0xF25 -> (PT shr 4) and 0xF
            0xF26 -> RD and 0xF; 0xF27 -> (RD shr 4) and 0xF
            0xF30 -> SD and 0xF; 0xF31 -> (SD shr 4) and 0xF
            0xF40 -> K0; 0xF41 -> DFK0; 0xF42 -> K1
            0xF50 -> R0; 0xF51 -> R1; 0xF52 -> R2; 0xF53 -> R3; 0xF54 -> R4
            0xF60 -> readLinkedEffectivePort(P0, 0)
            0xF61 -> readLinkedEffectivePort(P1, 1)
            0xF62 -> readLinkedEffectivePort(P2, 2)
            0xF63 -> readLinkedEffectivePort(P3, 3)
            0xF70 -> CTRL_OSC; 0xF71 -> CTRL_LCD; 0xF72 -> LC; 0xF73 -> 0
            0xF74 -> CTRL_BZ1
            0xF75 -> (CTRL_BZ2 and (IO_ENVRT or IO_ENVON)) or (if (isOneShotRinging()) IO_BZSHOT else 0)
            0xF77 -> CTRL_SW and IO_SWRUN; 0xF78 -> CTRL_PT and IO_PTRUN
            0xF79 -> PTC; 0xF7D -> IOC; 0xF7E -> PUP
            else -> 0
        }
        onIoAccess?.invoke(false, addr, result and 0xF)
        return result
    }

    private fun writeIoReg(addr: Int, value: Int) {
        onIoAccess?.invoke(true, addr, value and 0xF)
        when (addr) {
            0xF10 -> EIT = value
            0xF11 -> EISW = value and 0x3
            0xF12 -> EIPT = value and 0x1
            0xF13 -> EISIO = value and 0x1
            0xF14 -> EIK0 = value
            0xF15 -> EIK1 = value
            0xF26 -> RD = (RD and 0xF0) or (value and 0x0F)
            0xF27 -> RD = (RD and 0x0F) or ((value shl 4) and 0xF0)
            0xF30 -> {
                SD = (SD and 0xF0) or (value and 0x0F)
                sdWriteMask = sdWriteMask or 0x1
                if (sdWriteMask == 0x3) {
                    emitSerialTxByte(SD)
                    sdWriteMask = 0
                }
            }
            0xF31 -> {
                SD = (SD and 0x0F) or ((value shl 4) and 0xF0)
                sdWriteMask = sdWriteMask or 0x2
                if (sdWriteMask == 0x3) {
                    emitSerialTxByte(SD)
                    sdWriteMask = 0
                }
            }
            0xF41 -> DFK0 = value
            0xF50 -> R0 = value; 0xF51 -> R1 = value; 0xF52 -> R2 = value; 0xF53 -> R3 = value
            0xF54 -> {
                R4 = value
                if ((value and IO_R43) != 0) setBuzzerOff() else setBuzzerOn()
            }
            0xF60 -> { P0_OUT = value; if (IOC and IO_IOC0 != 0) P0 = value }
            0xF61 -> { P1_OUT = value; if (IOC and IO_IOC1 != 0) P1 = value }
            0xF62 -> { P2_OUT = value; if (IOC and IO_IOC2 != 0) P2 = value }
            0xF63 -> {
                P3_OUT = value
                if (IOC and IO_IOC3 != 0 || p3Dedicated) P3 = value
            }
            0xF70 -> CTRL_OSC = value
            0xF71 -> {
                if (CTRL_LCD != value) {
                    CTRL_LCD = value
                    markDisplayDirty()
                }
            }
            0xF72 -> LC = value
            0xF74 -> {
                CTRL_BZ1 = value
                updateBuzzerFreqFromCtrl()
            }
            0xF75 -> {
                CTRL_BZ2 = value and (IO_ENVRT or IO_ENVON)
                setBuzzerEnvelopeCycle(if ((value and IO_ENVRT) != 0) 1 else 0)
                if ((value and IO_BZSHOT) != 0) {
                    triggerBuzzerOneShot((CTRL_BZ1 and IO_SHOTPW) != 0)
                }
                if ((value and IO_ENVON) != 0) {
                    setBuzzerEnvelopeOn()
                } else {
                    setBuzzerEnvelopeOff()
                }
                if ((value and IO_ENVRST) != 0) {
                    resetBuzzerEnvelope()
                }
            }
            0xF76 -> { if (value and IO_TMRST != 0) TM = 0 }
            0xF77 -> {
                if (value and IO_SWRST != 0) { SWL = 0; SWH = 0 }
                CTRL_SW = value and IO_SWRUN
            }
            0xF78 -> {
                if (value and IO_PTRST != 0) PT = RD
                CTRL_PT = value and IO_PTRUN
            }
            0xF79 -> PTC = value
            0xF7D -> {
                IOC = value
                if (IOC and IO_IOC0 != 0) P0 = P0_OUT else applyLinkedInputPort(0)
                if (IOC and IO_IOC1 != 0) P1 = P1_OUT else applyLinkedInputPort(1)
                if (IOC and IO_IOC2 != 0) P2 = P2_OUT else applyLinkedInputPort(2)
                if (IOC and IO_IOC3 != 0) P3 = P3_OUT else applyLinkedInputPort(3)
                emitAllDrivenPortsIfChanged()
            }
            0xF7E -> PUP = value
        }
        when (addr) {
            0xF60 -> emitDrivenPortIfChanged(0)
            0xF61 -> emitDrivenPortIfChanged(1)
            0xF62 -> emitDrivenPortIfChanged(2)
            0xF63 -> emitDrivenPortIfChanged(3)
        }
    }

    private fun emitSerialTxByte(value: Int) {
        onSerialTx?.invoke(value and 0xFF)
    }

    private fun computeDrivenPortValue(portIndex: Int): Int {
        return when (portIndex) {
            0 -> if ((IOC and IO_IOC0) != 0) (P0_OUT and 0xF) else -1
            1 -> if ((IOC and IO_IOC1) != 0) (P1_OUT and 0xF) else -1
            2 -> if ((IOC and IO_IOC2) != 0) (P2_OUT and 0xF) else -1
            3 -> if ((IOC and IO_IOC3) != 0 || p3Dedicated) (P3_OUT and 0xF) else -1
            else -> -1
        }
    }

    private fun emitDrivenPortIfChanged(portIndex: Int) {
        if (portIndex !in 0..3) return
        val driven = computeDrivenPortValue(portIndex)
        if (lastDrivenPortValues[portIndex] == driven) return
        lastDrivenPortValues[portIndex] = driven
        val portName = when (portIndex) {
            0 -> "P0"
            1 -> "P1"
            2 -> "P2"
            else -> "P3"
        }
        onPortDriveChange?.invoke(portName, if (driven >= 0) driven else null)
    }

    private fun emitAllDrivenPortsIfChanged() {
        emitDrivenPortIfChanged(0)
        emitDrivenPortIfChanged(1)
        emitDrivenPortIfChanged(2)
        emitDrivenPortIfChanged(3)
    }

    private fun isPortInput(portIndex: Int): Boolean {
        return when (portIndex) {
            0 -> (IOC and IO_IOC0) == 0
            1 -> (IOC and IO_IOC1) == 0
            2 -> (IOC and IO_IOC2) == 0
            3 -> (IOC and IO_IOC3) == 0 && !p3Dedicated
            else -> false
        }
    }

    private fun getLinkedPortInputValue(portIndex: Int): Int {
        val linked = if (portIndex in 0..3) linkedDrivenPortValues[portIndex] else -1
        return if (linked >= 0) linked and 0xF else 0xF
    }

    private fun applyLinkedInputPort(portIndex: Int) {
        if (!isPortInput(portIndex)) return
        val value = getLinkedPortInputValue(portIndex)
        when (portIndex) {
            0 -> P0 = value
            1 -> P1 = value
            2 -> P2 = value
            3 -> P3 = value
        }
    }

    private fun readLinkedEffectivePort(localValue: Int, portIndex: Int): Int {
        val linked = getLinkedPortInputValue(portIndex)
        return (localValue and linked) and 0xF
    }

    /**
     * Inject a serial RX byte from an external link peer.
     * This latches into RD and raises ISIO so firmware can handle it via interrupt vector 0xA.
     */
    @Synchronized
    fun receiveSerialByte(value: Int) {
        RD = value and 0xFF
        ISIO = ISIO or 0x1
    }

    /**
     * Apply remote linked-port drive. null means released/high.
     */
    @Synchronized
    fun applyLinkedPortDrive(port: String, value: Int?) {
        val portIndex = when (port) {
            "P0" -> 0
            "P1" -> 1
            "P2" -> 2
            "P3" -> 3
            else -> return
        }
        linkedDrivenPortValues[portIndex] = if (value == null) -1 else value and 0xF
        applyLinkedInputPort(portIndex)
    }

    @Synchronized
    fun syncLinkedPortDriveState() {
        emitAllDrivenPortsIfChanged()
    }

    // ========== Button input ==========

    @Synchronized
    fun pinSet(port: String, pin: Int, level: Int) {
        when (port) {
            "K0" -> {
                val newK0 = (K0 and (1 shl pin).inv()) or (level shl pin)
                if (EIK0 != 0 && (DFK0 shr pin) != level && (K0 shr pin) != level) {
                    IK0 = IK0 or IO_IK0
                }
                if (pin == 3 && (PTC and IO_PTC) < 2 && (DFK0 shr pin) != level && (K0 shr pin) != level) {
                    processPtimer()
                }
                K0 = newK0
            }
            "K1" -> {
                val newK1 = (K1 and (1 shl pin).inv()) or (level shl pin)
                if (EIK1 != 0 && level == 0 && (K1 shr pin) != level) {
                    IK1 = IK1 or IO_IK1
                }
                K1 = newK1
            }
            "P0" -> if (IOC and IO_IOC0 == 0) P0 = (P0 and (1 shl pin).inv()) or (level shl pin)
            "P1" -> if (IOC and IO_IOC1 == 0) P1 = (P1 and (1 shl pin).inv()) or (level shl pin)
            "P2" -> if (IOC and IO_IOC2 == 0) P2 = (P2 and (1 shl pin).inv()) or (level shl pin)
            "P3" -> if (IOC and IO_IOC3 == 0 && !p3Dedicated) P3 = (P3 and (1 shl pin).inv()) or (level shl pin)
            "RES" -> { resetCpu(); RESET = 1 }
        }
    }

    @Synchronized
    fun pinRelease(port: String, pin: Int) {
        when (port) {
            "K0" -> {
                val level = (portPullupK0 shr pin) and 0x1
                val newK0 = (K0 and (1 shl pin).inv()) or (level shl pin)
                if (EIK0 != 0 && (DFK0 shr pin) != level && (K0 shr pin) != level) {
                    IK0 = IK0 or IO_IK0
                }
                if (pin == 3 && (PTC and IO_PTC) < 2 && (DFK0 shr pin) != level && (K0 shr pin) != level) {
                    processPtimer()
                }
                K0 = newK0
            }
            "K1" -> {
                val level = (portPullupK1 shr pin) and 0x1
                val newK1 = (K1 and (1 shl pin).inv()) or (level shl pin)
                if (EIK1 != 0 && level == 0 && (K1 shr pin) != level) {
                    IK1 = IK1 or IO_IK1
                }
                K1 = newK1
            }
            "P0" -> if (IOC and IO_IOC0 == 0) P0 = (P0 and (1 shl pin).inv()) or (PUP and IO_PUP0)
            "P1" -> if (IOC and IO_IOC1 == 0) P1 = (P1 and (1 shl pin).inv()) or (PUP and IO_PUP1)
            "P2" -> if (IOC and IO_IOC2 == 0) P2 = (P2 and (1 shl pin).inv()) or (PUP and IO_PUP2)
            "P3" -> if (IOC and IO_IOC3 == 0 && !p3Dedicated) P3 = (P3 and (1 shl pin).inv()) or (PUP and IO_PUP3)
            "RES" -> RESET = 0
        }
    }

    // ========== VRAM access for display ==========

    @Synchronized
    fun getVRAM(): IntArray {
        return buildDisplayVram()
    }

    @Synchronized
    fun getDisplaySnapshot(lastGeneration: Long): DisplaySnapshot? {
        if (displayGeneration == lastGeneration) return null
        return DisplaySnapshot(displayGeneration, buildDisplayVram())
    }

    private fun buildDisplayVram(): IntArray {
        if ((CTRL_LCD and IO_ALOFF) != 0 || RESET != 0) return IntArray(VRAM_SIZE)
        if ((CTRL_LCD and IO_ALON) != 0) return IntArray(VRAM_SIZE) { 0xF }
        // Return VRAM + port values appended (P0,P1,P2,P3,R0,R1,R2,R4)
        val result = IntArray(VRAM_SIZE + 8)
        System.arraycopy(VRAM, 0, result, 0, VRAM_SIZE)
        result[VRAM_SIZE] = P0; result[VRAM_SIZE + 1] = P1
        result[VRAM_SIZE + 2] = P2; result[VRAM_SIZE + 3] = P3
        result[VRAM_SIZE + 4] = R0; result[VRAM_SIZE + 5] = R1
        result[VRAM_SIZE + 6] = R2; result[VRAM_SIZE + 7] = R4
        return result
    }

    // ========== Timer peripherals ==========

    private fun processPtimer() {
        PT = (PT - 1) and 0xFF
        if (PT == 0) { PT = RD; IPT = IPT or IO_IPT }
        if (PTC and IO_PTCOUT != 0) R3 = R3 xor IO_R33
    }

    private fun processStopwatch() {
        diagStopwatchTicks++
        if (CTRL_SW and IO_SWRUN != 0) {
            SWL = (SWL + 1) % 10
            if (SWL == 0) {
                SWH = (SWH + 1) % 10
                ISW = ISW or IO_ISW1
                if (SWH == 0) ISW = ISW or IO_ISW0
            }
        }
    }

    private fun processTimer() {
        diagTimerTicks++
        val newTM = (TM + 1) and 0xFF
        if ((newTM and IO_TM2) < (TM and IO_TM2)) {
            IT = IT or IO_IT32
            dbgIt32SetCount++
        }
        if (((newTM shr 4) and IO_TM4) < ((TM shr 4) and IO_TM4)) {
            IT = IT or IO_IT8
            dbgIt8SetCount++
        }
        if (((newTM shr 4) and IO_TM6) < ((TM shr 4) and IO_TM6)) {
            IT = IT or IO_IT2
            dbgIt2SetCount++
        }
        if (((newTM shr 4) and IO_TM7) < ((TM shr 4) and IO_TM7)) {
            IT = IT or IO_IT1
            dbgIt1SetCount++
        }
        TM = newTM
    }

    private fun clockOSC1() {
        diagOsc1Ticks++
        clockBuzzer()
        if ((PTC and IO_PTC) > 1) {
            ptimerCounter -= 1
            if (ptimerCounter <= 0) {
                ptimerCounter += PTIMER_CLOCK_DIV[PTC and IO_PTC]
                processPtimer()
            }
        }
        stopwatchCounter -= 1
        if (stopwatchCounter <= 0) {
            stopwatchCounter += STOPWATCH_CLOCK_DIV
            processStopwatch()
        }
        timerCounter -= 1
        if (timerCounter <= 0) {
            timerCounter += TIMER_CLOCK_DIV
            processTimer()
        }
    }

    // ========== Interrupt ==========

    private fun interrupt(vector: Int): Int {
        dbgInterruptCount++
        dbgLastInterruptVector = vector
        setMem((SP - 1) and 0xFF, (PC shr 8) and 0x0F)
        setMem((SP - 2) and 0xFF, (PC shr 4) and 0x0F)
        SP = (SP - 3) and 0xFF
        setMem(SP, PC and 0x0F)
        IF = 0; HALT = 0
        PC = (NPC and 0x1000) or 0x0100 or vector
        NPC = PC
        return 13
    }

    // ========== Main clock ==========

    private fun clockInternal(): Double {
        var execCycles = 7
        var elapsedCycles = execCycles.toDouble()
        diagClockCalls++
        if (RESET == 0) {
            if (HALT == 0) {
                ifDelay = false
                val opcode = romWord(PC * 2)
                execCycles = execute[opcode](opcode)
                instrCounter++
            } else {
                diagHaltCycles++
            }
            if (IF != 0 && !ifDelay) {
                if (IPT and EIPT != 0) execCycles += interrupt(0xC)
                else if (ISIO and EISIO != 0) execCycles += interrupt(0xA)
                else if (IK1 != 0) execCycles += interrupt(0x8)
                else if (IK0 != 0) execCycles += interrupt(0x6)
                else if (ISW and EISW != 0) execCycles += interrupt(0x4)
                else if (IT and EIT != 0) execCycles += interrupt(0x2)
            }
            elapsedCycles = execCycles.toDouble()
            diagTotalExecCycles += execCycles
            if (CTRL_OSC and IO_CLKCHG == 0) elapsedCycles *= osc1ClockDiv
            diagTotalElapsedCycles += elapsedCycles
            osc1Counter -= elapsedCycles
            while (osc1Counter <= 0) {
                osc1Counter += osc1ClockDiv
                clockOSC1()
            }
        }
        return elapsedCycles
    }

    fun clock(): Double = clockInternal()

    /**
     * Execute until at least [targetCycles] have elapsed and return actual executed cycles.
     * This batches monitor acquisition and lowers hot-path overhead versus repeated clock() calls.
     */
    fun runForCycles(targetCycles: Double): Double {
        if (targetCycles <= 0.0) return 0.0
        var elapsed = 0.0
        while (elapsed < targetCycles) {
            elapsed += clockInternal()
        }
        return elapsed
    }

    /**
     * Fast-forward the OSC1 oscillator by [ticks] ticks WITHOUT executing
     * CPU instructions. Advances all timer/stopwatch/buzzer subsystems
     * correctly, as if the CPU had been HALTed for the equivalent time.
     *
     * Used by EmulatorLoop to keep internal clocks in sync when wall-clock
     * resync drops CPU execution time (e.g., Android throttling during idle).
     *
     * @param ticks Number of OSC1 ticks (32768 Hz) to advance
     * @return The number of OSC1 ticks actually processed
     */
    fun fastForwardOsc1(ticks: Long): Long {
        if (ticks <= 0) return 0
        // Safety cap: 10 minutes at 32768 Hz = 19,660,800 ticks
        val cappedTicks = ticks.coerceAtMost(32768L * 600)
        for (i in 0 until cappedTicks) {
            clockOSC1()
        }
        // Update elapsed-cycle diagnostics for drift tracking accuracy
        diagTotalElapsedCycles += cappedTicks.toDouble() * osc1ClockDiv
        return cappedTicks
    }

    @Synchronized
    fun resetCpu() {
        A = 0; B = 0; IX = 0; IY = 0; SP = 0
        PC = 0x100; NPC = 0x100
        CF = 0; ZF = 0; DF = 0; IF = 0
        HALT = 0; ifDelay = false
        RAM.fill(0); VRAM.fill(0)
        P0_OUT = 0; P1_OUT = 0; P2_OUT = 0; P3_OUT = 0
        IT = 0; ISW = 0; IPT = 0; ISIO = 0; IK0 = 0; IK1 = 0
        EIT = 0; EISW = 0; EIPT = 0; EISIO = 0; EIK0 = 0; EIK1 = 0
        TM = 0; SWL = 0; SWH = 0; PT = 0; RD = 0; SD = 0
        sdWriteMask = 0
        lastDrivenPortValues.fill(-2)
        linkedDrivenPortValues.fill(-1)
        K0 = portPullupK0; DFK0 = 0xF; K1 = portPullupK1
        R0 = 0; R1 = 0; R2 = 0; R3 = 0; R4 = 0xF
        P0 = 0; P1 = 0; P2 = 0; P3 = 0
        CTRL_OSC = 0; CTRL_LCD = IO_ALOFF; LC = 0
        CTRL_SVD = IO_SVDDT; CTRL_BZ1 = 0; CTRL_BZ2 = 0
        CTRL_SW = 0; CTRL_PT = 0; PTC = 0
        SC = 0; HZR = 0; IOC = 0; PUP = 0
        osc1Counter = 0.0; timerCounter = 0.0; stopwatchCounter = 0.0; ptimerCounter = 0.0
        buzzerSoundOn = false
        buzzerFreqHz = OSC1_CLOCK / BUZZER_FREQ_DIV[0]
        buzzerEnvelopeOn = false
        buzzerEnvelopeStep = 7
        buzzerEnvelopeCycle = ENVELOPE_CYCLE_DIV[0]
        buzzerEnvelopeCounter = 0
        buzzerOneShotCounter = 0
        buzzerOutputOn = false
        buzzerOutputGain = 1f
        lastBuzzerEmitOn = false
        lastBuzzerEmitFreq = -1
        lastBuzzerEmitGain = -1f
        dbgInterruptCount = 0
        dbgLastInterruptVector = 0
        dbgIt32SetCount = 0
        dbgIt8SetCount = 0
        dbgIt2SetCount = 0
        dbgIt1SetCount = 0
        emitBuzzerStateIfChanged(force = true)
        emitAllDrivenPortsIfChanged()
        instrCounter = 0
        markDisplayDirty()
    }

    // ========== State serialization ==========

    @Synchronized
    fun getState(): Map<String, Any> = mapOf(
        "A" to A, "B" to B, "IX" to IX, "IY" to IY, "SP" to SP,
        "PC" to PC, "NPC" to NPC,
        "CF" to CF, "ZF" to ZF, "DF" to DF, "IF" to IF,
        "HALT" to HALT, "RAM" to RAM.copyOf(), "VRAM" to VRAM.copyOf(),
        "IT" to IT, "ISW" to ISW, "IPT" to IPT, "ISIO" to ISIO,
        "IK0" to IK0, "IK1" to IK1,
        "EIT" to EIT, "EISW" to EISW, "EIPT" to EIPT, "EISIO" to EISIO,
        "EIK0" to EIK0, "EIK1" to EIK1,
        "TM" to TM, "SWL" to SWL, "SWH" to SWH, "PT" to PT, "RD" to RD, "SD" to SD,
        "K0" to K0, "DFK0" to DFK0, "K1" to K1,
        "R0" to R0, "R1" to R1, "R2" to R2, "R3" to R3, "R4" to R4,
        "P0" to P0, "P1" to P1, "P2" to P2, "P3" to P3,
        "P0_OUT" to P0_OUT, "P1_OUT" to P1_OUT, "P2_OUT" to P2_OUT, "P3_OUT" to P3_OUT,
        "CTRL_OSC" to CTRL_OSC, "CTRL_LCD" to CTRL_LCD, "LC" to LC,
        "CTRL_BZ1" to CTRL_BZ1, "CTRL_BZ2" to CTRL_BZ2,
        "CTRL_SW" to CTRL_SW, "CTRL_PT" to CTRL_PT, "PTC" to PTC,
        "IOC" to IOC, "PUP" to PUP,
        "DBG_INT_COUNT" to dbgInterruptCount,
        "DBG_INT_VEC" to dbgLastInterruptVector,
        "DBG_IT32_SET" to dbgIt32SetCount,
        "DBG_IT8_SET" to dbgIt8SetCount,
        "DBG_IT2_SET" to dbgIt2SetCount,
        "DBG_IT1_SET" to dbgIt1SetCount
    )

    /** Timing chain diagnostics for EmulatorLoop rate analysis. */
    fun getDiagnostics(): Map<String, Any> = mapOf(
        "osc1" to diagOsc1Ticks, "timer" to diagTimerTicks, "sw" to diagStopwatchTicks,
        "calls" to diagClockCalls, "halt" to diagHaltCycles,
        "execCyc" to diagTotalExecCycles, "elapsedCyc" to diagTotalElapsedCycles,
        "TM" to TM, "CTRL_OSC" to CTRL_OSC, "CTRL_SW" to CTRL_SW,
        "EIT" to EIT, "HALT" to HALT, "SWL" to SWL, "SWH" to SWH
    )

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun restoreState(state: Map<String, Any>) {
        A = state["A"] as? Int ?: 0; B = state["B"] as? Int ?: 0
        IX = state["IX"] as? Int ?: 0; IY = state["IY"] as? Int ?: 0
        SP = state["SP"] as? Int ?: 0; PC = state["PC"] as? Int ?: 0x100; NPC = state["NPC"] as? Int ?: 0x100
        CF = state["CF"] as? Int ?: 0; ZF = state["ZF"] as? Int ?: 0
        DF = state["DF"] as? Int ?: 0; IF = state["IF"] as? Int ?: 0
        HALT = state["HALT"] as? Int ?: 0
        (state["RAM"] as? IntArray)?.copyInto(RAM)
        (state["VRAM"] as? IntArray)?.copyInto(VRAM)
        IT = state["IT"] as? Int ?: 0; ISW = state["ISW"] as? Int ?: 0
        IPT = state["IPT"] as? Int ?: 0; ISIO = state["ISIO"] as? Int ?: 0
        IK0 = state["IK0"] as? Int ?: 0; IK1 = state["IK1"] as? Int ?: 0
        EIT = state["EIT"] as? Int ?: 0; EISW = state["EISW"] as? Int ?: 0
        EIPT = state["EIPT"] as? Int ?: 0; EISIO = state["EISIO"] as? Int ?: 0
        EIK0 = state["EIK0"] as? Int ?: 0; EIK1 = state["EIK1"] as? Int ?: 0
        TM = state["TM"] as? Int ?: 0; SWL = state["SWL"] as? Int ?: 0; SWH = state["SWH"] as? Int ?: 0
        PT = state["PT"] as? Int ?: 0; RD = state["RD"] as? Int ?: 0; SD = state["SD"] as? Int ?: 0
        K0 = state["K0"] as? Int ?: portPullupK0; DFK0 = state["DFK0"] as? Int ?: 0xF
        K1 = state["K1"] as? Int ?: portPullupK1
        R0 = state["R0"] as? Int ?: 0; R1 = state["R1"] as? Int ?: 0; R2 = state["R2"] as? Int ?: 0
        R3 = state["R3"] as? Int ?: 0; R4 = state["R4"] as? Int ?: 0xF
        P0 = state["P0"] as? Int ?: 0; P1 = state["P1"] as? Int ?: 0
        P2 = state["P2"] as? Int ?: 0; P3 = state["P3"] as? Int ?: 0
        P0_OUT = state["P0_OUT"] as? Int ?: P0
        P1_OUT = state["P1_OUT"] as? Int ?: P1
        P2_OUT = state["P2_OUT"] as? Int ?: P2
        P3_OUT = state["P3_OUT"] as? Int ?: P3
        CTRL_OSC = state["CTRL_OSC"] as? Int ?: 0; CTRL_LCD = state["CTRL_LCD"] as? Int ?: IO_ALOFF
        LC = state["LC"] as? Int ?: 0; CTRL_BZ1 = state["CTRL_BZ1"] as? Int ?: 0
        CTRL_BZ2 = state["CTRL_BZ2"] as? Int ?: 0; CTRL_SW = state["CTRL_SW"] as? Int ?: 0
        CTRL_PT = state["CTRL_PT"] as? Int ?: 0; PTC = state["PTC"] as? Int ?: 0
        IOC = state["IOC"] as? Int ?: 0; PUP = state["PUP"] as? Int ?: 0
        sdWriteMask = 0
        lastDrivenPortValues.fill(-2)
        linkedDrivenPortValues.fill(-1)
        updateBuzzerFreqFromCtrl()
        setBuzzerEnvelopeCycle(if ((CTRL_BZ2 and IO_ENVRT) != 0) 1 else 0)
        if ((CTRL_BZ2 and IO_ENVON) != 0) {
            setBuzzerEnvelopeOn()
        } else {
            setBuzzerEnvelopeOff()
        }
        if ((R4 and IO_R43) == 0) {
            setBuzzerOn()
        } else {
            setBuzzerOff()
        }
        emitAllDrivenPortsIfChanged()
        markDisplayDirty()
    }

    // ========== PC advance helper ==========

    private fun advPC() { PC = (PC and 0x1000) or ((PC + 1) and 0xFFF); NPC = PC }

    // ========== Instruction dispatch table ==========

    private fun buildDispatchTable(): Array<(Int) -> Int> {
        val table = Array<(Int) -> Int>(4096) { { 5 } }  // default NOP

        // 0x000-0x0FF: JP s
        for (i in 0x000..0x0FF) table[i] = ::jpS
        // 0x100-0x1FF: RETD l
        for (i in 0x100..0x1FF) table[i] = ::retdL
        // 0x200-0x2FF: JP C,s
        for (i in 0x200..0x2FF) table[i] = ::jpCS
        // 0x300-0x3FF: JP NC,s
        for (i in 0x300..0x3FF) table[i] = ::jpNCS
        // 0x400-0x4FF: CALL s
        for (i in 0x400..0x4FF) table[i] = ::callS
        // 0x500-0x5FF: CALZ s
        for (i in 0x500..0x5FF) table[i] = ::calzS
        // 0x600-0x6FF: JP Z,s
        for (i in 0x600..0x6FF) table[i] = ::jpZS
        // 0x700-0x7FF: JP NZ,s
        for (i in 0x700..0x7FF) table[i] = ::jpNZS
        // 0x800-0x8FF: LD Y,y
        for (i in 0x800..0x8FF) table[i] = ::ldYY
        // 0x900-0x9FF: LBPX M(X),l
        for (i in 0x900..0x9FF) table[i] = ::lbpxMXL
        // 0xA00-0xA0F: ADC XH,i
        for (i in 0xA00..0xA0F) table[i] = ::adcXHI
        // 0xA10-0xA1F: ADC XL,i
        for (i in 0xA10..0xA1F) table[i] = ::adcXLI
        // 0xA20-0xA2F: ADC YH,i
        for (i in 0xA20..0xA2F) table[i] = ::adcYHI
        // 0xA30-0xA3F: ADC YL,i
        for (i in 0xA30..0xA3F) table[i] = ::adcYLI
        // 0xA40-0xA4F: CP XH,i
        for (i in 0xA40..0xA4F) table[i] = ::cpXHI
        // 0xA50-0xA5F: CP XL,i
        for (i in 0xA50..0xA5F) table[i] = ::cpXLI
        // 0xA60-0xA6F: CP YH,i
        for (i in 0xA60..0xA6F) table[i] = ::cpYHI
        // 0xA70-0xA7F: CP YL,i
        for (i in 0xA70..0xA7F) table[i] = ::cpYLI
        // 0xA80-0xA8F: ADD r,q
        for (i in 0xA80..0xA8F) table[i] = ::addRQ
        // 0xA90-0xA9F: ADC r,q
        for (i in 0xA90..0xA9F) table[i] = ::adcRQ
        // 0xAA0-0xAAF: SUB r,q
        for (i in 0xAA0..0xAAF) table[i] = ::subRQ
        // 0xAB0-0xABF: SBC r,q
        for (i in 0xAB0..0xABF) table[i] = ::sbcRQ
        // 0xAC0-0xACF: AND r,q
        for (i in 0xAC0..0xACF) table[i] = ::andRQ
        // 0xAD0-0xADF: OR r,q
        for (i in 0xAD0..0xADF) table[i] = ::orRQ
        // 0xAE0-0xAEF: XOR r,q
        for (i in 0xAE0..0xAEF) table[i] = ::xorRQ
        // 0xAF0-0xAFF: RLC r
        for (i in 0xAF0..0xAFF) table[i] = ::rlcR
        // 0xB00-0xBFF: LD X,x
        for (i in 0xB00..0xBFF) table[i] = ::ldXX
        // 0xC00-0xC3F: ADD r,i
        for (i in 0xC00..0xC3F) table[i] = ::addRI
        // 0xC40-0xC7F: ADC r,i
        for (i in 0xC40..0xC7F) table[i] = ::adcRI
        // 0xC80-0xCBF: AND r,i
        for (i in 0xC80..0xCBF) table[i] = ::andRI
        // 0xCC0-0xCFF: OR r,i
        for (i in 0xCC0..0xCFF) table[i] = ::orRI
        // 0xD00-0xD3F: XOR r,i
        for (i in 0xD00..0xD3F) table[i] = ::xorRI
        // 0xD40-0xD7F: SBC r,i
        for (i in 0xD40..0xD7F) table[i] = ::sbcRI
        // 0xD80-0xDBF: FAN r,i
        for (i in 0xD80..0xDBF) table[i] = ::fanRI
        // 0xDC0-0xDFF: CP r,i
        for (i in 0xDC0..0xDFF) table[i] = ::cpRI
        // 0xE00-0xE3F: LD r,i
        for (i in 0xE00..0xE3F) table[i] = ::ldRI
        // 0xE40-0xE5F: PSET p
        for (i in 0xE40..0xE5F) table[i] = ::psetP
        // 0xE60-0xE6F: LDPX M(X),i
        for (i in 0xE60..0xE6F) table[i] = ::ldpxMXI
        // 0xE70-0xE7F: LDPY M(Y),i
        for (i in 0xE70..0xE7F) table[i] = ::ldpyMYI
        // 0xE80-0xE83: LD XP,r
        for (i in 0xE80..0xE83) table[i] = ::ldXPR
        // 0xE84-0xE87: LD XH,r
        for (i in 0xE84..0xE87) table[i] = ::ldXHR
        // 0xE88-0xE8B: LD XL,r
        for (i in 0xE88..0xE8B) table[i] = ::ldXLR
        // 0xE8C-0xE8F: RRC r
        for (i in 0xE8C..0xE8F) table[i] = ::rrcR
        // 0xE90-0xE93: LD YP,r
        for (i in 0xE90..0xE93) table[i] = ::ldYPR
        // 0xE94-0xE97: LD YH,r
        for (i in 0xE94..0xE97) table[i] = ::ldYHR
        // 0xE98-0xE9B: LD YL,r
        for (i in 0xE98..0xE9B) table[i] = ::ldYLR
        // 0xEA0-0xEA3: LD r,XP
        for (i in 0xEA0..0xEA3) table[i] = ::ldRXP
        // 0xEA4-0xEA7: LD r,XH
        for (i in 0xEA4..0xEA7) table[i] = ::ldRXH
        // 0xEA8-0xEAB: LD r,XL
        for (i in 0xEA8..0xEAB) table[i] = ::ldRXL
        // 0xEB0-0xEB3: LD r,YP
        for (i in 0xEB0..0xEB3) table[i] = ::ldRYP
        // 0xEB4-0xEB7: LD r,YH
        for (i in 0xEB4..0xEB7) table[i] = ::ldRYH
        // 0xEB8-0xEBB: LD r,YL
        for (i in 0xEB8..0xEBB) table[i] = ::ldRYL
        // 0xEC0-0xECF: LD r,q
        for (i in 0xEC0..0xECF) table[i] = ::ldRQ
        // 0xEE0-0xEEF: LDPX r,q
        for (i in 0xEE0..0xEEF) table[i] = ::ldpxRQ
        // 0xEF0-0xEFF: LDPY r,q
        for (i in 0xEF0..0xEFF) table[i] = ::ldpyRQ
        // 0xF00-0xF0F: CP r,q
        for (i in 0xF00..0xF0F) table[i] = ::cpRQ
        // 0xF10-0xF1F: FAN r,q
        for (i in 0xF10..0xF1F) table[i] = ::fanRQ
        // 0xF28-0xF2B: ACPX M(X),r
        for (i in 0xF28..0xF2B) table[i] = ::acpxMXR
        // 0xF2C-0xF2F: ACPY M(Y),r (corrected: bits 1100-1111)
        for (i in 0xF2C..0xF2F) table[i] = ::acpyMYR
        // 0xF38-0xF3B: SCPX M(X),r
        for (i in 0xF38..0xF3B) table[i] = ::scpxMXR
        // 0xF3C-0xF3F: SCPY M(Y),r
        for (i in 0xF3C..0xF3F) table[i] = ::scpyMYR
        // 0xF40-0xF4F: SET F,i
        for (i in 0xF40..0xF4F) table[i] = ::setFI
        // 0xF50-0xF5F: RST F,i
        for (i in 0xF50..0xF5F) table[i] = ::rstFI
        // 0xF60-0xF6F: INC M(n)
        for (i in 0xF60..0xF6F) table[i] = ::incMN
        // 0xF70-0xF7F: DEC M(n)
        for (i in 0xF70..0xF7F) table[i] = ::decMN
        // 0xF80-0xF8F: LD M(n),A
        for (i in 0xF80..0xF8F) table[i] = ::ldMNA
        // 0xF90-0xF9F: LD M(n),B
        for (i in 0xF90..0xF9F) table[i] = ::ldMNB
        // 0xFA0-0xFAF: LD A,M(n)
        for (i in 0xFA0..0xFAF) table[i] = ::ldAMN
        // 0xFB0-0xFBF: LD B,M(n)
        for (i in 0xFB0..0xFBF) table[i] = ::ldBMN
        // 0xFC0-0xFC3: PUSH r
        for (i in 0xFC0..0xFC3) table[i] = ::pushR
        table[0xFC4] = ::pushXP; table[0xFC5] = ::pushXH; table[0xFC6] = ::pushXL
        table[0xFC7] = ::pushYP; table[0xFC8] = ::pushYH; table[0xFC9] = ::pushYL
        table[0xFCA] = ::pushF; table[0xFCB] = ::decSP
        // 0xFD0-0xFD3: POP r
        for (i in 0xFD0..0xFD3) table[i] = ::popR
        table[0xFD4] = ::popXP; table[0xFD5] = ::popXH; table[0xFD6] = ::popXL
        table[0xFD7] = ::popYP; table[0xFD8] = ::popYH; table[0xFD9] = ::popYL
        table[0xFDA] = ::popF; table[0xFDB] = ::incSP
        table[0xFDE] = ::rets; table[0xFDF] = ::ret
        // 0xFE0-0xFE3: LD SPH,r
        for (i in 0xFE0..0xFE3) table[i] = ::ldSPHR
        // 0xFE4-0xFE7: LD r,SPH
        for (i in 0xFE4..0xFE7) table[i] = ::ldRSPH
        table[0xFE8] = ::jpba
        // 0xFF0-0xFF3: LD SPL,r
        for (i in 0xFF0..0xFF3) table[i] = ::ldSPLR
        // 0xFF4-0xFF7: LD r,SPL
        for (i in 0xFF4..0xFF7) table[i] = ::ldRSPL
        table[0xFF8] = ::haltInstr
        table[0xFFB] = ::nop5
        table[0xFFF] = ::nop7

        return table
    }

    // ========== Instruction implementations ==========

    private fun jpS(op: Int): Int { PC = (NPC and 0x1F00) or (op and 0xFF); return 5 }

    private fun retdL(op: Int): Int {
        PC = (PC and 0x1000) or (RAM[SP + 2] shl 8) or (RAM[SP + 1] shl 4) or RAM[SP]
        NPC = PC; SP = (SP + 3) and 0xFF
        setMem(IX, op and 0x00F)
        setMem((IX and 0xF00) or ((IX + 1) and 0xFF), (op shr 4) and 0x00F)
        IX = (IX and 0xF00) or ((IX + 2) and 0xFF); return 12
    }

    private fun jpCS(op: Int): Int {
        if (CF != 0) PC = (NPC and 0x1F00) or (op and 0xFF) else advPC(); return 5
    }
    private fun jpNCS(op: Int): Int {
        if (CF == 0) PC = (NPC and 0x1F00) or (op and 0xFF) else advPC(); return 5
    }

    private fun callS(op: Int): Int {
        setMem((SP - 1) and 0xFF, ((PC + 1) shr 8) and 0x0F)
        setMem((SP - 2) and 0xFF, ((PC + 1) shr 4) and 0x0F)
        SP = (SP - 3) and 0xFF
        setMem(SP, (PC + 1) and 0x0F)
        PC = (NPC and 0x1F00) or (op and 0xFF); return 7
    }
    private fun calzS(op: Int): Int {
        setMem((SP - 1) and 0xFF, ((PC + 1) shr 8) and 0x0F)
        setMem((SP - 2) and 0xFF, ((PC + 1) shr 4) and 0x0F)
        SP = (SP - 3) and 0xFF
        setMem(SP, (PC + 1) and 0x0F)
        PC = (NPC and 0x1000) or (op and 0xFF); NPC = PC; return 7
    }

    private fun jpZS(op: Int): Int {
        if (ZF != 0) PC = (NPC and 0x1F00) or (op and 0xFF) else advPC(); return 5
    }
    private fun jpNZS(op: Int): Int {
        if (ZF == 0) PC = (NPC and 0x1F00) or (op and 0xFF) else advPC(); return 5
    }

    private fun ldYY(op: Int): Int { IY = (IY and 0xF00) or (op and 0xFF); advPC(); return 5 }

    private fun lbpxMXL(op: Int): Int {
        setMem(IX, op and 0x00F)
        setMem((IX and 0xF00) or ((IX + 1) and 0xFF), (op shr 4) and 0x00F)
        IX = (IX and 0xF00) or ((IX + 2) and 0xFF); advPC(); return 5
    }

    private fun adcXHI(op: Int): Int {
        val r = ((IX shr 4) and 0xF) + (op and 0xF) + CF
        ZF = if (r and 0xF == 0) 1 else 0; CF = if (r > 15) 1 else 0
        IX = (IX and 0xF0F) or ((r and 0xF) shl 4); advPC(); return 7
    }
    private fun adcXLI(op: Int): Int {
        val r = (IX and 0xF) + (op and 0xF) + CF
        ZF = if (r and 0xF == 0) 1 else 0; CF = if (r > 15) 1 else 0
        IX = (IX and 0xFF0) or (r and 0xF); advPC(); return 7
    }
    private fun adcYHI(op: Int): Int {
        val r = ((IY shr 4) and 0xF) + (op and 0xF) + CF
        ZF = if (r and 0xF == 0) 1 else 0; CF = if (r > 15) 1 else 0
        IY = (IY and 0xF0F) or ((r and 0xF) shl 4); advPC(); return 7
    }
    private fun adcYLI(op: Int): Int {
        val r = (IY and 0xF) + (op and 0xF) + CF
        ZF = if (r and 0xF == 0) 1 else 0; CF = if (r > 15) 1 else 0
        IY = (IY and 0xFF0) or (r and 0xF); advPC(); return 7
    }

    private fun cpXHI(op: Int): Int {
        val r = ((IX shr 4) and 0xF) - (op and 0xF)
        ZF = if (r == 0) 1 else 0; CF = if (r < 0) 1 else 0; advPC(); return 7
    }
    private fun cpXLI(op: Int): Int {
        val r = (IX and 0xF) - (op and 0xF)
        ZF = if (r == 0) 1 else 0; CF = if (r < 0) 1 else 0; advPC(); return 7
    }
    private fun cpYHI(op: Int): Int {
        val r = ((IY shr 4) and 0xF) - (op and 0xF)
        ZF = if (r == 0) 1 else 0; CF = if (r < 0) 1 else 0; advPC(); return 7
    }
    private fun cpYLI(op: Int): Int {
        val r = (IY and 0xF) - (op and 0xF)
        ZF = if (r == 0) 1 else 0; CF = if (r < 0) 1 else 0; advPC(); return 7
    }

    // Arithmetic r,q instructions (r = bits 3:2, q = bits 1:0)
    private fun addRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        var res = getReg[ri]() + getReg[qi]()
        CF = if (res > 15) 1 else 0
        if (DF != 0 && res > 9) { res += 6; CF = 1 }
        ZF = if (res and 0xF == 0) 1 else 0; setReg[ri](res and 0xF); advPC(); return 7
    }
    private fun adcRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        var res = getReg[ri]() + getReg[qi]() + CF
        CF = if (res > 15) 1 else 0
        if (DF != 0 && res > 9) { res += 6; CF = 1 }
        ZF = if (res and 0xF == 0) 1 else 0; setReg[ri](res and 0xF); advPC(); return 7
    }
    private fun subRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        var res = getReg[ri]() - getReg[qi]()
        CF = if (res < 0) 1 else 0
        if (DF != 0 && res < 0) res += 10
        ZF = if (res and 0xF == 0) 1 else 0; setReg[ri](res and 0xF); advPC(); return 7
    }
    private fun sbcRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        var res = getReg[ri]() - getReg[qi]() - CF
        CF = if (res < 0) 1 else 0
        if (DF != 0 && res < 0) res += 10
        ZF = if (res and 0xF == 0) 1 else 0; setReg[ri](res and 0xF); advPC(); return 7
    }
    private fun andRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        val res = getReg[ri]() and getReg[qi]()
        ZF = if (res == 0) 1 else 0; setReg[ri](res); advPC(); return 7
    }
    private fun orRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        val res = getReg[ri]() or getReg[qi]()
        ZF = if (res == 0) 1 else 0; setReg[ri](res); advPC(); return 7
    }
    private fun xorRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        val res = getReg[ri]() xor getReg[qi]()
        ZF = if (res == 0) 1 else 0; setReg[ri](res); advPC(); return 7
    }
    private fun rlcR(op: Int): Int {
        val ri = op and 3
        val res = (getReg[ri]() shl 1) + CF
        CF = if (res > 15) 1 else 0; setReg[ri](res and 0xF); advPC(); return 7
    }

    private fun ldXX(op: Int): Int { IX = (IX and 0xF00) or (op and 0xFF); advPC(); return 5 }

    // Arithmetic r,i instructions (r = bits 5:4, i = bits 3:0)
    private fun addRI(op: Int): Int {
        val ri = (op shr 4) and 3
        var res = getReg[ri]() + (op and 0xF)
        CF = if (res > 15) 1 else 0
        if (DF != 0 && res > 9) { res += 6; CF = 1 }
        ZF = if (res and 0xF == 0) 1 else 0; setReg[ri](res and 0xF); advPC(); return 7
    }
    private fun adcRI(op: Int): Int {
        val ri = (op shr 4) and 3
        var res = getReg[ri]() + (op and 0xF) + CF
        CF = if (res > 15) 1 else 0
        if (DF != 0 && res > 9) { res += 6; CF = 1 }
        ZF = if (res and 0xF == 0) 1 else 0; setReg[ri](res and 0xF); advPC(); return 7
    }
    private fun andRI(op: Int): Int {
        val ri = (op shr 4) and 3
        val res = getReg[ri]() and (op and 0xF)
        ZF = if (res == 0) 1 else 0; setReg[ri](res); advPC(); return 7
    }
    private fun orRI(op: Int): Int {
        val ri = (op shr 4) and 3
        val res = getReg[ri]() or (op and 0xF)
        ZF = if (res == 0) 1 else 0; setReg[ri](res); advPC(); return 7
    }
    private fun xorRI(op: Int): Int {
        val ri = (op shr 4) and 3
        val res = getReg[ri]() xor (op and 0xF)
        ZF = if (res == 0) 1 else 0; setReg[ri](res); advPC(); return 7
    }
    private fun sbcRI(op: Int): Int {
        val ri = (op shr 4) and 3
        var res = getReg[ri]() - (op and 0xF) - CF
        CF = if (res < 0) 1 else 0
        if (DF != 0 && CF != 0) res += 10
        ZF = if (res and 0xF == 0) 1 else 0; setReg[ri](res and 0xF); advPC(); return 7
    }
    private fun fanRI(op: Int): Int {
        val ri = (op shr 4) and 3
        ZF = if (getReg[ri]() and (op and 0xF) == 0) 1 else 0; advPC(); return 7
    }
    private fun cpRI(op: Int): Int {
        val ri = (op shr 4) and 3
        val r = getReg[ri]() - (op and 0xF)
        ZF = if (r == 0) 1 else 0; CF = if (r < 0) 1 else 0; advPC(); return 7
    }
    private fun ldRI(op: Int): Int {
        val ri = (op shr 4) and 3; setReg[ri](op and 0xF); advPC(); return 5
    }

    private fun psetP(op: Int): Int {
        ifDelay = true; NPC = (op and 0x1F) shl 8
        PC = (PC and 0x1000) or ((PC + 1) and 0xFFF); return 5
    }

    private fun ldpxMXI(op: Int): Int {
        setMem(IX, op and 0xF); IX = (IX and 0xF00) or ((IX + 1) and 0xFF); advPC(); return 5
    }
    private fun ldpyMYI(op: Int): Int {
        setMem(IY, op and 0xF); IY = (IY and 0xF00) or ((IY + 1) and 0xFF); advPC(); return 5
    }

    private fun ldXPR(op: Int): Int {
        IX = (getReg[op and 3]() shl 8) or (IX and 0x0FF); advPC(); return 5
    }
    private fun ldXHR(op: Int): Int {
        IX = (getReg[op and 3]() shl 4) or (IX and 0xF0F); advPC(); return 5
    }
    private fun ldXLR(op: Int): Int {
        IX = getReg[op and 3]() or (IX and 0xFF0); advPC(); return 5
    }
    private fun rrcR(op: Int): Int {
        val ri = op and 3; val v = getReg[ri]() + (CF shl 4)
        CF = v and 1; setReg[ri](v shr 1); advPC(); return 5
    }

    private fun ldYPR(op: Int): Int {
        IY = (getReg[op and 3]() shl 8) or (IY and 0x0FF); advPC(); return 5
    }
    private fun ldYHR(op: Int): Int {
        IY = (getReg[op and 3]() shl 4) or (IY and 0xF0F); advPC(); return 5
    }
    private fun ldYLR(op: Int): Int {
        IY = getReg[op and 3]() or (IY and 0xFF0); advPC(); return 5
    }

    private fun ldRXP(op: Int): Int { setReg[op and 3](IX shr 8); advPC(); return 5 }
    private fun ldRXH(op: Int): Int { setReg[op and 3]((IX shr 4) and 0xF); advPC(); return 5 }
    private fun ldRXL(op: Int): Int { setReg[op and 3](IX and 0xF); advPC(); return 5 }
    private fun ldRYP(op: Int): Int { setReg[op and 3](IY shr 8); advPC(); return 5 }
    private fun ldRYH(op: Int): Int { setReg[op and 3]((IY shr 4) and 0xF); advPC(); return 5 }
    private fun ldRYL(op: Int): Int { setReg[op and 3](IY and 0xF); advPC(); return 5 }

    private fun ldRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        setReg[ri](getReg[qi]()); advPC(); return 5
    }
    private fun ldpxRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        setReg[ri](getReg[qi]()); IX = (IX and 0xF00) or ((IX + 1) and 0xFF); advPC(); return 5
    }
    private fun ldpyRQ(op: Int): Int {
        val ri = (op shr 2) and 3; val qi = op and 3
        setReg[ri](getReg[qi]()); IY = (IY and 0xF00) or ((IY + 1) and 0xFF); advPC(); return 5
    }

    private fun cpRQ(op: Int): Int {
        val r = getReg[(op shr 2) and 3]() - getReg[op and 3]()
        ZF = if (r == 0) 1 else 0; CF = if (r < 0) 1 else 0; advPC(); return 7
    }
    private fun fanRQ(op: Int): Int {
        ZF = if (getReg[(op shr 2) and 3]() and getReg[op and 3]() == 0) 1 else 0; advPC(); return 7
    }

    private fun acpxMXR(op: Int): Int {
        var res = getMem(IX) + getReg[op and 3]() + CF
        CF = if (res > 15) 1 else 0
        if (DF != 0 && res > 9) { res += 6; CF = 1 }
        ZF = if (res and 0xF == 0) 1 else 0
        setMem(IX, res and 0xF); IX = (IX and 0xF00) or ((IX + 1) and 0xFF); advPC(); return 7
    }
    private fun acpyMYR(op: Int): Int {
        var res = getMem(IY) + getReg[op and 3]() + CF
        CF = if (res > 15) 1 else 0
        if (DF != 0 && res > 9) { res += 6; CF = 1 }
        ZF = if (res and 0xF == 0) 1 else 0
        setMem(IY, res and 0xF); IY = (IY and 0xF00) or ((IY + 1) and 0xFF); advPC(); return 7
    }
    private fun scpxMXR(op: Int): Int {
        var res = getMem(IX) - getReg[op and 3]() - CF
        CF = if (res < 0) 1 else 0
        if (DF != 0 && res < 0) res += 10
        ZF = if (res and 0xF == 0) 1 else 0
        setMem(IX, res and 0xF); IX = (IX and 0xF00) or ((IX + 1) and 0xFF); advPC(); return 7
    }
    private fun scpyMYR(op: Int): Int {
        var res = getMem(IY) - getReg[op and 3]() - CF
        CF = if (res < 0) 1 else 0
        if (DF != 0 && res < 0) res += 10
        ZF = if (res and 0xF == 0) 1 else 0
        setMem(IY, res and 0xF); IY = (IY and 0xF00) or ((IY + 1) and 0xFF); advPC(); return 7
    }

    private fun setFI(op: Int): Int {
        CF = CF or (op and 1); ZF = ZF or ((op shr 1) and 1); DF = DF or ((op shr 2) and 1)
        val newIF = (op shr 3) and 1
        ifDelay = newIF != 0 && IF == 0; IF = IF or newIF; advPC(); return 7
    }
    private fun rstFI(op: Int): Int {
        CF = CF and op; ZF = ZF and (op shr 1); DF = DF and (op shr 2); IF = IF and (op shr 3)
        advPC(); return 7
    }

    private fun incMN(op: Int): Int {
        val n = op and 0xF; val res = getMem(n) + 1
        ZF = if (res == 16) 1 else 0; CF = if (res > 15) 1 else 0
        setMem(n, res and 0xF); advPC(); return 7
    }
    private fun decMN(op: Int): Int {
        val n = op and 0xF; val res = getMem(n) - 1
        ZF = if (res == 0) 1 else 0; CF = if (res < 0) 1 else 0
        setMem(n, res and 0xF); advPC(); return 7
    }

    private fun ldMNA(op: Int): Int { setMem(op and 0xF, A); advPC(); return 5 }
    private fun ldMNB(op: Int): Int { setMem(op and 0xF, B); advPC(); return 5 }
    private fun ldAMN(op: Int): Int { A = getMem(op and 0xF); advPC(); return 5 }
    private fun ldBMN(op: Int): Int { B = getMem(op and 0xF); advPC(); return 5 }

    private fun pushR(op: Int): Int {
        SP = (SP - 1) and 0xFF; setMem(SP, getReg[op and 3]()); advPC(); return 5
    }
    private fun pushXP(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        SP = (SP - 1) and 0xFF; setMem(SP, IX shr 8); advPC(); return 5
    }
    private fun pushXH(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        SP = (SP - 1) and 0xFF; setMem(SP, (IX shr 4) and 0xF); advPC(); return 5
    }
    private fun pushXL(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        SP = (SP - 1) and 0xFF; setMem(SP, IX and 0xF); advPC(); return 5
    }
    private fun pushYP(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        SP = (SP - 1) and 0xFF; setMem(SP, IY shr 8); advPC(); return 5
    }
    private fun pushYH(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        SP = (SP - 1) and 0xFF; setMem(SP, (IY shr 4) and 0xF); advPC(); return 5
    }
    private fun pushYL(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        SP = (SP - 1) and 0xFF; setMem(SP, IY and 0xF); advPC(); return 5
    }
    private fun pushF(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        SP = (SP - 1) and 0xFF
        setMem(SP, (IF shl 3) or (DF shl 2) or (ZF shl 1) or CF); advPC(); return 5
    }
    private fun decSP(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        SP = (SP - 1) and 0xFF; advPC(); return 5
    }

    private fun popR(op: Int): Int {
        setReg[op and 3](getMem(SP)); SP = (SP + 1) and 0xFF; advPC(); return 5
    }
    private fun popXP(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        IX = (getMem(SP) shl 8) or (IX and 0x0FF); SP = (SP + 1) and 0xFF; advPC(); return 5
    }
    private fun popXH(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        IX = (getMem(SP) shl 4) or (IX and 0xF0F); SP = (SP + 1) and 0xFF; advPC(); return 5
    }
    private fun popXL(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        IX = getMem(SP) or (IX and 0xFF0); SP = (SP + 1) and 0xFF; advPC(); return 5
    }
    private fun popYP(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        IY = (getMem(SP) shl 8) or (IY and 0x0FF); SP = (SP + 1) and 0xFF; advPC(); return 5
    }
    private fun popYH(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        IY = (getMem(SP) shl 4) or (IY and 0xF0F); SP = (SP + 1) and 0xFF; advPC(); return 5
    }
    private fun popYL(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        IY = getMem(SP) or (IY and 0xFF0); SP = (SP + 1) and 0xFF; advPC(); return 5
    }
    private fun popF(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        val f = getMem(SP)
        CF = f and 1; ZF = (f shr 1) and 1; DF = (f shr 2) and 1
        val newIF = (f shr 3) and 1
        ifDelay = newIF != 0 && IF == 0; IF = newIF
        SP = (SP + 1) and 0xFF; advPC(); return 5
    }
    private fun incSP(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        SP = (SP + 1) and 0xFF; advPC(); return 5
    }

    private fun rets(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        PC = (PC and 0x1000) or getMem(SP) or (getMem(SP + 1) shl 4) or (getMem(SP + 2) shl 8)
        SP = (SP + 3) and 0xFF; advPC(); return 12
    }
    private fun ret(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        PC = (PC and 0x1000) or getMem(SP) or (getMem(SP + 1) shl 4) or (getMem(SP + 2) shl 8)
        NPC = PC; SP = (SP + 3) and 0xFF; return 7
    }

    private fun ldSPHR(op: Int): Int {
        SP = (getReg[op and 3]() shl 4) or (SP and 0x0F); advPC(); return 5
    }
    private fun ldRSPH(op: Int): Int { setReg[op and 3](SP shr 4); advPC(); return 5 }

    private fun jpba(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        PC = (NPC and 0x1F00) or (B shl 4) or A; return 5
    }

    private fun ldSPLR(op: Int): Int {
        SP = getReg[op and 3]() or (SP and 0xF0); advPC(); return 5
    }
    private fun ldRSPL(op: Int): Int { setReg[op and 3](SP and 0xF); advPC(); return 5 }

    private fun haltInstr(@Suppress("UNUSED_PARAMETER") op: Int): Int {
        HALT = 1; advPC(); return 5
    }
    private fun nop5(@Suppress("UNUSED_PARAMETER") op: Int): Int { advPC(); return 5 }
    private fun nop7(@Suppress("UNUSED_PARAMETER") op: Int): Int { advPC(); return 7 }
}
