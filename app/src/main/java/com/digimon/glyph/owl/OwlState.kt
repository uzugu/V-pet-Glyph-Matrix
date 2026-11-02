package com.digimon.glyph.owl

import com.digimon.glyph.emulator.EmulatorCommandBus

/** Bind every remote input to the pet state that the launcher actually displayed. */
data class OwlState(val ready: Boolean, val frozen: Boolean, val dpower: Boolean, val inputEpoch: String) {
    fun button(key: String, epoch: String): Int? {
        if (!ready || frozen || epoch.isEmpty() || epoch != inputEpoch) return null
        return if (dpower) when (key) {
            "left" -> 20
            "right" -> 21
            "down" -> 22
            "up" -> 23
            "step" -> 24
            "card" -> 25
            else -> null
        } else when (key) {
            "a" -> EmulatorCommandBus.BUTTON_A
            "b" -> EmulatorCommandBus.BUTTON_B
            "c" -> EmulatorCommandBus.BUTTON_C
            else -> null
        }
    }
}
