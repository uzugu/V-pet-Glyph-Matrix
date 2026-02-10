package com.digimon.glyph.emulator

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Converts E0C6200 VRAM (160 nibbles + 8 port values) into a 25x25 Bitmap
 * for the Nothing Phone 3 Glyph Matrix.
 *
 * The Digimon V3 display is a 32x16 pixel LCD + 8 status icons.
 * VRAM layout (from DigimonV3.svg analysis):
 * - Each VRAM nibble has 4 bits, each bit is one LCD segment (pixel)
 * - Bit 0 = top pixel in a 4-pixel column, bit 3 = bottom
 * - Segments are arranged in columns from right-to-left in the SVG
 *
 * For the 25x25 Glyph Matrix, we:
 * 1. Extract the 32x16 game area from VRAM
 * 2. Center-crop 32 columns -> 25 columns (preserve middle gameplay area)
 * 3. Center the 16 rows vertically in the 25-row grid
 * 4. Render overlay menu dots in top and bottom rows
 */
class DisplayBridge {

    companion object {
        const val GLYPH_SIZE = 25
        const val LCD_WIDTH = 32
        const val LCD_HEIGHT = 16
        const val FULL_DEBUG_WIDTH = 32
        const val FULL_DEBUG_HEIGHT = 24

        // Green-on-black LCD aesthetic
        private val PIXEL_ON = Color.rgb(0, 255, 80)
        private val PIXEL_OFF = Color.BLACK
        private val ICON_ON = Color.rgb(255, 200, 0)
        private val ICON_OFF = Color.rgb(120, 90, 24)

        // Vertical offset: center 16 rows in 25 (pad 4 top for icons, 5 bottom for icons)
        private const val GAME_Y_OFFSET = 4

        // Center-crop from 32 -> 25, dropping side pixels first.
        private const val H_CROP_LEFT = (LCD_WIDTH - GLYPH_SIZE) / 2 // 3
        private const val FULL_GAME_Y_OFFSET = 4
    }

    // Exact per-column mapping from DigimonV3.svg (<g id="col_X"> blocks).
    // Each column has four nibbles listed bottom->top, while each nibble's bit0 is bottom pixel.
    private val lcdColNibblesBottomToTop = arrayOf(
        intArrayOf(40, 41, 120, 121),
        intArrayOf(42, 43, 122, 123),
        intArrayOf(44, 45, 124, 125),
        intArrayOf(46, 47, 126, 127),
        intArrayOf(48, 49, 128, 129),
        intArrayOf(50, 51, 130, 131),
        intArrayOf(52, 53, 132, 133),
        intArrayOf(54, 55, 134, 135),
        intArrayOf(58, 59, 138, 139),
        intArrayOf(60, 61, 140, 141),
        intArrayOf(62, 63, 142, 143),
        intArrayOf(64, 65, 144, 145),
        intArrayOf(66, 67, 146, 147),
        intArrayOf(68, 69, 148, 149),
        intArrayOf(70, 71, 150, 151),
        intArrayOf(72, 73, 152, 153),
        intArrayOf(38, 39, 118, 119),
        intArrayOf(36, 37, 116, 117),
        intArrayOf(34, 35, 114, 115),
        intArrayOf(32, 33, 112, 113),
        intArrayOf(30, 31, 110, 111),
        intArrayOf(28, 29, 108, 109),
        intArrayOf(26, 27, 106, 107),
        intArrayOf(24, 25, 104, 105),
        intArrayOf(16, 17, 96, 97),
        intArrayOf(14, 15, 94, 95),
        intArrayOf(12, 13, 92, 93),
        intArrayOf(10, 11, 90, 91),
        intArrayOf(8, 9, 88, 89),
        intArrayOf(6, 7, 86, 87),
        intArrayOf(4, 5, 84, 85),
        intArrayOf(2, 3, 82, 83),
    )

    // Game area pixel extraction: returns 16x32 boolean grid
    private val lcdPixels = Array(LCD_HEIGHT) { BooleanArray(LCD_WIDTH) }

