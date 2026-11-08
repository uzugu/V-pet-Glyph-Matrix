package com.digimon.glyph.emulator

import android.content.Context

/** Real-time debt is the default. Only D-Power may opt into discarding standby time. */
object StandbySettings {
    @Volatile var freezeDPower = false; private set
    @Volatile var intervalMinutes = 15; private set
    fun init(context: Context) {
        val p=context.getSharedPreferences("emulator_standby",0)
        freezeDPower=p.getBoolean("freeze_dpower",false)
        intervalMinutes=p.getInt("interval_minutes",15).coerceIn(15,120)
    }
    fun setFreezeDPower(context: Context, freeze: Boolean) {
        freezeDPower=freeze
        context.getSharedPreferences("emulator_standby",0).edit().putBoolean("freeze_dpower",freeze).apply()
    }
    fun setIntervalMinutes(context: Context, minutes: Int) {
        intervalMinutes=minutes.coerceIn(15,120)
        context.getSharedPreferences("emulator_standby",0).edit().putInt("interval_minutes",intervalMinutes).apply()
    }
}
