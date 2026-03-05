package com.digimon.glyph.widget.skins

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.caverock.androidsvg.SVG

/**
 * Shared icon atlas for V-Pet device skins.
 *
 * Loads 8 icon SVG shapes extracted from the original DigimonV1 device SVG,
 * caches rendered bitmaps, and provides icon state detection by sampling
 * the fullFrame bitmap's known dot positions.
 *
 * Icon order matches DisplayBridge.readMenuDotStates():
 *   0-3: top row (137_3, 155_3, 157_3, 159_3)
 *   4-7: bottom row (22_0, 20_0, 18_0, 0_0)
 */
class VpetIconAtlas(context: Context) {

    companion object {
        const val ICON_COUNT = 8

        private val ICON_ASSETS = arrayOf(
            "icons/icon_0.svg", "icons/icon_1.svg",
            "icons/icon_2.svg", "icons/icon_3.svg",
            "icons/icon_4.svg", "icons/icon_5.svg",
            "icons/icon_6.svg", "icons/icon_7.svg"
        )

        // Dot positions in the 32×24 fullFrame (from DisplayBridge.renderFullDebugIcons)
        private val DOT_POSITIONS = arrayOf(
            intArrayOf(4, 1), intArrayOf(10, 1), intArrayOf(21, 1), intArrayOf(27, 1),       // top
            intArrayOf(4, 22), intArrayOf(10, 22), intArrayOf(21, 22), intArrayOf(27, 22)     // bottom
        )

        // Brightness threshold: ICON_ON rgb(255,200,0) avg≈152, ICON_OFF rgb(120,90,24) avg≈78
        private const val ACTIVE_THRESHOLD = 100
    }

    private val appContext = context.applicationContext
    private val svgs = arrayOfNulls<SVG>(ICON_COUNT)
    private var svgsLoaded = false

    // Cached icon bitmaps at a specific size
    private val cachedBitmaps = arrayOfNulls<Bitmap>(ICON_COUNT)
    private var cachedW = 0
    private var cachedH = 0

    /**
     * Sample the fullFrame bitmap to extract 8 icon active/inactive states.
     * Reads the center pixel of each known dot position and checks brightness.
     */
    fun readIconStates(fullFrame: Bitmap): BooleanArray {
        val states = BooleanArray(ICON_COUNT)
        for (i in 0 until ICON_COUNT) {
            val x = DOT_POSITIONS[i][0].coerceIn(0, fullFrame.width - 1)
            val y = DOT_POSITIONS[i][1].coerceIn(0, fullFrame.height - 1)
            val pixel = fullFrame.getPixel(x, y)
            val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            states[i] = brightness > ACTIVE_THRESHOLD
        }
        return states
    }

    /**
     * Clear the dot marker pixels (and their "+" cross neighbors) from a bitmap copy.
     * Call this on a COPY of the fullFrame before drawing it to the LCD, so the original
     * dot markers don't show through behind the SVG icon overlays.
     */
    fun maskDotPixels(bitmap: Bitmap) {
        for (pos in DOT_POSITIONS) {
            val cx = pos[0]
            val cy = pos[1]
            // Clear a 3×3 area around each dot to remove the "+" cross pattern
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val px = (cx + dx).coerceIn(0, bitmap.width - 1)
                    val py = (cy + dy).coerceIn(0, bitmap.height - 1)
                    bitmap.setPixel(px, py, Color.TRANSPARENT)
                }
            }
        }
    }

    /**
     * Get icon bitmap at desired size. Bitmaps are cached and only re-rendered if size changes.
     * Returns null if SVG failed to load.
     */
    fun getIconBitmap(index: Int, width: Int, height: Int): Bitmap? {
        if (index < 0 || index >= ICON_COUNT) return null
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)

        if (!svgsLoaded) loadSvgs()

        // Re-render if size changed
        if (cachedW != w || cachedH != h) {
            renderAll(w, h)
        }

        return cachedBitmaps[index]
    }

    private fun loadSvgs() {
        for (i in 0 until ICON_COUNT) {
            try {
                svgs[i] = SVG.getFromAsset(appContext.assets, ICON_ASSETS[i])
            } catch (_: Exception) {
                svgs[i] = null
            }
        }
        svgsLoaded = true
    }

    private fun renderAll(w: Int, h: Int) {
        for (i in 0 until ICON_COUNT) {
            val svg = svgs[i]
            if (svg != null) {
                try {
                    svg.documentWidth = w.toFloat()
                    svg.documentHeight = h.toFloat()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    svg.renderToCanvas(canvas)
                    cachedBitmaps[i] = bmp
                } catch (_: Exception) {
                    cachedBitmaps[i] = null
                }
            }
        }
        cachedW = w
        cachedH = h
    }
}
