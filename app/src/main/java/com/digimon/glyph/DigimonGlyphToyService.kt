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
import com.digimon.glyph.emulator.DisplayBridge
import com.digimon.glyph.emulator.E0C6200
import com.digimon.glyph.emulator.EmulatorLoop
import com.digimon.glyph.emulator.FrameDebugState
import com.digimon.glyph.emulator.StateManager
import com.digimon.glyph.input.InputController
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

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
    }

    private lateinit var glyphManager: GlyphMatrixManager
    private lateinit var stateManager: StateManager
    private var emulator: E0C6200? = null
    private var emulatorLoop: EmulatorLoop? = null
    private var displayBridge: DisplayBridge? = null
    private var glyphRenderer: GlyphRenderer? = null
    private var inputController: InputController? = null
    private var romName: String? = null
    private var lastDebugFramePublishMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = object : Runnable {
        override fun run() {
            saveState()
            mainHandler.postDelayed(this, AUTOSAVE_INTERVAL_MS)
        }
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
        initGlyphManager()
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        stopEmulator()
        saveState()
        releaseGlyphManager()
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
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
                stopEmulator()
                saveState()
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
    }

    private fun releaseGlyphManager() {
        glyphRenderer?.release()
        glyphManager.unInit()
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

        // Restore saved state if same ROM
        if (stateManager.getSavedRomName() == romName) {
            stateManager.restore(emu)
            Log.d(TAG, "State restored for $romName")
        }

        val bridge = DisplayBridge()
        displayBridge = bridge

        val renderer = GlyphRenderer(this)
        renderer.init(glyphManager)
        glyphRenderer = renderer

        val input = InputController(this)
        input.attach(emu)
        input.start()
        inputController = input

        val loop = EmulatorLoop(emu, bridge) { bitmap, vram ->
            renderer.pushFrame(bitmap)
            val now = SystemClock.uptimeMillis()
            if (now - lastDebugFramePublishMs >= DEBUG_FRAME_INTERVAL_MS) {
                val fullDebug = bridge.renderFullDebugFrame(vram)
                FrameDebugState.update(bitmap, fullDebug, now)
                lastDebugFramePublishMs = now
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
        inputController?.stop()
        inputController = null
        glyphRenderer?.turnOff()
        lastDebugFramePublishMs = 0L
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

    private fun saveState() {
        val emu = emulator ?: return
        val name = romName ?: return
        stateManager.save(emu, name)
        Log.d(TAG, "State saved")
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
            romFile.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ROM", e)
            null
        }
    }
}
