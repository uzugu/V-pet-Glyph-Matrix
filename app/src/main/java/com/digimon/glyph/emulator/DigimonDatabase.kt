package com.digimon.glyph.emulator

data class DigimonInfo(val name: String, val stage: String)

data class DigimonState(
    val info: DigimonInfo?,
    val age: Int,
    val weight: Int
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
        0xD to DigimonInfo("Mamemon", "Ultimate"),
        0xE to DigimonInfo("Monzaemon", "Ultimate"),
        0xF to DigimonInfo("Grave", "Dead")
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
        0xB to DigimonInfo("Vegiemon", "Champion"),
        0xC to DigimonInfo("SkullGreymon", "Ultimate"),
        0xD to DigimonInfo("MetalMamemon", "Ultimate"),
        0xE to DigimonInfo("Vademon", "Ultimate"),
        0xF to DigimonInfo("Grave", "Dead")
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
        0xF to DigimonInfo("Grave", "Dead")
    )

    fun getDigimonInfo(romName: String, speciesId: Int): DigimonInfo? {
        val upperName = romName.uppercase()
        return when {
            upperName.contains("V1") -> v1Map[speciesId]
            upperName.contains("V2") -> v2Map[speciesId]
            upperName.contains("V3") -> v3Map[speciesId]
            else -> null // Unknown ROM version
        }
    }
}
