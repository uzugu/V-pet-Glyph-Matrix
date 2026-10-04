package com.digimon.glyph.battle

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonParser
import java.net.InetAddress
import java.net.ServerSocket
import java.security.Permission
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.RETURNS_SELF

/** Real loopback TCP transport; only Android preferences/URI/scheduler/log boundaries are faked.
 * Does not establish Android process-death durability or original-emulator fidelity. */
@Suppress("DEPRECATION")
class InternetBattleTransportTest {
    @Test
    fun hostConsumesSavedRelayUrlAfterMemoryReset() = assertSavedRelayConsumed("host")

    @Test
    fun joinConsumesSavedRelayUrlAfterMemoryReset() = assertSavedRelayConsumed("join")

    @Test
    fun encodedSlashRemainsInsideFirstRoomSegment() =
        assertSavedRelayConsumed("join", "room%2Fone/ignored", "room/one")

    private fun assertSavedRelayConsumed(
        role: String,
        roomPath: String = "restarted-room",
        expectedRoom: String = "restarted-room"
    ) {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        val prefs = mock(SharedPreferences::class.java)
        val statePrefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
        val stateEditor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
        val stored = mutableMapOf<String, String?>()
        `when`(context.getSharedPreferences("battle_transport_settings", Context.MODE_PRIVATE)).thenReturn(prefs)
        `when`(context.getSharedPreferences("battle_state", Context.MODE_PRIVATE)).thenReturn(statePrefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(statePrefs.edit()).thenReturn(stateEditor)
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenAnswer {
            val key = it.getArgument<String>(0)
            if (stored.containsKey(key)) stored[key] else it.getArgument<String?>(1)
        }
        `when`(editor.putString(anyString(), nullable(String::class.java))).thenAnswer {
            stored[it.getArgument(0)] = it.getArgument(1)
            editor
        }
        val settingFields = listOf("relayUrl", "transportType", "simulationPreset", "tamerName", "lobbyName")
            .map { BattleTransportSettings::class.java.getDeclaredField(it).apply { isAccessible = true } }
        val previousSettings = settingFields.map { it.get(BattleTransportSettings) }
        val previousSecurityManager = System.getSecurityManager()
        var transport: InternetBattleTransport? = null
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            // Fail closed if a regression attempts the shipped default or any non-test endpoint.
            System.setSecurityManager(object : SecurityManager() {
                override fun checkPermission(permission: Permission) { }
                override fun checkConnect(host: String, port: Int) {
                    check(host == "127.0.0.1" && (port == server.localPort || port == -1)) {
                        "Non-fixture endpoint blocked: $host:$port"
                    }
                }
            })
            try {
                val savedUrl = "tcp://127.0.0.1:${server.localPort}/$roomPath"
                BattleTransportSettings.setRelayUrl(context, "  $savedUrl  ")
                assertEquals(savedUrl, stored["relay_url"])
                // A distinct loopback-only stale value proves reload, without exposing a live relay.
                settingFields.first().set(BattleTransportSettings, "tcp://127.0.0.1:1/stale-room")
                BattleTransportSettings.init(context)
                assertEquals(savedUrl, BattleTransportSettings.getRelayUrl())
                transport = InternetBattleTransport(context, endpointNameProvider = { "local-contract-test" })
                assertEquals("battle $role requested", if (role == "host") transport.startHost() else transport.startJoin())
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    assertEquals(server.localPort, socket.localPort)
                    assertTrue(socket.inetAddress.isLoopbackAddress)
                    val line = socket.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
                    val envelope = JsonParser.parseString(line).asJsonObject
                    assertEquals("join", envelope.get("op").asString)
                    assertEquals(expectedRoom, envelope.get("room").asString)
                    assertEquals(role, envelope.get("role").asString)
                    assertEquals("local-contract-test", envelope.get("name").asString)
                    assertEquals("digimon-battle-v1", envelope.get("proto").asString)
                    transport.stop()
                }
            } finally {
                transport?.let {
                    it.stop()
                    val executorField = InternetBattleTransport::class.java.getDeclaredField("ioExecutor")
                    executorField.isAccessible = true
                    val executor = executorField.get(it) as ExecutorService
                    executor.shutdownNow()
                    assertTrue("Transport executor must terminate", executor.awaitTermination(5, TimeUnit.SECONDS))
                }
                System.setSecurityManager(previousSecurityManager)
                settingFields.zip(previousSettings).forEach { (field, value) -> field.set(BattleTransportSettings, value) }
            }
        }
    }
}
