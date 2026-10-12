package com.digimon.glyph.emulator

/** Phone elapsed time determines the CPU budget. Only executed instructions repay that budget. */
class RealtimePacer(private val frequency: Int, startNs: Long) {
    init { require(frequency > 0) }
    private var wallAnchorNs = startNs
    private var cycleAnchor = 0.0
    var cycles = 0.0
        private set
    val virtualNs: Double get() = cycles * (1_000_000_000.0 / frequency)

    fun debtCycles(nowNs: Long): Double = cycleAnchor + (nowNs - wallAnchorNs).coerceAtLeast(0) * (frequency / 1_000_000_000.0) - cycles
    fun debtNs(nowNs: Long): Double = debtCycles(nowNs) * (1_000_000_000.0 / frequency)
    fun advance(executedCycles: Double) {
        require(executedCycles.isFinite() && executedCycles > 0)
        cycles += executedCycles
    }
    /** Used only for intentional debug stepping; display mode changes never rebase the clock. */
    fun rebase(nowNs: Long) {
        wallAnchorNs = nowNs
        cycleAnchor = cycles
    }
}
