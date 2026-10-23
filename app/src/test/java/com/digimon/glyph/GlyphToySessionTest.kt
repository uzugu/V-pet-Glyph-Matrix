package com.digimon.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphToySessionTest {
    @Test fun aodAfterEndRecoversWithoutRebind() {
        val session = GlyphToySession()
        session.bind(); session.end()
        assertFalse(session.active)
        assertTrue(session.aod())
        assertTrue(session.active)
        assertFalse(session.interactive)
    }
    @Test fun lateEventsCannotReclaimAnUnboundDisplay() {
        val session = GlyphToySession()
        session.bind(); session.aod(); session.unbind()
        assertFalse(session.aod())
        assertFalse(session.start())
        assertFalse(session.active)
    }
    @Test fun repeatedAodAndReturnToInteractiveAreSupported() {
        val session = GlyphToySession()
        session.bind()
        repeat(3) { assertTrue(session.aod()); assertTrue(session.active) }
        assertTrue(session.start())
        assertTrue(session.interactive)
        session.unbind(); session.bind()
        assertTrue(session.active)
    }
}
