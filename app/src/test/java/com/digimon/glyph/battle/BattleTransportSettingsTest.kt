package com.digimon.glyph.battle

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/** Contract: README Internet Relay Battle step 4, consumed by InternetBattleTransport.start. */
class BattleTransportSettingsTest {
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private val stored = mutableMapOf<String, String?>()

    @Before
    fun setUp() {
        stored.clear()
        context = mock(Context::class.java)
        prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(context.getSharedPreferences("battle_transport_settings", Context.MODE_PRIVATE))
            .thenReturn(prefs)
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenAnswer {
            val key = it.getArgument<String>(0)
            if (stored.containsKey(key)) stored[key] else it.getArgument<String?>(1)
        }
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), nullable(String::class.java))).thenAnswer {
            stored[it.getArgument(0)] = it.getArgument(1)
            editor
        }
        BattleTransportSettings.init(context)
    }

    @Test
    fun initReloadsConfiguredRelayUrlAfterProcessMemoryIsReset() {
        val relayUrlField = BattleTransportSettings::class.java.getDeclaredField("relayUrl")
        relayUrlField.isAccessible = true
        val previousUrl = BattleTransportSettings.getRelayUrl()
        try {
            BattleTransportSettings.setRelayUrl(context, "  tcp://example.test:19792/restarted-room  ")
            assertEquals("tcp://example.test:19792/restarted-room", stored["relay_url"])

            // Model a fresh process: retain preferences, discard the singleton's cached URL.
            relayUrlField.set(BattleTransportSettings, "tcp://109.224.229.205:19792/auto")
            BattleTransportSettings.init(context)

            assertEquals("tcp://example.test:19792/restarted-room", BattleTransportSettings.getRelayUrl())
        } finally {
            relayUrlField.set(BattleTransportSettings, previousUrl)
        }
    }

    @Test
    fun setterMakesTrimmedConfiguredUrlAvailableToTransport() {
        BattleTransportSettings.setRelayUrl(context, "  tcp://example.test:19792/room  ")
        assertEquals("tcp://example.test:19792/room", BattleTransportSettings.getRelayUrl())
        assertEquals("tcp://example.test:19792/room", stored["relay_url"])
    }
}
