package com.digimon.glyph.owl

import org.junit.Assert.assertEquals
import org.junit.Test

class OwlPixelsTest {
    @Test fun preservesLcdAndBothIndicatorStatesWithoutColors() {
        val rows = OwlPixels.rows(intArrayOf(0xff000000.toInt(), 0xff00ff50.toInt(), 0xffffc800.toInt(), 0xff785a18.toInt()), 4, 1)
        assertEquals(listOf(".pid"), rows)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidBuffer() {
        OwlPixels.rows(intArrayOf(0), 2, 2)
    }
}
