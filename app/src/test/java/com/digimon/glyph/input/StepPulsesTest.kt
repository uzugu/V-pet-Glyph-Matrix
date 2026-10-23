package com.digimon.glyph.input

import org.junit.Assert.*
import org.junit.Test

class StepPulsesTest {
    @Test fun burstStepsHaveDistinctPressAndReleaseEdges() {
        val pulses = StepPulses()
        val edges = mutableListOf<Boolean>()
        repeat(3) { assertTrue(pulses.enqueue()) }
        repeat(700) { pulses.advance(2200) { edges.add(it) } }
        assertEquals(listOf(true, false, true, false, true, false), edges)
        assertEquals(0, pulses.pending)
        assertEquals(0, pulses.phase)
    }
    @Test fun snapshotContinuationPreservesPulseAndGap() {
        val original = StepPulses()
        original.enqueue(); original.enqueue()
        original.advance(2200) {}
        original.advance(22000) {}
        val restored = StepPulses().apply { restore(original.pending, original.phase, original.remaining) }
        val a = mutableListOf<Boolean>(); val b = mutableListOf<Boolean>()
        repeat(500) {
            original.advance(2200) { a.add(it) }
            restored.advance(2200) { b.add(it) }
        }
        assertEquals(a, b)
        assertEquals(listOf(false, true, false), a)
    }
    @Test fun queueIsBoundedAndInvalidRestoreIsAtomic() {
        val pulses = StepPulses()
        repeat(128) { assertTrue(pulses.enqueue()) }
        assertFalse(pulses.enqueue())
        try { pulses.restore(129, 0, 0); fail("invalid") } catch (_: IllegalArgumentException) {}
        assertEquals(128, pulses.pending)
        pulses.restore(0, 0, 0)
        val edges = mutableListOf<Boolean>()
        pulses.advance(2200) { edges.add(it) }
        assertTrue(edges.isEmpty())
    }
}
