package com.digimon.glyph.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelayEndpointTest {
    @Test fun autoRoomUsesSavedLobby() {
        val result = RelayEndpoint.websocket("wss://vpet.uzucloud.moe/ws?room=auto", "My Lobby")!!
        assertEquals("My Lobby", result.room)
        assertEquals("wss://vpet.uzucloud.moe/ws?room=My%20Lobby", result.url)
    }
    @Test fun preservesProxyPathAndExplicitRoom() {
        val result = RelayEndpoint.websocket("https://example.test:8443/relay/ws?room=one%2Ftwo&token=test", "default")!!
        assertEquals("one/two", result.room)
        assertEquals("wss://example.test:8443/relay/ws?token=test&room=one%2Ftwo", result.url)
    }
    @Test fun rejectsInvalidEndpoint() {
        assertNull(RelayEndpoint.websocket("file:///tmp/ws", "room"))
        assertNull(RelayEndpoint.websocket("wss://", "room"))
    }
}
