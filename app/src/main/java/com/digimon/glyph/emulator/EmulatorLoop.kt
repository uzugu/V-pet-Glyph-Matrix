package com.digimon.glyph.emulator

import android.graphics.Bitmap
import android.os.SystemClock

/**
 * Runs the E0C6200 emulator at the correct clock rate on a dedicated thread.
 * Renders frames at ~15 FPS and delivers them via callback.
 *
 * Digimon CPU clock: 1,060,000 Hz
 * Each clock() call returns the number of CPU cycles consumed.
 * We target ~15 FPS display updates — each frame executes enough cycles
 * to cover 1/15th of a second of wall-clock time.
 */
class EmulatorLoop(
    private val emulator: E0C6200,
    private val displayBridge: DisplayBridge,
    private val onFrame: (Bitmap) -> Unit
) {
    companion object {
        private const val TARGET_FPS = 15
        private const val FRAME_TIME_NS = 1_000_000_000L / TARGET_FPS
        private const val CPU_CLOCK = 1_060_000.0
        private const val CYCLE_TIME_NS = 1_000_000_000.0 / CPU_CLOCK
    }

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::runLoop, "E0C6200-Emulator").apply {
            priority = Thread.MAX_PRIORITY
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun runLoop() {
        var lastFrameNs = System.nanoTime()
        var accumulatedNs = 0.0

        while (running && !Thread.currentThread().isInterrupted) {
            val now = System.nanoTime()
            val deltaNs = now - lastFrameNs
            lastFrameNs = now

            // Accumulate time, cap at 2 frames worth to avoid spiral
            accumulatedNs += deltaNs.coerceAtMost(FRAME_TIME_NS * 2)

            // Execute CPU cycles for the accumulated time
            while (accumulatedNs > 0) {
                val cycles = emulator.clock()
                accumulatedNs -= cycles * CYCLE_TIME_NS
            }

            // Render and push frame
            val vram = emulator.getVRAM()
            val bitmap = displayBridge.renderFrame(vram)
            onFrame(bitmap)

            // Sleep for remainder of frame budget
            val elapsed = System.nanoTime() - now
            val sleepMs = (FRAME_TIME_NS - elapsed) / 1_000_000
            if (sleepMs > 1) {
                SystemClock.sleep(sleepMs)
            }
        }
    }
}
