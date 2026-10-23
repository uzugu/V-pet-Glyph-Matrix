package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Test

class GlyphClockHeaderTest {
    @Test fun matchesReferenceZeroDigitAndLeavesLcdUntouched() {
        val points = GlyphClockHeader.points(0, 0)
        assertEquals(listOf(6 to 20, 5 to 21, 7 to 21, 6 to 22), points.take(4))
        assertEquals(16, points.size)
        assertTrue(points.all { (x, y) -> x in 5..19 && y in 20..22 })
    }
    @Test fun everyTimeFitsBelowLcdInsideCircularMatrix() {
        for (hour in 0..23) for (minute in 0..59) {
            for ((x, y) in GlyphClockHeader.points(hour, minute)) {
                assertTrue(y > 19)
                assertTrue((x - 12) * (x - 12) + (y - 12) * (y - 12) <= 12.5 * 12.5)
            }
        }
    }
    @Test fun digitsMatchUpstreamNanoFontBitmaps() {
        val upstream = intArrayOf(0x4A40, 0x8440, 0xC460, 0xE6E0, 0xAE20, 0x64C0, 0x8EE0, 0xE220, 0x6EC0, 0xEE20)
        for (digit in 0..9) {
            var bits = 0
            for ((x, y) in GlyphClockHeader.points(0, digit).filter { it.first >= 17 }) {
                bits = bits or (1 shl (15 - (y - 20) * 4 - (x - 17)))
            }
            assertEquals(upstream[digit], bits)
        }
    }
    @Test fun clockRevisionChangesAtMidnightAndWhenDisabled() {
        assertNotEquals(GlyphClockHeader.revision(true, 23, 59), GlyphClockHeader.revision(true, 0, 0))
        assertNotEquals(GlyphClockHeader.revision(true, 0, 0), GlyphClockHeader.revision(false, 0, 0))
        assertEquals(0L, GlyphClockHeader.revision(false, 12, 30))
    }
}
