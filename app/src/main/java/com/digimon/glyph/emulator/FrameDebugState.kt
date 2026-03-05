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
        val digimonState: DigimonState?
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
    private var lastReadAtMs: Long = 0L

    @Synchronized
    fun update(glyph: Bitmap, full: Bitmap, atMs: Long, state: DigimonState? = null) {
        // Copy the glyph bitmap — the original is a shared mutable object owned by the
        // emulator loop / Glyph SDK and may be recycled before the widget reads it.
        // fullDebug is already freshly allocated each call so no copy needed there.
        glyphFrame = glyph.copy(glyph.config ?: Bitmap.Config.ARGB_8888, false)
        fullFrame = full
        digimonState = state
        updatedAtMs = atMs
    }

    @Synchronized
    fun clear() {
        glyphFrame = null
        fullFrame = null
        digimonState = null
        updatedAtMs = 0L
        lastReadAtMs = 0L
    }

    fun snapshot(): Snapshot {
        lastReadAtMs = SystemClock.uptimeMillis()
        return Snapshot(updatedAtMs, glyphFrame, fullFrame, digimonState)
    }

    fun isObserved(windowMs: Long, nowMs: Long = SystemClock.uptimeMillis()): Boolean {
        val last = lastReadAtMs
        return last > 0L && nowMs - last <= windowMs
    }
}
