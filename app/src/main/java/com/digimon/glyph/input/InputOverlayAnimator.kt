package com.digimon.glyph.input

import android.graphics.Bitmap
import android.graphics.Color
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Draws a brief circle animation on the 25x25 Glyph bitmap when the input
 * system transitions between IDLE and ACTIVE modes.
 *
 * - ACTIVE (expand): circle grows from center outward
 * - IDLE (contract): circle shrinks from edge inward, then disappears
 *
 * Thread safety: [onStateChanged] is called from the sensor thread,
 * [applyOverlay] from the emulator thread. Shared state uses atomics.
 */
class InputOverlayAnimator {

    private enum class AnimDirection { EXPAND, CONTRACT }

    companion object {
        private const val GRID = 25
        private const val CENTER = 12               // center of 25x25 grid
        private const val MAX_RADIUS = 12
        private const val DURATION_MS = 1000L
        private val CIRCLE_COLOR = Color.rgb(0, 255, 80)  // match LCD green
    }

    // Shared between sensor thread (writer) and emulator thread (reader)
    private val animStartMs = AtomicLong(0L)
    private val animDirection = AtomicReference<AnimDirection?>(null)

    /**
     * Called when the input rate mode changes. Safe to call from any thread.
     */
    fun onStateChanged(isActive: Boolean) {
        animDirection.set(if (isActive) AnimDirection.EXPAND else AnimDirection.CONTRACT)
        animStartMs.set(System.currentTimeMillis())
    }

    /**
     * Overlay the current animation frame onto the bitmap, if animating.
     * Called from the emulator thread on every frame (~15 FPS).
     * No-op when not animating.
     */
    fun applyOverlay(bitmap: Bitmap) {
        val dir = animDirection.get() ?: return
        val startMs = animStartMs.get()
        if (startMs == 0L) return

        val elapsed = System.currentTimeMillis() - startMs
        if (elapsed >= DURATION_MS) {
            // Animation complete — stop drawing
            animDirection.set(null)
            animStartMs.set(0L)
            return
        }

        val progress = elapsed.toFloat() / DURATION_MS  // 0..1
        val radius = when (dir) {
            AnimDirection.EXPAND -> (progress * MAX_RADIUS).toInt()
            AnimDirection.CONTRACT -> ((1f - progress) * MAX_RADIUS).toInt()
        }

        if (radius <= 0) return

        // Read pixels, draw circle, write back
        val pixels = IntArray(GRID * GRID)
        bitmap.getPixels(pixels, 0, GRID, 0, 0, GRID, GRID)
        drawCircle(pixels, CENTER, CENTER, radius)
        bitmap.setPixels(pixels, 0, GRID, 0, 0, GRID, GRID)
    }

    /**
     * Midpoint circle algorithm — draws a 1-pixel-wide ring.
     */
    private fun drawCircle(pixels: IntArray, cx: Int, cy: Int, r: Int) {
        var x = r
        var y = 0
        var d = 3 - 2 * r

        while (x >= y) {
            plot8(pixels, cx, cy, x, y)
            if (d < 0) {
                d += 4 * y + 6
            } else {
                d += 4 * (y - x) + 10
                x--
            }
            y++
        }
    }

    private fun plot8(pixels: IntArray, cx: Int, cy: Int, x: Int, y: Int) {
        setPixel(pixels, cx + x, cy + y)
        setPixel(pixels, cx - x, cy + y)
        setPixel(pixels, cx + x, cy - y)
        setPixel(pixels, cx - x, cy - y)
        setPixel(pixels, cx + y, cy + x)
        setPixel(pixels, cx - y, cy + x)
        setPixel(pixels, cx + y, cy - x)
        setPixel(pixels, cx - y, cy - x)
    }

    private fun setPixel(pixels: IntArray, x: Int, y: Int) {
        if (x in 0 until GRID && y in 0 until GRID) {
            pixels[y * GRID + x] = CIRCLE_COLOR
        }
    }
}
