package com.digimon.glyph.emulator

import android.content.Context
import android.content.SharedPreferences
import org.junit.Test
import org.mockito.Mockito.*

class StatePersistenceTest {
    @Test(expected = IllegalStateException::class)
    fun failedDurableSaveCannotAuthoriseRomSwitch() {
        val context = mock(Context::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java, RETURNS_SELF)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.commit()).thenReturn(false)
        StateManager(context).saveAutosave(TimingRom.core(), "test", "hash", sync = true)
    }
}
