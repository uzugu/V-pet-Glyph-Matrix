package com.digimon.glyph.emulator

import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

/**
 * Runs the E0C6200 emulator at the correct clock rate on a dedicated thread.
 * Renders frames at ~15 FPS and delivers them via callback.
 *
 * Timing model (matches BrickEmuPy brick.py):
 * - Execute one CPU instruction at a time via emulator.clock()
 * - clock() returns cycles already scaled by OSC1 clock divider
 * - Use the core's oscillator frequency to budget real elapsed time
 * - When behind: execute every owed instruction, preserving firmware interrupts
 * - When ahead: sleep using LockSupport.parkNanos() for sub-ms precision
 */
class EmulatorLoop(
    private val emulator: EmulatorCore,
    private val onFrame: (IntArray) -> Unit,
    private val elapsedNs: () -> Long = { SystemClock.elapsedRealtimeNanos() },
    private val displayRevision: () -> Long = { 0L },
) {
    var onFailure: ((Throwable) -> Unit)? = null
    var onVirtualTick: ((Double) -> Unit)? = null
    enum class TimingMode { EXACT, FULL_ACCURATE, POWER_SAVE }

    private data class LoopProfile(val frameTimeNs: Double, val timeCheckBatch: Int)

    companion object {
        private const val TAG = "E0C6200-Emulator"

        private const val EXACT_TARGET_FPS = 15
        private const val EXACT_FRAME_TIME_NS = 1_000_000_000.0 / EXACT_TARGET_FPS
        private const val FULL_ACCURATE_TARGET_FPS = 30
        private const val FULL_ACCURATE_FRAME_TIME_NS = 1_000_000_000.0 / FULL_ACCURATE_TARGET_FPS
        private const val POWER_SAVE_TARGET_FPS = 4
        private const val POWER_SAVE_FRAME_TIME_NS = 1_000_000_000.0 / POWER_SAVE_TARGET_FPS

        // Timing instrumentation interval
        private const val DRIFT_LOG_INTERVAL_NS = 10_000_000_000.0  // 10 seconds
        private const val STEP_GATE_IDLE_SLEEP_MS = 6L
        private const val STEP_GATE_MAX_BUDGET_NS = 3_000_000_000L
    }

    @Volatile private var running = false
    @Volatile private var timingMode: TimingMode = TimingMode.EXACT
    @Volatile private var stepGateEnabled = false
    private var thread: Thread? = null
    private val commandQueue = ConcurrentLinkedQueue<(EmulatorCore) -> Unit>()
    private val stepGateLock = Any()
    private var stepGateBudgetNs: Long = 0L
    private var stepGateResetAnchorRequested = false

    // Render modes change display cadence, never CPU/OSC1 speed.
    private val exactProfile = LoopProfile(EXACT_FRAME_TIME_NS, 64)
    private val fullAccurateProfile = LoopProfile(FULL_ACCURATE_FRAME_TIME_NS, 16)
    private val powerSaveProfile = LoopProfile(POWER_SAVE_FRAME_TIME_NS, 64)

    fun setTimingMode(mode: TimingMode) {
        timingMode = mode
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
        val worker = thread
        running = false
        worker?.interrupt()
        if (worker != null && worker !== Thread.currentThread()) {
            try { worker.join(1500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        commandQueue.clear()
        thread = null
    }

    fun enqueue(command: (EmulatorCore) -> Unit): Boolean {
        if (!running) return false
        commandQueue.offer(command)
        return true
    }

    fun <T> executeSync(timeoutMs: Long = 1200L, command: (EmulatorCore) -> T): T? {
        val loopThread = thread ?: return null
        if (!running) return null
        if (Thread.currentThread() === loopThread) {
            return command(emulator)
        }

        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<T?>()
        val errorRef = AtomicReference<Throwable?>()
        // 0=queued, 1=executing, 2=cancelled. Once execution starts, wait for its
        // actual outcome rather than reporting a timeout followed by late mutation.
        val phase = AtomicInteger(0)
        val queued: (EmulatorCore) -> Unit = { core ->
            if (phase.compareAndSet(0, 1)) try {
                resultRef.set(command(core))
            } catch (t: Throwable) {
                errorRef.set(t)
            } finally {
                latch.countDown()
            }
        }
        commandQueue.offer(queued)
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            if (phase.compareAndSet(0, 2)) {
                commandQueue.remove(queued)
                return null
            }
            latch.await()
        }
        val error = errorRef.get()
        if (error != null) {
            throw RuntimeException("Emulator command failed", error)
        }
        return resultRef.get()
    }


    private fun runLoop() {
        try {
            runLoopBody()
        } catch (failure: Throwable) {
            onFailure?.invoke(failure)
        } finally {
            running = false
            commandQueue.clear()
        }
    }

    private fun runLoopBody() {
        try { Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY) } catch (_: Exception) {}
        // elapsedRealtime includes Android suspend; nanoTime does not.
        val startNs = elapsedNs()
        val pacer = RealtimePacer(emulator.clockFrequency, startNs)
        val cycleNs = 1_000_000_000.0 / emulator.clockFrequency
        var nextFrameNs = startNs
        var lastDriftLogNs = startNs
        var lastDisplayGeneration = Long.MIN_VALUE
        var lastDisplayRevision = Long.MIN_VALUE
        var lastMode = timingMode

        while (running) {
            val mode = timingMode
            val profile = when (mode) {
                TimingMode.EXACT -> exactProfile
                TimingMode.FULL_ACCURATE -> fullAccurateProfile
                TimingMode.POWER_SAVE -> powerSaveProfile
            }
            var nowNs = elapsedNs()
            val stepGateActive = stepGateEnabled
            if (consumeStepGateResetAnchor()) pacer.rebase(nowNs)
            if (mode != lastMode) {
                nextFrameNs = nowNs
                lastMode = mode
            }
            drainCommands()
            // Keep command/input/render latency bounded while repaying all CPU debt.
            val batchDeadlineNs = nowNs + 2_000_000L
            var instructions = 0
            var stepRemainingNs = if (stepGateActive) snapshotStepGateBudgetNs().toDouble() else 0.0
            var stepUsedNs = 0.0
            while (running && if (stepGateActive) stepRemainingNs > 0 else pacer.debtCycles(nowNs) > 0) {
                val cycles = emulator.clock()
                if (cycles <= 0) break
                pacer.advance(cycles)
                val advanceNs = cycles * cycleNs
                if (stepGateActive) {
                    stepRemainingNs -= advanceNs
                    stepUsedNs += advanceNs
                }
                onVirtualTick?.invoke(pacer.virtualNs)
                if (++instructions % profile.timeCheckBatch == 0) {
                    drainCommands()
                    nowNs = elapsedNs()
                    if (nowNs >= batchDeadlineNs) break
                }
            }
            if (stepGateActive && stepUsedNs > 0) consumeStepGateBudget(kotlin.math.ceil(stepUsedNs).toLong())

            nowNs = elapsedNs()
            if (nowNs >= nextFrameNs || (stepGateActive && stepUsedNs > 0)) {
                val revision = displayRevision()
                if (revision != lastDisplayRevision) lastDisplayGeneration = Long.MIN_VALUE
                lastDisplayRevision = revision
                val snapshot = emulator.getDisplaySnapshot(lastDisplayGeneration)
                if (snapshot != null) {
                    lastDisplayGeneration = snapshot.generation
                    onFrame(snapshot.vramData)
                }
                // Missed display frames are expendable. Firmware time and interrupts are not.
                nextFrameNs = elapsedNs() + profile.frameTimeNs.toLong()
            }
            drainCommands()
            nowNs = elapsedNs()
            if (nowNs - lastDriftLogNs >= DRIFT_LOG_INTERVAL_NS) {
                val elapsedSeconds = (nowNs - startNs) / 1_000_000_000.0
                Log.d(TAG, "Clock: phone=%.3fs firmware=%.3fs debt=%.3fms mode=%s".format(
                    elapsedSeconds, pacer.virtualNs / 1_000_000_000.0, pacer.debtNs(nowNs) / 1_000_000.0, mode))
                lastDriftLogNs = nowNs
            }
            if (stepGateActive) {
                if (snapshotStepGateBudgetNs() <= 0) SystemClock.sleep(STEP_GATE_IDLE_SLEEP_MS)
                continue
            }
            val aheadNs = -pacer.debtNs(elapsedNs())
            if (aheadNs > 1_000) {
                // All modes run firmware at 1x. Power saving changes rendering/waiting only.
                val sleepNs = (aheadNs - 20_000).toLong()
                if (sleepNs > 0) LockSupport.parkNanos(sleepNs)
            }
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

    fun getCurrentDigimonStateSync(romName: String?): DigimonState? {
        if (!running) return null
        return emulator.getCurrentDigimonState(romName)
    }
}
