package com.digimon.glyph.emulator

/** 32x16 → 22x11: equal scale on both axes, centred nearest-neighbour samples. */
internal object LcdFit {
    fun sourceX(x: Int): Int = ((2 * x + 1) * 32 / 44).coerceIn(0, 31)
    fun sourceY(y: Int): Int = ((2 * y + 1) * 16 / 22).coerceIn(0, 15)
}
