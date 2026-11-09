package com.digimon.glyph.emulator

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class DPowerIdlePollingTest {
    private fun rom(vararg code: Int) = DPowerPortTest.synthetic().apply {
        code.forEachIndexed { i, value -> this[0x400 + i] = value.toByte() }
    }

    private fun compare(a: DPowerRuntime, b: DPowerRuntime) {
        DPowerPortTest.same("polling", JsonParser.parseString(a.snapshot()), JsonParser.parseString(b.snapshot()))
        assertArrayEquals(a.playback(), b.playback())
    }

    @Test fun firmwareFlagLoopMatchesAcrossTimerIrqsInputsAndTinyBudgets() {
        val bytes = rom(0x85, 0x0f, 0xa5, 0xd3, 0xc9, 0x14, 0x90, 0xf8)
        val a = DPowerRuntime(bytes)
        val b = DPowerRuntime(bytes).apply { core.idleSkippingEnabled = true }
        for (r in listOf(a, b)) {
            r.core._mem[0xd3] = 18
            r.core._set_sfr_timer_ctrl(128)
            r.core._TM0_PRELOAD = 65532
            r.core._TM1_PRELOAD = 65533
            r.core._NMI_CTRL = 1
            // The synthetic IRQ vector returns to the loop: exercise exact entry/stack timing.
            r.core._IF = 0
            r.core._INT_CTRL = 32
        }
        repeat(3000) { i ->
            if (i % 100 == 0) {
                a.pin("EF", 0, i % 2); b.pin("EF", 0, i % 2)
            }
            val budget = (i % 997 + 1).toDouble()
            assertEquals(a.advance(budget), b.advance(budget), 0.0)
            if (i % 10 == 0) compare(a, b)
        }
        compare(a, b)
        assertTrue(b.core.idleSkippedInstructions > 0)
    }

    @Test fun flagPortAndTimerPollingPreserveReadsAndBranchPageCosts() {
        for (address in listOf(4, 10, 13, 18, 19, 21, 22, 0x140)) {
            // LDA abs; AND #1; BEQ loop. Place branch across a page boundary.
            val bytes = rom(0xea)
            val code = intArrayOf(0xad, address and 255, address shr 8, 0x29, 1, 0xf0, 0xf9)
            code.forEachIndexed { i, value -> bytes[0x4fc + i] = value.toByte() }
            val a = DPowerRuntime(bytes)
            val b = DPowerRuntime(bytes).apply { core.idleSkippingEnabled = true }
            for (r in listOf(a, b)) {
                r.core._PC = 0x4fc
                r.core._T64HZ_counter = 1000.0
                r.core._T128HZ_counter = 1000.0
                r.core._T2K_counter = 1000.0
                r.core._TIMER_CTRL = 128
                r.core._TM0_div = 67.138671875
                r.core._TM1_div = 67.138671875
                r.core._TM0_counter = 67.138671875
                r.core._TM1_counter = 67.138671875
                r.core._NMI_CTRL = 1
            }
            repeat(1000) { i ->
                if (i == 500) { a.pin("EF", 0, 1); b.pin("EF", 0, 1) }
                assertEquals(a.advance(2200.0), b.advance(2200.0), 0.0)
                if (i % 10 == 0) compare(a, b)
            }
            compare(a, b)
            assertTrue("Address $address exercised", b.core.idleSkippedInstructions > 0)
        }
    }

    @Test fun supportedBranchesKeepStableRegistersAndExactPartialIterations() {
        for ((branch, value) in listOf(0x10 to 1, 0x30 to 128, 0x90 to 1,
                0xb0 to 1, 0xd0 to 1, 0xf0 to 0)) {
            val code = mutableListOf(0xa5, 0x40)
            if (branch == 0x90 || branch == 0xb0) code.addAll(listOf(0xc9, if (branch == 0x90) 2 else 0))
            code.add(branch); code.add(256 - code.size - 1)
            val bytes = rom(*code.toIntArray())
            val a = DPowerRuntime(bytes)
            val b = DPowerRuntime(bytes).apply { core.idleSkippingEnabled = true }
            for (r in listOf(a, b)) r.core._mem[0x40] = value
            repeat(1000) { i ->
                val budget = (1 + i % 500).toDouble()
                assertEquals(a.advance(budget), b.advance(budget), 0.0)
                if (i % 10 == 0) compare(a, b)
            }
            compare(a, b)
            assertTrue("Branch $branch exercised", b.core.idleSkippedInstructions > 0)
        }
    }

    @Test fun sideEffectWritesAndNonBinaryCountersUseReferencePath() {
        for (sink in listOf(0x10, 0x0d, 0x40)) {
            val bytes = rom(0x85, sink, 0xa5, 0xd3, 0xc9, 0x14, 0x90, 0xf8)
            val a = DPowerRuntime(bytes)
            val b = DPowerRuntime(bytes).apply { core.idleSkippingEnabled = true }
            repeat(100) { assertEquals(a.advance(), b.advance(), 0.0); compare(a, b) }
            assertEquals(0L, b.core.idleSkippedInstructions)
        }
        val bytes = rom(0xa5, 0x04, 0xf0, 0xfc)
        val a = DPowerRuntime(bytes)
        val b = DPowerRuntime(bytes).apply { core.idleSkippingEnabled = true }
        a.core._T64HZ_counter = 123.1; b.core._T64HZ_counter = 123.1
        repeat(100) { assertEquals(a.advance(), b.advance(), 0.0); compare(a, b) }
        assertEquals(0L, b.core.idleSkippedInstructions)
    }
}
