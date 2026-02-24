package com.digimon.glyph.widget.skins

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/**
 * Renders the 25x25 glyph frame as a Nothing Phone-style LED dot grid.
 *
 * Visual style (from GlyphMatrixEditor):
 * - Black background
 * - Each dot is a rounded rectangle
 * - Active: white, inactive: dark gray at low opacity
 * - Diamond mask: per-row widths narrow at top/bottom, wide in the middle
 */
class GlyphMatrixSkin {

    companion object {
        private const val GRID = 25
        private val BG_COLOR = Color.BLACK
        private val DOT_ON = Color.WHITE
        private val DOT_OFF = Color.argb(51, 54, 54, 54)  // #363636 at 20% alpha
        private val DOT_MENU_OFF = Color.argb(115, 95, 95, 95)
        private val SRC_ICON_OFF = Color.rgb(120, 90, 24)

        // Diamond shape mask: number of active dots per row.
        // Matches the Nothing Phone 3 Glyph Matrix physical shape.
        // Rows 0-12 grow from 7 to 25, rows 13-24 mirror back down.
        private val ROW_WIDTHS = intArrayOf(
            7, 9, 11, 13, 15, 17, 19, 21, 23, 25,
            25, 25, 25, 25, 25,
            23, 21, 19, 17, 15, 13, 11, 9, 7, 7
        )
    }

    private val dotPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    fun render(glyphFrame: Bitmap?, outputSize: Int): Bitmap? {
        val src = glyphFrame ?: return null

        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(BG_COLOR)

        // Read source pixels
        val srcPixels = IntArray(GRID * GRID)
        src.getPixels(srcPixels, 0, GRID, 0, 0, GRID, GRID)

        // Calculate dot geometry
        val cellSize = outputSize.toFloat() / GRID
        val dotSize = cellSize * 0.78f  // dot takes ~78% of cell, leaving gap
        val cornerRadius = dotSize * 0.20f  // 20% border-radius like the web editor
        val offset = (cellSize - dotSize) / 2f

        val rect = RectF()

        for (row in 0 until GRID) {
            val width = ROW_WIDTHS.getOrElse(row) { GRID }
            val startCol = (GRID - width) / 2
            val endColExclusive = startCol + width

            for (col in 0 until GRID) {
                val srcColor = srcPixels[row * GRID + col]
                val hasSignal = srcColor != Color.BLACK && srcColor != 0
                val inMask = col in startCol until endColExclusive

                // Preserve menu dots that sit outside the strict diamond mask.
                // Inside mask: draw full matrix (on/off). Outside mask: draw only lit icon signals.
                if (!inMask && !hasSignal) continue

                dotPaint.color = when {
                    srcColor == SRC_ICON_OFF -> DOT_MENU_OFF
                    hasSignal -> DOT_ON
                    else -> DOT_OFF
                }

                val x = col * cellSize + offset
                val y = row * cellSize + offset
                rect.set(x, y, x + dotSize, y + dotSize)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, dotPaint)
            }
        }

        return output
    }
}
