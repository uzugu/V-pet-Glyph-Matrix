package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Test
import com.google.gson.Gson
import android.content.Context
import android.content.SharedPreferences
import org.mockito.Mockito.*

class CoreSafetyTest {
    @Test fun onlyEnabledSelectedPinsRaiseInterrupts() {
        val core = E0C6200(ByteArray(8192))
        core.setMem(0xF14, 8)
        core.pinSet("K0", 2, 0)
        assertEquals(0, core.getMem(0xF04))
        core.setMem(0xF15, 8)
        core.pinSet("K1", 0, 0)
        assertEquals(0, core.getMem(0xF05))
        core.setMem(0xF14, 4)
        core.pinRelease("K0", 2)
        assertEquals(0, core.getMem(0xF04))
        core.pinSet("K0", 2, 0)
        assertEquals(1, core.getMem(0xF04))
    }
    @Test fun releasingAnAlreadyHighPinDoesNotInterrupt() {
        val core = E0C6200(ByteArray(8192))
        core.setMem(0xF14, 4)
        core.pinRelease("K0", 2)
        assertEquals(0, core.getMem(0xF04))
    }
    @Test fun rejectedSnapshotLeavesEveryMachineFieldUntouched() {
        val core = TimingRom.core()
        core.runForCycles(1060000.0 * 0.071)
        val before = core.getState()
        for (invalid in listOf(
            before + mapOf("PC" to 0x345, "RAM" to IntArray(769)),
            before + mapOf("PC" to 0x345, "VRAM" to IntArray(1)),
            before + mapOf("PC" to 999999),
            before + mapOf("TIMER_COUNTER" to Double.NaN),
            before - "PC"
        )) {
            try { core.restoreState(invalid); fail("Must reject malformed snapshot") } catch (_: IllegalArgumentException) {}
            for ((key, value) in before) {
                val after = core.getState()[key]
                if (value is IntArray) assertArrayEquals(key, value, after as IntArray) else assertEquals(key, value, after)
            }
        }
    }
    @Test fun buzzerOneShotAndEnvelopeSurviveJsonSaveCodec() {
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        val manager = StateManager(context)
        val original = E0C6200(ByteArray(8192))
        original.setMem(0xF75, 8)
        assertEquals(8, original.getMem(0xF75) and 8)
        val encode = StateManager::class.java.getDeclaredMethod("serializeState", Map::class.java).apply { isAccessible = true }
        val decode = StateManager::class.java.getDeclaredMethod("deserializeState", String::class.java).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val saved = decode.invoke(manager, Gson().toJson(encode.invoke(manager, original.getState()))) as Map<String, Any>
        val restored = E0C6200(ByteArray(8192)).apply { restoreState(saved) }
        assertEquals(8, restored.getMem(0xF75) and 8)
        repeat(100) { assertEquals(original.clock(), restored.clock(), 0.0) }
        assertEquals(original.getMem(0xF75), restored.getMem(0xF75))
        for (key in original.getState().keys.filter { it.startsWith("BZ_") }) assertEquals(key, original.getState()[key], restored.getState()[key])
    }
    @Test fun legacySnapshotWithoutNewRuntimeFieldsStillLoads() {
        val core = TimingRom.core()
        val old = core.getState().filterKeys { !it.startsWith("BZ_") && it !in listOf("STATE_VERSION", "SC", "HZR", "CTRL_SVD", "RESET", "IF_DELAY") }
        val restored = TimingRom.core()
        restored.restoreState(old)
        assertEquals(core.getState()["PC"], restored.getState()["PC"])
    }
}
