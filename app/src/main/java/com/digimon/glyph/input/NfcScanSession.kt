package com.digimon.glyph.input

/** One card per explicit scan, rejecting callbacks from cancelled or replaced sessions. */
class NfcScanSession {
    private var generation = 0L
    private var armed = false
    private var revision = 0L
    fun arm(deviceRevision: Long): Long {
        generation++
        revision = deviceRevision
        armed = true
        return generation
    }
    fun cancel() { armed = false }
    fun consume(token: Long, deviceRevision: Long, ready: Boolean): Boolean {
        if (!armed || token != generation) return false
        armed = false
        return ready && revision == deviceRevision
    }
}
