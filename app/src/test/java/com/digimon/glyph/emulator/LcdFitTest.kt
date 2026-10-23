package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Test

class LcdFitTest {
    @Test fun sameScaleOnBothAxesAndBothEdgesVisible() {
        assertEquals(0, LcdFit.sourceX(0))
        assertEquals(31, LcdFit.sourceX(21))
        assertEquals(0, LcdFit.sourceY(0))
        assertEquals(15, LcdFit.sourceY(10))
        for (i in 0..10) assertEquals(LcdFit.sourceX(i), LcdFit.sourceY(i))
    }
    @Test fun fittedRectangleStaysInsideCircularMatrixAndBelowClock() {
        for (y in 7..17) for (x in 1..22) {
            assertTrue((x - 12) * (x - 12) + (y - 12) * (y - 12) <= 12.5 * 12.5)
            assertTrue(y >= 4)
        }
    }
}
