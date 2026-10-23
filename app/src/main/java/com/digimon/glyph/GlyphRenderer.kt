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
class GlyphRenderer(private val context: Context, private val ownsToyDisplay: () -> Boolean) {

    @Volatile private var manager: GlyphMatrixManager? = null
    @Volatile private var ready = false

    fun init(mgr: GlyphMatrixManager) {
        manager = mgr
        ready = true
    }

    fun pushFrame(bitmap: Bitmap) {
        val mgr = manager ?: return
        if (!ready || !ownsToyDisplay()) return
        try {
            val obj = GlyphMatrixObject.Builder()
                .setImageSource(bitmap)
                .setPosition(0, 0)
                .setBrightness(255)
                .build()

            val frame = GlyphMatrixFrame.Builder()
                .addTop(obj)
                .build(context)

            if (!ready || !ownsToyDisplay()) return
            mgr.setMatrixFrame(frame.render())
            com.digimon.glyph.emulator.EmulatorStatus.glyph(com.digimon.glyph.emulator.EmulatorStatus.Glyph.CONNECTED)
        } catch (_: Exception) {
            com.digimon.glyph.emulator.EmulatorStatus.glyph(com.digimon.glyph.emulator.EmulatorStatus.Glyph.OUTPUT_ERROR)
        }
    }

    fun turnOff() {
        if (!ownsToyDisplay()) return
        try {
            manager?.turnOff()
        } catch (_: Exception) {}
    }

    fun release() {
        ready = false
        manager = null
    }
}