    /**
     * Render VRAM data to a 25x25 Bitmap.
     * @param vramData Array from E0C6200.getVRAM() — 160 VRAM nibbles + 8 port values
     */
    fun renderFrame(vramData: IntArray): Bitmap {
        // Extract LCD pixels from VRAM using the segment mapping
        extractPixels(vramData)

        val bitmap = Bitmap.createBitmap(GLYPH_SIZE, GLYPH_SIZE, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(GLYPH_SIZE * GLYPH_SIZE)

        // Fill background
        pixels.fill(PIXEL_OFF)

        // Render game area: 16 rows, center-cropped from 32 to 25 columns
        for (row in 0 until LCD_HEIGHT) {
            val outY = row + GAME_Y_OFFSET
            if (outY >= GLYPH_SIZE) break
            for (col in 0 until GLYPH_SIZE) {
                val srcCol = col + H_CROP_LEFT
                if (lcdPixels[row][srcCol]) {
                    pixels[outY * GLYPH_SIZE + col] = PIXEL_ON
                }
            }
        }

        // Render all 8 indicator dots (4 top + 4 bottom).
        renderMenuDots(pixels, readMenuDotStates(vramData))

        bitmap.setPixels(pixels, 0, GLYPH_SIZE, 0, 0, GLYPH_SIZE, GLYPH_SIZE)
        return bitmap
    }

    /**
     * Render an uncropped digivice-style debug frame:
     * 32x16 LCD plus 8 menu icons in a 32x24 canvas.
     */
    fun renderFullDebugFrame(vramData: IntArray): Bitmap {
        extractPixels(vramData)

        val bitmap = Bitmap.createBitmap(FULL_DEBUG_WIDTH, FULL_DEBUG_HEIGHT, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(FULL_DEBUG_WIDTH * FULL_DEBUG_HEIGHT)
        pixels.fill(PIXEL_OFF)

        for (row in 0 until LCD_HEIGHT) {
            val outY = row + FULL_GAME_Y_OFFSET
            if (outY >= FULL_DEBUG_HEIGHT) break
            for (col in 0 until LCD_WIDTH) {
                if (lcdPixels[row][col]) {
                    pixels[outY * FULL_DEBUG_WIDTH + col] = PIXEL_ON
                }
            }
        }

        renderFullDebugIcons(pixels, readMenuDotStates(vramData))

        bitmap.setPixels(pixels, 0, FULL_DEBUG_WIDTH, 0, 0, FULL_DEBUG_WIDTH, FULL_DEBUG_HEIGHT)
        return bitmap
    }

    private fun extractPixels(vramData: IntArray) {
        // Clear
        for (row in lcdPixels) row.fill(false)

        if (vramData.size < 160) return

        // Top->bottom rows use reversed nibble order and reversed bit order.
        for (col in 0 until LCD_WIDTH) {
            val nibbleStack = lcdColNibblesBottomToTop[col]
            for (stackIdxTopDown in 0 until 4) {
                val nibble = nibbleStack[3 - stackIdxTopDown]
                if (nibble >= vramData.size) continue
                for (bitTopDown in 0 until 4) {
                    val row = stackIdxTopDown * 4 + bitTopDown
                    val bit = 3 - bitTopDown
                    lcdPixels[row][col] = (vramData[nibble] shr bit) and 1 == 1
                }
            }
        }
    }

    private fun readMenuDotStates(vramData: IntArray): BooleanArray {
        // Exactly 8 indicator bits in DigimonV3.svg:
        // Top:    nibble 0/18/20/22, bit 0
        // Bottom: nibble 137/155/157/159, bit 3
        val nibbles = intArrayOf(0, 18, 20, 22, 137, 155, 157, 159)
        val masks = intArrayOf(0x1, 0x1, 0x1, 0x1, 0x8, 0x8, 0x8, 0x8)
        val active = BooleanArray(8)
        for (i in 0 until 8) {
            val nibbleIdx = nibbles[i]
            val mask = masks[i]
            active[i] = nibbleIdx < vramData.size && (vramData[nibbleIdx] and mask) != 0
        }
        return active
    }

    private fun renderMenuDots(pixels: IntArray, states: BooleanArray) {
        // Layout for circular visible area:
        // 0-1: top pair, 2-3: upper side pair, 4-5: lower side pair, 6-7: bottom pair
        val positions = arrayOf(
            intArrayOf(8, 1),   // top-left
            intArrayOf(16, 1),  // top-right
            intArrayOf(2, 7),   // upper-left side
            intArrayOf(22, 7),  // upper-right side
            intArrayOf(2, 17),  // lower-left side
            intArrayOf(22, 17), // lower-right side
            intArrayOf(8, 23),  // bottom-left
            intArrayOf(16, 23), // bottom-right
        )

        for (i in 0 until 8) {
            val cx = positions[i][0]
            val cy = positions[i][1]
            drawDot(pixels, cx, cy, states.getOrElse(i) { false }, GLYPH_SIZE, GLYPH_SIZE)
        }
    }

    private fun renderFullDebugIcons(pixels: IntArray, states: BooleanArray) {
        // Show all 8 indicator states in a compact "full device" strip:
        // 4 top + 4 bottom across full 32-pixel width.
        val positions = arrayOf(
            intArrayOf(4, 1),
            intArrayOf(10, 1),
            intArrayOf(21, 1),
            intArrayOf(27, 1),
            intArrayOf(4, 22),
            intArrayOf(10, 22),
            intArrayOf(21, 22),
            intArrayOf(27, 22),
        )
        for (i in 0 until 8) {
            val cx = positions[i][0]
            val cy = positions[i][1]
            drawDot(pixels, cx, cy, states.getOrElse(i) { false }, FULL_DEBUG_WIDTH, FULL_DEBUG_HEIGHT)
        }
    }

    private fun drawDot(
        pixels: IntArray,
        cx: Int,
        cy: Int,
        active: Boolean,
        width: Int,
        height: Int
    ) {
        val color = if (active) ICON_ON else ICON_OFF
        val px = cx.coerceIn(0, width - 1)
        val py = cy.coerceIn(0, height - 1)

        // Keep inactive dots subtle; make active dots easier to spot.
        setPixelSafe(pixels, px, py, color, width, height)
        if (active) {
            setPixelSafe(pixels, px - 1, py, color, width, height)
            setPixelSafe(pixels, px + 1, py, color, width, height)
            setPixelSafe(pixels, px, py - 1, color, width, height)
            setPixelSafe(pixels, px, py + 1, color, width, height)
        }
    }

    private fun setPixelSafe(pixels: IntArray, x: Int, y: Int, color: Int, width: Int, height: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        pixels[y * width + x] = color
    }
}
