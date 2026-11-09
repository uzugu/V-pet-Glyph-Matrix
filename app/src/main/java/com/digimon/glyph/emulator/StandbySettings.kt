package com.digimon.glyph.emulator

import android.content.Context

/** Real-time debt is the default. Only D-Power may opt into discarding standby time. */
object StandbySettings {
    @Volatile var freezeDPower = false; private set
    @Volatile var intervalMinutes = 15; private set
    @Volatile var classicAlwaysLive = false; private set
    @Volatile var classicHorizonMinutes = 60; private set
    fun init(context: Context) {
        val p=context.getSharedPreferences("emulator_standby",0)
        freezeDPower=p.getBoolean("freeze_dpower",false)
        intervalMinutes=p.getInt("interval_minutes",15).coerceIn(15,120)
        classicAlwaysLive=p.getBoolean("classic_always_live",false)
        classicHorizonMinutes=p.getInt("classic_horizon_minutes",60).coerceIn(15,240)
    }
    fun setFreezeDPower(context: Context, freeze: Boolean) {
        freezeDPower=freeze
        context.getSharedPreferences("emulator_standby",0).edit().putBoolean("freeze_dpower",freeze).apply()
    }
    fun setIntervalMinutes(context: Context, minutes: Int) {
        intervalMinutes=minutes.coerceIn(15,120)
        context.getSharedPreferences("emulator_standby",0).edit().putInt("interval_minutes",intervalMinutes).apply()
    }
    fun setClassicAlwaysLive(context: Context, enabled: Boolean) {
        classicAlwaysLive = enabled
        context.getSharedPreferences("emulator_standby",0).edit().putBoolean("classic_always_live",enabled).apply()
    }
    fun setClassicHorizonMinutes(context: Context, minutes: Int) {
        classicHorizonMinutes = minutes.coerceIn(15,240)
        context.getSharedPreferences("emulator_standby",0).edit().putInt("classic_horizon_minutes",classicHorizonMinutes).apply()
    }
}
