package com.digimon.glyph.battle

import android.content.Context

enum class BattleTransportType {
    NEARBY,
    INTERNET_RELAY,
    SIMULATION
}

enum class SimulationPreset {
    PURE_ECHO,
    XOR_CHECKSUM,
    GLOBAL_CHECKSUM
}

/**
 * Shared settings for selecting battle transport backend.
 */
object BattleTransportSettings {

    private const val PREFS_NAME = "battle_transport_settings"
    private const val KEY_TRANSPORT_TYPE = "transport_type"
    private const val KEY_RELAY_URL = "relay_url"
    private const val KEY_SIMULATION_PRESET = "simulation_preset"

    @Volatile
    private var transportType: BattleTransportType = BattleTransportType.NEARBY

    @Volatile
    private var relayUrl: String = "tcp://109.224.229.205:19792/bunnyTest"

    @Volatile
    private var simulationPreset: SimulationPreset = SimulationPreset.PURE_ECHO

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        transportType = parseType(prefs.getString(KEY_TRANSPORT_TYPE, null))
        val savedUrl = prefs.getString(KEY_RELAY_URL, null)?.trim()
        relayUrl = if (!savedUrl.isNullOrEmpty()) savedUrl else "tcp://109.224.229.205:19792/bunnyTest"
        simulationPreset = parsePreset(prefs.getString(KEY_SIMULATION_PRESET, null))
    }

    fun getTransportType(): BattleTransportType = transportType

    fun setTransportType(context: Context, type: BattleTransportType) {
        transportType = type
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRANSPORT_TYPE, type.name)
            .apply()
    }

    fun getRelayUrl(): String = relayUrl

    fun setRelayUrl(context: Context, url: String) {
        relayUrl = url.trim()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RELAY_URL, relayUrl)
            .apply()
    }

    fun getSimulationPreset(): SimulationPreset = simulationPreset

    fun setSimulationPreset(context: Context, preset: SimulationPreset) {
        simulationPreset = preset
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SIMULATION_PRESET, preset.name)
            .apply()
    }

    private fun parseType(value: String?): BattleTransportType {
        return runCatching {
            if (value.isNullOrBlank()) BattleTransportType.NEARBY
            else BattleTransportType.valueOf(value)
        }.getOrDefault(BattleTransportType.NEARBY)
    }

    private fun parsePreset(value: String?): SimulationPreset {
        return runCatching {
            if (value.isNullOrBlank()) SimulationPreset.PURE_ECHO
            else SimulationPreset.valueOf(value)
        }.getOrDefault(SimulationPreset.PURE_ECHO)
    }
}
