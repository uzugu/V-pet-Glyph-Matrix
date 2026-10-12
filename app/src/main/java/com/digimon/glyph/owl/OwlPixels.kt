package com.digimon.glyph.owl

/** Theme-neutral LCD pixels: p = LCD, i = active indicator, d = inactive indicator. */
object OwlPixels {
    fun rows(pixels: IntArray, width: Int, height: Int): ArrayList<String> {
        require(width in 1..64 && height in 1..64 && pixels.size == width * height)
        return ArrayList((0 until height).map { y ->
            buildString(width) {
                for (x in 0 until width) {
                    val color = pixels[y * width + x]
                    val red = (color ushr 16) and 255
                    val green = (color ushr 8) and 255
                    append(when {
                        red >= 180 && green >= 150 -> 'i'
                        green >= 180 -> 'p'
                        red >= 80 && green >= 50 -> 'd'
                        else -> '.'
                    })
                }
            }
        })
    }
}
