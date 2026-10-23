package com.digimon.glyph.input

import org.junit.Assert.*
import org.junit.Test

class DPowerInputTest {
    @Test fun glyphGesturesReachEveryHardwareInput() {
        val expected = listOf(DPowerInput.left, DPowerInput.right, DPowerInput.down, DPowerInput.up)
        val actual = listOf(true, false).flatMap { horizontal -> listOf(false, true).map { DPowerInput.flick(horizontal, it, false) } }
        assertEquals(expected, actual)
        for (horizontal in listOf(true, false)) {
            assertEquals(DPowerInput.step, DPowerInput.flick(horizontal, false, true))
            assertEquals(DPowerInput.card, DPowerInput.flick(horizontal, true, true))
        }
        assertEquals(listOf(0,1,2,4), expected.map { it.pin })
        assertEquals("CD", DPowerInput.step.port)
        assertEquals(1, DPowerInput.step.pin)
        assertEquals(2, DPowerInput.card.pin)
    }
}
