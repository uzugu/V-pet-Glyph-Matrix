package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Test

class GlyphClockHeaderTest {
    @Test fun matchesReferenceZeroDigitAndLeavesLcdUntouched() {
        val points = GlyphClockHeader.points(0, 0)
        assertEquals(listOf(6 to 0, 5 to 1, 7 to 1, 6 to 2), points.take(4))
        assertEquals(16, points.size)
        assertTrue(points.all { (x, y) -> x in 5..19 && y in 0..2 })
    }
    @Test fun clockRevisionChangesAtMidnightAndWhenDisabled() {
        assertNotEquals(GlyphClockHeader.revision(true, 23, 59), GlyphClockHeader.revision(true, 0, 0))
        assertNotEquals(GlyphClockHeader.revision(true, 0, 0), GlyphClockHeader.revision(false, 0, 0))
        assertEquals(0L, GlyphClockHeader.revision(false, 12, 30))
    }
}
