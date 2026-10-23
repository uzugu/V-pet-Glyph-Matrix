package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Test

class GlyphClockHeaderTest {
    @Test fun matchesReferenceZeroDigitAndLeavesLcdUntouched() {
        val points = GlyphClockHeader.points(0, 0)
        assertEquals(listOf(10 to 0, 9 to 1, 11 to 1, 10 to 2), points.take(4))
        assertEquals(16, points.size)
        assertTrue(points.all { (x, y) -> x in 9..15 && (y in 0..2 || y in 21..23) })
    }
    @Test fun everyTimeFitsAboveAndBelowLcdInsideCircularMatrix() {
        for (hour in 0..23) for (minute in 0..59) {
            for ((x, y) in GlyphClockHeader.points(hour, minute)) {
                assertTrue(y !in 3..20) // blank rows 3 and 20 separate clock from LCD
                assertNotEquals(12, x) // blank centre column separates the two digits
                assertTrue((x - 12) * (x - 12) + (y - 12) * (y - 12) <= 12.5 * 12.5)
            }
        }
    }
    @Test fun digitsMatchUpstreamNanoFontBitmaps() {
        val upstream = intArrayOf(0x4A40, 0x8440, 0xC460, 0xE6E0, 0xAE20, 0x64C0, 0x8EE0, 0xE220, 0x6EC0, 0xEE20)
        for (digit in 0..9) {
            var bits = 0
            for ((x, y) in GlyphClockHeader.points(0, digit).filter { it.first >= 13 && it.second >= 21 }) {
                bits = bits or (1 shl (15 - (y - 21) * 4 - (x - 13)))
            }
            assertEquals(upstream[digit], bits)
        }
    }
    @Test fun hoursAndMinutesOccupySeparateBands() {
        val first = GlyphClockHeader.points(12, 34)
        val changedMinute = GlyphClockHeader.points(12, 56)
        val changedHour = GlyphClockHeader.points(23, 34)
        assertEquals(first.filter { it.second < 4 }, changedMinute.filter { it.second < 4 })
        assertEquals(first.filter { it.second > 19 }, changedHour.filter { it.second > 19 })
        assertNotEquals(first.filter { it.second < 4 }, changedHour.filter { it.second < 4 })
        assertNotEquals(first.filter { it.second > 19 }, changedMinute.filter { it.second > 19 })
    }
    @Test fun clockRevisionChangesAtMidnightAndWhenDisabled() {
        assertNotEquals(GlyphClockHeader.revision(true, 23, 59), GlyphClockHeader.revision(true, 0, 0))
        assertNotEquals(GlyphClockHeader.revision(true, 0, 0), GlyphClockHeader.revision(false, 0, 0))
        assertEquals(0L, GlyphClockHeader.revision(false, 12, 30))
    }
}
