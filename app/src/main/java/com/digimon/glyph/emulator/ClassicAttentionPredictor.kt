package com.digimon.glyph.emulator

/** Results describe a possible future; scratch state is never installed into the live core. */
object ClassicAttentionPredictor {
    data class Prediction(val offsetMs: Long, val onset: Boolean)

    fun predict(live: E0C6200, horizonMinutes: Int = 60, cancelled: () -> Boolean = { false }): Prediction? {
        require(horizonMinutes in 1..240)
        val scratch = live.createLookaheadCore()
        return scan(scratch, horizonMinutes * 60_000L, cancelled)
    }

    internal fun scan(scratch: E0C6200, horizonMs: Long, cancelled: () -> Boolean): Prediction? {
        var previous = scratch.careCallActive()
        var cycles = 0.0
        val limit = horizonMs * scratch.clockFrequency / 1000.0
        var checks = 0
        while (cycles < limit) {
            if (checks++ % 256 == 0 && cancelled()) return null
            cycles += scratch.clock(minOf(limit - cycles, scratch.clockFrequency * .05))
            val current = scratch.careCallActive()
            if (current && !previous) return Prediction(kotlin.math.ceil(cycles * 1000 / scratch.clockFrequency).toLong(), true)
            previous = current
        }
        return if (cancelled()) null else Prediction(horizonMs, false)
    }
}
