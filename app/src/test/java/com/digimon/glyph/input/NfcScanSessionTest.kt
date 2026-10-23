package com.digimon.glyph.input

import org.junit.Assert.*
import org.junit.Test

class NfcScanSessionTest {
    @Test fun oneInputPerScanEvenWithDuplicateCallbacks() {
        val session = NfcScanSession()
        val token = session.arm(10)
        assertTrue(session.consume(token, 10, true))
        assertFalse(session.consume(token, 10, true))
    }
    @Test fun cancelledScanCannotDeliverAfterPause() {
        val session = NfcScanSession()
        val token = session.arm(10)
        session.cancel()
        assertFalse(session.consume(token, 10, true))
    }
    @Test fun oldCallbackDoesNotConsumeNewScan() {
        val session = NfcScanSession()
        val old = session.arm(10)
        val current = session.arm(10)
        assertFalse(session.consume(old, 10, true))
        assertTrue(session.consume(current, 10, true))
    }
    @Test fun changedRomOrStoppedPetRejectsScan() {
        val session = NfcScanSession()
        assertFalse(session.consume(session.arm(10), 11, true))
        assertFalse(session.consume(session.arm(11), 11, false))
    }
}
