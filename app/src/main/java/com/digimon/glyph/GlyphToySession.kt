package com.digimon.glyph

/** AOD can follow interactive END without a new bind. Unbind revokes display ownership. */
class GlyphToySession {
    private enum class Mode { UNBOUND, INTERACTIVE, ENDED, AOD }
    @Volatile private var mode = Mode.UNBOUND
    val active get() = mode == Mode.INTERACTIVE || mode == Mode.AOD
    val interactive get() = mode == Mode.INTERACTIVE
    fun bind() { mode = Mode.INTERACTIVE }
    fun unbind() { mode = Mode.UNBOUND }
    fun end() { if (mode != Mode.UNBOUND) mode = Mode.ENDED }
    fun start(): Boolean {
        if (mode == Mode.UNBOUND) return false
        mode = Mode.INTERACTIVE
        return true
    }
    fun aod(): Boolean {
        if (mode == Mode.UNBOUND) return false
        mode = Mode.AOD
        return true
    }
}
