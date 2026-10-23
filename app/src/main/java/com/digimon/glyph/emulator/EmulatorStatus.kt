package com.digimon.glyph.emulator

/** One immutable snapshot for UI state. Frame changes are not runtime heartbeats. */
object EmulatorStatus {
    enum class Run { STOPPED, STARTING, RUNNING, FROZEN, ERROR }
    enum class Glyph { DISCONNECTED, CONNECTED, OUTPUT_ERROR }
    enum class Import { NONE, SCANNING, CHOOSING, SWITCHING, COMPLETE, FAILED, CANCELLED }
    data class Snapshot(
        val revision: Long = 0, val name: String? = null, val hash: String? = null,
        val run: Run = Run.STOPPED, val glyph: Glyph = Glyph.DISCONNECTED,
        val importing: Import = Import.NONE, val request: Long = 0,
        val message: String = "", val error: String? = null
    ) {
        val isDPower get() = hash == RomCatalog.DPOWER_HASH
        val hasClassicMetadata get() = name?.let { DigimonDatabase.resolveVersion(it) } != null && !isDPower
        val busy get() = importing in listOf(Import.SCANNING, Import.CHOOSING, Import.SWITCHING)
    }
    @Volatile private var current = Snapshot()
    fun snapshot() = current
    @Synchronized fun device(name: String?, hash: String?) {
        current = current.copy(revision = current.revision + 1, name = name, hash = hash)
    }
    @Synchronized fun initialDevice(name: String?, hash: String?, expectedRevision: Long) {
        if (current.revision == expectedRevision && current.name == null && !current.busy) device(name, hash)
    }
    @Synchronized fun runtime(run: Run, error: String? = null) {
        current = current.copy(revision = current.revision + 1, run = run, error = error)
    }
    @Synchronized fun glyph(state: Glyph) {
        if (current.glyph != state) current = current.copy(revision = current.revision + 1, glyph = state)
    }
    @Synchronized fun beginImport(): Long? {
        if (current.busy) return null
        val id = current.request + 1
        current = current.copy(revision = current.revision + 1, request = id, importing = Import.SCANNING, message = "Scanning ROM…")
        return id
    }
    @Synchronized fun importResult(id: Long, state: Import, message: String) {
        if (id != current.request || (!current.busy && state != Import.SCANNING)) return
        current = current.copy(revision = current.revision + 1, importing = state, message = message)
    }
}
