package com.digimon.glyph.widget.skins

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF

/**
 * Renders the 32x24 full debug frame inside a simplified Digimon device shell.
 *
 * Accepts a [ColorScheme] so multiple colorways can be created from the same rendering logic.
 * Use the companion factory functions [v1], [v2], [v3], [white] to get pre-built variants.
 */
class DigiviceSkin(private val colors: ColorScheme) {

    data class ColorScheme(
        val bezel: Int,
        val bezelDark: Int,
        val frame: Int,
        val lcdBg: Int,
        val buttonHole: Int,
        val button: Int,
        val contact: Int
    )

    companion object {
        /** Gray/Blue — Digimon Digital Monster V1, 1997 original */
        fun v1() = DigiviceSkin(ColorScheme(
            bezel      = Color.parseColor("#5A6070"),
            bezelDark  = Color.parseColor("#3A4050"),
            frame      = Color.parseColor("#1A2050"),
            lcdBg      = Color.parseColor("#0A1218"),
            buttonHole = Color.parseColor("#001824"),
            button     = Color.parseColor("#2A3440"),
            contact    = Color.parseColor("#C0C8D0")
        ))

        /** Black/Red — Digimon Digital Monster V2 colorway */
        fun v2() = DigiviceSkin(ColorScheme(
            bezel      = Color.parseColor("#1A1A1A"),
            bezelDark  = Color.parseColor("#0A0A0A"),
            frame      = Color.parseColor("#1A0000"),
            lcdBg      = Color.parseColor("#0A0808"),
            buttonHole = Color.parseColor("#1A0000"),
            button     = Color.parseColor("#3A1010"),
            contact    = Color.parseColor("#CC2200")
        ))

        /** Orange — Digimon V3 original colorway (existing default) */
        fun v3() = DigiviceSkin(ColorScheme(
            bezel      = Color.parseColor("#F35C00"),
            bezelDark  = Color.parseColor("#C04900"),
            frame      = Color.parseColor("#00257C"),
            lcdBg      = Color.parseColor("#0A1A0A"),
            buttonHole = Color.parseColor("#002D1D"),
            button     = Color.parseColor("#334433"),
            contact    = Color.parseColor("#C8A030")
        ))

        /** White/Gold — V-Tamer / Burst Mode style */
        fun white() = DigiviceSkin(ColorScheme(
            bezel      = Color.parseColor("#F0F0E8"),
            bezelDark  = Color.parseColor("#C8C8C0"),
            frame      = Color.parseColor("#001899"),
            lcdBg      = Color.parseColor("#08100A"),
            buttonHole = Color.parseColor("#001018"),
            button     = Color.parseColor("#D0D8E0"),
            contact    = Color.parseColor("#D4A800")
        ))
    }

    private val paint = Paint().apply { isAntiAlias = true }
    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    fun render(fullFrame: Bitmap?, outputWidth: Int, outputHeight: Int): Bitmap? {
        val src = fullFrame ?: return null

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.TRANSPARENT)

        val totalW = outputWidth.toFloat()
        val totalH = outputHeight.toFloat()                       // device body fills full bitmap height
        val offsetY = 0f

        val bodyW = totalW * 0.74f
        val bodyH = totalH
        val bodyLeft = 0f
        val bodyTop = offsetY
        val bodyRight = bodyLeft + bodyW
        val bodyBottom = bodyTop + bodyH
        val bodyCorner = bodyW * 0.07f

        // Top contact (gold/colored ellipse)
        paint.color = colors.contact
        val contactCx = bodyLeft + bodyW * 0.5f
        val contactCy = bodyTop - totalH * 0.015f
        canvas.drawOval(
            RectF(contactCx - bodyW * 0.05f, contactCy - totalH * 0.025f,
                  contactCx + bodyW * 0.05f, contactCy + totalH * 0.025f),
            paint
        )

        // Device body (bezel) — spans full width so button column is inside the shell
        paint.color = colors.bezel
        canvas.drawRoundRect(RectF(bodyLeft, bodyTop, totalW, bodyBottom),
            bodyCorner, bodyCorner, paint)

        // Inner bezel shadow — also full width
        paint.color = colors.bezelDark
        val innerM = bodyW * 0.03f
        canvas.drawRoundRect(
            RectF(bodyLeft + innerM, bodyTop + innerM, totalW - innerM, bodyBottom - innerM),
            bodyCorner * 0.65f, bodyCorner * 0.65f, paint)

        // Display frame
        val frameML = bodyW * 0.07f
        val frameMV = bodyH * 0.09f
        val frameLeft   = bodyLeft + frameML
        val frameRight  = bodyRight - frameML
        val frameTop    = bodyTop + frameMV
        val frameBottom = bodyBottom - frameMV
        val frameCorner = bodyCorner * 0.25f

        paint.color = colors.frame
        canvas.drawRoundRect(RectF(frameLeft, frameTop, frameRight, frameBottom),
            frameCorner, frameCorner, paint)

        // LCD area
        val lcdM = (frameRight - frameLeft) * 0.05f
        val lcdLeft   = frameLeft + lcdM
        val lcdRight  = frameRight - lcdM
        val lcdTop    = frameTop + lcdM
        val lcdBottom = frameBottom - lcdM
        val lcdCorner = frameCorner * 0.7f
        val lcdRect = RectF(lcdLeft, lcdTop, lcdRight, lcdBottom)

        paint.color = colors.lcdBg
        canvas.drawRoundRect(lcdRect, lcdCorner, lcdCorner, paint)

        // Draw 32×24 pixels into LCD area, preserving 4:3 aspect ratio
        val lcdW = lcdRight - lcdLeft
        val lcdH = lcdBottom - lcdTop
        val srcAspect = 32f / 24f
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

        canvas.save()
        val lcdClipPath = Path().also {
            it.addRoundRect(lcdRect, lcdCorner, lcdCorner, Path.Direction.CW)
        }
        canvas.clipPath(lcdClipPath)
        canvas.drawBitmap(src, srcRect, dstRect, pixelPaint)
        canvas.restore()

        // Three buttons in the right column
        val buttonColW = totalW - bodyW
        val btnGap = buttonColW * 0.08f
        val btnCx = bodyRight + btnGap + buttonColW * 0.42f
        val maxHoleR   = buttonColW * 0.40f
        // Button Y: fixed % of OUTPUT height so they always match the XML click zones
        // (19/21/21/21/18 layout → buttons at 29.5%, 50.5%, 71.5% of widget height)
        val btnSpacing = outputHeight * 0.21f
        val btnMidY    = outputHeight * 0.505f
        // Clamp hole radius so adjacent buttons never overlap (45% of spacing = 10% gap)
        val holeRadius = minOf(maxHoleR, btnSpacing * 0.45f)
        val btnRadius  = holeRadius * 0.70f

        for (i in -1..1) {
            val cy = btnMidY + i * btnSpacing
            paint.color = colors.buttonHole
            canvas.drawCircle(btnCx, cy, holeRadius, paint)
            paint.color = colors.button
            canvas.drawCircle(btnCx, cy, btnRadius, paint)
        }

        return output
    }
}
