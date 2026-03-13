package com.digimon.glyph.emulator

import android.graphics.Bitmap
import android.os.SystemClock

/**
 * In-process shared frame previews for debug UI in the launcher activity.
 */
object FrameDebugState {

    data class Snapshot(
        val updatedAtMs: Long,
        val glyphFrame: Bitmap?,
        val fullFrame: Bitmap?,
        val digimonState: DigimonState?,
        val ram: IntArray?,
        val vram: IntArray?
    )

    @Volatile
    private var updatedAtMs: Long = 0L

    @Volatile
    private var glyphFrame: Bitmap? = null

    @Volatile
    private var fullFrame: Bitmap? = null

    @Volatile
    private var digimonState: DigimonState? = null

    @Volatile
    private var ram: IntArray? = null

    @Volatile
    private var vram: IntArray? = null

    @Volatile
    private var lastReadAtMs: Long = 0L

    @Synchronized
    fun update(
        glyph: Bitmap,
        full: Bitmap,
        atMs: Long,
        state: DigimonState? = null,
        ramData: IntArray? = null,
        vramData: IntArray? = null
    ) {
        // Copy both bitmaps — DisplayBridge reuses backing bitmaps, so widgets/debug
        // readers need stable snapshots that cannot be mutated under them.
        glyphFrame = glyph.copy(glyph.config ?: Bitmap.Config.ARGB_8888, false)
        fullFrame = full.copy(full.config ?: Bitmap.Config.ARGB_8888, false)
        digimonState = state
        ram = ramData?.copyOf()
        vram = vramData?.copyOf()
        updatedAtMs = atMs
    }

    @Synchronized
    fun clear() {
        glyphFrame = null
        fullFrame = null
        digimonState = null
        ram = null
        vram = null
        updatedAtMs = 0L
        lastReadAtMs = 0L
    }

    fun snapshot(): Snapshot {
        lastReadAtMs = SystemClock.uptimeMillis()
        return Snapshot(updatedAtMs, glyphFrame, fullFrame, digimonState, ram, vram)
    }

    fun isObserved(windowMs: Long, nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        val last = lastReadAtMs
        return last > 0L && nowMs - last <= windowMs
    }
}
