package com.digimon.glyph

import android.content.Context
import android.graphics.Bitmap
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject

/**
 * Bridges emulator Bitmap output to the Nothing Glyph Matrix hardware.
 * Creates a GlyphMatrixFrame from a 25x25 Bitmap and pushes it to the LED grid.
 */
class GlyphRenderer(private val context: Context) {

    private var manager: GlyphMatrixManager? = null
    private var ready = false

    fun init(mgr: GlyphMatrixManager) {
        manager = mgr
        ready = true
    }

    fun pushFrame(bitmap: Bitmap) {
        val mgr = manager ?: return
        if (!ready) return
        try {
            val obj = GlyphMatrixObject.Builder()
                .setImageSource(bitmap)
                .setPosition(0, 0)
                .setBrightness(4095)
                .build()

            val frame = GlyphMatrixFrame.Builder()
                .addTop(obj)
                .build(context)

            mgr.setMatrixFrame(frame.render())
        } catch (_: Exception) {
            // SDK may throw if service disconnected
        }
    }

    fun turnOff() {
        try {
            manager?.turnOff()
        } catch (_: Exception) {}
    }

    fun release() {
        ready = false
        manager = null
    }
}
