package com.digimon.glyph.emulator

import android.graphics.Bitmap
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

/**
 * Runs the E0C6200 emulator at the correct clock rate on a dedicated thread.
 * Renders frames at ~15 FPS and delivers them via callback.
 *
 * Timing model (matches BrickEmuPy brick.py):
 * - Execute one CPU instruction at a time via emulator.clock()
 * - clock() returns cycles already scaled by OSC1 clock divider
 * - Multiply by cycleTimeNs (1e9 / CPU_CLOCK) to get wall-clock nanoseconds
 * - When behind: tight-loop to catch up (no yield/sleep in EXACT mode)
 * - When ahead: sleep using LockSupport.parkNanos() for sub-ms precision
 */
class EmulatorLoop(
    private val emulator: E0C6200,
    private val displayBridge: DisplayBridge,
    private val onFrame: (Bitmap, IntArray) -> Unit
) {
    var onVirtualTick: ((Double) -> Unit)? = null
    enum class TimingMode { EXACT, POWER_SAVE }

    private data class LoopProfile(
        val frameTimeNs: Double,
        val cycleTimeNs: Double,
        val resyncMarginNs: Double,
        val maxSleepMs: Long,
        val fallbackSleepMs: Long,
        val timeCheckBatch: Int,
        val isExact: Boolean
    )

    companion object {
        private const val TAG = "E0C6200-Emulator"

        private const val EXACT_TARGET_FPS = 15
        private const val EXACT_FRAME_TIME_NS = 1_000_000_000.0 / EXACT_TARGET_FPS
        private const val POWER_SAVE_TARGET_FPS = 4
        private const val POWER_SAVE_FRAME_TIME_NS = 1_000_000_000.0 / POWER_SAVE_TARGET_FPS

        private const val CPU_CLOCK = 1_060_000.0
        private const val EXACT_CYCLE_TIME_NS = 1_000_000_000.0 / CPU_CLOCK
        private const val POWER_SAVE_SPEED_FACTOR = 0.25
        private const val POWER_SAVE_CYCLE_TIME_NS = EXACT_CYCLE_TIME_NS / POWER_SAVE_SPEED_FACTOR

        // Catch-up hysteresis: only fast-forward OSC1 if behind by more than this.
        // 16ms avoids micro-corrections from scheduling jitter while catching
        // any meaningful throttle event (Android doze bursts are typically 20-500ms+).
        private const val CATCHUP_HYSTERESIS_NS = 16_000_000.0       // 16ms

        // After catch-up, leave this much margin to allow smooth normal execution
        private const val EXACT_RESYNC_MARGIN_NS = 5_000_000.0       // 5ms
        private const val POWER_SAVE_RESYNC_MARGIN_NS = 5_000_000.0  // 5ms

        private const val EXACT_MAX_SLEEP_MS = 2L
        private const val POWER_SAVE_MAX_SLEEP_MS = 40L

        private const val POWER_SAVE_FALLBACK_SLEEP_MS = 8L

        // Check wall clock every N instructions (balances precision vs nanoTime() overhead)
        private const val EXACT_TIME_CHECK_BATCH = 64
        private const val POWER_SAVE_TIME_CHECK_BATCH = 16

        // Timing instrumentation interval
        private const val DRIFT_LOG_INTERVAL_NS = 10_000_000_000.0  // 10 seconds
        private const val STEP_GATE_IDLE_SLEEP_MS = 6L
        private const val STEP_GATE_MAX_BUDGET_NS = 3_000_000_000L
    }

    @Volatile private var running = false
    @Volatile private var timingMode: TimingMode = TimingMode.EXACT
    @Volatile private var clockCorrectionOverride: Double? = null
    @Volatile private var stepGateEnabled = false
    private var thread: Thread? = null
    private val commandQueue = ConcurrentLinkedQueue<(E0C6200) -> Unit>()
    private val stepGateLock = Any()
    private var stepGateBudgetNs: Long = 0L
    private var stepGateResetAnchorRequested = false

    // Cached profiles to avoid allocation in hot path
    private val exactProfile = LoopProfile(
        frameTimeNs = EXACT_FRAME_TIME_NS,
        cycleTimeNs = EXACT_CYCLE_TIME_NS,
        resyncMarginNs = EXACT_RESYNC_MARGIN_NS,
        maxSleepMs = EXACT_MAX_SLEEP_MS,
        fallbackSleepMs = 0L,
        timeCheckBatch = EXACT_TIME_CHECK_BATCH,
        isExact = true
    )

    private val powerSaveProfile = LoopProfile(
        frameTimeNs = POWER_SAVE_FRAME_TIME_NS,
        cycleTimeNs = POWER_SAVE_CYCLE_TIME_NS,
        resyncMarginNs = POWER_SAVE_RESYNC_MARGIN_NS,
        maxSleepMs = POWER_SAVE_MAX_SLEEP_MS,
        fallbackSleepMs = POWER_SAVE_FALLBACK_SLEEP_MS,
        timeCheckBatch = POWER_SAVE_TIME_CHECK_BATCH,
        isExact = false
    )

    fun setTimingMode(mode: TimingMode) {
        timingMode = mode
    }

    fun setClockCorrectionOverride(factor: Double?) {
        clockCorrectionOverride = factor?.coerceIn(0.5, 3.0)
    }

    fun setStepGateEnabled(enabled: Boolean) {
        synchronized(stepGateLock) {
            if (stepGateEnabled == enabled) return
            stepGateEnabled = enabled
            stepGateBudgetNs = 0L
            stepGateResetAnchorRequested = true
        }
        Log.d(TAG, "Step gate ${if (enabled) "enabled" else "disabled"}")
    }

    fun pulseStepBudget(stepMs: Int) {
        val addNs = stepMs.coerceIn(1, 1000).toLong() * 1_000_000L
        synchronized(stepGateLock) {
            if (!stepGateEnabled) return
            stepGateBudgetNs = (stepGateBudgetNs + addNs).coerceAtMost(STEP_GATE_MAX_BUDGET_NS)
        }
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

        // Timing instrumentation
        val startWallNs = lastTickNs
        var totalEmulatedNs = 0.0
        var lastDriftLogNs = lastTickNs
        var resyncCount = 0L
        var frameRenderTotalNs = 0.0
        var frameRenderCount = 0L

        while (running) {
            val profile = if (timingMode == TimingMode.EXACT) exactProfile else powerSaveProfile
            val stepGateActive = stepGateEnabled
            if (consumeStepGateResetAnchor()) {
                val anchor = System.nanoTime().toDouble()
                lastTickNs = anchor
                nextFrameNs = anchor + profile.frameTimeNs
            }

            // Clock speed correction: when enabled, divide cycleTimeNs by correction factor
            // to make emulation run faster, compensating for ROM-inherent slow timekeeping.
            val correctionFactor = clockCorrectionOverride ?: if (EmulatorTimingSettings.isClockCorrectionEnabled())
                EmulatorTimingSettings.getClockCorrectionFactor().toDouble() else 1.0
            val effectiveCycleTimeNs = profile.cycleTimeNs / correctionFactor

            drainCommands()

            var nowNs = System.nanoTime().toDouble()

            // Continuous catch-up: if emulated time is behind wall clock by more than
            // the hysteresis threshold, fast-forward OSC1 to close the gap.
            // This handles both large idle gaps AND accumulated small throttle delays.
            // Always uses REAL-TIME rate (not POWER_SAVE's 0.25x) so timers advance
            // at wall-clock speed regardless of emulation mode.
            val behindNs = nowNs - lastTickNs
            if (!stepGateActive && behindNs > CATCHUP_HYSTERESIS_NS) {
                val catchupNs = behindNs - profile.resyncMarginNs
                if (catchupNs > 0) {
                    // Use REAL-TIME rate for fast-forward: OSC1 ticks at 32768 Hz in real time.
                    // The correction factor speeds up real time to compensate for ROM slowness.
                    // This is independent of POWER_SAVE speed factor, which only affects
                    // live emulation pacing — not how fast real time passes during idle.
                    val osc1ClockDiv = CPU_CLOCK / E0C6200.OSC1_CLOCK.toDouble()
                    val realCycleTimeNs = EXACT_CYCLE_TIME_NS / correctionFactor
                    val wallNsPerRealOsc1Tick = osc1ClockDiv * realCycleTimeNs
                    val osc1TicksToFastForward = (catchupNs / wallNsPerRealOsc1Tick).toLong()

                    if (osc1TicksToFastForward > 0) {
                        val actualTicks = emulator.fastForwardOsc1(osc1TicksToFastForward)
                        lastTickNs = nowNs - profile.resyncMarginNs
                        
                        // Feed VirtualDCom smoothly during fast-forwards so it doesn't skip
                        // crucial millisecond bit states.
                        var catchupRemainingNs = catchupNs
                        while (catchupRemainingNs > 0.0) {
                            val stepNs = catchupRemainingNs.coerceAtMost(1_000_000.0) // 1ms steps
                            totalEmulatedNs += stepNs
                            onVirtualTick?.invoke(totalEmulatedNs)
                            catchupRemainingNs -= stepNs
                        }
                        
                        resyncCount++
                        if (catchupNs > 100_000_000.0) { // Only log if >100ms gap
                            Log.w(TAG, "Catch-up #$resyncCount: ff $actualTicks OSC1 ticks for ${(catchupNs / 1e6).toLong()}ms gap")
                        }
                    }
                }
            }

            // PHASE 1: Execute CPU instructions until caught up or frame deadline.
            // One instruction at a time, matching BrickEmuPy's approach.
            val frameDeadlineNs = nextFrameNs
            var instructionCount = 0
            val batchSize = profile.timeCheckBatch
            val stepBudgetSnapshotNs = if (stepGateActive) snapshotStepGateBudgetNs() else Long.MAX_VALUE
            var stepBudgetRemainingNs = stepBudgetSnapshotNs.toDouble()
            var stepBudgetUsedNs = 0L

            while (
                running &&
                (if (stepGateActive) stepBudgetRemainingNs > 0.0 else lastTickNs < nowNs) &&
                lastTickNs < frameDeadlineNs
            ) {
                val cycles = emulator.clock()
                if (cycles <= 0.0) break
                val advanceNs = cycles * effectiveCycleTimeNs
                lastTickNs += advanceNs
                totalEmulatedNs += advanceNs
                if (stepGateActive) {
                    val usedNs = advanceNs.toLong().coerceAtLeast(1L)
                    stepBudgetRemainingNs -= usedNs.toDouble()
                    stepBudgetUsedNs = (stepBudgetUsedNs + usedNs).coerceAtMost(STEP_GATE_MAX_BUDGET_NS)
                }

                instructionCount++
                onVirtualTick?.invoke(totalEmulatedNs)
                if (instructionCount % batchSize == 0) {
                    drainCommands()
                    nowNs = System.nanoTime().toDouble()
                    if (!stepGateActive && nowNs >= frameDeadlineNs) break
                }
            }
            if (stepGateActive && stepBudgetUsedNs > 0L) {
                consumeStepGateBudget(stepBudgetUsedNs)
            }

            // PHASE 2: Render frame if due (check wall clock, not just emulated time).
            nowNs = System.nanoTime().toDouble()
            val forceRenderStepGate = stepGateActive && stepBudgetUsedNs > 0L
            if (nowNs >= nextFrameNs || forceRenderStepGate) {
                val preRenderNs = System.nanoTime().toDouble()

                val snapshot = emulator.getDisplaySnapshot(lastDisplayGeneration)
                if (snapshot != null) {
                    lastDisplayGeneration = snapshot.generation
                    val bitmap = displayBridge.renderFrame(snapshot.vramData)
                    onFrame(bitmap, snapshot.vramData)
                }

                val postRenderNs = System.nanoTime().toDouble()
                val renderCostNs = postRenderNs - preRenderNs
                frameRenderTotalNs += renderCostNs
                frameRenderCount++

                if (forceRenderStepGate && postRenderNs < nextFrameNs) {
                    nextFrameNs = postRenderNs + profile.frameTimeNs
                } else {
                    // Advance frame deadline past current wall time
                    do {
                        nextFrameNs += profile.frameTimeNs
                    } while (nextFrameNs <= postRenderNs)
                }

                // After rendering, the emulator is likely behind wall clock.
                // That's expected — Phase 1 on the next iteration will catch up
                // because we don't yield/sleep when behind (in EXACT mode).
            }

            drainCommands()

            // Timing instrumentation: log drift every 10 seconds
            nowNs = System.nanoTime().toDouble()
            if (nowNs - lastDriftLogNs >= DRIFT_LOG_INTERVAL_NS) {
                val wallElapsedSec = (nowNs - startWallNs) / 1_000_000_000.0
                val emuElapsedSec = totalEmulatedNs / 1_000_000_000.0
                val driftSec = wallElapsedSec - emuElapsedSec
                val driftPct = if (wallElapsedSec > 0) (driftSec / wallElapsedSec * 100.0) else 0.0
                val avgRenderMs = if (frameRenderCount > 0) {
                    frameRenderTotalNs / frameRenderCount / 1_000_000.0
                } else 0.0
                Log.d(TAG, "Timing: wall=%.1fs emu=%.1fs drift=%.2fs (%.2f%%) resyncs=%d avgRender=%.1fms frames=%d correction=%.2fx".format(
                    wallElapsedSec, emuElapsedSec, driftSec, driftPct, resyncCount, avgRenderMs, frameRenderCount, correctionFactor
                ))
                // CPU-internal tick rate diagnostics
                val diag = emulator.getDiagnostics()
                val osc1Rate = (diag["osc1"] as Long).toDouble() / wallElapsedSec
                val timerRate = (diag["timer"] as Long).toDouble() / wallElapsedSec
                val swRate = (diag["sw"] as Long).toDouble() / wallElapsedSec
                Log.d(TAG, "CPU: osc1=%.1f/s(32768) timer=%.1f/s(256) sw=%.1f/s(100) halt=%d TM=%d CTRL_OSC=%X CTRL_SW=%X SWL=%d SWH=%d".format(
                    osc1Rate, timerRate, swRate,
                    diag["halt"], diag["TM"], diag["CTRL_OSC"], diag["CTRL_SW"],
                    diag["SWL"], diag["SWH"]
                ))
                lastDriftLogNs = nowNs
            }

            if (stepGateActive) {
                if (snapshotStepGateBudgetNs() <= 0L) {
                    SystemClock.sleep(STEP_GATE_IDLE_SLEEP_MS)
                } else {
                    Thread.yield()
                }
                continue
            }

            // PHASE 3: Sleep management.
            val postNs = System.nanoTime().toDouble()
            val aheadNs = lastTickNs - postNs

            if (aheadNs > 1_000.0) {
                // Emulator is AHEAD of wall clock — sleep to wait.
                if (profile.isExact) {
                    // EXACT mode: use LockSupport.parkNanos for sub-ms precision.
                    // Sleep ~70% of the gap, spin-wait the remainder for accuracy.
                    val sleepNs = (aheadNs * 0.7).toLong()
                    if (sleepNs > 100_000L) {
                        LockSupport.parkNanos(sleepNs)
                    }
                    // If <100μs ahead: just loop back (spin-wait)
                } else {
                    // POWER_SAVE mode: coarser sleep
                    val sleepMs = (aheadNs / 1_000_000.0).toLong().coerceAtMost(profile.maxSleepMs)
                    if (sleepMs >= 1L) {
                        SystemClock.sleep(sleepMs)
                    } else {
                        Thread.yield()
                    }
                }
            } else if (!profile.isExact && profile.fallbackSleepMs > 0L) {
                // POWER_SAVE mode when behind: yield to save battery
                SystemClock.sleep(profile.fallbackSleepMs)
            }
            // EXACT mode when behind (aheadNs <= 0): NO yield, NO sleep.
            // Loop immediately back to Phase 1 to catch up.
            // This is the KEY FIX: the old code called Thread.yield() here,
            // causing 1-15ms delays that prevented catch-up after frame rendering.
        }
    }

    private fun snapshotStepGateBudgetNs(): Long {
        synchronized(stepGateLock) {
            return stepGateBudgetNs
        }
    }

    private fun consumeStepGateBudget(usedNs: Long) {
        if (usedNs <= 0L) return
        synchronized(stepGateLock) {
            if (!stepGateEnabled) return
            stepGateBudgetNs = (stepGateBudgetNs - usedNs).coerceAtLeast(0L)
        }
    }

    private fun consumeStepGateResetAnchor(): Boolean {
        synchronized(stepGateLock) {
            val reset = stepGateResetAnchorRequested
            stepGateResetAnchorRequested = false
            return reset
        }
    }

    private fun drainCommands() {
        while (true) {
            val cmd = commandQueue.poll() ?: break
            cmd(emulator)
        }
    }
}
