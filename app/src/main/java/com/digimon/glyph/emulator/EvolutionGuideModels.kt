package com.digimon.glyph.emulator

data class EvolutionRoute(
    val targetSpeciesId: Int,
    val title: String,
    val requirements: List<String>,
    val note: String? = null
)

data class EvolutionProfile(
    val speciesId: Int,
    val summary: String,
    val evolvesFrom: List<String> = emptyList(),
    val evolvesTo: List<EvolutionRoute> = emptyList()
)

data class EvolutionVersionGuide(
    val code: String,
    val title: String,
    val intro: String,
    val rules: List<String>,
    val profiles: Map<Int, EvolutionProfile>
)
