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

    private val cached = mutableMapOf<String, Map<String, List<EvolutionDexEntry>>>()

    fun getVersionEntries(context: Context, version: String): List<EvolutionDexEntry> {
        val asset = if (com.digimon.glyph.LocalizedText.isJapanese(context))
            "evolution_dex.json" else "evolution_dex_en.json"
        val data = synchronized(cached) { cached.getOrPut(asset) { load(context, asset) } }
        return data[version.uppercase()] ?: emptyList()
    }

    private fun load(context: Context, asset: String): Map<String, List<EvolutionDexEntry>> {
        val json = context.assets.open(asset).bufferedReader().use { it.readText() }
        val type = object : TypeToken<Map<String, List<EvolutionDexEntry>>>() {}.type
        return Gson().fromJson(json, type)
    }
}
