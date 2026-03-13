package com.digimon.glyph.emulator

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.ByteBuffer

/**
 * Saves and restores E0C6200 emulator state to SharedPreferences.
 * Supports autosave and 3 manual save slots.
 */
class StateManager(context: Context) {

    data class SaveInfo(
        val exists: Boolean,
        val romName: String?,
        val romHash: String?,
        val timestampMs: Long
    )

    companion object {
        private const val PREFS_NAME = "digimon_state"
        private const val SLOT_COUNT = 3

        // New autosave keys
        private const val KEY_AUTOSAVE_STATE_JSON = "autosave_state_json"
        private const val KEY_AUTOSAVE_ROM_NAME = "autosave_rom_name"
        private const val KEY_AUTOSAVE_ROM_HASH = "autosave_rom_hash"
        private const val KEY_AUTOSAVE_SAVE_TIME = "autosave_save_time"
        private const val KEY_AUTOSAVE_SEQ = "autosave_seq"

        // Legacy keys (kept for migration compatibility)
        private const val KEY_STATE_JSON = "cpu_state"
        private const val KEY_ROM_NAME = "rom_name"
        private const val KEY_SAVE_TIME = "save_time"

        // Slot key prefixes
        private const val KEY_SLOT_STATE_PREFIX = "slot_state_json_"
        private const val KEY_SLOT_ROM_NAME_PREFIX = "slot_rom_name_"
        private const val KEY_SLOT_ROM_HASH_PREFIX = "slot_rom_hash_"
        private const val KEY_SLOT_SAVE_TIME_PREFIX = "slot_save_time_"
        private const val KEY_SLOT_SEQ_PREFIX = "slot_seq_"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(emulator: E0C6200, romName: String, romHash: String? = null) {
        saveAutosave(emulator, romName, romHash)
    }

    fun restore(emulator: E0C6200): Boolean {
        return restoreAutosave(emulator, expectedRomName = null, expectedRomHash = null)
    }

    fun saveAutosave(
        emulator: E0C6200,
        romName: String,
        romHash: String?,
        sync: Boolean = false
    ): Long {
        val scoped = scopedAutosaveKeys(romName, romHash)
        return saveToKeys(
            emulator = emulator,
            stateKey = scoped.stateKey,
            romNameKey = scoped.romNameKey,
            romHashKey = scoped.romHashKey,
            timeKey = scoped.timeKey,
            seqKey = scoped.seqKey,
            romName = romName,
            romHash = romHash,
            sync = sync
        )
    }

    fun restoreAutosave(
        emulator: E0C6200,
        expectedRomName: String?,
        expectedRomHash: String?
    ): Boolean {
        val scoped = scopedAutosaveKeys(expectedRomName, expectedRomHash)
        if (prefs.contains(scoped.stateKey)) {
            return restoreFromKeys(
                emulator = emulator,
                stateKey = scoped.stateKey,
                romNameKey = scoped.romNameKey,
                romHashKey = scoped.romHashKey,
                expectedRomName = expectedRomName,
                expectedRomHash = expectedRomHash
            )
        }
        val hasNew = prefs.contains(KEY_AUTOSAVE_STATE_JSON)
        if (hasNew) {
            return restoreFromKeys(
                emulator = emulator,
                stateKey = KEY_AUTOSAVE_STATE_JSON,
                romNameKey = KEY_AUTOSAVE_ROM_NAME,
                romHashKey = KEY_AUTOSAVE_ROM_HASH,
                expectedRomName = expectedRomName,
                expectedRomHash = expectedRomHash
            )
        }
        // Fallback to legacy autosave keys.
        return restoreFromKeys(
            emulator = emulator,
            stateKey = KEY_STATE_JSON,
            romNameKey = KEY_ROM_NAME,
            romHashKey = null,
            expectedRomName = expectedRomName,
            expectedRomHash = expectedRomHash
        )
    }

    fun saveSlot(
        slot: Int,
        emulator: E0C6200,
        romName: String,
        romHash: String?,
        sync: Boolean = false
    ): Long {
        val s = normalizeSlot(slot)
        val scoped = scopedSlotKeys(s, romName, romHash)
        return saveToKeys(
            emulator = emulator,
            stateKey = scoped.stateKey,
            romNameKey = scoped.romNameKey,
            romHashKey = scoped.romHashKey,
            timeKey = scoped.timeKey,
            seqKey = scoped.seqKey,
            romName = romName,
            romHash = romHash,
            sync = sync
        )
    }

    fun restoreSlot(
        slot: Int,
        emulator: E0C6200,
        expectedRomName: String?,
        expectedRomHash: String?
    ): Boolean {
        val s = normalizeSlot(slot)
        val scoped = scopedSlotKeys(s, expectedRomName, expectedRomHash)
        if (prefs.contains(scoped.stateKey)) {
            return restoreFromKeys(
                emulator = emulator,
                stateKey = scoped.stateKey,
                romNameKey = scoped.romNameKey,
                romHashKey = scoped.romHashKey,
                expectedRomName = expectedRomName,
                expectedRomHash = expectedRomHash
            )
        }
        return restoreFromKeys(
            emulator = emulator,
            stateKey = "$KEY_SLOT_STATE_PREFIX$s",
            romNameKey = "$KEY_SLOT_ROM_NAME_PREFIX$s",
            romHashKey = "$KEY_SLOT_ROM_HASH_PREFIX$s",
            expectedRomName = expectedRomName,
            expectedRomHash = expectedRomHash
        )
    }

    fun getAutosaveInfo(expectedRomName: String?, expectedRomHash: String?): SaveInfo {
        val scoped = scopedAutosaveKeys(expectedRomName, expectedRomHash)
        if (prefs.contains(scoped.stateKey)) {
            return SaveInfo(
                exists = true,
                romName = prefs.getString(scoped.romNameKey, null),
                romHash = prefs.getString(scoped.romHashKey, null),
                timestampMs = prefs.getLong(scoped.timeKey, 0L)
            )
        }
        if (prefs.contains(KEY_AUTOSAVE_STATE_JSON)) {
            val legacyRomName = prefs.getString(KEY_AUTOSAVE_ROM_NAME, null)
            val legacyRomHash = prefs.getString(KEY_AUTOSAVE_ROM_HASH, null)
            if (romMatches(legacyRomName, legacyRomHash, expectedRomName, expectedRomHash)) {
                return SaveInfo(
                    exists = true,
                    romName = legacyRomName,
                    romHash = legacyRomHash,
                    timestampMs = prefs.getLong(KEY_AUTOSAVE_SAVE_TIME, 0L)
                )
            }
        }
        // Legacy fallback
        if (prefs.contains(KEY_STATE_JSON)) {
            val legacyRomName = prefs.getString(KEY_ROM_NAME, null)
            if (!romMatches(legacyRomName, null, expectedRomName, expectedRomHash)) {
                return SaveInfo(false, null, null, 0L)
            }
            return SaveInfo(
                exists = true,
                romName = legacyRomName,
                romHash = null,
                timestampMs = prefs.getLong(KEY_SAVE_TIME, 0L)
            )
        }
        return SaveInfo(false, null, null, 0L)
    }

    fun getAutosaveSeq(expectedRomName: String?, expectedRomHash: String?): Long {
        val scoped = scopedAutosaveKeys(expectedRomName, expectedRomHash)
        if (prefs.contains(scoped.stateKey)) {
            return prefs.getLong(scoped.seqKey, 0L)
        }
        val info = getAutosaveInfo(expectedRomName, expectedRomHash)
        return if (info.exists && prefs.contains(KEY_AUTOSAVE_STATE_JSON)) {
            prefs.getLong(KEY_AUTOSAVE_SEQ, 0L)
        } else {
            0L
        }
    }

    fun getSlotSeq(slot: Int, expectedRomName: String?, expectedRomHash: String?): Long {
        val s = normalizeSlot(slot)
        val scoped = scopedSlotKeys(s, expectedRomName, expectedRomHash)
        if (prefs.contains(scoped.stateKey)) {
            return prefs.getLong(scoped.seqKey, 0L)
        }
        return if (legacySlotMatches(s, expectedRomName, expectedRomHash)) {
            prefs.getLong("$KEY_SLOT_SEQ_PREFIX$s", 0L)
        } else {
            0L
        }
    }

    fun getSlotInfo(slot: Int, expectedRomName: String?, expectedRomHash: String?): SaveInfo {
        val s = normalizeSlot(slot)
        val scoped = scopedSlotKeys(s, expectedRomName, expectedRomHash)
        if (prefs.contains(scoped.stateKey)) {
            return SaveInfo(
                exists = true,
                romName = prefs.getString(scoped.romNameKey, null),
                romHash = prefs.getString(scoped.romHashKey, null),
                timestampMs = prefs.getLong(scoped.timeKey, 0L)
            )
        }
        if (!legacySlotMatches(s, expectedRomName, expectedRomHash)) {
            return SaveInfo(false, null, null, 0L)
        }
        return SaveInfo(
            exists = true,
            romName = prefs.getString("$KEY_SLOT_ROM_NAME_PREFIX$s", null),
            romHash = prefs.getString("$KEY_SLOT_ROM_HASH_PREFIX$s", null),
            timestampMs = prefs.getLong("$KEY_SLOT_SAVE_TIME_PREFIX$s", 0L)
        )
    }

    fun getAllSlotInfo(expectedRomName: String?, expectedRomHash: String?): List<SaveInfo> {
        val list = ArrayList<SaveInfo>(SLOT_COUNT)
        for (slot in 1..SLOT_COUNT) {
            list.add(getSlotInfo(slot, expectedRomName, expectedRomHash))
        }
        return list
    }

    fun clearAutosave(expectedRomName: String?, expectedRomHash: String?) {
        val scoped = scopedAutosaveKeys(expectedRomName, expectedRomHash)
        prefs.edit()
            .remove(scoped.stateKey)
            .remove(scoped.romNameKey)
            .remove(scoped.romHashKey)
            .remove(scoped.timeKey)
            .remove(scoped.seqKey)
            .apply()
    }

    fun clearSlot(slot: Int, expectedRomName: String?, expectedRomHash: String?) {
        val s = normalizeSlot(slot)
        val scoped = scopedSlotKeys(s, expectedRomName, expectedRomHash)
        prefs.edit()
            .remove(scoped.stateKey)
            .remove(scoped.romNameKey)
            .remove(scoped.romHashKey)
            .remove(scoped.timeKey)
            .remove(scoped.seqKey)
            .apply()
    }

    fun getSavedRomName(expectedRomName: String?, expectedRomHash: String?): String? {
        val autosave = getAutosaveInfo(expectedRomName, expectedRomHash)
        if (autosave.exists) return autosave.romName
        return null
    }

    fun hasSavedState(expectedRomName: String?, expectedRomHash: String?): Boolean =
        getAutosaveInfo(expectedRomName, expectedRomHash).exists

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun saveToKeys(
        emulator: E0C6200,
        stateKey: String,
        romNameKey: String,
        romHashKey: String?,
        timeKey: String,
        seqKey: String,
        romName: String,
        romHash: String?,
        sync: Boolean
    ): Long {
        val serializable = serializeState(emulator.getState())
        val nextSeq = prefs.getLong(seqKey, 0L) + 1L
        val editor = prefs.edit()
            .putString(stateKey, gson.toJson(serializable))
            .putString(romNameKey, romName)
            .putLong(timeKey, System.currentTimeMillis())
            .putLong(seqKey, nextSeq)
        if (romHashKey != null) {
            editor.putString(romHashKey, romHash)
        }
        if (sync) {
            editor.commit()
        } else {
            editor.apply()
        }
        return nextSeq
    }

    private fun restoreFromKeys(
        emulator: E0C6200,
        stateKey: String,
        romNameKey: String,
        romHashKey: String?,
        expectedRomName: String?,
        expectedRomHash: String?
    ): Boolean {
        val json = prefs.getString(stateKey, null) ?: return false
        val savedRomName = prefs.getString(romNameKey, null)
        val savedRomHash = if (romHashKey != null) prefs.getString(romHashKey, null) else null
        if (!romMatches(savedRomName, savedRomHash, expectedRomName, expectedRomHash)) {
            return false
        }
        return try {
            emulator.restoreState(deserializeState(json))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun serializeState(state: Map<String, Any>): Map<String, Any> {
        val serializable = mutableMapOf<String, Any>()
        for ((key, value) in state) {
            when (value) {
                is IntArray -> serializable[key] = encodeIntArray(value)
                else -> serializable[key] = value
            }
        }
        return serializable
    }

    private fun deserializeState(json: String): Map<String, Any> {
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val raw: Map<String, Any> = gson.fromJson(json, type)
        val state = mutableMapOf<String, Any>()
        for ((key, value) in raw) {
            when {
                key == "RAM" || key == "VRAM" -> state[key] = decodeIntArray(value as String)
                value is Double -> state[key] = value.toInt()
                else -> state[key] = value
            }
        }
        return state
    }

    private fun romMatches(
        savedRomName: String?,
        savedRomHash: String?,
        expectedRomName: String?,
        expectedRomHash: String?
    ): Boolean {
        if (expectedRomHash != null && savedRomHash != null) {
            return savedRomHash == expectedRomHash
        }
        if (expectedRomName != null && savedRomName != null) {
            return savedRomName == expectedRomName
        }
        return true
    }

    private data class SlotKeys(
        val stateKey: String,
        val romNameKey: String,
        val romHashKey: String,
        val timeKey: String,
        val seqKey: String
    )

    private fun scopedAutosaveKeys(romName: String?, romHash: String?): SlotKeys {
        val token = romScopeToken(romName, romHash)
        return SlotKeys(
            stateKey = "${KEY_AUTOSAVE_STATE_JSON}_$token",
            romNameKey = "${KEY_AUTOSAVE_ROM_NAME}_$token",
            romHashKey = "${KEY_AUTOSAVE_ROM_HASH}_$token",
            timeKey = "${KEY_AUTOSAVE_SAVE_TIME}_$token",
            seqKey = "${KEY_AUTOSAVE_SEQ}_$token"
        )
    }

    private fun scopedSlotKeys(slot: Int, romName: String?, romHash: String?): SlotKeys {
        val token = romScopeToken(romName, romHash)
        return SlotKeys(
            stateKey = "${KEY_SLOT_STATE_PREFIX}${token}_$slot",
            romNameKey = "${KEY_SLOT_ROM_NAME_PREFIX}${token}_$slot",
            romHashKey = "${KEY_SLOT_ROM_HASH_PREFIX}${token}_$slot",
            timeKey = "${KEY_SLOT_SAVE_TIME_PREFIX}${token}_$slot",
            seqKey = "${KEY_SLOT_SEQ_PREFIX}${token}_$slot"
        )
    }

    private fun romScopeToken(romName: String?, romHash: String?): String {
        if (!romHash.isNullOrBlank()) return "h_${romHash.lowercase()}"
        if (!romName.isNullOrBlank()) {
            val sanitized = romName
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
            if (sanitized.isNotEmpty()) return "n_$sanitized"
        }
        return "global"
    }

    private fun legacySlotMatches(slot: Int, expectedRomName: String?, expectedRomHash: String?): Boolean {
        val s = normalizeSlot(slot)
        val stateKey = "$KEY_SLOT_STATE_PREFIX$s"
        if (!prefs.contains(stateKey)) return false
        val savedRomName = prefs.getString("$KEY_SLOT_ROM_NAME_PREFIX$s", null)
        val savedRomHash = prefs.getString("$KEY_SLOT_ROM_HASH_PREFIX$s", null)
        return romMatches(savedRomName, savedRomHash, expectedRomName, expectedRomHash)
    }

    private fun normalizeSlot(slot: Int): Int = slot.coerceIn(1, SLOT_COUNT)

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
