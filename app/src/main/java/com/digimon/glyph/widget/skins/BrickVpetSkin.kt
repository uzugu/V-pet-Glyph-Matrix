package com.digimon.glyph.widget.skins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.caverock.androidsvg.SVG

/**
 * Renders the 32×24 full frame inside a faithful SVG-based Digimon V-Pet device shell.
 *
 * The SVG is rendered at its native 3:2 aspect ratio, fit-centered within the widget.
 * LCD content is positioned relative to the SVG render area so it always aligns with
 * the screen opening regardless of widget aspect ratio.
 *
 * The SVG shell bitmap is cached and only re-rendered when widget dimensions change.
 */
class BrickVpetSkin(context: Context, private val svgAssetPath: String, private val iconAtlas: VpetIconAtlas) {

    companion object {
        private const val SVG_ASPECT = 4028f / 2692f  // 1.496:1 — native SVG aspect ratio
    }

    private val appContext = context.applicationContext

    // Cached shell bitmap — only re-rendered when dimensions change
    private var cachedShell: Bitmap? = null
    private var cachedW = 0
    private var cachedH = 0

    // Cached SVG render area within the output bitmap (set by renderShell)
    private var svgX = 0f
    private var svgY = 0f
    private var svgW = 0f
    private var svgH = 0f

    // LCD area: pixel grid width, vertically centered in frame opening
    // X: pixel grid bounds (718.213 to 1975.93) — matches original segment columns
    // Y: centered in frame opening (635→2053, center≈1344), height=943.3 for 4:3 at grid width
    private val lcdLeftN  = 718.213f / 4028f   // 17.8%
    private val lcdTopN   = 872.2f   / 2692f   // 32.4%
    private val lcdRightN = 1975.93f / 4028f   // 49.1%
    private val lcdBotN   = 1815.5f  / 2692f   // 67.4%

    // Icon positions: normalized centers in 4028×2692 SVG space (from extracted bounding boxes)
    private val iconTopY  = 863f / 2692f     // top row center Y
    private val iconBotY  = 1834f / 2692f    // bottom row center Y
    private val iconXs = floatArrayOf(        // X centers for each of the 4 columns
        913f / 4028f, 1202f / 4028f, 1495f / 4028f, 1779f / 4028f
    )
    private val iconSizeW = 185f / 4028f     // average icon width as fraction of SVG
    private val iconSizeH = 162f / 2692f     // average icon height as fraction of SVG

    // Classic V-Pet LCD palette
    private val LCD_ON = Color.rgb(0x1A, 0x1A, 0x1A)    // near-black for active pixels

    private val pixelPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    private val iconPaint = Paint().apply { isFilterBitmap = true }

