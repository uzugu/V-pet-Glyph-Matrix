package com.digimon.glyph.emulator

/** Display cadence and wakeup batching; every result keeps firmware at real-time speed. */
internal object EmulatorTimingPolicy {
    fun select(powerSave: Boolean, smootherDisplay: Boolean, interactionBoost: Boolean, battleConnected: Boolean): EmulatorLoop.TimingMode =
        when {
            battleConnected || interactionBoost -> EmulatorLoop.TimingMode.FULL_ACCURATE
            powerSave -> EmulatorLoop.TimingMode.POWER_SAVE
            smootherDisplay -> EmulatorLoop.TimingMode.FULL_ACCURATE
            else -> EmulatorLoop.TimingMode.EXACT
        }
}
