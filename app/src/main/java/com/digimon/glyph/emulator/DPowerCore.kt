package com.digimon.glyph.emulator

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

/** Pinned upstream SPL61X engine. One clock call executes about 1 ms of firmware. */
class DPowerCore(context: Context, rom: ByteArray) : EmulatorCore {
    override val clockFrequency = 2_200_000
    override var onBuzzerChange: ((Boolean, Int, Float) -> Unit)? = null
    override var onSerialTx: ((Int) -> Unit)? = null
    override var onPortDriveChange: ((String, Int?) -> Unit)? = null
    override var onIoAccess: ((Boolean, Int, Int) -> Unit)? = null
    private val runtime: com.chaquo.python.PyObject
    private var generation = 0L
    private var previous = IntArray(0)
    private var audioPoll = 0
    private var lastTone = Pair(0, 0f)

    init {
        require(RomCatalog.identify(rom).isDPower)
        synchronized(Python::class.java) {
            if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
        }
        val file = File(context.filesDir, "dpower-eu101.bin")
        if (!file.exists() || !file.readBytes().contentEquals(rom)) file.writeBytes(rom)
        runtime = Python.getInstance().getModule("dpower_runtime").callAttr("Runtime", file.absolutePath)
    }
    @Synchronized override fun clock(): Double {
        val cycles = runtime.callAttr("advance").toDouble()
        if (++audioPoll >= 10) {
            audioPoll = 0
            val tone = runtime.callAttr("audio").asList()
            val next = Pair(tone[0].toDouble().toInt(), tone[1].toDouble().toFloat().coerceIn(0f, 1f))
            if (next != lastTone) {
                lastTone = next
                onBuzzerChange?.invoke(next.first > 0 && next.second > 0f, next.first, next.second)
            }
        }
        return cycles
    }
    @Synchronized override fun getVRAM(): IntArray = runtime.callAttr("display").toJava(IntArray::class.java)
    @Synchronized override fun getRAM(): IntArray = runtime.callAttr("ram").toJava(IntArray::class.java)
    @Synchronized override fun getRawVRAM(): IntArray = runtime.callAttr("raw_lcd").toJava(IntArray::class.java)
    @Synchronized override fun getDisplaySnapshot(lastGeneration: Long): E0C6200.DisplaySnapshot? {
        val pixels = getVRAM()
        if (!pixels.contentEquals(previous)) { previous = pixels; generation++ }
        return if (lastGeneration == generation) null else E0C6200.DisplaySnapshot(generation, pixels)
    }
    @Synchronized override fun getState(): Map<String, Any> = mapOf("CORE" to "SPL61X", "DPOWER_SNAPSHOT" to runtime.callAttr("snapshot").toString())
    @Synchronized override fun restoreState(state: Map<String, Any>) {
        require(state["CORE"] == "SPL61X") { "Snapshot belongs to another CPU" }
        runtime.callAttr("restore", state["DPOWER_SNAPSHOT"] as? String ?: error("Missing D-Power snapshot"))
        previous = IntArray(0)
        lastTone = Pair(-1, -1f)
    }
    override fun getDiagnostics(): Map<String, Any> = mapOf("core" to "SPL61X")
    override fun getCurrentDigimonState(romName: String?): DigimonState? = null
    @Synchronized override fun resetCpu() { runtime.callAttr("reset"); previous = IntArray(0); lastTone = Pair(-1, -1f) }
    @Synchronized override fun pinSet(port: String, pin: Int, level: Int) {
        // Existing three-button Glyph surface: A=left, B=up, C=right.
        if (port == "K0") {
            val mapped = when (pin) { 2 -> 0; 1 -> 4; 0 -> 1; else -> return }
            runtime.callAttr("pin", "EF", mapped, if (level == 0) 1 else 0)
        } else runtime.callAttr("pin", port, pin, level)
    }
    @Synchronized override fun pinRelease(port: String, pin: Int) {
        if (port == "K0") {
            val mapped = when (pin) { 2 -> 0; 1 -> 4; 0 -> 1; else -> return }
            runtime.callAttr("pin", "EF", mapped, -1)
        } else runtime.callAttr("pin", port, pin, -1)
    }
}
