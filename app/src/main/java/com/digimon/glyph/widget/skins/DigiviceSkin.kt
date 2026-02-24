package com.digimon.glyph.widget.skins

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

/**
 * Renders the 32x24 full debug frame inside a simplified Digimon V3 device shell.
 *
 * Visual style (from DigimonV3.svg):
 * - Orange rounded-rect bezel (#F35C00)
 * - Dark blue display frame (#00257C)
 * - Green-on-black LCD pixels
 * - Three circular buttons on the right side
 * - Gold top contact
 */
class DigiviceSkin {

    companion object {
        private val COLOR_BEZEL = Color.parseColor("#F35C00")
        private val COLOR_BEZEL_DARK = Color.parseColor("#C04900")
        private val COLOR_FRAME = Color.parseColor("#00257C")
        private val COLOR_LCD_BG = Color.parseColor("#0A1A0A")
        private val COLOR_BUTTON_HOLE = Color.parseColor("#002D1D")
        private val COLOR_BUTTON = Color.parseColor("#334433")
        private val COLOR_CONTACT = Color.parseColor("#C8A030")
        private val PIXEL_OFF = Color.BLACK

        private const val SRC_WIDTH = 32
        private const val SRC_HEIGHT = 24
    }

    private val paint = Paint().apply { isAntiAlias = true }
    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    fun render(fullFrame: Bitmap?, outputSize: Int): Bitmap? {
        val src = fullFrame ?: return null

        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        // The Digimon V3 device is landscape (~4:3 device body + button column on right).
        // We fit the whole thing into the square canvas with equal margins top/bottom.
        //
        // Total layout: [body (3 units wide)] [button column (1 unit wide)] = 4 units wide
        // We target a 4:3 total aspect (device+buttons wide, device tall).
        // Fit inside square: totalWidth = outputSize, totalHeight = outputSize * 3/4
        val totalW = outputSize.toFloat()
        val totalH = outputSize * 0.72f          // 72% of square height → comfortably fits
        val offsetY = (outputSize - totalH) / 2f  // center vertically in square

        // Column widths: body = 78%, buttons = 22%
        val bodyW = totalW * 0.74f
        val buttonColW = totalW - bodyW
        val bodyH = totalH
        val bodyLeft = 0f
        val bodyTop = offsetY
        val bodyRight = bodyLeft + bodyW
        val bodyBottom = bodyTop + bodyH
        val bodyCorner = bodyW * 0.07f

        // Top contact (gold ellipse) — centered on body, just above it
        paint.color = COLOR_CONTACT
        val contactCx = bodyLeft + bodyW * 0.5f
        val contactCy = bodyTop - totalH * 0.015f
        canvas.drawOval(
            RectF(contactCx - bodyW * 0.05f, contactCy - totalH * 0.025f,
                  contactCx + bodyW * 0.05f, contactCy + totalH * 0.025f),
            paint
        )

        // Device body (orange bezel)
        paint.color = COLOR_BEZEL
        canvas.drawRoundRect(RectF(bodyLeft, bodyTop, bodyRight, bodyBottom),
            bodyCorner, bodyCorner, paint)

        // Inner bezel shadow (slightly darker orange inset)
        paint.color = COLOR_BEZEL_DARK
        val innerM = bodyW * 0.03f
        canvas.drawRoundRect(
            RectF(bodyLeft + innerM, bodyTop + innerM, bodyRight - innerM, bodyBottom - innerM),
            bodyCorner * 0.65f, bodyCorner * 0.65f, paint)

        // Display frame (dark blue) — takes most of the body interior
        val frameML = bodyW * 0.07f   // left/right margin inside body
        val frameMV = bodyH * 0.09f   // top/bottom margin inside body
        val frameLeft   = bodyLeft + frameML
        val frameRight  = bodyRight - frameML
        val frameTop    = bodyTop + frameMV
        val frameBottom = bodyBottom - frameMV
        val frameCorner = bodyCorner * 0.25f

        paint.color = COLOR_FRAME
        canvas.drawRoundRect(RectF(frameLeft, frameTop, frameRight, frameBottom),
            frameCorner, frameCorner, paint)

        // LCD area with rounded corners matching the frame corner radius
        val lcdM = (frameRight - frameLeft) * 0.05f
        val lcdLeft   = frameLeft + lcdM
        val lcdRight  = frameRight - lcdM
        val lcdTop    = frameTop + lcdM
        val lcdBottom = frameBottom - lcdM
        val lcdCorner = frameCorner * 0.7f
        val lcdRect = RectF(lcdLeft, lcdTop, lcdRight, lcdBottom)

        // Fill LCD background with rounded corners
        paint.color = COLOR_LCD_BG
        canvas.drawRoundRect(lcdRect, lcdCorner, lcdCorner, paint)

        // Draw the 32×24 frame into the LCD area, preserving 32:24 (4:3) aspect ratio,
        // clipped to the same rounded rect so pixels don't bleed into the frame border
        val lcdW = lcdRight - lcdLeft
        val lcdH = lcdBottom - lcdTop
        val srcAspect = SRC_WIDTH.toFloat() / SRC_HEIGHT  // 4:3
        val dstAspect = lcdW / lcdH
        val (drawW, drawH) = if (dstAspect > srcAspect) {
            val h = lcdH; Pair(h * srcAspect, h)
        } else {
            val w = lcdW; Pair(w, w / srcAspect)
        }
        val drawLeft = lcdLeft + (lcdW - drawW) / 2f
        val drawTop  = lcdTop  + (lcdH - drawH) / 2f
        val srcRect = Rect(0, 0, src.width, src.height)
        val dstRect = Rect(drawLeft.toInt(), drawTop.toInt(),
                           (drawLeft + drawW).toInt(), (drawTop + drawH).toInt())

        // Clip to rounded LCD rect, draw pixels, restore clip
        canvas.save()
        val lcdClipPath = Path().also { it.addRoundRect(lcdRect, lcdCorner, lcdCorner, Path.Direction.CW) }
        canvas.clipPath(lcdClipPath)
        canvas.drawBitmap(src, srcRect, dstRect, pixelPaint)
        canvas.restore()

        // Three buttons in the right column — fully to the right of the bezel
        // Keep holeRadius small enough that it never crosses bodyRight
        val btnGap = buttonColW * 0.08f           // small gap from bezel edge
        val btnCx = bodyRight + btnGap + buttonColW * 0.42f
        val maxHoleR = buttonColW * 0.40f         // can't exceed half the column
        val btnRadius = maxHoleR * 0.70f
        val holeRadius = maxHoleR
        val btnSpacing = bodyH * 0.28f
        val btnMidY = bodyTop + bodyH * 0.5f

        for (i in -1..1) {
            val cy = btnMidY + i * btnSpacing
            paint.color = COLOR_BUTTON_HOLE
            canvas.drawCircle(btnCx, cy, holeRadius, paint)
            paint.color = COLOR_BUTTON
            canvas.drawCircle(btnCx, cy, btnRadius, paint)
        }

        return output
    }
}
