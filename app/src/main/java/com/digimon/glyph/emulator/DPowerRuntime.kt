package com.digimon.glyph.emulator

import com.google.gson.*
import java.util.Base64
import kotlin.math.abs

/** Android-free runtime and portable Python schema 1/2 snapshots. */
class DPowerRuntime(private val rom: ByteArray) {
    var sound = DPowerSound(); private set
    var core = Spl61xCore(rom, sound); private set
    fun advance(target: Double = 2200.0) = core.runCycles(target)
    fun displayEnabled() = core._CLK32K_ENABLE != 0 && core._LCD_CTRL and 2 != 0
    fun rawLcd() = if (displayEnabled()) core._mem.copyOfRange(0x300, 0x380) else IntArray(128)
    fun display(): IntArray {
        val enabled = displayEnabled()
        return IntArray(DPowerLcd.address.size) { i ->
            if (enabled && core._mem[DPowerLcd.address[i]] and DPowerLcd.mask[i] != 0) 1 else 0
        }
    }
    fun ram() = core._mem.copyOfRange(0x40, 0x300)
    fun pin(port: String, pin: Int, level: Int) {
        require(port in listOf("EF", "CD", "RES") && pin in 0..7 && level in -1..1)
        core.port_handler(when(port) { "CD" -> 1; "EF" -> 2; else -> 3 }, 1 shl pin, level)
    }
    fun playback(): ByteArray {
        sound.flush()
        val data = sound.mixer.drain()
        return if (data.any { it != 0.toByte() }) data else byteArrayOf()
    }
    fun haptic(): Pair<Int, Float> {
        val tone = sound.mixer.channels.filterNotNull().filter { it.frequency > 0 }.maxByOrNull { abs(it.amplitude) }
        return if (tone == null) 0 to 0f else tone.frequency.toInt() to abs(tone.amplitude).toFloat().coerceIn(0f, 1f)
    }
    fun snapshot(): String {
        sound.flush()
        return JsonObject().also { o ->
            o.addProperty("schema", 2); o.addProperty("rom", ROM_SHA256); o.add("fields", core.fields())
            o.addProperty("ram", Base64.getEncoder().encodeToString(ByteArray(0x340) { core._mem[it + 0x40].toByte() }))
            o.add("sound", JsonObject().also { s ->
                s.add("envelope", JsonArray().also { a -> sound.envelope.forEach { a.add(it) } })
                s.add("toggle", JsonArray().also { a -> sound.toggle.forEach { a.add(it) } })
            })
            o.add("audio", JsonArray().also { a -> sound.mixer.channels.forEach { a.add(it?.json() ?: JsonNull.INSTANCE) } })
            o.add("pcm", sound.mixer.state())
        }.toString()
    }
    fun restore(payload: String) {
        require(payload.length <= 32768) { "Snapshot too large" }
        val state = JsonParser.parseString(payload).asJsonObject
        require(state["schema"].isJsonPrimitive && state["schema"].asJsonPrimitive.isNumber)
        val schema = state["schema"].asInt
        require(state["schema"].asDouble == schema.toDouble())
        val keys = mutableSetOf("schema", "rom", "fields", "ram", "sound", "audio")
        if (schema == 2) keys.add("pcm")
        require(state.keySet() == keys && schema in 1..2 && state["rom"].asString == ROM_SHA256)
        val fresh = DPowerRuntime(rom)
        fun number(x: JsonElement): Double {
            require(x.isJsonPrimitive && (x.asJsonPrimitive.isNumber || x.asJsonPrimitive.isBoolean))
            return if (x.asJsonPrimitive.isBoolean) { if (x.asBoolean) 1.0 else 0.0 } else x.asDouble
        }
        fun check(value: JsonElement, template: JsonElement, fractional: Boolean = false) {
            when {
                template.isJsonObject -> {
                    require(value.isJsonObject && value.asJsonObject.keySet() == template.asJsonObject.keySet())
                    template.asJsonObject.entrySet().forEach { (k,t) -> check(value.asJsonObject[k], t, k.endsWith("_counter") || k.endsWith("_div")) }
                }
                template.isJsonArray -> {
                    require(value.isJsonArray && value.asJsonArray.size() == template.asJsonArray.size())
                    template.asJsonArray.forEachIndexed { i,t -> check(value.asJsonArray[i], t) }
                }
                else -> {
                    val n = number(value)
                    require(n.isFinite() && abs(n) <= 9007199254740992.0)
                    if (!fractional && !template.asJsonPrimitive.asString.contains('.')) {
                        require(value.asJsonPrimitive.isBoolean || value.asJsonPrimitive.asString.matches(Regex("-?[0-9]+"))) { "Integer register required" }
                    }
                }
            }
        }
        val fields = state["fields"].asJsonObject
        val template = fresh.core.fields()
        check(fields, template)
        for (key in listOf("_clock", "_sub_clock_div", "_mem_size", "_pullup_ext")) require(fields[key] == template[key]) { "Device configuration changed" }
        require(number(fields["_CPU_DIV"]) in listOf(2.0,4.0,8.0,16.0))
        require(number(fields["_TM0_div"]) >= 0 && number(fields["_TM1_div"]) >= 0)
        if (fields["_TIMER_CTRL"].asInt and 128 != 0) require(number(fields["_TM1_div"]) > 0)
        mapOf("_PC" to 65535,"_A" to 255,"_X" to 255,"_Y" to 255,"_SP" to 255,"_BANK" to 3).forEach { (k, limit) -> require(number(fields[k]) in 0.0..limit.toDouble()) }
        val soundState = state["sound"].asJsonObject
        check(soundState, JsonParser.parseString("{\"envelope\":[0.0,0.0],\"toggle\":[1,1]}"))
        val encodedRam = state["ram"].asString
        require(encodedRam.length % 4 == 0)
        val ram = Base64.getDecoder().decode(encodedRam)
        require(ram.size == 0x340)
        val audio = state["audio"].asJsonArray
        require(audio.size() == 2)
        for (i in 0..1) {
            val a = audio[i]
            if (!a.isJsonNull) {
                require(a.isJsonArray && a.asJsonArray.size() == 4)
                a.asJsonArray.forEach { require(number(it).isFinite()) }
                fresh.sound.mixer.channels[i] = DPowerMixer.Tone(number(a.asJsonArray[0]), number(a.asJsonArray[2]))
            }
            fresh.sound.envelope[i] = soundState["envelope"].asJsonArray[i].asDouble
            fresh.sound.toggle[i] = soundState["toggle"].asJsonArray[i].asInt
        }
        if (schema == 2) fresh.sound.mixer.restore(state["pcm"].asJsonObject)
        fresh.core.loadFields(fields)
        for (i in ram.indices) fresh.core._mem[i + 0x40] = ram[i].toInt() and 255
        core = fresh.core; sound = fresh.sound
    }
    companion object {
        const val ROM_SHA256 = "f1f0a92bdcbd87514b7c981ca126713ee7e050ec262fcbfe90e75d2e1bc852ca"
    }
}
