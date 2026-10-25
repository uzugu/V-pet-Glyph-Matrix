package com.digimon.glyph.emulator

/** Layout established by six private-ROM traces; distinct from the Glyph clock overlay. */
object ClassicFirmwareClock {
    const val FIRST_ADDRESS = 0x01A
    const val LAST_ADDRESS = 0x01F
    internal const val IDLE_PC = 0x016D

    data class Time(val hour: Int, val minute: Int, val second: Int = 0) {
        init {
            require(hour in 0..23 && minute in 0..59 && second in 0..59) { "Invalid clock time" }
        }
        val secondsOfDay: Int get() = hour * 3600 + minute * 60 + second
        internal fun nibbles() = intArrayOf(second % 10, second / 10, minute % 10, minute / 10,
            (hour % 12).let { if (it == 0) 12 else it }, if (hour >= 12) 1 else 0)
    }
    fun fromSecondsOfDay(seconds: Int): Time {
        require(seconds in 0 until 86400) { "Clock time must be within one day" }
        return Time(seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    internal fun read(nibbles: IntArray): Time? {
        if (nibbles.size != 6 || nibbles[0] !in 0..9 || nibbles[1] !in 0..5 ||
            nibbles[2] !in 0..9 || nibbles[3] !in 0..5 || nibbles[4] !in 1..12 || nibbles[5] !in 0..1) return null
        return Time(nibbles[4] % 12 + nibbles[5] * 12,
            nibbles[2] + 10 * nibbles[3], nibbles[0] + 10 * nibbles[1])
    }
}
