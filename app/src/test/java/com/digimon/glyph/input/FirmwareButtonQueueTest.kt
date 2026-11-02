package com.digimon.glyph.input

import org.junit.Assert.*
import org.junit.Test

class FirmwareButtonQueueTest {
    @Test fun tapsSurviveHostStallsAndHaveAReleasedGap() {
        val edges = mutableListOf<String>()
        val q = FirmwareButtonQueue(1000)
        q.enqueue { edges += "left:$it" }
        q.enqueue { edges += "right:$it" }
        q.advance(0.0)
        repeat(1000) { q.advance(0.0) } // Host work with no firmware progress cannot release the key.
        assertEquals(listOf("left:true"), edges)
        q.advance(119.0)
        assertEquals(listOf("left:true"), edges)
        q.advance(1.0)
        assertEquals(listOf("left:true", "left:false"), edges)
        q.advance(79.0)
        assertEquals(2, edges.size)
        q.advance(1.0)
        assertEquals(listOf("left:true", "left:false", "right:true"), edges)
        q.advance(120.0)
        assertEquals("right:false", edges.last())
    }

    @Test fun rapidSameButtonTapsRemainSeparateContacts() {
        val edges = mutableListOf<Boolean>()
        val q = FirmwareButtonQueue(2_200_000)
        repeat(5) { q.enqueue { edges += it } }
        q.advance(0.0)
        repeat(5) { q.advance(264_000.0); q.advance(176_000.0) }
        assertEquals(List(10) { it % 2 == 0 }, edges)
    }

    @Test fun stateReplacementDropsOldInputsAndReleasesAnActiveKey() {
        var session = 1
        val edges = mutableListOf<Boolean>()
        val q = FirmwareButtonQueue(1000)
        repeat(2) { q.enqueue(valid = { session == 1 }) { edges += it } }
        q.advance(0.0)
        session++
        q.advance(1.0)
        q.advance(1000.0)
        assertEquals(listOf(true, false), edges)
    }

    @Test fun cancellationClearsPendingInputsAndReleasesContacts() {
        val edges = mutableListOf<Boolean>()
        val q = FirmwareButtonQueue(1000)
        repeat(3) { q.enqueue { edges += it } }
        q.advance(0.0)
        q.clear()
        q.advance(1000.0)
        assertEquals(listOf(true, false), edges)
    }
}
