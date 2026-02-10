package com.digimon.glyph.emulator

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.ByteBuffer

/**
 * Saves and restores E0C6200 emulator state to SharedPreferences.
 * Allows the virtual pet to survive app restarts.
 */
class StateManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "digimon_state"
        private const val KEY_STATE_JSON = "cpu_state"
        private const val KEY_ROM_NAME = "rom_name"
        private const val KEY_SAVE_TIME = "save_time"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(emulator: E0C6200, romName: String) {
        val state = emulator.getState()
        // Convert IntArray values to Base64 for compact storage
        val serializable = mutableMapOf<String, Any>()
        for ((key, value) in state) {
            when (value) {
                is IntArray -> serializable[key] = encodeIntArray(value)
                else -> serializable[key] = value
            }
        }
        prefs.edit()
            .putString(KEY_STATE_JSON, gson.toJson(serializable))
            .putString(KEY_ROM_NAME, romName)
            .putLong(KEY_SAVE_TIME, System.currentTimeMillis())
            .apply()
    }

    fun restore(emulator: E0C6200): Boolean {
        val json = prefs.getString(KEY_STATE_JSON, null) ?: return false
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val raw: Map<String, Any> = gson.fromJson(json, type)
            val state = mutableMapOf<String, Any>()
            for ((key, value) in raw) {
                when {
                    key == "RAM" || key == "VRAM" -> {
                        state[key] = decodeIntArray(value as String)
                    }
                    value is Double -> state[key] = value.toInt()
                    else -> state[key] = value
                }
            }
            emulator.restoreState(state)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getSavedRomName(): String? = prefs.getString(KEY_ROM_NAME, null)

    fun hasSavedState(): Boolean = prefs.contains(KEY_STATE_JSON)

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encodeIntArray(arr: IntArray): String {
        val buf = ByteBuffer.allocate(arr.size * 4)
        for (v in arr) buf.putInt(v)
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    private fun decodeIntArray(b64: String): IntArray {
        val bytes = Base64.decode(b64, Base64.NO_WRAP)
        val buf = ByteBuffer.wrap(bytes)
        val arr = IntArray(bytes.size / 4)
        for (i in arr.indices) arr[i] = buf.getInt()
        return arr
    }
}
