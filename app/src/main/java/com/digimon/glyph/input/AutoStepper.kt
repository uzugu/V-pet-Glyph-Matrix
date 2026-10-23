package com.digimon.glyph.input

/** Cadence in emulated time; no background timer, catch-up burst, or game-speed change. */
class AutoStepper {
    private var accumulated = 0.0
    private var lastRate = 0
    fun reset() { accumulated = 0.0; lastRate = 0 }
    fun advance(cycles: Double, enabled: Boolean, halfStepsPerSecond: Int): Boolean {
        if (!enabled) { reset(); return false }
        val rate = halfStepsPerSecond.coerceIn(1, 8)
        if (rate != lastRate) { accumulated = 0.0; lastRate = rate }
        // Accumulate rate-scaled cycles to avoid fractional-period rounding.
        val interval = 4_400_000.0
        accumulated += cycles * rate
        if (accumulated < interval) return false
        accumulated %= interval
        return true
    }
}
