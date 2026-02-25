package com.digimon.glyph

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
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
import com.digimon.glyph.battle.BattleLinkManager
import com.digimon.glyph.battle.BattleStateStore
import com.digimon.glyph.battle.BattleTransport
import com.digimon.glyph.battle.BattleTransportSettings
import com.digimon.glyph.battle.BattleTransportType
import com.digimon.glyph.battle.InternetBattleTransport
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
import com.digimon.glyph.input.InputOverlayAnimator
import com.digimon.glyph.widget.DigimonWidgetProvider
import com.digimon.glyph.widget.WidgetFramePusher
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
        private const val BATTLE_ASSIST_BURST_STEP_MS = 85L
        private const val BATTLE_ASSIST_BURST_STEPS = 14
        private const val BATTLE_ASSIST_ENABLED = true
        private const val BATTLE_WAVE_ENABLED = false
        private const val BATTLE_WAVE_SETTLE_MS = 220L
        private const val BATTLE_WAVE_STEP_MS = 12
        private const val BATTLE_WAVE_TOTAL_MS = 720
        private const val BATTLE_LINK_LOW_MASK = 0xE
        private const val BATTLE_LINK_HIGH_MASK = 0xF
        // Experimental bridge: disabled, replaced by VirtualDCom
        private const val BATTLE_RD_WRITE_SERIAL_BRIDGE = false
        private const val BATTLE_RD_BRIDGE_MIN_REPEAT_MS = 1500L
        // Raw emu-driven P2 edges can burst too fast for network transport; keep disabled
        // and rely on button-assist edges + serial byte bridge for link protocol.
        private const val BATTLE_SEND_EMU_P2_EDGES = false
        // V-pet (2-prong) timing uses short pulses; stretching lows breaks packet decoding.
        // Keep this at 0 unless we add a profile-aware transport layer.
        private const val BATTLE_REMOTE_P2_MIN_LOW_MS = 0L
        // During initial handshake over wireless transport, hold remote-low a bit longer
        // to avoid missing narrow pulses before the ROM samples link input.
        private const val BATTLE_HANDSHAKE_MIN_LOW_MS = 180L
        private const val BATTLE_HANDSHAKE_MIN_LOW_WINDOW_MS = 12_000L
        // Heuristic mirror path; keep disabled in strict edge transport mode.
        private const val BATTLE_MIRROR_P2_TO_K0_PIN3 = false
        private const val BATTLE_LINK_B_HOLD_MS = 120L
        private const val BATTLE_FORCED_CORRECTION_FACTOR = 1.0
        private const val BATTLE_STEP_CONNECT_PRIME_MS = 180
        private const val BATTLE_STEP_LINK_EVENT_BONUS_MS = 8
        private const val BATTLE_STEP_BUTTON_BONUS_MS = 40
        private const val DEFAULT_BUTTON_HOLD_MS = 85L

        const val ACTION_START_WIDGET = "com.digimon.glyph.START_WIDGET"
        const val ACTION_STOP = "com.digimon.glyph.STOP"
        private const val NOTIF_CHANNEL_ID = "digimon_service"
        private const val NOTIF_ID = 1
    }

    private var glyphManager: Any? = null  // GlyphMatrixManager, typed as Any to avoid class-load on non-Nothing
    private lateinit var stateManager: StateManager
    private var standaloneMode = false
    private var emulator: E0C6200? = null
    private var emulatorLoop: EmulatorLoop? = null
    private var displayBridge: DisplayBridge? = null
    @Volatile private var glyphRenderer: GlyphRenderer? = null
    private var inputController: InputController? = null
    private var inputOverlayAnimator: InputOverlayAnimator? = null
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
    private var lastAppliedClockCorrectionOverride: Double? = null
    private var lastAppliedStepGateEnabled = false
    private var interactionBoostUntilMs: Long = 0L
    private var debugTelemetryEnabled = false
    private var battleConnected = false
    private var ioTraceCount = 0L
    private var ioTraceWindowStartMs = 0L
    private val ioTraceLastValues = HashMap<Int, Int>()
    private var glyphManagerInited = false
    private var frameHeartbeatCount: Long = 0L
    private var lastFrameHeartbeatLogMs: Long = 0L
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var battleTransport: BattleTransport? = null
    private var battleTransportType: BattleTransportType = BattleTransportType.NEARBY
    private var battleAssistActiveButtons = 0
    private var battleAssistLowUntilMs: Long = 0L
    private var battleAssistLowApplied = false
    private var battleAssistBurstToken = 0
    private var battleAssistWaveUntilMs: Long = 0L
    private var remoteP2LowSinceMs: Long = 0L
    private var remoteP2ReleaseToken = 0
    private var remoteLinkK0Pin3Low = false
    private var battleConnectedAtMs: Long = 0L
    private var localP2EdgeSeq: Long = 0L
    private var localP2LastSentValue: Int? = null
    private var remoteP2LastAppliedSeq: Long = -1L
    private var remoteP2LegacySeq: Long = 0L
    private var battleRdWriteMask = 0
    private var battleRdWriteValue = 0
    private var battleRdBridgeLastTxByte = -1
    private var battleRdBridgeLastTxAtMs = 0L

    private var virtualDCom: com.digimon.glyph.battle.VirtualDCom? = null

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
    private val battleAssistReleaseRunnable = Runnable {
        if (!battleConnected || battleAssistActiveButtons > 0) return@Runnable
        if (!battleAssistLowApplied) return@Runnable
        val sent = sendBattleP2Edge(BATTLE_LINK_HIGH_MASK, reason = "assist_delayed_up")
        battleAssistLowApplied = false
        Log.d(TAG, "Battle assist tx P2=15 (delayed up, sent=$sent)")
    }
    // Messenger handler for Glyph Toy messages from the system.
    // Only used on Nothing phones — initialized lazily to avoid loading GlyphToy class on other devices.
    private val messenger: Messenger? by lazy {
        if (!GlyphAvailability.isAvailable) return@lazy null
        val handler = Handler(Looper.getMainLooper()) { msg ->
            val event = msg.data?.getString(
                com.nothing.ketchum.GlyphToy.MSG_GLYPH_TOY_DATA
            )
            Log.d(TAG, "Toy message what=${msg.what}, event=$event")
            if (msg.what == com.nothing.ketchum.GlyphToy.MSG_GLYPH_TOY || event != null) {
                handleToyMessage(msg)
            }
            true
        }
        Messenger(handler)
    }

    override fun onCreate() {
        super.onCreate()
        BattleStateStore.init(this)
        BattleTransportSettings.init(this)
        battleTransport = createBattleTransport()
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind glyphAvailable=${GlyphAvailability.isAvailable}")
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
        if (GlyphAvailability.isAvailable) {
            initGlyphManager()
        }
        return messenger?.binder
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
        when (intent?.action) {
            ACTION_START_WIDGET -> {
                Log.d(TAG, "Starting in standalone widget mode")
                standaloneMode = true
                // Only call startForeground if this was a startForegroundService call.
                // When started via plain startService (widget path), we skip it — the service
                // runs as a background service, which is fine since we're on Nothing Phone
                // where the Glyph binding keeps the process alive, or the emulator runs briefly.
                tryStartForeground()
                stateManager = StateManager(this)
                DisplayRenderSettings.init(this)
                EmulatorAudioSettings.init(this)
                EmulatorDebugSettings.init(this)
                EmulatorTimingSettings.init(this)
                applyDebugSettingIfChanged(force = true)
                applyTimingSettingIfChanged(force = true)
                mainHandler.removeCallbacks(commandPollRunnable)
                mainHandler.post(commandPollRunnable)
                // Start the emulator immediately so the UI works on Nothing Phones as well.
                // If the Glyph Toy SDK connects later, we will late-bind the GlyphRenderer.
                startEmulator()
                // On Nothing Phone, WidgetFramePusher is wired inside startEmulator().
            }
            ACTION_STOP -> {
                Log.d(TAG, "Stop requested")
                saveState(sync = true, reason = "stop_action")
                stopEmulator()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        mainHandler.removeCallbacksAndMessages(null)
        saveState(sync = true, reason = "on_destroy")
        battleTransport?.stop()
        battleTransport = null
        stopEmulator()
        releaseGlyphManager()
    }

    private fun createBattleTransport(): BattleTransport {
        battleTransportType = BattleTransportSettings.getTransportType()
        Log.i(TAG, "Battle transport selected: $battleTransportType")
        return when (battleTransportType) {
            BattleTransportType.NEARBY -> BattleLinkManager(this, createBattleTransportListener())
            BattleTransportType.INTERNET_RELAY -> InternetBattleTransport(this, createBattleTransportListener())
        }
    }

    private fun refreshBattleTransportIfNeeded() {
        val requestedType = BattleTransportSettings.getTransportType()
        if (requestedType == battleTransportType && battleTransport != null) return
        if (battleConnected) {
            Log.w(
                TAG,
                "Battle transport change requested while connected; keeping current transport=$battleTransportType"
            )
            return
        }
        battleTransport?.stop()
        battleTransport = createBattleTransport()
    }

    private fun createBattleTransportListener(): BattleTransport.Listener {
        return object : BattleTransport.Listener {
            override fun onConnected(peerName: String) {
                mainHandler.post {
                    battleConnected = true
                    battleConnectedAtMs = SystemClock.uptimeMillis()
                    battleAssistActiveButtons = 0
                    battleAssistLowUntilMs = 0L
                    battleAssistLowApplied = false
                    battleAssistBurstToken++
                    battleAssistWaveUntilMs = 0L
                    remoteP2LowSinceMs = 0L
                    remoteP2ReleaseToken++
                    remoteLinkK0Pin3Low = false
                    resetBattleEdgeState()
                    battleRdWriteMask = 0
                    battleRdWriteValue = 0
                    battleRdBridgeLastTxByte = -1
                    battleRdBridgeLastTxAtMs = 0L
                    mainHandler.removeCallbacks(battleAssistReleaseRunnable)
                    Log.d(TAG, "Battle connected peer=$peerName")
                    runOnEmulatorAsync { core ->
                        core.syncLinkedPortDriveState()
                        virtualDCom?.reset()
                    }
                    sendBattleP2Edge(BATTLE_LINK_HIGH_MASK, force = true, reason = "connect_sync")
                    applyTimingSettingIfChanged(force = true)
                    pulseBattleStepBudget("battle_connect_prime", BATTLE_STEP_CONNECT_PRIME_MS)
                }
            }

            override fun onDisconnected(reason: String) {
                mainHandler.post {
                    battleConnected = false
                    battleConnectedAtMs = 0L
                    battleAssistActiveButtons = 0
                    battleAssistLowUntilMs = 0L
                    battleAssistLowApplied = false
                    battleAssistBurstToken++
                    battleAssistWaveUntilMs = 0L
                    remoteP2LowSinceMs = 0L
                    remoteP2ReleaseToken++
                    remoteLinkK0Pin3Low = false
                    clearRemoteP2EdgeQueue()
                    battleRdWriteMask = 0
                    battleRdWriteValue = 0
                    battleRdBridgeLastTxByte = -1
                    battleRdBridgeLastTxAtMs = 0L
                    mainHandler.removeCallbacks(battleAssistReleaseRunnable)
                    Log.d(TAG, "Battle disconnected reason=$reason")
                    runOnEmulatorAsync { core ->
                        core.applyLinkedPortDrive("P2", null)
                        if (BATTLE_MIRROR_P2_TO_K0_PIN3) {
                            core.pinRelease("K0", 3)
                        }
                        virtualDCom?.reset()
                    }
                    applyTimingSettingIfChanged(force = true)
                }
            }

            override fun onMessage(type: String, body: String?) {
                when (type) {
                    "vpet_packet" -> {
                        val packet = body?.toIntOrNull() ?: return
                        Log.d(TAG, "Battle vpet packet rx: 0x${packet.toString(16)}")
                        mainHandler.post {
                            pulseBattleStepBudget("vpet_rx", BATTLE_STEP_LINK_EVENT_BONUS_MS)
                            virtualDCom?.enqueuePacket(packet)
                        }
                    }
                    "serial_tx" -> {
                        val value = body?.toIntOrNull() ?: return
                        Log.d(TAG, "Battle serial rx byte=$value")
                        mainHandler.post {
                            pulseBattleStepBudget("serial_rx", BATTLE_STEP_LINK_EVENT_BONUS_MS)
                            runOnEmulatorAsync { core ->
                                core.receiveSerialByte(value)
                            }
                        }
                    }
                    "pin_edge" -> {
                        val edge = battleTransport?.parsePinEdge(body) ?: return
                        if (edge.port != "P2") return
                        Log.d(
                            TAG,
                            "Battle pin edge rx P2=${edge.value ?: -1} seq=${edge.seq} src=${edge.sourceUptimeMs}"
                        )
                        mainHandler.post {
                            pulseBattleStepBudget("pin_edge_rx", BATTLE_STEP_LINK_EVENT_BONUS_MS)
                            applyRemoteP2Edge(
                                seq = edge.seq,
                                value = edge.value
                            )
                        }
                    }
                    "pin_tx" -> {
                        val payload = body ?: return
                        val idx = payload.indexOf(':')
                        if (idx <= 0 || idx >= payload.lastIndex) return
                        val port = payload.substring(0, idx)
                        val valueToken = payload.substring(idx + 1)
                        val value = if (valueToken == "-") null else valueToken.toIntOrNull()
                        if (port != "P2") return
                        Log.d(TAG, "Battle pin rx P2=${value ?: -1}")
                        mainHandler.post {
                            pulseBattleStepBudget("pin_tx_rx", BATTLE_STEP_LINK_EVENT_BONUS_MS)
                            remoteP2LegacySeq += 1L
                            applyRemoteP2Edge(
                                seq = remoteP2LegacySeq,
                                value = value
                            )
                        }
                    }
                    "wave_start" -> {
                        mainHandler.post {
                            onRemoteBattleWaveStart(body)
                        }
                    }
                }
            }
        }
    }

    private fun handleToyMessage(msg: Message) {
        if (!GlyphAvailability.isAvailable) return
        val data = msg.data ?: return
        val event = data.getString(com.nothing.ketchum.GlyphToy.MSG_GLYPH_TOY_DATA) ?: return

        when {
            event.startsWith(com.nothing.ketchum.GlyphToy.STATUS_PREPARE) -> {
                Log.d(TAG, "Toy prepare")
            }
            event.startsWith(com.nothing.ketchum.GlyphToy.STATUS_START) -> {
                Log.d(TAG, "Toy start")
                startEmulator()
            }
            event.startsWith(com.nothing.ketchum.GlyphToy.STATUS_END) -> {
                Log.d(TAG, "Toy end")
                mainHandler.removeCallbacks(delayedShutdownRunnable)
                saveState(sync = true, reason = "status_end")
                stopEmulator()
                releaseGlyphManager()
                stopSelf()
            }
            event == com.nothing.ketchum.GlyphToy.EVENT_ACTION_DOWN -> {
                inputController?.onGlyphButtonDown()
            }
            event == com.nothing.ketchum.GlyphToy.EVENT_ACTION_UP -> {
                inputController?.onGlyphButtonUp()
            }
            event == com.nothing.ketchum.GlyphToy.EVENT_AOD -> {
                pushStaticFrame()
            }
        }
    }

    private fun initGlyphManager() {
        if (!GlyphAvailability.isAvailable) return
        if (glyphManagerInited) return
        val mgr = com.nothing.ketchum.GlyphMatrixManager.getInstance(this)
        glyphManager = mgr
        // Defensive cleanup: Nothing firmware can leave a stale remote connection
        // across rapid toy service restarts; clear it before init().
        safeGlyphManagerUninit("pre_init_cleanup")
        mgr.init(object : com.nothing.ketchum.GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                Log.d(TAG, "Glyph service connected")
                val targetDevice = Build.MODEL ?: "A024"
                mgr.register(targetDevice)
                Log.d(TAG, "Glyph manager registered with target=$targetDevice")
                
                if (emulatorLoop != null && glyphRenderer == null) {
                    Log.d(TAG, "Late-binding GlyphRenderer to running emulator")
                    val renderer = GlyphRenderer(this@DigimonGlyphToyService)
                    renderer.init(mgr)
                    glyphRenderer = renderer
                } else {
                    startEmulator()
                }
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
        val mgr = glyphManager as? com.nothing.ketchum.GlyphMatrixManager ?: return
        try {
            mgr.unInit()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Glyph manager already unbound during $stage")
        } catch (e: IllegalStateException) {
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

        val dcom = com.digimon.glyph.battle.VirtualDCom(
            onPacketDecoded = { packet ->
                mainHandler.post {
                    val sent = battleTransport?.sendVpetPacket(packet) == true
                    if (sent) {
                        pulseBattleStepBudget("vpet_tx", BATTLE_STEP_LINK_EVENT_BONUS_MS)
                    }
                }
            },
            onP2DriveChange = { drive ->
                emu.applyLinkedPortDrive("P2", drive)
            }
        )
        virtualDCom = dcom

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

        val renderer: GlyphRenderer?
        if (GlyphAvailability.isAvailable) {
            val mgr = glyphManager as? com.nothing.ketchum.GlyphMatrixManager
            if (mgr != null) {
                renderer = GlyphRenderer(this)
                renderer.init(mgr)
                glyphRenderer = renderer
            } else {
                renderer = null
            }
        } else {
            renderer = null
        }

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
        emu.onSerialTx = { value ->
            Log.d(TAG, "Battle serial tx byte=$value")
            mainHandler.post {
                val sent = battleTransport?.sendSerialByte(value) == true
                if (sent) {
                    pulseBattleStepBudget("serial_tx", BATTLE_STEP_LINK_EVENT_BONUS_MS)
                }
            }
        }
        emu.onPortDriveChange = { port, value ->
            if (port == "P2") {
                val isLow = (value != null && value != 0xF)
                val diag = emu.getDiagnostics()
                val elapsedCyc = diag["elapsedCyc"] as Double
                val timeUs = (elapsedCyc / 1.06).toLong()
                dcom.onLocalP2Edge(isLow, timeUs)
            }
            mainHandler.post {
                if (!battleConnected || port != "P2") return@post
                if (!BATTLE_SEND_EMU_P2_EDGES) return@post
                val sent = sendBattleP2Edge(value, reason = "emu_drive")
                if (sent) {
                    Log.d(TAG, "Battle pin tx P2=${value ?: -1}")
                }
            }
        }
        emu.onIoAccess = { isWrite, addr, value ->
            if (battleConnected &&
                BATTLE_RD_WRITE_SERIAL_BRIDGE &&
                isWrite &&
                (addr == 0xF26 || addr == 0xF27)
            ) {
                val nibble = value and 0xF
                if (addr == 0xF26) {
                    battleRdWriteValue = (battleRdWriteValue and 0xF0) or nibble
                    battleRdWriteMask = battleRdWriteMask or 0x1
                } else {
                    battleRdWriteValue = (battleRdWriteValue and 0x0F) or (nibble shl 4)
                    battleRdWriteMask = battleRdWriteMask or 0x2
                }
                if (battleRdWriteMask == 0x3) {
                    val tx = battleRdWriteValue and 0xFF
                    battleRdWriteMask = 0
                    mainHandler.post {
                        val now = SystemClock.uptimeMillis()
                        val allowRepeat =
                            battleRdBridgeLastTxByte != tx ||
                                now - battleRdBridgeLastTxAtMs >= BATTLE_RD_BRIDGE_MIN_REPEAT_MS
                        if (allowRepeat) {
                            val sent = battleTransport?.sendSerialByte(tx) == true
                            if (sent) {
                                battleRdBridgeLastTxByte = tx
                                battleRdBridgeLastTxAtMs = now
                                pulseBattleStepBudget("rd_bridge_tx", BATTLE_STEP_LINK_EVENT_BONUS_MS)
                            }
                            Log.d(
                                TAG,
                                "Battle rd-bridge serial tx byte=$tx sent=$sent allowRepeat=$allowRepeat"
                            )
                        } else {
                            Log.d(
                                TAG,
                                "Battle rd-bridge serial tx suppressed byte=$tx " +
                                    "deltaMs=${now - battleRdBridgeLastTxAtMs}"
                            )
                        }
                    }
                }
            }
            // Link-trace diagnostics: only track likely comm registers while connected.
            if (battleConnected &&
                addr in setOf(0xF14, 0xF15, 0xF26, 0xF27, 0xF30, 0xF31, 0xF42, 0xF60, 0xF61, 0xF62, 0xF63, 0xF7D)
            ) {
                // Skip idle spam; keep transitions and comm-relevant events.
                if (!(isWrite && addr == 0xF14 && value == 7)) {
                    if (isWrite && (addr == 0xF62 || addr == 0xF7D)) {
                        Log.d(
                            TAG,
                            "Battle io ${if (isWrite) "W" else "R"} addr=0x${addr.toString(16)} v=$value (direct)"
                        )
                    }
                    val now = SystemClock.uptimeMillis()
                    if (now - ioTraceWindowStartMs > 10_000L) {
                        ioTraceWindowStartMs = now
                        ioTraceCount = 0L
                    }
                    val valueKey = (if (isWrite) 0x10000 else 0) or (addr and 0xFFFF)
                    val prevValue = ioTraceLastValues[valueKey]
                    if (prevValue == null || prevValue != value) {
                        ioTraceLastValues[valueKey] = value
                        Log.d(
                            TAG,
                            "Battle io ${if (isWrite) "W" else "R"} addr=0x${addr.toString(16)} v=$value changed"
                        )
                    }
                    ioTraceCount++
                    if (ioTraceCount <= 24L || ioTraceCount % 100L == 0L) {
                        Log.d(
                            TAG,
                            "Battle io ${if (isWrite) "W" else "R"} addr=0x${addr.toString(16)} v=$value count=$ioTraceCount"
                        )
                    }
                }
            }
        }
        emu.syncLinkedPortDriveState()
        audioEngine = audio
        hapticEngine = haptic

        val loop = EmulatorLoop(
            emulator = emu,
            displayBridge = bridge,
            onFrame = { bitmap, vram ->
                inputOverlayAnimator?.applyOverlay(bitmap)
                this@DigimonGlyphToyService.glyphRenderer?.pushFrame(bitmap)
                val now = SystemClock.uptimeMillis()
                val shouldPublishFramePreview =
                    debugTelemetryEnabled || standaloneMode ||
                    FrameDebugState.isObserved(FRAME_PREVIEW_OBSERVER_WINDOW_MS, now) ||
                    WidgetFramePusher.isRunning
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
        loop.setStepGateEnabled(false)
        lastAppliedTimingMode = initialMode
        lastAppliedStepGateEnabled = false
        emulatorLoop = loop

        loop.onVirtualTick = { totalNs ->
            if (battleConnected) {
                val rawUs = (totalNs / 1000).toLong()
                virtualDCom?.updateVirtualTime(rawUs)
            }
        }

        loop.start()
        applyTimingSettingIfChanged(force = true)

        val overlay = InputOverlayAnimator()
        inputOverlayAnimator = overlay

        val input = InputController(this)
        input.attach(emu)
        input.onUserInteraction = {
            handleUserInteractionForTimingBoost()
        }
        input.onPinSet = { port, pin, level ->
            if (port == "K0" && level == 0) {
                onLocalBattleButtonDown(pin)
            }
            runOnEmulatorAsync { core -> core.pinSet(port, pin, level) }
        }
        input.onPinRelease = { port, pin ->
            if (port == "K0") {
                onLocalBattleButtonUp(pin)
            }
            runOnEmulatorAsync { core -> core.pinRelease(port, pin) }
        }
        input.onInputRateModeChanged = { isActive ->
            overlay.onStateChanged(isActive)
        }
        input.setDebugEnabled(debugTelemetryEnabled)
        input.start()
        inputController = input

        // Start autosave
        mainHandler.postDelayed(autosaveRunnable, AUTOSAVE_INTERVAL_MS)

        // Start widget frame push if widgets exist
        if (standaloneMode || DigimonWidgetProvider.getWidgetIds(this).isNotEmpty()) {
            WidgetFramePusher.ensureRunning(this)
        }

        Log.d(TAG, "Emulator started with ROM: $romName standaloneMode=$standaloneMode")
    }

    private fun stopEmulator() {
        WidgetFramePusher.stop()
        mainHandler.removeCallbacks(autosaveRunnable)
        emulatorLoop?.stop()
        emulatorLoop = null
        emulator?.onBuzzerChange = null
        emulator?.onSerialTx = null
        emulator?.onPortDriveChange = null
        emulator?.onIoAccess = null
        audioEngine?.stop()
        audioEngine = null
        hapticEngine?.stop()
        hapticEngine = null
        inputController?.stop()
        inputController = null
        inputOverlayAnimator = null
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
        lastAppliedClockCorrectionOverride = null
        lastAppliedStepGateEnabled = false
        battleAssistActiveButtons = 0
        battleAssistLowUntilMs = 0L
        battleAssistLowApplied = false
        battleAssistBurstToken++
        battleAssistWaveUntilMs = 0L
        battleConnectedAtMs = 0L
        mainHandler.removeCallbacks(battleAssistReleaseRunnable)
        clearRemoteP2EdgeQueue()
        localP2EdgeSeq = 0L
        localP2LastSentValue = null
        remoteP2LastAppliedSeq = -1L
        remoteP2LegacySeq = 0L
        battleRdWriteMask = 0
        battleRdWriteValue = 0
        battleRdBridgeLastTxByte = -1
        battleRdBridgeLastTxAtMs = 0L
        remoteLinkK0Pin3Low = false
        virtualDCom?.reset()
        virtualDCom = null
        ioTraceCount = 0L
        ioTraceWindowStartMs = 0L
        ioTraceLastValues.clear()
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
        val isBattleCommand = when (cmd.type) {
            EmulatorCommandBus.CMD_BATTLE_START_HOST,
            EmulatorCommandBus.CMD_BATTLE_START_JOIN,
            EmulatorCommandBus.CMD_BATTLE_STOP,
            EmulatorCommandBus.CMD_BATTLE_PING,
            EmulatorCommandBus.CMD_BATTLE_STEP_PULSE -> true
            else -> false
        }
        if (isBattleCommand) {
            Log.i(TAG, "Process command id=${cmd.id} type=${cmd.type} arg=${cmd.arg}")
        } else {
            Log.d(TAG, "Process command id=${cmd.id} type=${cmd.type} arg=${cmd.arg}")
        }
        val status = when (cmd.type) {
            EmulatorCommandBus.CMD_SAVE_AUTOSAVE -> saveAutosaveNow()
            EmulatorCommandBus.CMD_LOAD_AUTOSAVE -> loadAutosaveNow()
            EmulatorCommandBus.CMD_SAVE_SLOT -> saveSlot(cmd.arg)
            EmulatorCommandBus.CMD_LOAD_SLOT -> loadSlot(cmd.arg)
            EmulatorCommandBus.CMD_RESTART -> restartEmulator()
            EmulatorCommandBus.CMD_FULL_RESET -> fullResetEmulator()
            EmulatorCommandBus.CMD_PRESS_COMBO -> pressCombo(cmd.arg)
            EmulatorCommandBus.CMD_BUTTON_PRESS -> {
                pressWidgetButton(cmd.arg)
                "button pressed"
            }
            EmulatorCommandBus.CMD_REFRESH_SETTINGS -> {
                DisplayRenderSettings.init(this)
                EmulatorAudioSettings.init(this)
                EmulatorDebugSettings.init(this)
                EmulatorTimingSettings.init(this)
                BattleTransportSettings.init(this)
                refreshBattleTransportIfNeeded()
                applyAudioHapticSettingsIfChanged(force = true)
                applyDebugSettingIfChanged(force = true)
                applyTimingSettingIfChanged(force = true)
                "settings refreshed"
            }
            EmulatorCommandBus.CMD_BATTLE_START_HOST -> startBattleHost()
            EmulatorCommandBus.CMD_BATTLE_START_JOIN -> startBattleJoin()
            EmulatorCommandBus.CMD_BATTLE_STOP -> stopBattleLink()
            EmulatorCommandBus.CMD_BATTLE_PING -> sendBattlePing()
            EmulatorCommandBus.CMD_BATTLE_STEP_PULSE -> pulseBattleStepManual(cmd.arg)
            else -> "unknown command: ${cmd.type}"
        }
        if (isBattleCommand) {
            Log.i(TAG, "Command ${cmd.id} result: $status")
        } else {
            Log.d(TAG, "Command ${cmd.id} result: $status")
        }
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

    private fun ensureBattleTransport(): BattleTransport {
        val existing = battleTransport
        if (existing != null) return existing
        BattleTransportSettings.init(this)
        val created = createBattleTransport()
        battleTransport = created
        return created
    }

    private fun startBattleHost(): String {
        BattleTransportSettings.init(this)
        refreshBattleTransportIfNeeded()
        val manager = ensureBattleTransport()
        Log.i(TAG, "Battle start requested: host")
        return manager.startHost()
    }

    private fun startBattleJoin(): String {
        BattleTransportSettings.init(this)
        refreshBattleTransportIfNeeded()
        val manager = ensureBattleTransport()
        Log.i(TAG, "Battle start requested: join")
        return manager.startJoin()
    }

    private fun stopBattleLink(): String {
        val manager = battleTransport ?: return "battle link stopped"
        battleConnected = false
        battleConnectedAtMs = 0L
        battleAssistActiveButtons = 0
        battleAssistLowUntilMs = 0L
        battleAssistLowApplied = false
        battleAssistBurstToken++
        battleAssistWaveUntilMs = 0L
        remoteP2LowSinceMs = 0L
        remoteP2ReleaseToken++
        remoteLinkK0Pin3Low = false
        mainHandler.removeCallbacks(battleAssistReleaseRunnable)
        clearRemoteP2EdgeQueue()
        localP2EdgeSeq = 0L
        localP2LastSentValue = null
        remoteP2LastAppliedSeq = -1L
        remoteP2LegacySeq = 0L
        battleRdBridgeLastTxByte = -1
        battleRdBridgeLastTxAtMs = 0L
        runOnEmulatorAsync { core ->
            core.applyLinkedPortDrive("P2", null)
            if (BATTLE_MIRROR_P2_TO_K0_PIN3) {
                core.pinRelease("K0", 3)
            }
        }
        applyTimingSettingIfChanged(force = true)
        return manager.stop()
    }

    private fun sendBattlePing(): String {
        val manager = battleTransport ?: return "battle ping failed: not connected"
        return manager.sendPing()
    }

    private fun pulseBattleStepManual(argMs: Int): String {
        if (!battleConnected) return "battle step pulse ignored: link not connected"
        if (!EmulatorTimingSettings.isBattleStepModeEnabled()) {
            return "battle step pulse ignored: step mode disabled"
        }
        val base = EmulatorTimingSettings.getBattleStepSliceMs()
        val pulseMs = if (argMs <= 0) base else argMs.coerceIn(1, 1_000)
        emulatorLoop?.pulseStepBudget(pulseMs)
        Log.d(TAG, "Battle step manual pulse +${pulseMs}ms")
        return "battle step pulse +${pulseMs}ms"
    }

    private fun pulseBattleStepBudget(reason: String, bonusMs: Int = 0) {
        if (!battleConnected) return
        if (!EmulatorTimingSettings.isBattleStepModeEnabled()) return
        val base = EmulatorTimingSettings.getBattleStepSliceMs()
        val pulseMs = (base + bonusMs).coerceIn(1, 1_000)
        emulatorLoop?.pulseStepBudget(pulseMs)
        if (debugTelemetryEnabled) {
            Log.d(TAG, "Battle step pulse +${pulseMs}ms reason=$reason")
        }
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
        val stepModeEnabled = EmulatorTimingSettings.isBattleStepModeEnabled()
        val stepSliceMs = EmulatorTimingSettings.getBattleStepSliceMs()
        val now = SystemClock.uptimeMillis()
        val boostActive = !exactEnabled && now < interactionBoostUntilMs
        val battleTimingLock = battleConnected
        val battleStepGateActive = battleConnected && stepModeEnabled
        val desiredMode =
            if (battleTimingLock || exactEnabled || boostActive) EmulatorLoop.TimingMode.EXACT
            else EmulatorLoop.TimingMode.POWER_SAVE
        val correctionOverride = if (battleTimingLock) BATTLE_FORCED_CORRECTION_FACTOR else null
        if (!force &&
            exactEnabled == lastExactTimingEnabled &&
            desiredMode == lastAppliedTimingMode &&
            correctionOverride == lastAppliedClockCorrectionOverride &&
            battleStepGateActive == lastAppliedStepGateEnabled
        ) return
        lastExactTimingEnabled = exactEnabled
        if (force || desiredMode != lastAppliedTimingMode) {
            emulatorLoop?.setTimingMode(desiredMode)
        }
        if (force || battleStepGateActive != lastAppliedStepGateEnabled) {
            emulatorLoop?.setStepGateEnabled(battleStepGateActive)
            lastAppliedStepGateEnabled = battleStepGateActive
            if (battleStepGateActive) {
                emulatorLoop?.pulseStepBudget((stepSliceMs + BATTLE_STEP_CONNECT_PRIME_MS).coerceAtMost(1_000))
            }
        }
        if (force || correctionOverride != lastAppliedClockCorrectionOverride) {
            emulatorLoop?.setClockCorrectionOverride(correctionOverride)
            lastAppliedClockCorrectionOverride = correctionOverride
        }
        if (desiredMode == EmulatorLoop.TimingMode.EXACT) {
            acquireCpuWakeLock()
        } else {
            releaseCpuWakeLock()
        }
        lastAppliedTimingMode = desiredMode
        Log.d(
            TAG,
            "Timing mode set to $desiredMode " +
                "(userExact=$exactEnabled boostActive=$boostActive battleLock=$battleTimingLock " +
                "stepGate=$battleStepGateActive stepSliceMs=$stepSliceMs " +
                "correctionOverride=${correctionOverride ?: "none"})"
        )
    }

    private fun handleUserInteractionForTimingBoost() {
        if (EmulatorTimingSettings.isExactTimingEnabled()) return
        interactionBoostUntilMs = SystemClock.uptimeMillis() + INTERACTION_TIMING_BOOST_MS
        if (lastAppliedTimingMode != EmulatorLoop.TimingMode.EXACT) {
            applyTimingSettingIfChanged(force = true)
        }
    }

    private fun tryStartForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID, "Digimon V3 Emulator",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running the Digimon V3 virtual pet"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DigimonGlyphToyService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIF_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setSmallIcon(R.drawable.ic_digimon_preview)
            .setContentTitle("Digimon V3")
            .setContentText("Virtual pet is running")
            .setContentIntent(openIntent)
            .addAction(Notification.Action.Builder(
                null, "Stop", stopIntent
            ).build())
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIF_ID, notif)
        } catch (e: Exception) {
            // On Nothing Phone the service is already running as a Glyph Toy (bound service).
            // startForeground() is not needed/allowed in that case — emulator runs fine without it.
            Log.w(TAG, "startForeground skipped: ${e.message}")
        }
    }

    private fun pressWidgetButton(button: Int) {
        // K0 port pins: A=2, B=1, C=0 (matches InputController)
        val pin = when (button) {
            EmulatorCommandBus.BUTTON_A -> 2
            EmulatorCommandBus.BUTTON_B -> 1
            EmulatorCommandBus.BUTTON_C -> 0
            else -> return
        }
        val holdMs = if (battleConnected && button == EmulatorCommandBus.BUTTON_B) {
            BATTLE_LINK_B_HOLD_MS
        } else {
            DEFAULT_BUTTON_HOLD_MS
        }
        onLocalBattleButtonDown(pin)
        runOnEmulatorAsync { core -> core.pinSet("K0", pin, 0) }
        mainHandler.postDelayed({
            onLocalBattleButtonUp(pin)
            runOnEmulatorAsync { core -> core.pinRelease("K0", pin) }
        }, holdMs)
    }

    private fun onLocalBattleButtonDown(pin: Int) {
        if (!battleConnected) return
        if (pin != 1) return
        pulseBattleStepBudget("local_b_down", BATTLE_STEP_BUTTON_BONUS_MS)
        Log.d(TAG, "Battle local B down assistEnabled=$BATTLE_ASSIST_ENABLED")
        if (!BATTLE_ASSIST_ENABLED) return
        if (battleAssistLowApplied) return
        val sent = sendBattleP2Edge(BATTLE_LINK_LOW_MASK, reason = "assist_b_down")
        battleAssistLowApplied = true
        Log.d(TAG, "Battle assist tx P2=$BATTLE_LINK_LOW_MASK (b_down, sent=$sent)")
        startBattleAssistWaveFromLocalButton()
    }

    private fun onLocalBattleButtonUp(pin: Int) {
        if (!battleConnected) return
        if (pin != 1) return
        pulseBattleStepBudget("local_b_up", BATTLE_STEP_BUTTON_BONUS_MS)
        Log.d(TAG, "Battle local B up assistEnabled=$BATTLE_ASSIST_ENABLED")
        if (!BATTLE_ASSIST_ENABLED) return
        if (!battleAssistLowApplied) return
        val sent = sendBattleP2Edge(BATTLE_LINK_HIGH_MASK, reason = "assist_b_up")
        battleAssistLowApplied = false
        Log.d(TAG, "Battle assist tx P2=$BATTLE_LINK_HIGH_MASK (b_up, sent=$sent)")
    }

    private fun resetBattleEdgeState() {
        clearRemoteP2EdgeQueue()
        localP2EdgeSeq = 0L
        localP2LastSentValue = null
        remoteP2LastAppliedSeq = -1L
        remoteP2LegacySeq = 0L
        battleRdWriteMask = 0
        battleRdWriteValue = 0
        battleRdBridgeLastTxByte = -1
        battleRdBridgeLastTxAtMs = 0L
    }

    private fun clearRemoteP2EdgeQueue() {
        remoteP2LastAppliedSeq = -1L
    }

    private fun sendBattleP2Edge(value: Int?, force: Boolean = false, reason: String): Boolean {
        if (!battleConnected) return false
        val normalized = value?.and(0xF)
        if (!force && normalized == localP2LastSentValue) {
            return false
        }
        val seq = ++localP2EdgeSeq
        val sourceUptimeMs = SystemClock.uptimeMillis()
        val sent = battleTransport?.sendPinEdge(
            port = "P2",
            value = normalized,
            seq = seq,
            sourceUptimeMs = sourceUptimeMs
        ) == true
        if (sent) {
            localP2LastSentValue = normalized
            pulseBattleStepBudget("pin_edge_tx:$reason", BATTLE_STEP_LINK_EVENT_BONUS_MS)
        }
        Log.d(
            TAG,
            "Battle pin edge tx P2=${normalized ?: -1} seq=$seq src=$sourceUptimeMs reason=$reason sent=$sent"
        )
        return sent
    }

    private fun applyRemoteP2Edge(seq: Long, value: Int?) {
        if (!battleConnected) return
        if (seq <= remoteP2LastAppliedSeq) {
            return
        }
        pulseBattleStepBudget("pin_edge_apply", BATTLE_STEP_LINK_EVENT_BONUS_MS)
        remoteP2LastAppliedSeq = seq
        applyRemoteP2DriveWithMinLow(value)
    }

    private fun startBattleAssistWaveFromLocalButton() {
        if (!BATTLE_ASSIST_ENABLED) return
        if (!BATTLE_WAVE_ENABLED) return
        if (!battleConnected) return
        val now = SystemClock.uptimeMillis()
        if (now < battleAssistWaveUntilMs - BATTLE_WAVE_SETTLE_MS) return
        val stepMs = BATTLE_WAVE_STEP_MS
        val totalMs = BATTLE_WAVE_TOTAL_MS
        val announced = battleTransport?.sendWaveStart(stepMs, totalMs) == true
        Log.d(TAG, "Battle assist tx wave_start step=$stepMs total=$totalMs sent=$announced")
        startBattleAssistWave(stepMs, totalMs, source = "local")
    }

    private fun onRemoteBattleWaveStart(body: String?) {
        if (!BATTLE_ASSIST_ENABLED) return
        if (!BATTLE_WAVE_ENABLED) return
        if (!battleConnected) return
        val now = SystemClock.uptimeMillis()
        if (now < battleAssistWaveUntilMs - (BATTLE_WAVE_SETTLE_MS / 2L)) {
            Log.d(TAG, "Battle assist remote wave ignored (local wave active until=$battleAssistWaveUntilMs)")
            return
        }
        val (stepMs, totalMs) = parseBattleWaveSpec(body)
        startBattleAssistWave(stepMs, totalMs, source = "remote")
    }

    private fun parseBattleWaveSpec(body: String?): Pair<Int, Int> {
        val defaultStep = BATTLE_WAVE_STEP_MS
        val defaultTotal = BATTLE_WAVE_TOTAL_MS
        if (body.isNullOrBlank()) return defaultStep to defaultTotal
        val idx = body.indexOf(':')
        if (idx <= 0 || idx >= body.lastIndex) return defaultStep to defaultTotal
        val step = body.substring(0, idx).toIntOrNull() ?: defaultStep
        val total = body.substring(idx + 1).toIntOrNull() ?: defaultTotal
        return step to total
    }

    private fun startBattleAssistWave(stepMs: Int, totalMs: Int, source: String) {
        if (!BATTLE_ASSIST_ENABLED) return
        if (!battleConnected) return
        val safeStepMs = stepMs.coerceIn(4, 50)
        val safeTotalMs = totalMs.coerceIn(safeStepMs * 6, 8_000)
        val stepCount = (safeTotalMs / safeStepMs).coerceAtLeast(6)
        val token = ++battleAssistBurstToken
        battleAssistWaveUntilMs = SystemClock.uptimeMillis() + safeTotalMs + BATTLE_WAVE_SETTLE_MS
        Log.d(
            TAG,
            "Battle assist wave start source=$source stepMs=$safeStepMs totalMs=$safeTotalMs steps=$stepCount token=$token"
        )
        for (step in 0 until stepCount) {
            val low = (step % 2 == 0)
            val delayMs = step.toLong() * safeStepMs
            mainHandler.postDelayed({
                if (!battleConnected || token != battleAssistBurstToken) return@postDelayed
                applySyntheticRemoteLinkLevel(low, token)
                if (step % 32 == 0 || step == stepCount - 1) {
                    Log.d(TAG, "Battle assist wave step=$step low=$low token=$token")
                }
            }, delayMs)
        }
        mainHandler.postDelayed({
            if (!battleConnected || token != battleAssistBurstToken) return@postDelayed
            applySyntheticRemoteLinkLevel(false, token)
            Log.d(TAG, "Battle assist wave settle-high token=$token")
        }, (safeTotalMs + safeStepMs).toLong())
    }

    private fun applySyntheticRemoteLinkLevel(low: Boolean, token: Int) {
        if (!battleConnected || token != battleAssistBurstToken) return
        val value = if (low) BATTLE_LINK_LOW_MASK else BATTLE_LINK_HIGH_MASK
        runOnEmulatorAsync { core ->
            core.applyLinkedPortDrive("P2", value)
            applyRemoteK0Pin3Mirror(core, low)
        }
    }

    private fun applyRemoteP2DriveWithMinLow(value: Int?) {
        val normalized = value?.and(0xF)
        val linkLow = normalized != null && normalized != BATTLE_LINK_HIGH_MASK
        val now = SystemClock.uptimeMillis()
        val inHandshakeWindow =
            battleConnectedAtMs > 0L &&
                (now - battleConnectedAtMs) <= BATTLE_HANDSHAKE_MIN_LOW_WINDOW_MS
        val minLowMs =
            if (inHandshakeWindow) {
                BATTLE_HANDSHAKE_MIN_LOW_MS.coerceAtLeast(BATTLE_REMOTE_P2_MIN_LOW_MS)
            } else {
                BATTLE_REMOTE_P2_MIN_LOW_MS
            }
        if (minLowMs <= 0L) {
            remoteP2LowSinceMs = 0L
            remoteP2ReleaseToken++
            runOnEmulatorAsync { core ->
                core.applyLinkedPortDrive("P2", normalized)
                applyRemoteK0Pin3Mirror(core, linkLow)
            }
            return
        }
        if (now < battleAssistWaveUntilMs) {
            remoteP2LowSinceMs = 0L
            remoteP2ReleaseToken++
            runOnEmulatorAsync { core ->
                core.applyLinkedPortDrive("P2", normalized)
                applyRemoteK0Pin3Mirror(core, linkLow)
            }
            return
        }
        val isLow = normalized != null && normalized != 0xF
        if (isLow) {
            remoteP2LowSinceMs = now
            remoteP2ReleaseToken++
            runOnEmulatorAsync { core ->
                core.applyLinkedPortDrive("P2", normalized)
                applyRemoteK0Pin3Mirror(core, true)
            }
            return
        }
        val hadLow = remoteP2LowSinceMs > 0L
        val elapsedLowMs = if (hadLow) now - remoteP2LowSinceMs else Long.MAX_VALUE
        if (!hadLow || elapsedLowMs >= minLowMs) {
            remoteP2LowSinceMs = 0L
            remoteP2ReleaseToken++
            runOnEmulatorAsync { core ->
                core.applyLinkedPortDrive("P2", normalized)
                applyRemoteK0Pin3Mirror(core, linkLow)
            }
            return
        }
        val delayMs = (minLowMs - elapsedLowMs).coerceAtLeast(0L)
        val token = ++remoteP2ReleaseToken
        mainHandler.postDelayed({
            if (token != remoteP2ReleaseToken) return@postDelayed
            remoteP2LowSinceMs = 0L
            runOnEmulatorAsync { core ->
                core.applyLinkedPortDrive("P2", normalized)
                applyRemoteK0Pin3Mirror(core, linkLow)
            }
            Log.d(
                TAG,
                "Battle pin rx P2 delayed release after ${minLowMs}ms (handshakeWindow=$inHandshakeWindow)"
            )
        }, delayMs)
    }

    private fun applyRemoteK0Pin3Mirror(core: E0C6200, low: Boolean) {
        if (!BATTLE_MIRROR_P2_TO_K0_PIN3) return
        if (remoteLinkK0Pin3Low == low) return
        remoteLinkK0Pin3Low = low
        if (low) {
            core.pinSet("K0", 3, 0)
        } else {
            core.pinRelease("K0", 3)
        }
        Log.d(TAG, "Battle mirror K0.3=${if (low) 0 else 1} from P2")
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
