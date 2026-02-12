package com.digimon.glyph.emulator

import android.graphics.Bitmap
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
    private val onFrame: (Bitmap, IntArray) -> Unit
) {
    enum class TimingMode { EXACT, POWER_SAVE }

    private data class LoopProfile(
        val frameTimeNs: Double,
        val cycleTimeNs: Double,
        val maxBacklogNs: Double,
        val maxSleepMs: Long,
        val maxBatchNs: Double,
        val maxClockCallsPerLoop: Int,
        val fallbackSleepMs: Long
    )

    companion object {
        private const val EXACT_TARGET_FPS = 15
        private const val EXACT_FRAME_TIME_NS = 1_000_000_000.0 / EXACT_TARGET_FPS
        private const val POWER_SAVE_TARGET_FPS = 4
        private const val POWER_SAVE_FRAME_TIME_NS = 1_000_000_000.0 / POWER_SAVE_TARGET_FPS
        private const val CPU_CLOCK = 1_060_000.0
        private const val EXACT_CYCLE_TIME_NS = 1_000_000_000.0 / CPU_CLOCK
        private const val POWER_SAVE_SPEED_FACTOR = 0.25
        private const val POWER_SAVE_CYCLE_TIME_NS = EXACT_CYCLE_TIME_NS / POWER_SAVE_SPEED_FACTOR
        private const val EXACT_MAX_BACKLOG_NS = 250_000_000.0
        private const val POWER_SAVE_MAX_BACKLOG_NS = 40_000_000.0
        private const val EXACT_MAX_SLEEP_MS = 1L
        private const val POWER_SAVE_MAX_SLEEP_MS = 40L
        private const val EXACT_MAX_BATCH_NS = 2_000_000.0
        private const val POWER_SAVE_MAX_BATCH_NS = 10_000_000.0
        private const val EXACT_MAX_CLOCK_CALLS_PER_LOOP = Int.MAX_VALUE
        private const val POWER_SAVE_MAX_CLOCK_CALLS_PER_LOOP = 64
        private const val EXACT_FALLBACK_SLEEP_MS = 0L
        private const val POWER_SAVE_FALLBACK_SLEEP_MS = 8L
    }

    @Volatile private var running = false
    @Volatile private var timingMode: TimingMode = TimingMode.EXACT
    private var thread: Thread? = null
    private val commandQueue = ConcurrentLinkedQueue<(E0C6200) -> Unit>()

    fun setTimingMode(mode: TimingMode) {
        timingMode = mode
    }

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
        commandQueue.clear()
        thread = null
    }

    fun enqueue(command: (E0C6200) -> Unit): Boolean {
        commandQueue.offer(command)
        return true
    }

    fun <T> executeSync(timeoutMs: Long = 1200L, command: (E0C6200) -> T): T? {
        val loopThread = thread ?: return null
        if (!running) return null
        if (Thread.currentThread() === loopThread) {
            return command(emulator)
        }

        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<T?>()
        val errorRef = AtomicReference<Throwable?>()
        commandQueue.offer { core ->
            try {
                resultRef.set(command(core))
            } catch (t: Throwable) {
                errorRef.set(t)
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
        val error = errorRef.get()
        if (error != null) {
            throw RuntimeException("Emulator command failed", error)
        }
        return resultRef.get()
    }

    private fun runLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
        } catch (_: Exception) {
            // Keep running with default priority if OEM policy rejects priority change.
        }
        var lastTickNs = System.nanoTime().toDouble()
        var nextFrameNs = lastTickNs + EXACT_FRAME_TIME_NS
        var lastDisplayGeneration = Long.MIN_VALUE

        while (running) {
            val profile = currentProfile()
            drainCommands()
            var nowNs = System.nanoTime().toDouble()
            if (nowNs - lastTickNs > profile.maxBacklogNs) {
                lastTickNs = nowNs - profile.maxBacklogNs
            }

            // Advance emulated time while wall clock is ahead.
            // Execute in cycle batches to reduce hot-path time-query overhead.
            var clockCalls = 0
            while (running && nowNs > lastTickNs && nowNs < nextFrameNs) {
                val remainingNs = minOf(nowNs, nextFrameNs) - lastTickNs
                if (remainingNs <= 0.0) break
                val budgetNs = minOf(remainingNs, profile.maxBatchNs)
                val targetCycles = maxOf(1.0, budgetNs / profile.cycleTimeNs)
                val executedCycles = emulator.runForCycles(targetCycles)
                if (executedCycles <= 0.0) break
                lastTickNs += executedCycles * profile.cycleTimeNs
                clockCalls++
                if (clockCalls >= profile.maxClockCallsPerLoop) {
                    break
                }
                nowNs = System.nanoTime().toDouble()
            }

            if (nowNs >= nextFrameNs) {
                val snapshot = emulator.getDisplaySnapshot(lastDisplayGeneration)
                if (snapshot != null) {
                    lastDisplayGeneration = snapshot.generation
                    val bitmap = displayBridge.renderFrame(snapshot.vramData)
                    onFrame(bitmap, snapshot.vramData)
                }
                do {
                    nextFrameNs += profile.frameTimeNs
                } while (nextFrameNs <= nowNs)
            }

            drainCommands()

            // If we're ahead of schedule, sleep briefly.
            // Never hard-resync lastTick to now here: that drops emulated time and causes drift/slowdown.
            val postNs = System.nanoTime().toDouble()
            if (lastTickNs > postNs + 1_000.0) {
                val sleepMs = ((lastTickNs - postNs) / 1_000_000.0).toLong().coerceAtMost(profile.maxSleepMs)
                if (sleepMs >= 1L) {
                    SystemClock.sleep(sleepMs)
                } else {
                    Thread.yield()
                }
            } else if (profile.fallbackSleepMs > 0L) {
                SystemClock.sleep(profile.fallbackSleepMs)
            } else {
                Thread.yield()
            }
        }
    }

    private fun currentProfile(): LoopProfile {
        return if (timingMode == TimingMode.EXACT) {
            LoopProfile(
                frameTimeNs = EXACT_FRAME_TIME_NS,
                cycleTimeNs = EXACT_CYCLE_TIME_NS,
                maxBacklogNs = EXACT_MAX_BACKLOG_NS,
                maxSleepMs = EXACT_MAX_SLEEP_MS,
                maxBatchNs = EXACT_MAX_BATCH_NS,
                maxClockCallsPerLoop = EXACT_MAX_CLOCK_CALLS_PER_LOOP,
                fallbackSleepMs = EXACT_FALLBACK_SLEEP_MS
            )
        } else {
            LoopProfile(
                frameTimeNs = POWER_SAVE_FRAME_TIME_NS,
                cycleTimeNs = POWER_SAVE_CYCLE_TIME_NS,
                maxBacklogNs = POWER_SAVE_MAX_BACKLOG_NS,
                maxSleepMs = POWER_SAVE_MAX_SLEEP_MS,
                maxBatchNs = POWER_SAVE_MAX_BATCH_NS,
                maxClockCallsPerLoop = POWER_SAVE_MAX_CLOCK_CALLS_PER_LOOP,
                fallbackSleepMs = POWER_SAVE_FALLBACK_SLEEP_MS
            )
        }
    }

    private fun drainCommands() {
        while (true) {
            val cmd = commandQueue.poll() ?: break
            cmd(emulator)
        }
    }
}
