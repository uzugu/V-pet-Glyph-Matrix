package com.digimon.glyph.input

/** Discrete contacts measured in firmware cycles, so phone stalls cannot swallow a tap or its released gap. Worker thread only. */
class FirmwareButtonQueue(private val clockFrequency: Int) {
    private data class Pulse(val valid: () -> Boolean, val contact: (Boolean) -> Unit, val hold: Double, val gap: Double)
    private val waiting = java.util.ArrayDeque<Pulse>()
    private var active: Pulse? = null
    private var remaining = 0.0
    private var releasedGap = 0.0

    fun enqueue(holdMs: Long = 120, gapMs: Long = 80, valid: () -> Boolean = { true }, contact: (Boolean) -> Unit): Boolean {
        require(holdMs in 1..1000 && gapMs in 1..1000)
        if (waiting.size >= 32) return false
        waiting.addLast(Pulse(valid, contact, clockFrequency * holdMs / 1000.0, clockFrequency * gapMs / 1000.0))
        return true
    }

    fun advance(cycles: Double) {
        require(cycles >= 0 && cycles.isFinite())
        val held = active
        if (held != null) {
            remaining -= cycles
            if (!held.valid() || remaining <= 0) {
                held.contact(false)
                active = null
                releasedGap = held.gap
            }
            return
        }
        if (releasedGap == 0.0 && waiting.isEmpty()) return
        releasedGap = (releasedGap - cycles).coerceAtLeast(0.0)
        if (releasedGap > 0) return
        while (waiting.isNotEmpty()) {
            val next = waiting.removeFirst()
            if (!next.valid()) continue
            active = next
            remaining = next.hold
            next.contact(true)
            return
        }
    }

    fun clear() {
        waiting.clear()
        active?.contact?.invoke(false)
        active = null
        remaining = 0.0
        releasedGap = 0.0
    }
}
