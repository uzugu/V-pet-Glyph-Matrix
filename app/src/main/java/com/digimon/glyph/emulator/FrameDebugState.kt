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
        val fullFrame: Bitmap?
    )

    @Volatile
    private var updatedAtMs: Long = 0L

    @Volatile
    private var glyphFrame: Bitmap? = null

    @Volatile
    private var fullFrame: Bitmap? = null

    @Volatile
    private var lastReadAtMs: Long = 0L

    @Synchronized
    fun update(glyph: Bitmap, full: Bitmap, atMs: Long) {
        // Copy the glyph bitmap — the original is a shared mutable object owned by the
        // emulator loop / Glyph SDK and may be recycled before the widget reads it.
        // fullDebug is already freshly allocated each call so no copy needed there.
        glyphFrame = glyph.copy(glyph.config ?: Bitmap.Config.ARGB_8888, false)
        fullFrame = full
        updatedAtMs = atMs
    }

    @Synchronized
    fun clear() {
        glyphFrame = null
        fullFrame = null
        updatedAtMs = 0L
        lastReadAtMs = 0L
    }

    fun snapshot(): Snapshot {
        lastReadAtMs = SystemClock.uptimeMillis()
        return Snapshot(updatedAtMs, glyphFrame, fullFrame)
    }

    fun isObserved(windowMs: Long, nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        val last = lastReadAtMs
        return last > 0L && nowMs - last <= windowMs
    }
}
