package com.digimon.glyph.emulator

import android.content.Context

enum class DPowerBrightness(val pet: Int, val clock: Int) {
    BALANCED(180, 180), DIM(48, 48), PET_BRIGHTER(220, 64), CLOCK_BRIGHTER(96, 220);
    val petColor get() = gray(pet)
    val clockColor get() = gray(clock)
    private fun gray(level: Int) = (0xFF shl 24) or (level shl 16) or (level shl 8) or level
}

object DPowerOptions {
    private const val PREFS = "dpower_options"
    @Volatile var autoStepEnabled = false; private set
    @Volatile var halfStepsPerSecond = 2; private set
    @Volatile var brightness = DPowerBrightness.BALANCED; private set
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        autoStepEnabled = prefs.getBoolean("auto_step", false)
        halfStepsPerSecond = prefs.getInt("step_rate", 2).coerceIn(1, 8)
        brightness = DPowerBrightness.entries.getOrElse(prefs.getInt("brightness", 0)) { DPowerBrightness.BALANCED }
    }
    fun setAutoStep(context: Context, enabled: Boolean) {
        autoStepEnabled = enabled
        context.getSharedPreferences(PREFS, 0).edit().putBoolean("auto_step", enabled).apply()
    }
    fun setStepRate(context: Context, halfSteps: Int) {
        halfStepsPerSecond = halfSteps.coerceIn(1, 8)
        context.getSharedPreferences(PREFS, 0).edit().putInt("step_rate", halfStepsPerSecond).apply()
    }
    fun setBrightness(context: Context, value: DPowerBrightness) {
        brightness = value
        context.getSharedPreferences(PREFS, 0).edit().putInt("brightness", value.ordinal).apply()
    }
}
