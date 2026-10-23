package com.digimon.glyph.emulator

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class CommandExecutionTest {
    @Test fun timedOutQueuedOperationNeverRunsLater() {
        val loop = EmulatorLoop(TimingRom.core(), {}, { System.nanoTime() })
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val barrier = CountDownLatch(1)
        val mutations = AtomicInteger()
        loop.start()
        try {
            loop.enqueue { entered.countDown(); release.await(2, TimeUnit.SECONDS) }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertNull(loop.executeSync(10) { mutations.incrementAndGet() })
            release.countDown()
            loop.enqueue { barrier.countDown() }
            assertTrue(barrier.await(2, TimeUnit.SECONDS))
            assertEquals(0, mutations.get())
        } finally { release.countDown(); loop.stop() }
    }
    @Test fun aStartedOperationReturnsItsActualResult() {
        val loop = EmulatorLoop(TimingRom.core(), {}, { System.nanoTime() })
        loop.start()
        try { assertEquals(42, loop.executeSync(100) { Thread.sleep(150); 42 }) }
        finally { loop.stop() }
    }
    @Test fun workerFailureIsReportedInsteadOfEscapingThread() {
        val loop = EmulatorLoop(TimingRom.core(), {}, { System.nanoTime() })
        val failed = CountDownLatch(1)
        loop.onFailure = { failed.countDown() }
        loop.start()
        loop.enqueue { throw IllegalStateException("injected") }
        assertTrue(failed.await(2, TimeUnit.SECONDS))
        loop.stop()
        assertFalse(loop.enqueue {})
    }
}
