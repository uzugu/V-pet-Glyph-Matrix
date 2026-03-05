package com.digimon.glyph.widget.skins

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF

/**
 * Renders the 32x24 full debug frame inside a simplified Digimon device shell.
 *
 * Accepts a [ColorScheme] so multiple colorways can be created from the same rendering logic.
 * Use the companion factory functions [v1], [v2], [v3], [white] to get pre-built variants.
 *
 * When [iconAtlas] is provided, proper V-Pet icon artwork is drawn above/below the LCD
 * instead of the simple dots baked into the fullFrame bitmap.
 */
class DigiviceSkin(private val colors: ColorScheme, private val iconAtlas: VpetIconAtlas? = null) {

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
        fun v1(iconAtlas: VpetIconAtlas? = null) = DigiviceSkin(ColorScheme(
            bezel      = Color.parseColor("#5A6070"),
            bezelDark  = Color.parseColor("#3A4050"),
            frame      = Color.parseColor("#1A2050"),
            lcdBg      = Color.parseColor("#0A1218"),
            buttonHole = Color.parseColor("#001824"),
            button     = Color.parseColor("#2A3440"),
            contact    = Color.parseColor("#C0C8D0")
        ), iconAtlas)

        /** Black/Red — Digimon Digital Monster V2 colorway */
        fun v2(iconAtlas: VpetIconAtlas? = null) = DigiviceSkin(ColorScheme(
            bezel      = Color.parseColor("#1A1A1A"),
            bezelDark  = Color.parseColor("#0A0A0A"),
            frame      = Color.parseColor("#1A0000"),
            lcdBg      = Color.parseColor("#0A0808"),
            buttonHole = Color.parseColor("#1A0000"),
            button     = Color.parseColor("#3A1010"),
            contact    = Color.parseColor("#CC2200")
        ), iconAtlas)

        /** Orange — Digimon V3 original colorway (existing default) */
        fun v3(iconAtlas: VpetIconAtlas? = null) = DigiviceSkin(ColorScheme(
            bezel      = Color.parseColor("#F35C00"),
            bezelDark  = Color.parseColor("#C04900"),
            frame      = Color.parseColor("#00257C"),
            lcdBg      = Color.parseColor("#0A1A0A"),
            buttonHole = Color.parseColor("#002D1D"),
            button     = Color.parseColor("#334433"),
            contact    = Color.parseColor("#C8A030")
        ), iconAtlas)

        /** White/Gold — V-Tamer / Burst Mode style */
        fun white(iconAtlas: VpetIconAtlas? = null) = DigiviceSkin(ColorScheme(
            bezel      = Color.parseColor("#F0F0E8"),
            bezelDark  = Color.parseColor("#C8C8C0"),
            frame      = Color.parseColor("#001899"),
            lcdBg      = Color.parseColor("#08100A"),
            buttonHole = Color.parseColor("#001018"),
            button     = Color.parseColor("#D0D8E0"),
            contact    = Color.parseColor("#D4A800")
        ), iconAtlas)
    }

    private val paint = Paint().apply { isAntiAlias = true }
    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    private val iconPaint = Paint().apply { isFilterBitmap = true }

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
        val dstRect = Rect(drawLeft.toInt(), drawTop.toInt(),
                           (drawLeft + drawW).toInt(), (drawTop + drawH).toInt())

        // Draw fullFrame as-is (no dot masking — colored icons drawn on top will cover dots)
        val srcRect = Rect(0, 0, src.width, src.height)

        canvas.save()
        val lcdClipPath = Path().also {
            it.addRoundRect(lcdRect, lcdCorner, lcdCorner, Path.Direction.CW)
        }
        canvas.clipPath(lcdClipPath)
        // Fill LCD with black so it matches icon backing (no lcdBg bands visible)
        if (iconAtlas != null) {
            paint.color = Color.BLACK
            canvas.drawRect(lcdRect, paint)
        }
        canvas.drawBitmap(src, srcRect, dstRect, pixelPaint)
        canvas.restore()

        // Draw V-Pet status icons inside the LCD at the same positions where dots appear
        if (iconAtlas != null) {
            drawIcons(canvas, src, drawLeft, drawTop, drawW, drawH)
        }

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

    /**
     * Draw 8 V-Pet status icons inside the LCD area at the same relative positions
     * where the dot markers appear in the 32×24 fullFrame grid.
     * Icons are tinted to match the emulator palette: green (inactive) / yellow (active).
     *
     * Dot positions in 32×24 grid:
     *   Top row (y=1):   x = 4, 10, 21, 27
     *   Bottom row (y=22): x = 4, 10, 21, 27
     */
    private fun drawIcons(
        canvas: Canvas, fullFrame: Bitmap,
        drawLeft: Float, drawTop: Float, drawW: Float, drawH: Float
    ) {
        val atlas = iconAtlas ?: return
        val states = atlas.readIconStates(fullFrame)

        // Icon size proportional to the fitted display area
        val iconSize = (drawW * 0.14f).toInt().coerceAtLeast(1)

        // Emulator-matching colors: active = golden yellow, inactive = dim green
        val activeColor = Color.rgb(255, 200, 0)
        val inactiveColor = Color.rgb(0, 160, 0)

        // Relative X positions of dots in the 32-wide grid
        val dotXs = floatArrayOf(4f / 32f, 10f / 32f, 21f / 32f, 27f / 32f)
        // Relative Y positions of dots in the 24-tall grid
        val dotTopY = 1f / 24f
        val dotBotY = 23f / 24f

        for (i in 0 until VpetIconAtlas.ICON_COUNT) {
            val col = i % 4
            val isTop = i < 4

            val cx = drawLeft + drawW * dotXs[col]
            val cy = drawTop + drawH * (if (isTop) dotTopY else dotBotY)

            val bmp = atlas.getIconBitmap(i, iconSize, iconSize) ?: continue
            val ix = cx - iconSize / 2f
            val iy = cy - iconSize / 2f

            // Black backing to hide the original dots underneath
            paint.color = Color.BLACK
            canvas.drawRect(ix, iy, ix + iconSize, iy + iconSize, paint)

            // Tint icon: SRC_IN replaces black pixels with the target color, preserving alpha
            val tintColor = if (states[i]) activeColor else inactiveColor
            iconPaint.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            iconPaint.alpha = if (states[i]) 255 else 120
            canvas.drawBitmap(bmp, ix, iy, iconPaint)
        }
        // Reset color filter
        iconPaint.colorFilter = null
    }
}
