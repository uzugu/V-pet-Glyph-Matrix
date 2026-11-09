package com.digimon.glyph.emulator

/** Wall-clock alarm policy is separate from firmware execution and can be tested without Android. */
object StandbyWakePolicy {
    data class Wake(val atMs: Long, val exact: Boolean)
    fun classic(nowMs: Long, firmwareAtMs: Long, prediction: ClassicAttentionPredictor.Prediction,
                exactAllowed: Boolean, intervalMinutes: Int): Wake =
        if (exactAllowed) Wake(maxOf(nowMs + 1, firmwareAtMs + prediction.offsetMs - if (prediction.onset) 5000 else 0), true)
        else periodic(nowMs, intervalMinutes)

    fun periodic(nowMs: Long, intervalMinutes: Int) = Wake(nowMs + intervalMinutes * 60_000L, false)
}
