package com.digimon.glyph.battle

import android.content.Context

enum class BattleTransportType {
    NEARBY,
    INTERNET_RELAY
}

/**
 * Shared settings for selecting battle transport backend.
 */
object BattleTransportSettings {

    private const val PREFS_NAME = "battle_transport_settings"
    private const val KEY_TRANSPORT_TYPE = "transport_type"
    private const val KEY_RELAY_URL = "relay_url"

    @Volatile
    private var transportType: BattleTransportType = BattleTransportType.NEARBY

    @Volatile
    private var relayUrl: String = ""

    @Synchronized
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        transportType = parseType(prefs.getString(KEY_TRANSPORT_TYPE, null))
        relayUrl = prefs.getString(KEY_RELAY_URL, "")?.trim().orEmpty()
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

    private fun parseType(value: String?): BattleTransportType {
        return runCatching {
            if (value.isNullOrBlank()) BattleTransportType.NEARBY
            else BattleTransportType.valueOf(value)
        }.getOrDefault(BattleTransportType.NEARBY)
    }
}
