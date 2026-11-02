package com.digimon.glyph

/** Main-thread policy: an opt-in timeout override belongs only to a running interactive toy. */
class GlyphDisplayAwake {
    private var overridden = false

    fun update(enabled: Boolean, interactive: Boolean, running: Boolean, setTimeout: (Boolean) -> Unit) {
        val wanted = enabled && interactive && running
        if (wanted == overridden) return
        setTimeout(!wanted)
        overridden = wanted
    }

    /** A replacement SDK connection must apply its own timeout policy. */
    fun disconnected() { overridden = false }
}
