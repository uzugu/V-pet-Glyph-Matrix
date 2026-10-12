package com.digimon.glyph.emulator

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.*

class StateClockTest {
    @Test fun realJsonSaveCodecPreservesFractionalTimerPhases() {
        val context = mock(Context::class.java)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        val manager = StateManager(context)
        val core = TimingRom.core()
        core.runForCycles(1_060_000 * 0.071234567)
        val saved = core.getState()
        val serialize = StateManager::class.java.getDeclaredMethod("serializeState", Map::class.java).apply { isAccessible = true }
        val deserialize = StateManager::class.java.getDeclaredMethod("deserializeState", String::class.java).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val decoded = deserialize.invoke(manager, Gson().toJson(serialize.invoke(manager, saved))) as Map<String, Any>
        assertTrue((saved.getValue("OSC1_COUNTER") as Double) % 1.0 != 0.0)
        for (key in listOf("OSC1_COUNTER", "TIMER_COUNTER", "STOPWATCH_COUNTER", "PTIMER_COUNTER")) {
            assertEquals(key, saved[key], decoded[key])
        }
        val resumed = TimingRom.core().apply { restoreState(decoded) }
        val budget = 1_060_000 * 3.02345678
        core.runForCycles(budget)
        resumed.runForCycles(budget)
        assertEquals(core.getRAM()[0], resumed.getRAM()[0])
        assertEquals(core.getState()["TM"], resumed.getState()["TM"])
    }
}
