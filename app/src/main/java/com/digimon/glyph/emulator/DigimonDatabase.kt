package com.digimon.glyph.emulator

data class DigimonInfo(val name: String, val stage: String)

data class DigimonState(
    val info: DigimonInfo?,
    val age: Int,
    val weight: Int,
    val hunger: Int? = null,
    val protein: Int? = null,
    val overfeed: Int? = null,
    val training: Int? = null,
    val careMistakes: Int? = null,
    val wins: Int? = null,
    val losses: Int? = null,
    val winRate: Int? = null,
    val careTimerMinutesLeft: Int? = null,
    val needsAttention: Boolean = false
)

object DigimonDatabase {

    private val v1Map = mapOf(
        0 to DigimonInfo("Egg", "Egg"),
        1 to DigimonInfo("Botamon", "Baby I"),
        2 to DigimonInfo("Koromon", "Baby II"),
        3 to DigimonInfo("Agumon", "Rookie"),
        4 to DigimonInfo("Betamon", "Rookie"),
        5 to DigimonInfo("Greymon", "Champion"),
        6 to DigimonInfo("Tyranomon", "Champion"),
        7 to DigimonInfo("Devimon", "Champion"),
        8 to DigimonInfo("Meramon", "Champion"),
        9 to DigimonInfo("Airdramon", "Champion"),
        0xA to DigimonInfo("Seadramon", "Champion"),
        0xB to DigimonInfo("Numemon", "Champion"),
        0xC to DigimonInfo("MetalGreymon", "Ultimate"),
        0xD to DigimonInfo("Monzaemon", "Ultimate"),
        0xE to DigimonInfo("Mamemon", "Ultimate"),
        0xF to DigimonInfo("Death", "Dead")
    )

    private val v2Map = mapOf(
        0 to DigimonInfo("Egg", "Egg"),
        1 to DigimonInfo("Punimon", "Baby I"),
        2 to DigimonInfo("Tsunomon", "Baby II"),
        3 to DigimonInfo("Gabumon", "Rookie"),
        4 to DigimonInfo("Elecmon", "Rookie"),
        5 to DigimonInfo("Kabuterimon", "Champion"),
        6 to DigimonInfo("Garurumon", "Champion"),
        7 to DigimonInfo("Angemon", "Champion"),
        8 to DigimonInfo("Frigimon", "Champion"),
        9 to DigimonInfo("Birdramon", "Champion"),
        0xA to DigimonInfo("Whamon", "Champion"),
        0xB to DigimonInfo("Vegimon", "Champion"),
        0xC to DigimonInfo("SkullGreymon", "Ultimate"),
        0xD to DigimonInfo("MetalMamemon", "Ultimate"),
        0xE to DigimonInfo("Vademon", "Ultimate"),
        0xF to DigimonInfo("Death", "Dead")
    )

    private val v3Map = mapOf(
        0 to DigimonInfo("Egg", "Egg"),
        1 to DigimonInfo("Poyomon", "Baby I"),
        2 to DigimonInfo("Tokomon", "Baby II"),
        3 to DigimonInfo("Patamon", "Rookie"),
        4 to DigimonInfo("Kunemon", "Rookie"),
        5 to DigimonInfo("Unimon", "Champion"),
        6 to DigimonInfo("Centarumon", "Champion"),
        7 to DigimonInfo("Ogremon", "Champion"),
        8 to DigimonInfo("Bakemon", "Champion"),
        9 to DigimonInfo("Shellmon", "Champion"),
        0xA to DigimonInfo("Drimogemon", "Champion"),
        0xB to DigimonInfo("Scumon", "Champion"),
        0xC to DigimonInfo("Andromon", "Ultimate"),
        0xD to DigimonInfo("Giromon", "Ultimate"),
        0xE to DigimonInfo("Etemon", "Ultimate"),
        0xF to DigimonInfo("Death", "Dead")
    )

    fun resolveVersion(romName: String?): String? {
        val upperName = romName?.uppercase()?.replace(Regex("[^A-Z0-9]"), "") ?: return null
        return when {
            upperName.contains("DIGIMONV1") || upperName == "DIGIMON" || upperName.endsWith("V1") -> "V1"
            upperName.contains("DIGIMONV2") || upperName.endsWith("V2") -> "V2"
            upperName.contains("DIGIMONV3") || upperName.endsWith("V3") -> "V3"
            else -> null
        }
    }

    fun getDigimonInfo(romName: String, speciesId: Int): DigimonInfo? {
        return when (resolveVersion(romName)) {
            "V1" -> v1Map[speciesId]
            "V2" -> v2Map[speciesId]
            "V3" -> v3Map[speciesId]
            else -> null
        }
    }

    fun getVersionRoster(version: String): List<Pair<Int, DigimonInfo>> {
        val roster = when (version.uppercase()) {
            "V1" -> v1Map
            "V2" -> v2Map
            "V3" -> v3Map
            else -> emptyMap()
        }
        return roster
            .filterKeys { it in 1..0xE }
            .toSortedMap()
            .map { it.key to it.value }
    }
}
