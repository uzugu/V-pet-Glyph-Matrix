package com.digimon.glyph.emulator

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class EvolutionDexEntry(
    val speciesId: Int,
    val name: String,
    val jpName: String,
    val anchor: String,
    val header: String,
    val activity: String,
    val lifespan: String,
    val weight: String,
    val fullness: String,
    val cycle: String,
    val stamina: String,
    val injury: String,
    val conditionText: String,
    val evolvesToText: List<String>,
    val flavor: String
)

object EvolutionDexRepository {

    private var cached: Map<String, List<EvolutionDexEntry>>? = null

    fun getVersionEntries(context: Context, version: String): List<EvolutionDexEntry> {
        val data = cached ?: load(context).also { cached = it }
        return data[version.uppercase()] ?: emptyList()
    }

    private fun load(context: Context): Map<String, List<EvolutionDexEntry>> {
        val json = context.assets.open("evolution_dex_en.json").bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, List<EvolutionDexEntry>>>() {}.type
        return Gson().fromJson(json, type)
    }
}
