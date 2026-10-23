package com.digimon.glyph.input

/** Physical Glyph controls, independent of sensor plumbing for reproducible tests. */
object DPowerInput {
    class ButtonGesture {
        private var held = false
        private var modified = false
        fun down(): Boolean {
            if (held) return false
            held = true; modified = false
            return true
        }
        fun modifierUsed() { if (held) modified = true }
        fun up(): Boolean {
            val tap = held && !modified
            cancel()
            return tap
        }
        fun cancel() { held = false; modified = false }
    }
    data class Key(val port: String, val pin: Int, val label: String)
    val left = Key("EF", 0, "Left")
    val right = Key("EF", 1, "Right")
    val down = Key("EF", 2, "Down")
    val up = Key("EF", 4, "Up")
    val step = Key("CD", 1, "Step")
    val card = Key("CD", 2, "Card")
    fun flick(horizontal: Boolean, positive: Boolean, modifier: Boolean): Key =
        if (modifier) { if (positive) card else step }
        else if (horizontal) { if (positive) right else left }
        else { if (positive) up else down }
}
