package com.digimon.glyph.emulator

import android.content.Context

/** Pinned upstream SPL61X engine. One clock call executes about 1 ms of firmware. */
class DPowerCore(context: Context, rom: ByteArray) : EmulatorCore {
    private val romBytes = rom.copyOf()
    override val clockFrequency = 2_200_000
    override var onBuzzerChange: ((Boolean, Int, Float) -> Unit)? = null
    override var onSerialTx: ((Int) -> Unit)? = null
    override var onPortDriveChange: ((String, Int?) -> Unit)? = null
    override var onIoAccess: ((Boolean, Int, Int) -> Unit)? = null
    private var runtime: DPowerRuntime
    private val autoStepper = com.digimon.glyph.input.AutoStepper()
    private val steps = com.digimon.glyph.input.StepPulses()
    @Synchronized fun step(): Boolean = steps.enqueue()
    private var hasShownLcd = false
    private var displaySleeping = false
    override fun isDisplaySleeping() = displaySleeping
    private var generation = 0L
    private var previous = IntArray(0)
    var onPcm: ((ByteArray) -> Unit)? = null
    var onAudioReset: (() -> Unit)? = null
    private var audioPoll = 0
    private var lastTone = Pair(0, 0f)

    init {
        require(RomCatalog.identify(rom).isDPower)
        DPowerOptions.init(context)
        runtime = DPowerRuntime(rom)
    }
    @Synchronized override fun clock(): Double = clock(2200.0)
    @Synchronized override fun clock(budgetCycles: Double): Double {
        runtime.core.idleSkippingEnabled = EmulatorDebugSettings.isDPowerIdleSkippingEnabled()
        val edge = if (steps.phase != 0 && steps.remaining > 0) steps.remaining.toDouble() else 2200.0
        val cycles = runtime.advance(minOf(2200.0, budgetCycles, edge).coerceAtLeast(1.0))
        if (autoStepper.advance(cycles, DPowerOptions.autoStepEnabled, DPowerOptions.halfStepsPerSecond)
            && steps.pending == 0 && steps.phase != 1) steps.enqueue()
        steps.advance(cycles.toInt()) { pressed -> runtime.pin("CD", 1, if (pressed) 1 else -1) }
        if (++audioPoll >= 10) {
            audioPoll = 0
            onPcm?.invoke(runtime.playback())
            val next = runtime.haptic()
            if (next != lastTone) {
                lastTone = next
                onBuzzerChange?.invoke(next.first > 0 && next.second > 0f, next.first, next.second)
            }
        }
        return cycles
    }
    @Synchronized override fun getVRAM(): IntArray = runtime.display()
    @Synchronized override fun getRAM(): IntArray = runtime.ram()
    @Synchronized override fun getRawVRAM(): IntArray = runtime.rawLcd()
    @Synchronized override fun getDisplaySnapshot(lastGeneration: Long): E0C6200.DisplaySnapshot? {
        val lcdEnabled = runtime.displayEnabled()
        if (lcdEnabled) hasShownLcd = true
        // A cold/reset core starts with LCD disabled: allow its boot sequence to run.
        displaySleeping = hasShownLcd && !lcdEnabled
        val pixels = getVRAM()
        if (!pixels.contentEquals(previous)) { previous = pixels; generation++ }
        return if (lastGeneration == generation) null else E0C6200.DisplaySnapshot(generation, pixels)
    }
    @Synchronized override fun getState(): Map<String, Any> = mapOf("CORE" to "SPL61X", "DPOWER_SNAPSHOT" to runtime.snapshot(),
        "STEP_PENDING" to steps.pending, "STEP_PHASE" to steps.phase, "STEP_REMAINING" to steps.remaining)
    @Synchronized override fun restoreState(state: Map<String, Any>) {
        require(state["CORE"] == "SPL61X") { "Snapshot belongs to another CPU" }
        val checked = com.digimon.glyph.input.StepPulses().apply {
            restore((state["STEP_PENDING"] as? Number)?.toInt() ?: 0,
                (state["STEP_PHASE"] as? Number)?.toInt() ?: 0,
                (state["STEP_REMAINING"] as? Number)?.toInt() ?: 0)
        }
        runtime.restore(state["DPOWER_SNAPSHOT"] as? String ?: error("Missing D-Power snapshot"))
        autoStepper.reset()
        onAudioReset?.invoke()
        audioPoll = 0
        steps.restore(checked.pending, checked.phase, checked.remaining)
        previous = IntArray(0)
        lastTone = Pair(-1, -1f)
    }
    override fun getDiagnostics(): Map<String, Any> = mapOf("core" to "SPL61X")
    override fun getCurrentDigimonState(romName: String?): DigimonState? = null
    @Synchronized override fun resetCpu() { hasShownLcd = false; displaySleeping = false; runtime = DPowerRuntime(romBytes); autoStepper.reset(); onAudioReset?.invoke(); audioPoll = 0; steps.restore(0, 0, 0); previous = IntArray(0); lastTone = Pair(-1, -1f) }
    @Synchronized override fun pinSet(port: String, pin: Int, level: Int) {
        // Existing three-button Glyph surface: A=left, B=up, C=right.
        if (port == "K0") {
            val mapped = when (pin) { 2 -> 0; 1 -> 4; 0 -> 1; else -> return }
            runtime.pin( "EF", mapped, if (level == 0) 1 else 0)
        } else runtime.pin( port, pin, level)
    }
    @Synchronized override fun pinRelease(port: String, pin: Int) {
        if (port == "K0") {
            val mapped = when (pin) { 2 -> 0; 1 -> 4; 0 -> 1; else -> return }
            runtime.pin( "EF", mapped, -1)
        } else runtime.pin( port, pin, -1)
    }
}
