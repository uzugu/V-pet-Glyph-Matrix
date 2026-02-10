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
 * 2. Scale 32 columns -> 25 columns (nearest-neighbor)
 * 3. Center the 16 rows vertically in the 25-row grid
 * 4. Render icons in top and bottom rows
 */
class DisplayBridge {

    companion object {
        const val GLYPH_SIZE = 25
        const val LCD_WIDTH = 32
        const val LCD_HEIGHT = 16

        // Green-on-black LCD aesthetic
        private val PIXEL_ON = Color.rgb(0, 255, 80)
        private val PIXEL_OFF = Color.BLACK
        private val ICON_ON = Color.rgb(255, 200, 0)

        // Vertical offset: center 16 rows in 25 (pad 4 top for icons, 5 bottom for icons)
        private const val GAME_Y_OFFSET = 4

        // Horizontal mapping: 25 glyph columns -> 32 LCD columns (nearest-neighbor)
        private val H_MAP = IntArray(GLYPH_SIZE) { col ->
            (col * LCD_WIDTH / GLYPH_SIZE).coerceIn(0, LCD_WIDTH - 1)
        }
    }

    // VRAM segment-to-pixel mapping table.
    // Built from DigimonV3.svg: segment ID "{nibble}_{bit}" -> pixel (col, row).
    // The SVG maps VRAM nibbles to specific X,Y positions on the display.
    //
    // The LCD is organized as:
    //   Columns 0-15 (left half): VRAM nibbles 24-73 (pairs: each pair = 1 column, 8 rows)
    //   Columns 16-31 (right half): VRAM nibbles 2-17 + 82-153
    //   Top row icons: nibbles 0, 18, 20, 22 (partial)
    //   Bottom row icons: nibbles 137, 155, 157, 159 (partial)
    //
    // We extract pixel state: pixel[row][col] = (VRAM[nibble] >> bit) & 1

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

        // Render game area: 16 rows, scaled from 32 to 25 columns
        for (row in 0 until LCD_HEIGHT) {
            val outY = row + GAME_Y_OFFSET
            if (outY >= GLYPH_SIZE) break
            for (col in 0 until GLYPH_SIZE) {
                val srcCol = H_MAP[col]
                if (lcdPixels[row][srcCol]) {
                    pixels[outY * GLYPH_SIZE + col] = PIXEL_ON
                }
            }
        }

        // Render top icons (4 icons in rows 0-2)
        renderIcons(pixels, vramData, topRow = true)

        // Render bottom icons (4 icons in rows 22-24)
        renderIcons(pixels, vramData, topRow = false)

