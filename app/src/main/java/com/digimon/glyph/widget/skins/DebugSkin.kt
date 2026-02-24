package com.digimon.glyph.widget.skins

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * Simple debug skin: nearest-neighbor upscale of the 32x24 full debug frame.
 * Green on black, matching the existing LCD pixel color.
 */
class DebugSkin {

    companion object {
        private val PIXEL_ON = Color.rgb(0, 255, 80)
        private val PIXEL_OFF = Color.BLACK
        private const val SRC_WIDTH = 32
        private const val SRC_HEIGHT = 24
    }

    private val paint = Paint().apply {
        isFilterBitmap = false  // nearest-neighbor for pixel art
        isAntiAlias = false
    }

    fun render(fullFrame: Bitmap?, outputSize: Int): Bitmap? {
        val src = fullFrame ?: return null

        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(PIXEL_OFF)

        // Scale to fit while maintaining aspect ratio, centered
        val scale = minOf(
            outputSize.toFloat() / SRC_WIDTH,
            outputSize.toFloat() / SRC_HEIGHT
        )
        val scaledW = (SRC_WIDTH * scale).toInt()
        val scaledH = (SRC_HEIGHT * scale).toInt()
        val offsetX = (outputSize - scaledW) / 2
        val offsetY = (outputSize - scaledH) / 2

        val srcRect = Rect(0, 0, src.width, src.height)
        val dstRect = Rect(offsetX, offsetY, offsetX + scaledW, offsetY + scaledH)
        canvas.drawBitmap(src, srcRect, dstRect, paint)

        return output
    }
}
