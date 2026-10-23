package com.digimon.glyph.emulator

import android.graphics.Bitmap
import android.graphics.Color
import java.util.Calendar

/** NanoFont 3x4 digits by Michaelangel007, one LED per font pixel.
 * Source/provenance: docs/glyph/nanofont.md. Ink occupies 3 rows; fourth row is blank.
 */
object GlyphClockHeader {
    private val digits = arrayOf(
        arrayOf("010", "101", "010"), arrayOf("100", "010", "010"),
        arrayOf("110", "010", "011"), arrayOf("111", "011", "111"),
        arrayOf("101", "111", "001"), arrayOf("011", "010", "110"),
        arrayOf("100", "111", "111"), arrayOf("111", "001", "001"),
        arrayOf("011", "111", "110"), arrayOf("111", "111", "001")
    )
    fun points(hour: Int, minute: Int): List<Pair<Int, Int>> {
        require(hour in 0..23 && minute in 0..59)
        return buildList {
            listOf(hour / 10, hour % 10, minute / 10, minute % 10).forEachIndexed { index, digit ->
                digits[digit].forEachIndexed { y, row ->
                    row.forEachIndexed { x, value -> if (value == '1') add((5 + index * 4 + x) to (20 + y)) }
                }
            }
        }
    }
    fun revision(enabled: Boolean, hour: Int, minute: Int): Long =
        if (enabled) 1L + hour * 60 + minute else 0L

    fun currentRevision(): Long {
        if (!DisplayRenderSettings.isDPowerClockEnabled()) return 0L
        val now = Calendar.getInstance()
        return revision(true, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
    }
    fun draw(bitmap: Bitmap) {
        val now = Calendar.getInstance()
        // LCD occupies rows 4..19. Rows 20..22 fit the circular lower margin.
        for ((x, y) in points(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))) {
            bitmap.setPixel(x, y, Color.WHITE)
        }
    }
}
