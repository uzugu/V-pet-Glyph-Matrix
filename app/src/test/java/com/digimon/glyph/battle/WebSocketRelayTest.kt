package com.digimon.glyph.battle

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonParser
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class WebSocketRelayTest {
    @Test fun releasedProtocolJoinsAndSendsNativeBattlePackets() {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        val joined = CountDownLatch(1)
        val ready = CountDownLatch(1)
        val packet = CountDownLatch(1)
        var join = ""
        var received = ""
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JsonParser.parseString(text).asJsonObject
                if (json["op"].asString == "join") {
                    join = text
                    joined.countDown()
                    webSocket.send("{\"op\":\"ready\",\"peer\":\"fixture\"}")
                } else {
                    received = text
                    packet.countDown()
                }
            }
        }))
        server.start()
        val urlField = BattleTransportSettings::class.java.getDeclaredField("relayUrl").apply { isAccessible = true }
        val previousUrl = urlField.get(BattleTransportSettings)
        val transport = InternetBattleTransport(context, listener = object : BattleTransport.Listener {
            override fun onConnected(peerName: String) { ready.countDown() }
            override fun onDisconnected(reason: String) {}
            override fun onMessage(type: String, body: String?) {}
        }, joinIdentityProvider = { InternetBattleTransport.RelayIdentity("tamer", "Agumon", "Rookie", "V1", "guide_v1_agumon.png") })
        try {
            urlField.set(BattleTransportSettings, server.url("/ws?room=test-room").toString().replace("http://", "ws://"))
            assertEquals("battle join requested", transport.startJoin())
            assertTrue(joined.await(5, TimeUnit.SECONDS))
            val envelope = JsonParser.parseString(join).asJsonObject
            assertEquals("test-room", envelope["room"].asString)
            assertEquals("digimon-battle-v1", envelope["proto"].asString)
            assertEquals("Agumon", envelope["digimon"].asString)
            assertEquals("guide_v1_agumon.png", envelope["sprite"].asString)
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            assertTrue(transport.sendVpetPacket(123))
            assertTrue(packet.await(5, TimeUnit.SECONDS))
            val payload = JsonParser.parseString(received).asJsonObject
            assertEquals("vpet_packet", payload["type"].asString)
            assertEquals("123", payload["body"].asString)
        } finally {
            transport.stop()
            val executor = InternetBattleTransport::class.java.getDeclaredField("ioExecutor").apply { isAccessible = true }.get(transport) as ExecutorService
            executor.shutdownNow()
            urlField.set(BattleTransportSettings, previousUrl)
            server.shutdown()
        }
    }
}