        bitmap.setPixels(pixels, 0, GLYPH_SIZE, 0, 0, GLYPH_SIZE, GLYPH_SIZE)
        return bitmap
    }

    /**
     * Extract the 32x16 pixel grid from VRAM nibbles.
     *
     * Based on the DigimonV3.svg segment layout:
     * - The display has two halves stacked: top 8 rows and bottom 8 rows
     * - Each column is represented by 2 VRAM nibbles (4 bits each = 8 rows)
     *
     * Column mapping (from SVG coordinate analysis):
     *   LCD col 0:  VRAM[24] (bits 0-3 = rows 0-3), VRAM[25] (bits 0-3 = rows 4-7)
     *   LCD col 1:  VRAM[26], VRAM[27]
     *   ...
     *   LCD col 15: VRAM[54], VRAM[55]  (left half, 16 columns)
     *   LCD col 16: VRAM[2], VRAM[3] + VRAM[82], VRAM[83]
     *   ...etc for right half
     *
     * Simplified: Each column pair at offset i uses:
     *   Top 4 rows: VRAM[base + i*2], bits 0-3
     *   Next 4 rows: VRAM[base + i*2 + 1], bits 0-3
     *   Bottom 8 rows: VRAM[base2 + i*2], VRAM[base2 + i*2 + 1]
     */
    private fun extractPixels(vramData: IntArray) {
        // Clear
        for (row in lcdPixels) row.fill(false)

        if (vramData.size < 160) return

        // Left half columns 0-15: VRAM nibbles 24-55 (top 8 rows)
        for (col in 0 until 16) {
            val nibbleTop = 24 + col * 2      // rows 0-3
            val nibbleBot = 24 + col * 2 + 1  // rows 4-7
            if (nibbleTop < vramData.size) {
                for (bit in 0 until 4) {
                    lcdPixels[bit][col] = (vramData[nibbleTop] shr bit) and 1 == 1
                }
            }
            if (nibbleBot < vramData.size) {
                for (bit in 0 until 4) {
                    lcdPixels[4 + bit][col] = (vramData[nibbleBot] shr bit) and 1 == 1
                }
            }
        }

        // Right half columns 16-31: VRAM nibbles 2-17 (top 8 rows)
        for (col in 0 until 8) {
            val nibbleTop = 2 + col * 2
            val nibbleBot = 2 + col * 2 + 1
            if (nibbleTop < vramData.size) {
                for (bit in 0 until 4) {
                    lcdPixels[bit][16 + col] = (vramData[nibbleTop] shr bit) and 1 == 1
                }
            }
            if (nibbleBot < vramData.size) {
                for (bit in 0 until 4) {
                    lcdPixels[4 + bit][16 + col] = (vramData[nibbleBot] shr bit) and 1 == 1
                }
            }
        }

        // Columns 24-31 right side: also from top VRAM section (nibbles continuing)
        for (col in 0 until 8) {
            val nibbleTop = 56 + col * 2
            val nibbleBot = 56 + col * 2 + 1
            if (nibbleTop < vramData.size) {
                for (bit in 0 until 4) {
                    lcdPixels[bit][24 + col] = (vramData[nibbleTop] shr bit) and 1 == 1
                }
            }
            if (nibbleBot < vramData.size) {
                for (bit in 0 until 4) {
                    lcdPixels[4 + bit][24 + col] = (vramData[nibbleBot] shr bit) and 1 == 1
                }
            }
        }

        // Bottom 8 rows: VRAM nibbles 82-145
        for (col in 0 until 32) {
            val nibbleTop = 82 + col * 2      // rows 8-11
            val nibbleBot = 82 + col * 2 + 1  // rows 12-15
            if (nibbleTop < vramData.size) {
                for (bit in 0 until 4) {
                    lcdPixels[8 + bit][col] = (vramData[nibbleTop] shr bit) and 1 == 1
                }
            }
            if (nibbleBot < vramData.size) {
                for (bit in 0 until 4) {
                    lcdPixels[12 + bit][col] = (vramData[nibbleBot] shr bit) and 1 == 1
                }
            }
        }
    }

    private fun renderIcons(pixels: IntArray, vramData: IntArray, topRow: Boolean) {
        // 8 icons total (4 top, 4 bottom)
        // Top icons use VRAM nibbles: 0, 18, 20, 22
        // Bottom icons use VRAM nibbles: 137, 155, 157, 159
        val iconNibbles = if (topRow) intArrayOf(0, 18, 20, 22) else intArrayOf(137, 155, 157, 159)
        val baseY = if (topRow) 1 else 22
        val spacing = GLYPH_SIZE / 4  // 6 pixels per icon

        for (i in 0 until 4) {
            val nibbleIdx = iconNibbles[i]
            if (nibbleIdx < vramData.size && vramData[nibbleIdx] != 0) {
                val cx = spacing / 2 + i * spacing
                // Draw a small 3x2 dot for active icon
                for (dy in -1..0) {
                    for (dx in -1..1) {
                        val px = (cx + dx).coerceIn(0, GLYPH_SIZE - 1)
                        val py = (baseY + dy).coerceIn(0, GLYPH_SIZE - 1)
                        pixels[py * GLYPH_SIZE + px] = ICON_ON
                    }
                }
            }
        }
    }
}
