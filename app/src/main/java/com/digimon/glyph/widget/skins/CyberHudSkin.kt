package com.digimon.glyph.widget.skins

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Renders the 32x24 full frame inside a cyberpunk terminal HUD panel,
 * matching the aesthetic of RomLoaderActivity (dark bg, cyan/yellow accents).
 *
 * Layout (400×400):
 *   ┌──────────────────────────────────────┐
 *   │ ◈ DIGIMON GLYPH            [LINK]   │  ← header (~15%)
 *   ├──────────────────────────────────────┤  ← cyan divider
 *   │                                      │
 *   │      [32×24 LCD pixels, centered]    │  ← pixel area (~65%)
 *   │                                      │
 *   ├──────────────────────────────────────┤  ← cyan divider
 *   │ ► V-PET  ◈  FRAME ACTIVE            │  ← footer (~15%)
 *   └──────────────────────────────────────┘
 *   ↑ yellow corner L-brackets at all 4 corners
 *   ↑ cyan left accent strip (4px)
 */
class CyberHudSkin {

    companion object {
        private val COLOR_BG         = Color.parseColor("#0A0D0F")
        private val COLOR_LCD_BG     = Color.parseColor("#060A06")
        private val COLOR_CYAN       = Color.parseColor("#00E5FF")
        private val COLOR_CYAN_DIM   = Color.argb(100, 0, 229, 255)   // 40% alpha cyan
        private val COLOR_YELLOW     = Color.parseColor("#FFD600")
        private val COLOR_TEXT_CYAN  = Color.parseColor("#00E5FF")
        private val COLOR_TEXT_DIM   = Color.parseColor("#557080")
    }

    private val bgPaint    = Paint().apply { isAntiAlias = false }
    private val accentPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val linePaint  = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val textPaint  = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    fun render(fullFrame: Bitmap?, outputWidth: Int, outputHeight: Int): Bitmap? {
        val src = fullFrame ?: return null

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val W = outputWidth.toFloat()
        val H = outputHeight.toFloat()

        // ── Background ────────────────────────────────────────────────
        bgPaint.color = COLOR_BG
        canvas.drawRect(0f, 0f, W, H, bgPaint)

        // ── Zone heights ──────────────────────────────────────────────
        val headerH = H * 0.15f
        val footerH = H * 0.15f
        val pixelAreaTop    = headerH
        val pixelAreaBottom = H - footerH

        // ── Cyan divider lines ────────────────────────────────────────
        linePaint.color = COLOR_CYAN_DIM
        canvas.drawRect(0f, pixelAreaTop - 1f, W, pixelAreaTop + 1f, linePaint)
        canvas.drawRect(0f, pixelAreaBottom - 1f, W, pixelAreaBottom + 1f, linePaint)

        // ── LCD pixel area background ─────────────────────────────────
        bgPaint.color = COLOR_LCD_BG
        canvas.drawRect(0f, pixelAreaTop, W, pixelAreaBottom, bgPaint)

        // ── Draw 32×24 pixels into LCD area, 4:3 aspect-ratio centered
        val lcdW = W
        val lcdH = pixelAreaBottom - pixelAreaTop
        val srcAspect = 32f / 24f
        val dstAspect = lcdW / lcdH
        val (drawW, drawH) = if (dstAspect > srcAspect) {
            val h = lcdH; Pair(h * srcAspect, h)
        } else {
            val w = lcdW; Pair(w, w / srcAspect)
        }
        val drawLeft = (lcdW - drawW) / 2f
        val drawTop  = pixelAreaTop + (lcdH - drawH) / 2f
        val srcRect = Rect(0, 0, src.width, src.height)
        val dstRect = RectF(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH)
        canvas.drawBitmap(src, srcRect, dstRect, pixelPaint)

        // ── Left cyan accent strip ────────────────────────────────────
        accentPaint.color = COLOR_CYAN
        canvas.drawRect(0f, 0f, 4f, H, accentPaint)

        // ── Yellow corner L-brackets ──────────────────────────────────
        val bLen = minOf(W, H) * 0.07f  // bracket arm length (based on smaller dimension)
        val bThk = minOf(W, H) * 0.012f
        val bPad = 8f
        accentPaint.color = COLOR_YELLOW

        // Top-left
        canvas.drawRect(bPad, bPad, bPad + bLen, bPad + bThk, accentPaint)
        canvas.drawRect(bPad, bPad, bPad + bThk, bPad + bLen, accentPaint)
        // Top-right
        canvas.drawRect(W - bPad - bLen, bPad, W - bPad, bPad + bThk, accentPaint)
        canvas.drawRect(W - bPad - bThk, bPad, W - bPad, bPad + bLen, accentPaint)
        // Bottom-left
        canvas.drawRect(bPad, H - bPad - bThk, bPad + bLen, H - bPad, accentPaint)
        canvas.drawRect(bPad, H - bPad - bLen, bPad + bThk, H - bPad, accentPaint)
        // Bottom-right
        canvas.drawRect(W - bPad - bLen, H - bPad - bThk, W - bPad, H - bPad, accentPaint)
        canvas.drawRect(W - bPad - bThk, H - bPad - bLen, W - bPad, H - bPad, accentPaint)

        // ── Header text ───────────────────────────────────────────────
        val textSz = H * 0.065f
        textPaint.textSize = textSz
        textPaint.color = COLOR_TEXT_CYAN

        val headerMidY = headerH / 2f + textSz * 0.35f
        canvas.drawText("◈ DIGIMON GLYPH", 14f, headerMidY, textPaint)

        val linkLabel = "[LINK]"
        val linkW = textPaint.measureText(linkLabel)
        textPaint.color = COLOR_TEXT_DIM
        canvas.drawText(linkLabel, W - linkW - 12f, headerMidY, textPaint)

        // ── Footer text ───────────────────────────────────────────────
        val footerMidY = pixelAreaBottom + footerH / 2f + textSz * 0.35f
        textPaint.color = COLOR_TEXT_CYAN
        canvas.drawText("► V-PET", 14f, footerMidY, textPaint)

        val statusLabel = "◈ FRAME ACTIVE"
        val statusW = textPaint.measureText(statusLabel)
        textPaint.color = COLOR_TEXT_DIM
        canvas.drawText(statusLabel, W - statusW - 12f, footerMidY, textPaint)

        return output
    }
}
