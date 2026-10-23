package com.digimon.glyph.emulator

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

class CommandQueueTest {
    private lateinit var context: Context
    private val stored = mutableMapOf<String, Any?>()
    @Before fun setup() {
        stored.clear()
        context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenAnswer { stored[it.getArgument<String>(0)] ?: it.getArgument<String?>(1) }
        `when`(prefs.getLong(anyString(), anyLong())).thenAnswer { stored[it.getArgument<String>(0)] ?: it.getArgument<Long>(1) }
        `when`(editor.putString(anyString(), nullable(String::class.java))).thenAnswer { stored[it.getArgument<String>(0)] = it.getArgument<String?>(1); editor }
        `when`(editor.putLong(anyString(), anyLong())).thenAnswer { stored[it.getArgument<String>(0)] = it.getArgument<Long>(1); editor }
    }
    @Test fun rapidSaveLoadAndResetAreNotOverwritten() {
        val actions = listOf(EmulatorCommandBus.CMD_SAVE_SLOT, EmulatorCommandBus.CMD_LOAD_SLOT, EmulatorCommandBus.CMD_FULL_RESET)
        val ids = actions.map { EmulatorCommandBus.post(context, it, 1) }
        for (i in actions.indices) {
            val pending = EmulatorCommandBus.readPending(context, 0)!!
            assertEquals(ids[i], pending.id)
            assertEquals(actions[i], pending.type)
            EmulatorCommandBus.ack(context, pending.id, "done")
        }
        assertNull(EmulatorCommandBus.readPending(context, 0))
        EmulatorCommandBus.ack(context, ids[0], "late")
        assertEquals(ids.last(), EmulatorCommandBus.readAck(context)!!.id)
    }
    @Test fun deviceCommandsCarryTheSelectedRomHash() {
        EmulatorStatus.device("classic", "rom-one")
        EmulatorCommandBus.post(context, EmulatorCommandBus.CMD_LOAD_SLOT, 1)
        EmulatorStatus.device("D-Power", "rom-two")
        assertEquals("rom-one", EmulatorCommandBus.readPending(context, 0)!!.romHash)
    }
}
