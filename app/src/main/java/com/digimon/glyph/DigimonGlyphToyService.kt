package com.digimon.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.digimon.glyph.audio.BuzzerAudioEngine
import com.digimon.glyph.audio.BuzzerHapticEngine
import com.digimon.glyph.emulator.DisplayBridge
import com.digimon.glyph.emulator.DisplayRenderSettings
import com.digimon.glyph.emulator.E0C6200
import com.digimon.glyph.emulator.EmulatorAudioSettings
import com.digimon.glyph.emulator.EmulatorCommandBus
import com.digimon.glyph.emulator.EmulatorDebugSettings
import com.digimon.glyph.emulator.EmulatorLoop
import com.digimon.glyph.emulator.EmulatorTimingSettings
import com.digimon.glyph.emulator.FrameDebugState
import com.digimon.glyph.emulator.StateManager
import com.digimon.glyph.input.InputController
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import java.security.MessageDigest

/**
 * Glyph Toy service that runs the Digimon V3 emulator on the Nothing Phone 3 Glyph Matrix.
 *
 * Lifecycle:
 * - onBind: Init SDK, load ROM, restore state, start emulator + input
 * - MSG_GLYPH_TOY messages: Handle status changes and button events
 * - onUnbind: Save state, stop emulator, release SDK
 */
class DigimonGlyphToyService : Service() {

    companion object {
        private const val TAG = "DigimonGlyphToy"
        private const val AUTOSAVE_INTERVAL_MS = 60_000L
        private const val DEBUG_FRAME_INTERVAL_MS = 120L
        private const val FRAME_PREVIEW_OBSERVER_WINDOW_MS = 2_500L
        private const val INTERACTION_TIMING_BOOST_MS = 60_000L
        private const val UNBIND_STOP_GRACE_MS = 15000L
    }

    private lateinit var glyphManager: GlyphMatrixManager
    private lateinit var stateManager: StateManager
    private var emulator: E0C6200? = null
    private var emulatorLoop: EmulatorLoop? = null
    private var displayBridge: DisplayBridge? = null
    private var glyphRenderer: GlyphRenderer? = null
    private var inputController: InputController? = null
    private var romName: String? = null
    private var romHash: String? = null
    private var lastDebugFramePublishMs: Long = 0L
    private var lastHandledCommandId: Long = 0L
    private var audioEngine: BuzzerAudioEngine? = null
    private var hapticEngine: BuzzerHapticEngine? = null
    private var lastAudioEnabled = false
    private var lastHapticEnabled = false
    private var lastExactTimingEnabled = true
    private var lastAppliedTimingMode: EmulatorLoop.TimingMode? = null
    private var interactionBoostUntilMs: Long = 0L
    private var debugTelemetryEnabled = false
    private var glyphManagerInited = false
    private var frameHeartbeatCount: Long = 0L
    private var lastFrameHeartbeatLogMs: Long = 0L
    private var cpuWakeLock: PowerManager.WakeLock? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = object : Runnable {
        override fun run() {
            saveState(sync = false, reason = "interval")
            mainHandler.postDelayed(this, AUTOSAVE_INTERVAL_MS)
        }
    }
    private val commandPollRunnable = object : Runnable {
        override fun run() {
            processPendingCommands()
            applyAudioHapticSettingsIfChanged()
            applyTimingSettingIfChanged()
            mainHandler.postDelayed(this, 150L)
        }
    }
    private val delayedShutdownRunnable = Runnable {
        Log.d(TAG, "Delayed shutdown after unbind")
        saveState(sync = true, reason = "unbind_delayed_shutdown")
        stopEmulator()
        releaseGlyphManager()
        mainHandler.removeCallbacks(commandPollRunnable)
        stopSelf()
    }

