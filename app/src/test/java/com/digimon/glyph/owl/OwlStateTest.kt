package com.digimon.glyph.owl

import com.digimon.glyph.input.InputSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwlStateTest {
    @Test fun mapsControlsOnlyForTheDisplayedDevice() {
        val classic = OwlState(true, false, false, "session")
        assertEquals(listOf(10, 11, 12), listOf("a", "b", "c").map { classic.button(it, "session") })
        assertNull(classic.button("up", "session"))
        val dpower = classic.copy(dpower = true)
        assertEquals(listOf(20, 21, 22, 23, 24, 25),
            listOf("left", "right", "down", "up", "step", "card").map { dpower.button(it, "session") })
        assertNull(dpower.button("a", "session"))
        assertNull(dpower.button("reset", "session"))
    }

    @Test fun rejectsInputDuringSleepAndUnavailableStates() {
        val live = OwlState(true, false, false, "session")
        assertNull(live.copy(ready = false).button("a", "session"))
        assertNull(live.copy(frozen = true).button("a", "session"))
        assertNull(live.button("a", ""))
    }

    @Test fun rejectsOldLauncherInputAfterSaveReplacementAndServiceRestart() {
        val session = InputSession()
        val displayed = session.wireEpoch()
        val replacement = session.beginReplacement()!!
        session.finishReplacement(replacement)
        val resumed = OwlState(true, false, false, session.wireEpoch())
        assertNull(resumed.button("a", displayed))
        assertEquals(10, resumed.button("a", session.wireEpoch()))
        val restarted = resumed.copy(inputEpoch = InputSession().wireEpoch())
        assertNull(restarted.button("a", session.wireEpoch()))
    }
}
