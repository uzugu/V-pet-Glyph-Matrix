package com.digimon.glyph.input

/** Serialises Step contacts using emulated cycles, including a released gap. */
class StepPulses {
    var pending = 0; private set
    var phase = 0; private set // idle, pressed, released gap
    var remaining = 0; private set
    fun enqueue(): Boolean {
        if (pending >= 128) return false
        pending++
        return true
    }
    fun advance(cycles: Int, contact: (Boolean) -> Unit) {
        remaining = (remaining - cycles).coerceAtLeast(0)
        if (remaining > 0) return
        if (phase == 1) {
            contact(false); phase = 2; remaining = 264_000 // 120 ms at 2.2 MHz
        } else if (pending > 0) {
            pending--; contact(true); phase = 1; remaining = 176_000 // 80 ms
        } else phase = 0
    }
    fun restore(pending: Int, phase: Int, remaining: Int) {
        require(pending in 0..128 && phase in 0..2 && remaining in 0..264_000)
        require(phase != 0 || remaining == 0)
        this.pending = pending; this.phase = phase; this.remaining = remaining
    }
}