    // Messenger handler for Glyph Toy messages from the system
    private val toyHandler = Handler(Looper.getMainLooper()) { msg ->
        val event = msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
        Log.d(TAG, "Toy message what=${msg.what}, event=$event")
        if (msg.what == GlyphToy.MSG_GLYPH_TOY || event != null) {
            handleToyMessage(msg)
        }
        true
    }
    private val messenger = Messenger(toyHandler)

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        ensureServiceStarted()
        mainHandler.removeCallbacks(delayedShutdownRunnable)
        mainHandler.removeCallbacks(commandPollRunnable)
        stateManager = StateManager(this)
        DisplayRenderSettings.init(this)
        EmulatorAudioSettings.init(this)
        EmulatorDebugSettings.init(this)
        EmulatorTimingSettings.init(this)
        applyDebugSettingIfChanged(force = true)
        applyTimingSettingIfChanged(force = true)
        mainHandler.post(commandPollRunnable)
        initGlyphManager()
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        // Nothing OS may transiently unbind/rebind toy services.
        // Delay shutdown so brief binder churn does not freeze gameplay.
        mainHandler.removeCallbacks(delayedShutdownRunnable)
        mainHandler.postDelayed(delayedShutdownRunnable, UNBIND_STOP_GRACE_MS)
        return true
    }

    override fun onRebind(intent: Intent?) {
        Log.d(TAG, "onRebind")
        mainHandler.removeCallbacks(delayedShutdownRunnable)
        mainHandler.removeCallbacks(commandPollRunnable)
        mainHandler.post(commandPollRunnable)
        initGlyphManager()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keep service process alive across transient toy unbind/rebind churn.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        mainHandler.removeCallbacksAndMessages(null)
        saveState(sync = true, reason = "on_destroy")
        stopEmulator()
        releaseGlyphManager()
    }

    private fun handleToyMessage(msg: Message) {
        val data = msg.data ?: return
        val event = data.getString(GlyphToy.MSG_GLYPH_TOY_DATA) ?: return

        when {
            event.startsWith(GlyphToy.STATUS_PREPARE) -> {
                Log.d(TAG, "Toy prepare")
            }
            event.startsWith(GlyphToy.STATUS_START) -> {
                Log.d(TAG, "Toy start")
                startEmulator()
            }
            event.startsWith(GlyphToy.STATUS_END) -> {
                Log.d(TAG, "Toy end")
                mainHandler.removeCallbacks(delayedShutdownRunnable)
                saveState(sync = true, reason = "status_end")
                stopEmulator()
                releaseGlyphManager()
                stopSelf()
            }
            event == GlyphToy.EVENT_ACTION_DOWN -> {
                inputController?.onGlyphButtonDown()
            }
            event == GlyphToy.EVENT_ACTION_UP -> {
                inputController?.onGlyphButtonUp()
            }
            event == GlyphToy.EVENT_AOD -> {
                // Push a static frame for Always-On Display
                pushStaticFrame()
            }
        }
    }

    private fun initGlyphManager() {
        if (glyphManagerInited) return
        glyphManager = GlyphMatrixManager.getInstance(this)
        // Defensive cleanup: Nothing firmware can leave a stale remote connection
        // across rapid toy service restarts; clear it before init().
        safeGlyphManagerUninit("pre_init_cleanup")
        glyphManager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                Log.d(TAG, "Glyph service connected")
                val targetDevice = Build.MODEL ?: "A024"
                glyphManager.register(targetDevice)
                Log.d(TAG, "Glyph manager registered with target=$targetDevice")
                // Some firmware builds may not dispatch STATUS_START reliably.
                // Ensure emulator starts once the glyph service is ready.
                startEmulator()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "Glyph service disconnected")
            }
        })
        glyphManagerInited = true
    }

    private fun releaseGlyphManager() {
        if (!glyphManagerInited) return
        glyphRenderer?.release()
        safeGlyphManagerUninit("release")
        glyphManagerInited = false
    }

    private fun safeGlyphManagerUninit(stage: String) {
        try {
            glyphManager.unInit()
        } catch (e: IllegalArgumentException) {
            // Nothing OS may already have torn down this binder connection.
            // Treat unInit as best-effort to avoid service crash on duplicate unbind.
            Log.w(TAG, "Glyph manager already unbound during $stage")
        } catch (e: IllegalStateException) {
            // Defensive: some firmware paths can report bad manager state at teardown.
            Log.w(TAG, "Glyph manager release in invalid state during $stage")
        }
    }

    private fun ensureServiceStarted() {
        try {
            startService(Intent(this, DigimonGlyphToyService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to keep service started; falling back to bound-only mode", e)
        }
    }

    private fun acquireCpuWakeLock() {
        if (cpuWakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager ?: return
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:DigimonGlyphEmu")
            lock.setReferenceCounted(false)
            lock.acquire()
            cpuWakeLock = lock
            Log.d(TAG, "CPU wakelock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire CPU wakelock", e)
        }
    }

    private fun releaseCpuWakeLock() {
        val lock = cpuWakeLock ?: return
        try {
            if (lock.isHeld) {
                lock.release()
                Log.d(TAG, "CPU wakelock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release CPU wakelock", e)
        } finally {
            cpuWakeLock = null
        }
    }

    private fun startEmulator() {
        if (emulatorLoop != null) return // Already running

        stateManager = StateManager(this)
        val romData = loadRom()
        if (romData == null) {
            Log.e(TAG, "No ROM loaded, cannot start emulator")
            return
        }

        val emu = E0C6200(romData)
        emulator = emu

        // Restore saved state if same ROM/hash.
        val autosaveInfo = stateManager.getAutosaveInfo()
        val autosaveSeq = stateManager.getAutosaveSeq()
        Log.d(
            TAG,
            "Restore attempt rom=$romName hash=$romHash " +
                "exists=${autosaveInfo.exists} seq=$autosaveSeq ts=${autosaveInfo.timestampMs} " +
                "savedRom=${autosaveInfo.romName} savedHash=${autosaveInfo.romHash}"
        )
        val restored = stateManager.restoreAutosave(emu, romName, romHash)
        val pcAfterRestore = emu.getState()["PC"] as? Int
        Log.d(TAG, "Restore result ok=$restored pc=$pcAfterRestore")

        val bridge = DisplayBridge()
        displayBridge = bridge

        val renderer = GlyphRenderer(this)
        renderer.init(glyphManager)
        glyphRenderer = renderer

        val audio = BuzzerAudioEngine()
        audio.start()
        lastAudioEnabled = EmulatorAudioSettings.isAudioEnabled()
        audio.setEnabled(lastAudioEnabled)
        val haptic = BuzzerHapticEngine(this)
        lastHapticEnabled = EmulatorAudioSettings.isHapticAudioEnabled()
        haptic.setEnabled(lastHapticEnabled)
        emu.onBuzzerChange = { on, freqHz, gain ->
            audio.onBuzzerChange(on, freqHz, gain)
            haptic.onBuzzerChange(on, freqHz, gain)
        }
        audioEngine = audio
        hapticEngine = haptic

        val loop = EmulatorLoop(
            emulator = emu,
            displayBridge = bridge,
            onFrame = { bitmap, vram ->
                renderer.pushFrame(bitmap)
                val now = SystemClock.uptimeMillis()
                val shouldPublishFramePreview =
                    debugTelemetryEnabled || FrameDebugState.isObserved(FRAME_PREVIEW_OBSERVER_WINDOW_MS, now)
                if (shouldPublishFramePreview && now - lastDebugFramePublishMs >= DEBUG_FRAME_INTERVAL_MS) {
                    val fullDebug = bridge.renderFullDebugFrame(vram)
                    FrameDebugState.update(bitmap, fullDebug, now)
                    lastDebugFramePublishMs = now
                }
                if (debugTelemetryEnabled) {
                    frameHeartbeatCount++
                    if (now - lastFrameHeartbeatLogMs >= 1000L) {
                        Log.d(
                            TAG,
                            "Frame heartbeat count=$frameHeartbeatCount vramSize=${vram.size} " +
                                formatCoreStateForLog(emu)
                        )
                        lastFrameHeartbeatLogMs = now
                    }
                }
            },
        )
        lastExactTimingEnabled = EmulatorTimingSettings.isExactTimingEnabled()
        val initialMode =
            if (lastExactTimingEnabled) EmulatorLoop.TimingMode.EXACT
            else EmulatorLoop.TimingMode.POWER_SAVE
        loop.setTimingMode(initialMode)
        lastAppliedTimingMode = initialMode
        emulatorLoop = loop
        loop.start()
        applyTimingSettingIfChanged(force = true)

        val input = InputController(this)
        input.attach(emu)
        input.onUserInteraction = {
            handleUserInteractionForTimingBoost()
        }
        input.onPinSet = { port, pin, level ->
            runOnEmulatorAsync { core -> core.pinSet(port, pin, level) }
        }
        input.onPinRelease = { port, pin ->
            runOnEmulatorAsync { core -> core.pinRelease(port, pin) }
        }
        input.setDebugEnabled(debugTelemetryEnabled)
        input.start()
        inputController = input

        // Start autosave
        mainHandler.postDelayed(autosaveRunnable, AUTOSAVE_INTERVAL_MS)

        Log.d(TAG, "Emulator started with ROM: $romName")
    }

    private fun stopEmulator() {
        mainHandler.removeCallbacks(autosaveRunnable)
        emulatorLoop?.stop()
        emulatorLoop = null
        emulator?.onBuzzerChange = null
        audioEngine?.stop()
        audioEngine = null
        hapticEngine?.stop()
        hapticEngine = null
        inputController?.stop()
        inputController = null
        glyphRenderer?.turnOff()
        glyphRenderer?.release()
        glyphRenderer = null
        displayBridge = null
        emulator = null
        lastDebugFramePublishMs = 0L
        frameHeartbeatCount = 0L
        lastFrameHeartbeatLogMs = 0L
        FrameDebugState.clear()
        interactionBoostUntilMs = 0L
        lastAppliedTimingMode = null
        releaseCpuWakeLock()
    }

    private fun pushStaticFrame() {
        // For AOD, render one frame from current VRAM state
        val bridge = displayBridge ?: return
        val renderer = glyphRenderer ?: return
        val vram = runOnEmulatorSync { core -> core.getVRAM() } ?: return
        val bitmap = bridge.renderFrame(vram)
        renderer.pushFrame(bitmap)
    }

    private fun saveState(sync: Boolean = false, reason: String = "unspecified") {
        val name = romName ?: return
        val result = runOnEmulatorSync { core ->
            val pc = core.getState()["PC"] as? Int
            val seq = stateManager.saveAutosave(core, name, romHash, sync = sync)
            Pair(seq, pc)
        } ?: return
        val ts = stateManager.getAutosaveInfo().timestampMs
        val seq = result.first
        val pc = result.second
        Log.d(TAG, "State saved reason=$reason sync=$sync seq=$seq pc=$pc ts=$ts")
    }

    /**
     * Load ROM from internal storage. The RomLoaderActivity extracts .bin from .zip
     * and saves it to filesDir as "current_rom.bin" with the name in "current_rom_name".
     */
    private fun loadRom(): ByteArray? {
        return try {
            val nameFile = java.io.File(filesDir, "current_rom_name")
            val romFile = java.io.File(filesDir, "current_rom.bin")
            if (!romFile.exists()) {
                Log.e(TAG, "No ROM file found at ${romFile.absolutePath}")
                return null
            }
            romName = if (nameFile.exists()) nameFile.readText().trim() else "unknown"
            val bytes = romFile.readBytes()
            romHash = sha256(bytes)
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ROM", e)
            null
        }
    }

    private fun processPendingCommands() {
        val cmd = EmulatorCommandBus.readPending(this, lastHandledCommandId) ?: return
        lastHandledCommandId = cmd.id
        Log.d(TAG, "Process command id=${cmd.id} type=${cmd.type} arg=${cmd.arg}")
        val status = when (cmd.type) {
            EmulatorCommandBus.CMD_SAVE_AUTOSAVE -> saveAutosaveNow()
            EmulatorCommandBus.CMD_LOAD_AUTOSAVE -> loadAutosaveNow()
            EmulatorCommandBus.CMD_SAVE_SLOT -> saveSlot(cmd.arg)
            EmulatorCommandBus.CMD_LOAD_SLOT -> loadSlot(cmd.arg)
            EmulatorCommandBus.CMD_RESTART -> restartEmulator()
            EmulatorCommandBus.CMD_FULL_RESET -> fullResetEmulator()
            EmulatorCommandBus.CMD_PRESS_COMBO -> pressCombo(cmd.arg)
            EmulatorCommandBus.CMD_REFRESH_SETTINGS -> {
                DisplayRenderSettings.init(this)
                EmulatorAudioSettings.init(this)
                EmulatorDebugSettings.init(this)
                EmulatorTimingSettings.init(this)
                applyAudioHapticSettingsIfChanged(force = true)
                applyDebugSettingIfChanged(force = true)
                applyTimingSettingIfChanged(force = true)
                "settings refreshed"
            }
            else -> "unknown command: ${cmd.type}"
        }
        Log.d(TAG, "Command ${cmd.id} result: $status")
        EmulatorCommandBus.ack(this, cmd.id, status)
    }

    private fun saveAutosaveNow(): String {
        val name = romName ?: return "save failed: rom missing"
        val result = runOnEmulatorSync { core ->
            val pc = core.getState()["PC"] as? Int
            val seq = stateManager.saveAutosave(core, name, romHash, sync = true)
            Pair(seq, pc)
        } ?: return "save failed: emulator not running"
        val seq = result.first
        val pc = result.second
        Log.d(TAG, "Manual autosave seq=$seq pc=$pc")
        return "autosave saved"
    }

    private fun loadAutosaveNow(): String {
        val ok = runOnEmulatorSync { core ->
            stateManager.restoreAutosave(core, romName, romHash)
        } ?: return "load failed: emulator not running"
        return if (ok) "autosave loaded" else "load failed: no compatible autosave"
    }

    private fun saveSlot(slot: Int): String {
        val name = romName ?: return "slot $slot save failed: rom missing"
        val result = runOnEmulatorSync { core ->
            val pc = core.getState()["PC"] as? Int
            val seq = stateManager.saveSlot(slot, core, name, romHash, sync = true)
            Pair(seq, pc)
        } ?: return "slot $slot save failed: emulator not running"
        val seq = result.first
        val pc = result.second
        Log.d(TAG, "Manual slot save slot=$slot seq=$seq pc=$pc")
        return "slot $slot saved"
    }

    private fun loadSlot(slot: Int): String {
        val ok = runOnEmulatorSync { core ->
            stateManager.restoreSlot(slot, core, romName, romHash)
        } ?: return "slot $slot load failed: emulator not running"
        return if (ok) "slot $slot loaded" else "slot $slot load failed: no compatible save"
    }

    private fun restartEmulator(): String {
        if (emulatorLoop == null) return "restart skipped: emulator not running"
        saveState(sync = true, reason = "restart_command")
        stopEmulator()
        startEmulator()
        return "emulator restarted"
    }

    private fun fullResetEmulator(): String {
        val name = romName
        if (name != null) {
            val seq = runOnEmulatorSync { core ->
                core.resetCpu()
                stateManager.saveAutosave(core, name, romHash, sync = true)
            } ?: return "reset failed: emulator not running"
            Log.d(TAG, "Full reset autosave seq=$seq")
        } else {
            val ok = runOnEmulatorSync { core ->
                core.resetCpu()
                true
            } ?: return "reset failed: emulator not running"
            if (!ok) return "reset failed: emulator not running"
            stateManager.clearAutosave()
        }
        return "full reset complete"
    }

    private fun pressCombo(combo: Int): String {
        val input = inputController ?: return "combo failed: input not running"
        val name = when (combo) {
            EmulatorCommandBus.COMBO_AB -> "A+B"
            EmulatorCommandBus.COMBO_AC -> "A+C"
            EmulatorCommandBus.COMBO_BC -> "B+C"
            else -> return "combo failed: unknown combo"
        }
        val ok = input.triggerComboTap(combo)
        return if (ok) "combo $name triggered" else "combo $name failed"
    }

    private fun applyAudioHapticSettingsIfChanged(force: Boolean = false) {
        val hapticEnabled = EmulatorAudioSettings.isHapticAudioEnabled()
        val audioEnabled = EmulatorAudioSettings.isAudioEnabled()
        if (!force && audioEnabled == lastAudioEnabled && hapticEnabled == lastHapticEnabled) return
        if (force || audioEnabled != lastAudioEnabled) {
            lastAudioEnabled = audioEnabled
            audioEngine?.setEnabled(audioEnabled)
        }
        if (force || hapticEnabled != lastHapticEnabled) {
            lastHapticEnabled = hapticEnabled
            hapticEngine?.setEnabled(hapticEnabled)
        }
    }

    private fun applyDebugSettingIfChanged(force: Boolean = false) {
        val enabled = EmulatorDebugSettings.isDebugEnabled()
        if (!force && enabled == debugTelemetryEnabled) return
        debugTelemetryEnabled = enabled
        inputController?.setDebugEnabled(enabled)
        if (!enabled) {
            frameHeartbeatCount = 0L
            lastFrameHeartbeatLogMs = 0L
        }
    }

    private fun applyTimingSettingIfChanged(force: Boolean = false) {
        val exactEnabled = EmulatorTimingSettings.isExactTimingEnabled()
        val now = SystemClock.uptimeMillis()
        val boostActive = !exactEnabled && now < interactionBoostUntilMs
        val desiredMode =
            if (exactEnabled || boostActive) EmulatorLoop.TimingMode.EXACT
            else EmulatorLoop.TimingMode.POWER_SAVE
        if (!force && exactEnabled == lastExactTimingEnabled && desiredMode == lastAppliedTimingMode) return
        lastExactTimingEnabled = exactEnabled
        if (force || desiredMode != lastAppliedTimingMode) {
            emulatorLoop?.setTimingMode(desiredMode)
        }
        if (desiredMode == EmulatorLoop.TimingMode.EXACT) {
            acquireCpuWakeLock()
        } else {
            releaseCpuWakeLock()
        }
        lastAppliedTimingMode = desiredMode
        Log.d(TAG, "Timing mode set to $desiredMode (userExact=$exactEnabled boostActive=$boostActive)")
    }

    private fun handleUserInteractionForTimingBoost() {
        if (EmulatorTimingSettings.isExactTimingEnabled()) return
        interactionBoostUntilMs = SystemClock.uptimeMillis() + INTERACTION_TIMING_BOOST_MS
        if (lastAppliedTimingMode != EmulatorLoop.TimingMode.EXACT) {
            applyTimingSettingIfChanged(force = true)
        }
    }

    private fun <T> runOnEmulatorSync(
        timeoutMs: Long = 1500L,
        block: (E0C6200) -> T
    ): T? {
        val emu = emulator ?: return null
        val loop = emulatorLoop
        return if (loop == null) {
            block(emu)
        } else {
            loop.executeSync(timeoutMs, block)
        }
    }

    private fun runOnEmulatorAsync(block: (E0C6200) -> Unit): Boolean {
        val emu = emulator ?: return false
        val loop = emulatorLoop
        return if (loop == null) {
            block(emu)
            true
        } else {
            loop.enqueue(block)
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append("%02x".format(b))
        }
        return sb.toString()
    }

    private fun formatCoreStateForLog(emu: E0C6200): String {
        val s = emu.getState()
        fun i(key: String): Int = (s[key] as? Int) ?: -1
        return "pc=${i("PC")} npc=${i("NPC")} halt=${i("HALT")} if=${i("IF")} " +
            "it=${i("IT")} isw=${i("ISW")} ipt=${i("IPT")} ik0=${i("IK0")} ik1=${i("IK1")} " +
            "eit=${i("EIT")} eisw=${i("EISW")} eipt=${i("EIPT")} eik0=${i("EIK0")} eik1=${i("EIK1")} " +
            "tm=${i("TM")} pt=${i("PT")} rd=${i("RD")} sw=${i("SWH")}:${i("SWL")} " +
            "k0=${i("K0")} k1=${i("K1")} r4=${i("R4")} osc=${i("CTRL_OSC")} swc=${i("CTRL_SW")} ptc=${i("CTRL_PT")} " +
            "dbg_int=${i("DBG_INT_COUNT")} dbg_vec=${i("DBG_INT_VEC")} " +
            "dbg_it32=${i("DBG_IT32_SET")} dbg_it8=${i("DBG_IT8_SET")} dbg_it2=${i("DBG_IT2_SET")} dbg_it1=${i("DBG_IT1_SET")}"
    }
}
