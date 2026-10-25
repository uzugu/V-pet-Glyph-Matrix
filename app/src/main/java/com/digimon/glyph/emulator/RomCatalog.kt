package com.digimon.glyph.emulator

import java.security.MessageDigest

object RomCatalog {
    const val MAX_ROM_BYTES = 98304
    const val DPOWER_HASH = "f1f0a92bdcbd87514b7c981ca126713ee7e050ec262fcbfe90e75d2e1bc852ca"
    data class Device(val id: String, val isDPower: Boolean = false)
    private val known = mapOf(
        "eae93f82b8cf6ec017a32188545fe96425f1ecf3584a74afa109a25a53c56cb9" to "DigimonV1JA",
        "eac3c98bf623cfcfb751b165e0707de43216b745e4abbf5621cd37e169dbb2d9" to "DigimonV1JB",
        "b8cf00494b84a3e71b30025d848ba10f9e792e7878afb750c29a9b7b06ef6765" to "DigimonV1ES",
        "9a8458c11c66d3e71a7b94bc426eb791a2404be1b8e4c925b42290a8cb3330c7" to "DigimonV2J",
        "059df7ee74de7e26a9df9f7f9d82bcd87af4c47af180728e44f3c3d056289db3" to "DigimonV3J",
        "3b7eae59a7469e941b6059a3c0b03123aabb13e4e7ea20bd02fabfff2320890e" to "DigimonV4J"
    )
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun isVerifiedClassicHash(hash: String?): Boolean = hash != null && known.containsKey(hash)

    /** Matches actual bytes only; caller-supplied profile names cannot enable RAM edits. */
    internal fun verifiedClassicId(bytes: ByteArray): String? = known[hash(bytes)]

    fun identify(bytes: ByteArray): Device {
        require(bytes.size in listOf(8192, 16384, MAX_ROM_BYTES)) { "Expected an 8/16 KB classic ROM or the 96 KB D-Power EU 1.01 ROM" }
        val hash = hash(bytes)
        if (hash == DPOWER_HASH) return Device("DPowerDigiviceEU101", true)
        require(bytes.size != MAX_ROM_BYTES) { "Unknown D-Power dump; only verified EU 1.01 is supported" }
        require(bytes.indices.step(2).all { (bytes[it].toInt() and 255) <= 15 }) { "Invalid classic ROM instruction encoding" }
        return Device(known[hash] ?: "UnknownClassic")
    }
}
