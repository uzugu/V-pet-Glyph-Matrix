package com.digimon.glyph.emulator

import android.graphics.Bitmap

/**
 * In-process shared frame previews for debug UI in the launcher activity.
 */
object FrameDebugState {

    data class Snapshot(
        val updatedAtMs: Long,
        val glyphFrame: Bitmap?,
        val fullFrame: Bitmap?
    )

    @Volatile
    private var updatedAtMs: Long = 0L

    @Volatile
    private var glyphFrame: Bitmap? = null

    @Volatile
    private var fullFrame: Bitmap? = null

    @Synchronized
    fun update(glyph: Bitmap, full: Bitmap, atMs: Long) {
        glyphFrame = glyph
        fullFrame = full
        updatedAtMs = atMs
    }

    @Synchronized
    fun clear() {
        glyphFrame = null
        fullFrame = null
        updatedAtMs = 0L
    }

    fun snapshot(): Snapshot = Snapshot(updatedAtMs, glyphFrame, fullFrame)
}
