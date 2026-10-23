package com.digimon.glyph.emulator

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.*

class DisplayRefreshTest {
    @Test fun headerRevisionRefreshesAnUnchangedLcd() {
        val core = mock(EmulatorCore::class.java)
        `when`(core.clockFrequency).thenReturn(32768)
        `when`(core.clock()).thenReturn(1.0)
        `when`(core.getDisplaySnapshot(anyLong())).thenAnswer {
            if (it.getArgument<Long>(0) == Long.MIN_VALUE) E0C6200.DisplaySnapshot(1L, intArrayOf(0)) else null
        }
        val revision = AtomicLong(1)
        val first = CountDownLatch(1)
        val changed = CountDownLatch(1)
        val loop = EmulatorLoop(core, {
            if (revision.get() == 1L) first.countDown() else changed.countDown()
        }, { System.nanoTime() }, { revision.get() })
        loop.start()
        try {
            assertTrue(first.await(2, TimeUnit.SECONDS))
            revision.set(2)
            assertTrue(changed.await(2, TimeUnit.SECONDS))
        } finally { loop.stop() }
    }
}
