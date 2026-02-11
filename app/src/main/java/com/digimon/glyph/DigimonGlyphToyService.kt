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
import android.os.SystemClock
import android.util.Log
import com.digimon.glyph.audio.BuzzerAudioEngine
import com.digimon.glyph.emulator.DisplayBridge
import com.digimon.glyph.emulator.DisplayRenderSettings
import com.digimon.glyph.emulator.E0C6200
import com.digimon.glyph.emulator.EmulatorAudioSettings
import com.digimon.glyph.emulator.EmulatorCommandBus
import com.digimon.glyph.emulator.EmulatorDebugSettings
import com.digimon.glyph.emulator.EmulatorLoop
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
        private const val UNBIND_STOP_GRACE_MS = 3500L
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
    private var lastAudioEnabled = false
    private var debugTelemetryEnabled = false
    private var glyphManagerInited = false
    private var frameHeartbeatCount: Long = 0L
    private var lastFrameHeartbeatLogMs: Long = 0L

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
            applyAudioSettingIfChanged()
            mainHandler.postDelayed(this, 150L)
        }
    }
    private val delayedShutdownRunnable = Runnable {
        Log.d(TAG, "Delayed shutdown after unbind")
        saveState(sync = true, reason = "unbind_delayed_shutdown")
        stopEmulator()
        releaseGlyphManager()
        mainHandler.removeCallbacks(commandPollRunnable)
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
        mainHandler.removeCallbacks(delayedShutdownRunnable)
        mainHandler.removeCallbacks(commandPollRunnable)
        stateManager = StateManager(this)
        DisplayRenderSettings.init(this)
        EmulatorAudioSettings.init(this)
        EmulatorDebugSettings.init(this)
        applyDebugSettingIfChanged(force = true)
        mainHandler.post(commandPollRunnable)
        initGlyphManager()
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        saveState(sync = true, reason = "on_unbind_immediate")
        // Nothing OS may transiently unbind/rebind toy services.
        // Delay shutdown so brief binder churn does not freeze gameplay.
        mainHandler.removeCallbacks(delayedShutdownRunnable)
        mainHandler.postDelayed(delayedShutdownRunnable, UNBIND_STOP_GRACE_MS)
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
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
        glyphManager.unInit()
        glyphManagerInited = false
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

        val input = InputController(this)
        input.attach(emu)
        input.setDebugEnabled(debugTelemetryEnabled)
        input.start()
        inputController = input

        val audio = BuzzerAudioEngine()
        audio.start()
        lastAudioEnabled = EmulatorAudioSettings.isAudioEnabled()
        audio.setEnabled(lastAudioEnabled)
        emu.onBuzzerChange = { on, freqHz, gain -> audio.onBuzzerChange(on, freqHz, gain) }
        audioEngine = audio

        val loop = EmulatorLoop(emu, bridge) { bitmap, vram ->
            renderer.pushFrame(bitmap)
            if (debugTelemetryEnabled) {
                frameHeartbeatCount++
                val now = SystemClock.uptimeMillis()
                if (now - lastFrameHeartbeatLogMs >= 1000L) {
                    Log.d(
                        TAG,
                        "Frame heartbeat count=$frameHeartbeatCount vramSize=${vram.size} " +
                            formatCoreStateForLog(emu)
                    )
                    lastFrameHeartbeatLogMs = now
                }
                if (now - lastDebugFramePublishMs >= DEBUG_FRAME_INTERVAL_MS) {
                    val fullDebug = bridge.renderFullDebugFrame(vram)
                    FrameDebugState.update(bitmap, fullDebug, now)
                    lastDebugFramePublishMs = now
                }
            }
        }
        emulatorLoop = loop
        loop.start()

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
    }

    private fun pushStaticFrame() {
        // For AOD, render one frame from current VRAM state
        val emu = emulator ?: return
        val bridge = displayBridge ?: return
        val renderer = glyphRenderer ?: return
        val vram = emu.getVRAM()
        val bitmap = bridge.renderFrame(vram)
        renderer.pushFrame(bitmap)
    }

    private fun saveState(sync: Boolean = false, reason: String = "unspecified") {
        val emu = emulator ?: return
        val name = romName ?: return
        val pc = emu.getState()["PC"] as? Int
        val seq = stateManager.saveAutosave(emu, name, romHash, sync = sync)
        val ts = stateManager.getAutosaveInfo().timestampMs
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
            EmulatorCommandBus.CMD_REFRESH_SETTINGS -> {
                DisplayRenderSettings.init(this)
                EmulatorAudioSettings.init(this)
                EmulatorDebugSettings.init(this)
                applyAudioSettingIfChanged(force = true)
                applyDebugSettingIfChanged(force = true)
                "settings refreshed"
            }
            else -> "unknown command: ${cmd.type}"
        }
        Log.d(TAG, "Command ${cmd.id} result: $status")
        EmulatorCommandBus.ack(this, cmd.id, status)
    }

    private fun saveAutosaveNow(): String {
        val emu = emulator ?: return "save failed: emulator not running"
        val name = romName ?: return "save failed: rom missing"
        val pc = emu.getState()["PC"] as? Int
        val seq = stateManager.saveAutosave(emu, name, romHash, sync = true)
        Log.d(TAG, "Manual autosave seq=$seq pc=$pc")
        return "autosave saved"
    }

    private fun loadAutosaveNow(): String {
        val emu = emulator ?: return "load failed: emulator not running"
        val ok = stateManager.restoreAutosave(emu, romName, romHash)
        return if (ok) "autosave loaded" else "load failed: no compatible autosave"
    }

    private fun saveSlot(slot: Int): String {
        val emu = emulator ?: return "slot $slot save failed: emulator not running"
        val name = romName ?: return "slot $slot save failed: rom missing"
        val pc = emu.getState()["PC"] as? Int
        val seq = stateManager.saveSlot(slot, emu, name, romHash, sync = true)
        Log.d(TAG, "Manual slot save slot=$slot seq=$seq pc=$pc")
        return "slot $slot saved"
    }

    private fun loadSlot(slot: Int): String {
        val emu = emulator ?: return "slot $slot load failed: emulator not running"
        val ok = stateManager.restoreSlot(slot, emu, romName, romHash)
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
        val emu = emulator ?: return "reset failed: emulator not running"
        emu.resetCpu()
        val name = romName
        if (name != null) {
            val seq = stateManager.saveAutosave(emu, name, romHash, sync = true)
            Log.d(TAG, "Full reset autosave seq=$seq")
        } else {
            stateManager.clearAutosave()
        }
        return "full reset complete"
    }

    private fun applyAudioSettingIfChanged(force: Boolean = false) {
        val enabled = EmulatorAudioSettings.isAudioEnabled()
        if (!force && enabled == lastAudioEnabled) return
        lastAudioEnabled = enabled
        audioEngine?.setEnabled(enabled)
    }

    private fun applyDebugSettingIfChanged(force: Boolean = false) {
        val enabled = EmulatorDebugSettings.isDebugEnabled()
        if (!force && enabled == debugTelemetryEnabled) return
        debugTelemetryEnabled = enabled
        inputController?.setDebugEnabled(enabled)
        if (!enabled) {
            FrameDebugState.clear()
            frameHeartbeatCount = 0L
            lastFrameHeartbeatLogMs = 0L
            lastDebugFramePublishMs = 0L
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
