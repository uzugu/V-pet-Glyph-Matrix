package com.digimon.glyph.sync

import com.digimon.glyph.emulator.EmulatorCore
import com.digimon.glyph.emulator.RomCatalog
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.UUID

/** Immutable handoff files. Payload stays a string so the checksum is identical across runtimes. */
object PortableSave {
    const val MAX_BYTES = 262144
    private val gson = com.google.gson.GsonBuilder().serializeNulls().create()
    data class Record(val schema: String = "vpet.save-exchange", val version: Int = 1,
        val id: String, val parent: String?, val origin: String, val createdMs: Long,
        val romHash: String, val profile: String, val core: String, val payload: String, val payloadSha256: String)
    fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun create(emulator: EmulatorCore, hash: String, profile: String, origin: String, parent: String?): Record {
        val payload = gson.toJson(emulator.getState())
        return Record(id = UUID.randomUUID().toString(), parent = parent, origin = origin,
            createdMs = System.currentTimeMillis(), romHash = hash, profile = profile,
            core = if (hash == RomCatalog.DPOWER_HASH) "SPL61X" else "E0C6200", payload = payload, payloadSha256 = digest(payload))
    }
    fun encode(record: Record): String = gson.toJson(record)
    fun decode(text: String): Record {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Save is too large" }
        val obj = JsonParser.parseString(text).asJsonObject
        require(obj.keySet() == setOf("schema","version","id","parent","origin","createdMs","romHash","profile","core","payload","payloadSha256")) { "Invalid save fields" }
        for (key in setOf("schema","id","origin","romHash","profile","core","payload","payloadSha256")) {
            require(obj[key].isJsonPrimitive && obj[key].asJsonPrimitive.isString) { "Invalid $key" }
        }
        require(obj["parent"].isJsonNull || obj["parent"].isJsonPrimitive && obj["parent"].asJsonPrimitive.isString) { "Invalid parent" }
        for (key in setOf("version", "createdMs")) require(obj[key].isJsonPrimitive && obj[key].asJsonPrimitive.isNumber) { "Invalid $key" }
        require(obj["version"].asString.toBigDecimal().intValueExact() == 1) { "Unsupported save version" }
        obj["createdMs"].asString.toBigDecimal().longValueExact()
        val r = gson.fromJson(obj, Record::class.java)
        require(r.schema == "vpet.save-exchange" && r.version == 1) { "Unsupported save format" }
        require(r.id.matches(Regex("[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}")) && (r.parent == null || r.parent.matches(Regex("[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}")))) { "Invalid save identity" }
        require(r.origin.matches(Regex("[a-zA-Z0-9_-]{1,64}")) && r.createdMs > 0) { "Invalid save metadata" }
        require(r.romHash.matches(Regex("[a-f0-9]{64}")) && r.payloadSha256 == digest(r.payload)) { "Save checksum failed" }
        require(r.profile in setOf("DigimonV1JA","DigimonV1JB","DigimonV1ES","DigimonV2J","DigimonV3J","DigimonV4J","DPowerEU101")) { "Unsupported ROM profile" }
        require(r.core == if (r.profile == "DPowerEU101") "SPL61X" else "E0C6200") { "Core/profile mismatch" }
        require((r.profile == "DPowerEU101") == (r.romHash == RomCatalog.DPOWER_HASH)) { "D-Power ROM mismatch" }
        return r
    }
    fun state(record: Record, emulator: EmulatorCore, expectedHash: String, expectedProfile: String): Map<String, Any> {
        require(record.romHash == expectedHash && record.profile == expectedProfile) { "Load the same ROM revision before importing" }
        val obj = JsonParser.parseString(record.payload).asJsonObject
        val template = emulator.getState()
        require(obj.keySet() == template.keys) { "Incompatible emulator state" }
        return template.mapValues { (key, value) ->
            val v = obj.get(key)
            when (value) {
                is IntArray -> { require(v.isJsonArray && v.asJsonArray.size() == value.size) { "Invalid $key" }; v.asJsonArray.map { exactInt(it.asString) }.toIntArray() }
                is Int -> exactInt(v.asString)
                is Double, is Float -> v.asDouble.also { require(it.isFinite()) { "Invalid $key" } }
                is String -> v.asString
                else -> error("Unsupported state field $key")
            }
        }
    }
    private fun exactInt(value: String): Int = value.toBigDecimal().intValueExact()
}
