package com.digimon.glyph.input

import com.digimon.glyph.emulator.EmulatorLoop
import com.digimon.glyph.emulator.TimingRom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class InputSessionTest {
    @Test fun queuedOldInputsCannotMutateTheResumedStateInAnyTimingMode() {
        for (mode in EmulatorLoop.TimingMode.values()) {
            val core = TimingRom.core()
            val loop = EmulatorLoop(core, onFrame = {}, elapsedNs = System::nanoTime)
            loop.setTimingMode(mode)
            val session = InputSession()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            try {
                loop.start()
                assertTrue(loop.enqueue { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) })
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                // Simulate work accepted in one queue while replacement is queued
                // in another. The worker-side check must still reject that input.
                val stale = session.capture()
                var token = -1L
                assertTrue(loop.enqueue { active ->
                    active.restoreState(active.getState() + ("B" to 9))
                    assertTrue(session.finishReplacement(token))
                })
                assertTrue(session.enqueue(loop::enqueue, stale) { active ->
                    active.pinSet("K0", 2, 0)
                    active.restoreState(active.getState() + ("B" to 1))
                })
                token = session.beginReplacement()!!
                assertNull(session.capture())
                assertFalse(session.enqueue(loop::enqueue) { fail("Blocked input executed") })
                release.countDown()
                assertEquals(9, loop.executeSync { it.getState()["B"] })
                assertEquals(15, loop.executeSync { it.getState()["K0"] })
                assertFalse(session.enqueue(loop::enqueue, stale) { fail("Old delayed release executed") })
                assertTrue(session.enqueue(loop::enqueue) { active ->
                    active.restoreState(active.getState() + ("B" to 5))
                })
                assertEquals(5, loop.executeSync { it.getState()["B"] })
            } finally { release.countDown(); loop.stop() }
        }
    }

    @Test fun failedReplacementResumesFreshInputsAndCannotBeFinishedTwice() {
        val session = InputSession()
        val old = session.capture()
        val token = session.beginReplacement()!!
        assertNull(session.beginReplacement())
        var cleaned = 0
        assertTrue(session.finishReplacement(token) {
            assertNull(session.capture())
            cleaned++
        })
        assertFalse(session.accepts(old))
        assertTrue(session.accepts(session.capture()))
        assertFalse(session.finishReplacement(token) { cleaned++ })
        assertEquals(1, cleaned)
    }

    @Test fun oldLifecycleCompletionCannotClearInputsOrUnlockANewerReplacement() {
        val session = InputSession()
        val old = session.beginReplacement()!!
        session.reset()
        val current = session.beginReplacement()!!
        assertFalse(session.finishReplacement(old) { fail("Old pet cleared new inputs") })
        assertTrue(session.isBlocked())
        assertTrue(session.finishReplacement(current))
        assertFalse(session.isBlocked())
    }
    @Test fun networkEpochRejectsLateRequestsAfterReplacementAndProcessRestart() {
        val session = InputSession()
        val old = session.wireEpoch()
        assertTrue(session.acceptsWire(old))
        val token = session.beginReplacement()!!
        assertFalse(session.acceptsWire(old))
        assertFalse(session.acceptsWire(session.wireEpoch()))
        session.finishReplacement(token)
        assertFalse(session.acceptsWire(old))
        assertTrue(session.acceptsWire(session.wireEpoch()))
        assertFalse(InputSession().acceptsWire(session.wireEpoch()))
    }

}
