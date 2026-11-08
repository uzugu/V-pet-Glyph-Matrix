package com.digimon.glyph.emulator

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import kotlin.math.*

/** Two-channel, 48 kHz PCM integrator matching dpower_audio.py. */
class DPowerMixer {
    data class Tone(val frequency: Double, val amplitude: Double) {
        fun json() = JsonArray().also { it.add(frequency); it.add(false); it.add(amplitude); it.add(0) }
    }
    val channels = arrayOfNulls<Tone>(2)
    val phase = DoubleArray(2)
    var fraction = 0.0
    var area = 0.0
    private val output = ByteArrayOutputStream(9600)
    fun change(channel: Int, value: Tone?) {
        if (channels[channel] == null && value != null) phase[channel] = 0.0
        channels[channel] = value
    }
    // Python round uses nearest-even, as does Math.rint.
    private fun sample() {
        val s = Math.rint(area.coerceIn(-1.0, 1.0) * 32767).toInt()
        output.write(s and 255); output.write((s shr 8) and 255)
        fraction = 0.0; area = 0.0
    }
    fun advance(cycles: Double) {
        var remaining = cycles * 48000 / 2200000
        if (channels[0] == null && channels[1] == null) {
            if (fraction > 0 && remaining > 0) {
                val width = min(remaining, 1 - fraction)
                fraction += width; remaining -= width
                if (fraction >= 1 - 1e-10) sample()
            }
            if (remaining > 1e-10) {
                val whole = (remaining + 1e-10).toInt()
                repeat(min(whole, 4800)) { output.write(0); output.write(0) }
                fraction = max(0.0, remaining - whole); area = 0.0
            }
        } else {
            while (remaining > 1e-10) {
                val width = min(remaining, 1 - fraction)
                var mixed = 0.0
                for (i in 0..1) {
                    val channel = channels[i] ?: continue
                    val value = if (channel.frequency > 0) {
                        val delta = channel.frequency * width / 48000
                        val p = phase[i]
                        val integrated = (cos(2 * PI * p) - cos(2 * PI * (p + delta))) / (2 * PI * delta)
                        phase[i] = (p + delta) % 1
                        integrated
                    } else 1.0
                    mixed += value * channel.amplitude * .5
                }
                area += mixed * width; fraction += width; remaining -= width
                if (fraction >= 1 - 1e-10) sample()
            }
        }
        if (output.size() > 9600) {
            val bytes = output.toByteArray(); output.reset(); output.write(bytes, bytes.size - 9600, 9600)
        }
    }
    fun drain(): ByteArray = output.toByteArray().also { output.reset() }
    fun state() = JsonObject().also { o ->
        o.add("phase", JsonArray().also { a -> phase.forEach { a.add(it) } })
        o.addProperty("fraction", fraction); o.addProperty("area", area)
    }
    fun restore(o: JsonObject) {
        require(o.keySet() == setOf("phase", "fraction", "area"))
        val p = o["phase"].asJsonArray
        require(p.size() == 2)
        for (n in listOf(p[0], p[1], o["fraction"], o["area"])) {
            require(n.isJsonPrimitive && n.asJsonPrimitive.isNumber)
        }
        val a = p[0].asDouble; val b = p[1].asDouble
        val f = o["fraction"].asDouble; val sum = o["area"].asDouble
        require(a.isFinite() && b.isFinite() && f.isFinite() && sum.isFinite())
        require(a >= 0 && a < 1 && b >= 0 && b < 1 && f >= 0 && f < 1 && abs(sum) <= 1)
        phase[0] = a; phase[1] = b; fraction = f; area = sum
    }
}

/** Hardware sound registers plus the reference bus's deferred audio timing. */
class DPowerSound {
    val mixer = DPowerMixer()
    val envelope = DoubleArray(2)
    val toggle = intArrayOf(1, 1)
    var totalCycles = 0.0
    var pendingCycles = 0.0
    var onEvent: ((Int, DPowerMixer.Tone?) -> Unit)? = null
    fun advanceAudio(cycles: Double) { pendingCycles += cycles; totalCycles += cycles }
    fun flush() { if (pendingCycles != 0.0) { mixer.advance(pendingCycles); pendingCycles = 0.0 } }
    private fun emit(channel: Int, tone: DPowerMixer.Tone?) {
        flush(); mixer.change(channel, tone); onEvent?.invoke(channel, tone)
    }
    fun set_data(channel: Int, ctrl: Int, data: Int) {
        if (ctrl and 0x40 != 0) {
            envelope[channel] = (data and 255) / 255.0
            if (data == 0) emit(channel, null)
        } else emit(channel, if (data != 0) DPowerMixer.Tone(0.0, (data and 255) / 128.0 - 1) else null)
    }
    fun tone(channel: Int, div: Double) {
        if (envelope[channel] != 0.0) emit(channel, DPowerMixer.Tone(2200000 / div, toggle[channel] * envelope[channel]))
    }
    fun stop(channel: Int) = emit(channel, null)
}
