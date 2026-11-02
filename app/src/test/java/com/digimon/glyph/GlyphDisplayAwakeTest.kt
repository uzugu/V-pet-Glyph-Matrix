package com.digimon.glyph

import org.junit.Assert.*
import org.junit.Test

class GlyphDisplayAwakeTest {
    @Test fun defaultAndBackgroundRuntimeNeverChangeTimeout() {
        val policy = GlyphDisplayAwake()
        val requests = mutableListOf<Boolean>()
        policy.update(false, true, true, requests::add)
        policy.update(true, false, true, requests::add)
        policy.update(true, true, false, requests::add)
        assertTrue(requests.isEmpty())
    }

    @Test fun optInAppliesOnceAndReleasesOnEndAodStopAndDisable() {
        for (exit in listOf("end", "aod", "stop", "disable")) {
            val policy = GlyphDisplayAwake()
            val session = GlyphToySession().apply { bind() }
            val requests = mutableListOf<Boolean>()
            repeat(3) { policy.update(true, session.interactive, true, requests::add) }
            when (exit) {
                "end" -> session.end()
                "aod" -> session.aod()
            }
            policy.update(exit != "disable", session.interactive, exit != "stop", requests::add)
            assertEquals(exit, listOf(false, true), requests)
        }
    }

    @Test fun reconnectedManagerReceivesFreshRequest() {
        val policy = GlyphDisplayAwake()
        val requests = mutableListOf<Boolean>()
        policy.update(true, true, true, requests::add)
        policy.disconnected()
        policy.update(true, true, true, requests::add)
        policy.update(false, true, true, requests::add)
        assertEquals(listOf(false, false, true), requests)
    }

    @Test fun failedRequestsDoNotSuppressRetryOrForgetRequiredRestoration() {
        val policy = GlyphDisplayAwake()
        fun failure(): (Boolean) -> Unit = { throw IllegalStateException("disconnected") }
        try { policy.update(true, true, true, failure()); fail() } catch (_: IllegalStateException) {}
        val requests = mutableListOf<Boolean>()
        policy.update(true, true, true, requests::add)
        try { policy.update(false, false, false, failure()); fail() } catch (_: IllegalStateException) {}
        policy.update(false, false, false, requests::add)
        assertEquals(listOf(false, true), requests)
    }
}
