package com.digimon.glyph.emulator

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class EmulatorBoundaryCommandTest {
    @Test fun waitingForFirmwareProgressDoesNotBlockOrFastForwardTheWorker() {
        val core = TimingRom.core()
        val loop = EmulatorLoop(core, {}, elapsedNs = { System.nanoTime() })
        loop.setTimingMode(EmulatorLoop.TimingMode.POWER_SAVE)
        loop.start()
        try {
            val start = System.nanoTime()
            assertEquals(1, loop.executeSyncWhen(2000, { it.getRAM()[0] == 1 }) { it.getState()["DBG_INT_COUNT"] })
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            assertTrue("Firmware must execute a real second, got ${elapsedMs}ms", elapsedMs >= 900)
        } finally { loop.stop() }
    }
    @Test fun timedOutBoundaryCommandCannotMutateLaterAndDoesNotStarveOtherCommands() {
        val ready = AtomicBoolean(false)
        val core = TimingRom.core()
        val loop = EmulatorLoop(core, {}, elapsedNs = { System.nanoTime() })
        loop.start()
        try {
            assertNull(loop.executeSyncWhen(40, { ready.get() }) { (it as E0C6200).setMem(12, 7); true })
            ready.set(true)
            Thread.sleep(80)
            assertEquals(0, loop.executeSync { it.getRAM()[12] })
            assertThrows(RuntimeException::class.java) {
                loop.executeSyncWhen(1000, { error("predicate failure") }) { true }
            }
            assertEquals(42, loop.executeSync { 42 })
        } finally { loop.stop() }
    }
}