    /**
     * Remap the emulator's green-on-black palette to authentic V-Pet LCD.
     * Source: ON pixels are bright (green/gold), OFF pixels are dark (black/brown).
     * Output: ON → near-black, OFF → transparent (SVG background shows through).
     */
    private fun remapLcdColors(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val remapped = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = src.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                remapped.setPixel(x, y, if (brightness > 80) LCD_ON else Color.TRANSPARENT)
            }
        }
        return remapped
    }

    fun render(fullFrame: Bitmap?, outputWidth: Int, outputHeight: Int): Bitmap? {
        val src = fullFrame ?: return null

        // Re-render shell SVG if dimensions changed
        if (cachedShell == null || cachedW != outputWidth || cachedH != outputHeight) {
            renderShell(outputWidth, outputHeight)
        }

        val shell = cachedShell ?: return null

        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.TRANSPARENT)

        // 1. Draw cached SVG shell (fit-centered within widget)
        canvas.drawBitmap(shell, 0f, 0f, null)

        // 2. Composite LCD pixels relative to SVG render area
        drawLcd(canvas, src)

        // 3. Draw V-Pet status icons above/below LCD
        drawIcons(canvas, src)

        return output
    }

    private fun renderShell(w: Int, h: Int) {
        try {
            val svg = SVG.getFromAsset(appContext.assets, svgAssetPath)

            // Fit SVG at native aspect ratio within widget (no stretching)
            val widgetAspect = w.toFloat() / h.toFloat()
            if (widgetAspect > SVG_ASPECT) {
                // Widget wider than SVG → height-limited, pillarbox sides
                svgH = h.toFloat()
                svgW = svgH * SVG_ASPECT
            } else {
                // Widget taller than SVG → width-limited, letterbox top/bottom
                svgW = w.toFloat()
                svgH = svgW / SVG_ASPECT
            }
            svgX = (w - svgW) / 2f
            svgY = (h - svgH) / 2f

            svg.documentWidth = svgW
            svg.documentHeight = svgH

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.translate(svgX, svgY)  // center SVG in widget
            svg.renderToCanvas(canvas)

            cachedShell = bmp
            cachedW = w
            cachedH = h
        } catch (e: Exception) {
            // SVG load failed — leave cache null, render() returns null
            cachedShell = null
        }
    }

    private fun drawLcd(canvas: Canvas, src: Bitmap) {
        // LCD positions relative to SVG render area (not widget dimensions)
        val areaL = svgX + svgW * lcdLeftN
        val areaT = svgY + svgH * lcdTopN
        val areaR = svgX + svgW * lcdRightN
        val areaB = svgY + svgH * lcdBotN
        val lcdW = areaR - areaL
        val lcdH = areaB - areaT

        // Clip to LCD area with rounded corners
        val cornerR = lcdW * 0.03f
        canvas.save()
        val clipPath = Path().also {
            it.addRoundRect(
                RectF(areaL, areaT, areaR, areaB),
                cornerR, cornerR, Path.Direction.CW
            )
        }
        canvas.clipPath(clipPath)

        // No background fill — SVG reflector gradients show through

        // Preserve 4:3 aspect ratio (32×24 source), centered in LCD area
        val srcAspect = 32f / 24f
        val dstAspect = lcdW / lcdH
        val (fitW, fitH) = if (dstAspect > srcAspect) {
            val fh = lcdH; Pair(fh * srcAspect, fh)
        } else {
            val fw = lcdW; Pair(fw, fw / srcAspect)
        }

        val fitL = areaL + (lcdW - fitW) / 2f
        val fitT = areaT + (lcdH - fitH) / 2f

        // Remap emulator colors: ON → dark, OFF → transparent
        val remapped = remapLcdColors(src)
        // Mask out dot markers so they don't show behind SVG icons
        iconAtlas.maskDotPixels(remapped)

        val srcRect = Rect(0, 0, remapped.width, remapped.height)
        val dstRect = Rect(
            fitL.toInt(), fitT.toInt(),
            (fitL + fitW).toInt(), (fitT + fitH).toInt()
        )
        canvas.drawBitmap(remapped, srcRect, dstRect, pixelPaint)
        canvas.restore()
    }

    /**
     * Draw 8 V-Pet status icons above and below the LCD area.
     * Icon positions are relative to the SVG render area (svgX/svgY/svgW/svgH).
     * Active icons draw fully opaque; inactive icons draw as faint ghosts.
     */
    private fun drawIcons(canvas: Canvas, fullFrame: Bitmap) {
        val states = iconAtlas.readIconStates(fullFrame)
        val iw = (svgW * iconSizeW).toInt().coerceAtLeast(1)
        val ih = (svgH * iconSizeH).toInt().coerceAtLeast(1)

        for (i in 0 until VpetIconAtlas.ICON_COUNT) {
            val col = i % 4
            val isTop = i < 4

            // Center position relative to SVG render area
            val cx = svgX + svgW * iconXs[col]
            val cy = svgY + svgH * (if (isTop) iconTopY else iconBotY)

            val bmp = iconAtlas.getIconBitmap(i, iw, ih) ?: continue
            iconPaint.alpha = if (states[i]) 255 else 40
            canvas.drawBitmap(bmp, cx - iw / 2f, cy - ih / 2f, iconPaint)
        }
    }
}
