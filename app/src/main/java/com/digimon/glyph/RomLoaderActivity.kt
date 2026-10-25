package com.digimon.glyph

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.view.View
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.RadioGroup
import android.widget.RadioButton
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.digimon.glyph.battle.BattleStateStore
import com.digimon.glyph.battle.BattleTransportSettings
import com.digimon.glyph.battle.BattleTransportType
import com.digimon.glyph.battle.SimulationPreset
import com.digimon.glyph.emulator.DigimonAttentionSettings
import com.digimon.glyph.emulator.DigimonDatabase
import com.digimon.glyph.emulator.DigimonDndScheduler
import com.digimon.glyph.emulator.DigimonDndSettings
import com.digimon.glyph.emulator.EmulatorAudioSettings
import com.digimon.glyph.emulator.EmulatorCommandBus
import com.digimon.glyph.emulator.EmulatorDebugSettings
import com.digimon.glyph.emulator.EmulatorTimingSettings
import com.digimon.glyph.emulator.FrameDebugState
import com.digimon.glyph.emulator.EmulatorStatus
import com.digimon.glyph.emulator.DisplayRenderSettings
import com.digimon.glyph.emulator.StateManager
import com.digimon.glyph.input.InputDebugState
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Launcher activity for loading Digimon ROM files.
 * Supports both raw .bin ROMs and .zip archives containing .bin files.
 * After loading, the ROM is saved to internal storage for the Glyph Toy service to use.
 */
class RomLoaderActivity : AppCompatActivity() {
    private val exchangeFolderPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                com.digimon.glyph.sync.ExchangeFolder(this).setTree(uri)
            } catch (_: SecurityException) {
                Toast.makeText(this, "Choose a folder with persistent read and write access", Toast.LENGTH_LONG).show()
            }
        }
    }
    private var updatingRenderSwitch = false
    private var walkingButton: Button? = null
    private val nfcCardScanner by lazy {
        com.digimon.glyph.input.NfcCardScanner(this) { revision ->
            startService(Intent(this, DigimonGlyphToyService::class.java)
                .setAction(DigimonGlyphToyService.ACTION_NFC_CARD)
                .putExtra(DigimonGlyphToyService.EXTRA_NFC_REVISION, revision))
        }
    }
    private var dpowerMenu: com.digimon.glyph.ui.DPowerMenu? = null
    private val walkingPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        com.digimon.glyph.input.WalkingInput.setEnabled(this, granted)
        updateWalkingButton()
        if (!granted) Toast.makeText(this, R.string.walking_permission_denied, Toast.LENGTH_LONG).show()
    }
    private fun updateWalkingButton() {
        walkingButton?.setText(if (com.digimon.glyph.input.WalkingInput.enabled(this) && com.digimon.glyph.input.WalkingInput.permitted(this))
            R.string.walking_disable else R.string.walking_enable)
    }


    private lateinit var statusText: TextView
    private lateinit var autosaveText: TextView
    private lateinit var commandStatusText: TextView
    private lateinit var debugText: TextView
    private lateinit var ramDebugText: TextView
    private lateinit var indicatorA: TextView
    private lateinit var indicatorB: TextView
    private lateinit var indicatorC: TextView
    private lateinit var digimonStatsText: TextView
    private lateinit var slot1Text: TextView
    private lateinit var slot2Text: TextView
    private lateinit var slot3Text: TextView
    private lateinit var battleStatusText: TextView
    private lateinit var battlePeerName: TextView
    private lateinit var battleBadge: TextView
    private lateinit var statRomValue: TextView
    private lateinit var statEmuValue: TextView
    private lateinit var statEmuLabel: TextView
    private var lastFrameUpdateMs: Long = -1L
    private var lastAckId: Long = 0L
    private var lastAckText: String = "-"
    private var pendingNearbyAction: (() -> Unit)? = null
    private var attentionNotificationsSwitch: Switch? = null
    private var ramWatchProfile = RamWatchProfile.CORE
    private var baselineRam: IntArray? = null
    private var baselineVram: IntArray? = null
    private var baselineAtMs: Long = 0L
    private var baselineRomLabel: String? = null
    private val stateManager by lazy { StateManager(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastStatusRevision = -1L
    private var importWorker: Thread? = null
    private var importRequest = 0L
    private val debugRefresh = object : Runnable {
        override fun run() {
            updateStatus()
            renderDebugState()
            mainHandler.postDelayed(this, if (EmulatorDebugSettings.isDebugEnabled()) 120L else 500L)
        }
    }

    private val pickRom = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            loadRomFromUri(uri)
        }
    }

    private val requestNearbyPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val action = pendingNearbyAction
        pendingNearbyAction = null
        if (hasNearbyPermissions()) {
            action?.invoke()
        } else {
            Toast.makeText(this, "Nearby permissions are required for battle mode", Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val enabled = granted || hasNotificationPermission()
        DigimonAttentionSettings.setAttentionNotificationsEnabled(this, enabled)
        attentionNotificationsSwitch?.isChecked = enabled
        if (!enabled) {
            Toast.makeText(this, "Notification permission is required for attention alerts", Toast.LENGTH_LONG).show()
        }
    }

    private enum class RamWatchProfile(
        val label: String,
        val addresses: IntArray,
        val rowDump: Boolean = false
    ) {
        CORE(
            label = "CORE",
            addresses = intArrayOf(0x0B, 0x31, 0x34, 0x35, 0x3D, 0x3E, 0x40, 0x41, 0x56, 0x57, 0x62)
        ),
        CARE(
            label = "CARE",
            addresses = intArrayOf(0x31, 0x40, 0x41, 0x46, 0x47, 0x58, 0x59, 0x5A, 0x5B, 0x5C)
        ),
        TRAIN(
            label = "TRAIN",
            addresses = intArrayOf(0x29, 0x2A, 0x2B, 0x31, 0x34, 0x35, 0x3D, 0x9D, 0x9E, 0x9F)
        ),
        BATTLE(
            label = "BATTLE",
            addresses = ((0x20..0x3F).toList() + (0x60..0x7F).toList()).toIntArray(),
            rowDump = true
        ),
        PAGE0(
            label = "PAGE0",
            addresses = (0x00..0x9F).toList().toIntArray(),
            rowDump = true
        );

        fun next(): RamWatchProfile {
            val values = values()
            return values[(ordinal + 1) % values.size]
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRenderSettings.init(this)
        EmulatorAudioSettings.init(this)
        EmulatorDebugSettings.init(this)
        EmulatorTimingSettings.init(this)
        DigimonAttentionSettings.init(this)
        DigimonDndSettings.init(this)
        BattleStateStore.init(this)
        BattleTransportSettings.init(this)

        setContentView(R.layout.activity_rom_loader)

        val teko = Typeface.createFromAsset(assets, "fonts/Teko.ttf")
        val shareTech = Typeface.createFromAsset(assets, "fonts/ShareTechMono-Regular.ttf")

        val mainTitle = findViewById<TextView>(R.id.main_title)
        statusText = findViewById(R.id.statusText)
        findViewById<Button>(R.id.btn_connections).setOnClickListener {
            com.digimon.glyph.sync.ConnectionsMenu.show(this) { exchangeFolderPicker.launch(com.digimon.glyph.sync.ExchangeFolder(this).tree) }
        }
        val btnPickRom = findViewById<Button>(R.id.btn_pick_rom)
        findViewById<Button>(R.id.btn_dpower_controls).setOnClickListener { showDPowerControls() }
        findViewById<Button>(R.id.btn_cancel_import).setOnClickListener { cancelImport() }
        val btnStartEmu = findViewById<Button>(R.id.btn_start_emu)
        val btnStopEmu = findViewById<Button>(R.id.btn_stop_emu)
        digimonStatsText = findViewById(R.id.digimonStatsText)
        
        val switchZoom = findViewById<Switch>(R.id.switch_zoom)
        val switchAudio = findViewById<Switch>(R.id.switch_audio)
        val switchPowerSave = findViewById<Switch>(R.id.switch_power_save)
        val switchExactTiming = findViewById<Switch>(R.id.switch_exact_timing)
        val switchHaptic = findViewById<Switch>(R.id.switch_haptic)

        val btnToggleSave = findViewById<Button>(R.id.btn_toggle_save)
        val layoutSaveTools = findViewById<LinearLayout>(R.id.layout_save_tools)
        autosaveText = findViewById(R.id.autosaveText)
        val btnSaveAuto = findViewById<Button>(R.id.btn_save_auto)
        val btnLoadAuto = findViewById<Button>(R.id.btn_load_auto)
        
        val slotContainer = findViewById<LinearLayout>(R.id.slot_container)
        slot1Text = addSlotRow(slotContainer, 1)
        slot2Text = addSlotRow(slotContainer, 2)
        slot3Text = addSlotRow(slotContainer, 3)

        val btnRestart = findViewById<Button>(R.id.btn_restart)
        val btnFullReset = findViewById<Button>(R.id.btn_full_reset)
        
        val btnComboAb = findViewById<Button>(R.id.btn_combo_ab)
        val btnComboAc = findViewById<Button>(R.id.btn_combo_ac)
        val btnComboBc = findViewById<Button>(R.id.btn_combo_bc)

        val btnToggleBattle = findViewById<Button>(R.id.btn_toggle_battle)
        val layoutBattle = findViewById<LinearLayout>(R.id.layout_battle)
        val rgTransport = findViewById<RadioGroup>(R.id.rg_transport)
        val rbNearby = findViewById<RadioButton>(R.id.rb_nearby)
        val rbInternet = findViewById<RadioButton>(R.id.rb_internet)
        val rbSim = findViewById<RadioButton>(R.id.rb_sim)
        
        val rgPreset = findViewById<RadioGroup>(R.id.rg_preset)
        val rbPureEcho = findViewById<RadioButton>(R.id.rb_pure_echo)
        val rbXor = findViewById<RadioButton>(R.id.rb_xor)
        val rbGlobal = findViewById<RadioButton>(R.id.rb_global)

        val inputTamerName = findViewById<EditText>(R.id.input_tamer_name)
        val btnTutorial = findViewById<Button>(R.id.btn_tutorial)

        val btnBattleHost = findViewById<Button>(R.id.btn_battle_host)
        val btnBattleJoin = findViewById<Button>(R.id.btn_battle_join)
        val btnBattleStop = findViewById<Button>(R.id.btn_battle_stop)
        battleStatusText = findViewById(R.id.battleStatusText)
        battlePeerName = findViewById(R.id.battlePeerName)
        battleBadge = findViewById(R.id.battleBadge)
        statRomValue = findViewById(R.id.stat_rom_value)
        statEmuValue = findViewById(R.id.stat_emu_value)
        statEmuLabel = findViewById(R.id.stat_emu_label)

        val statRomCard = findViewById<android.widget.FrameLayout>(R.id.stat_rom_card)
        val statEmuCard = findViewById<android.widget.FrameLayout>(R.id.stat_emu_card)
        val btnToggleAdvanced = findViewById<Button>(R.id.btn_toggle_advanced)
        val layoutAdvanced = findViewById<LinearLayout>(R.id.layout_advanced)
        val clockSyncInfo = findViewById<TextView>(R.id.clock_sync_info)
        findViewById<Button>(R.id.btn_sync_firmware_clock).setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_SYNC_FIRMWARE_CLOCK, 0, "Set Digimon clock to phone time")
        }
        findViewById<Button>(R.id.btn_set_firmware_clock).setOnClickListener {
            val now = java.util.Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                sendCommand(EmulatorCommandBus.CMD_SET_FIRMWARE_CLOCK, hour * 3600 + minute * 60,
                    "Set Digimon clock")
            }, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true)
                .apply { setTitle("Classic Digimon clock"); show() }
        }
        commandStatusText = findViewById(R.id.commandStatusText)
        val btnShareDebug = findViewById<Button>(R.id.btn_share_debug)
        val btnExportStates = findViewById<Button>(R.id.btn_export_states)
        val btnRamProfile = findViewById<Button>(R.id.btn_ram_profile)
        val btnRamBaseline = findViewById<Button>(R.id.btn_ram_baseline)
        val btnRamClearBaseline = findViewById<Button>(R.id.btn_ram_clear_baseline)
        val btnExportRawSnapshot = findViewById<Button>(R.id.btn_export_raw_snapshot)
        ramDebugText = findViewById(R.id.ramDebugText)
        val switchAttentionNotifications = findViewById<Switch>(R.id.switch_attention_notifications)
        val switchShowCareMistakes = findViewById<Switch>(R.id.switch_show_care_mistakes)
        val switchDndEnabled = findViewById<Switch>(R.id.switch_dnd_enabled)
        val switchDndFreezeMode = findViewById<Switch>(R.id.switch_dnd_freeze_mode)
        val btnDndStartTime = findViewById<Button>(R.id.btn_dnd_start_time)
        val btnDndEndTime = findViewById<Button>(R.id.btn_dnd_end_time)
        val switchDndAutoResume = findViewById<Switch>(R.id.switch_dnd_auto_resume)
        val switchDndSuppressNotifications = findViewById<Switch>(R.id.switch_dnd_suppress_notifications)
        val textDndStatus = findViewById<TextView>(R.id.text_dnd_status)
        val switchDebugTelemetry = findViewById<Switch>(R.id.switch_debug_telemetry)
        attentionNotificationsSwitch = switchAttentionNotifications
        
        val indicatorRow = findViewById<LinearLayout>(R.id.indicatorRow)
        indicatorA = buildIndicator("A")
        indicatorB = buildIndicator("B")
        indicatorC = buildIndicator("C")
        indicatorRow.addView(indicatorA)
        indicatorRow.addView(indicatorB)
        indicatorRow.addView(indicatorC)

        debugText = TextView(this).apply {
            textSize = 13f
            typeface = shareTech
            setPadding(0, 0, 0, 16)
            setTextColor(Color.WHITE)
        }
        layoutAdvanced.addView(debugText)

        // Apply Fonts — also apply Teko to shadow layers so they wrap identically to main title
        val titleShadowCyan = findViewById<TextView>(R.id.title_shadow_cyan)
        val titleShadowPink = findViewById<TextView>(R.id.title_shadow_pink)
        mainTitle.typeface = teko
        titleShadowCyan.typeface = teko
        titleShadowPink.typeface = teko
        btnPickRom.typeface = teko
        btnStartEmu.typeface = teko
        btnStopEmu.typeface = teko
        btnToggleSave.typeface = teko
        btnToggleBattle.typeface = teko
        btnToggleAdvanced.typeface = teko
        btnTutorial.typeface = teko
        btnDndStartTime.typeface = teko
        btnDndEndTime.typeface = teko
        statRomValue.typeface = teko
        statEmuValue.typeface = teko

        val shareTechViews = listOf(
            statusText, switchZoom, switchAudio, switchExactTiming, switchPowerSave, switchHaptic,
            autosaveText, btnSaveAuto, btnLoadAuto, btnRestart, btnFullReset,
            btnComboAb, btnComboAc, btnComboBc,
            rbNearby, rbInternet, rbSim, rbPureEcho, rbXor, rbGlobal,
            btnBattleHost, btnBattleJoin, btnBattleStop, battleStatusText,
            battlePeerName, battleBadge, statEmuLabel,
            clockSyncInfo, commandStatusText, btnShareDebug, btnExportStates,
            btnRamProfile, btnRamBaseline, btnRamClearBaseline, btnExportRawSnapshot, ramDebugText,
            switchAttentionNotifications, switchShowCareMistakes,
            switchDndEnabled, switchDndFreezeMode, switchDndAutoResume, switchDndSuppressNotifications, textDndStatus,
            switchDebugTelemetry,
            debugText
        )
        shareTechViews.forEach { if (it is TextView) it.typeface = shareTech }

        // Stat card shortcuts: ROM card → mount, Emulator card → toggle start/stop
        statRomCard.setOnClickListener {
            pickRom.launch(arrayOf(
                "application/zip",
                "application/octet-stream",
                "application/x-zip-compressed",
                "*/*"
            ))
        }
        statEmuCard.setOnClickListener {
            if (statEmuValue.text.toString() == "ACTIVE") {
                stopBackendService()
                Toast.makeText(this@RomLoaderActivity, "Stop requested", Toast.LENGTH_SHORT).show()
            } else {
                startBackendService()
                Toast.makeText(this@RomLoaderActivity, "Start requested", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup Bindings
        btnPickRom.setOnClickListener {
            pickRom.launch(arrayOf(
                "application/zip",
                "application/octet-stream",
                "application/x-zip-compressed",
                "*/*"
            ))
        }

        btnStartEmu.setOnClickListener {
            startBackendService()
            Toast.makeText(this@RomLoaderActivity, "Start requested", Toast.LENGTH_SHORT).show()
        }

        btnStopEmu.setOnClickListener {
            stopBackendService()
            Toast.makeText(this@RomLoaderActivity, "Stop requested", Toast.LENGTH_SHORT).show()
        }

        switchZoom.isChecked = DisplayRenderSettings.isTextZoomOutEnabled()
        switchZoom.setOnCheckedChangeListener { _, isChecked ->
            if (updatingRenderSwitch) return@setOnCheckedChangeListener
            if (EmulatorStatus.snapshot().isDPower) DisplayRenderSettings.setDPowerFitEnabled(this@RomLoaderActivity, isChecked)
            else DisplayRenderSettings.setTextZoomOutEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        switchAudio.isChecked = EmulatorAudioSettings.isAudioEnabled()
        switchAudio.setOnCheckedChangeListener { _, isChecked ->
            EmulatorAudioSettings.setAudioEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        switchPowerSave.isChecked = EmulatorTimingSettings.isPowerSaveEnabled()
        switchPowerSave.setOnCheckedChangeListener { _, isChecked ->
            EmulatorTimingSettings.setPowerSaveEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        switchExactTiming.isChecked = EmulatorTimingSettings.isFullAccuracyEnabled()
        switchExactTiming.setOnCheckedChangeListener { _, isChecked ->
            EmulatorTimingSettings.setFullAccuracyEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        switchHaptic.isChecked = EmulatorAudioSettings.isHapticAudioEnabled()
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            EmulatorAudioSettings.setHapticAudioEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        updateRamProfileButton(btnRamProfile)

        btnSaveAuto.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_SAVE_AUTOSAVE, 0, "Save autosave")
        }

        btnLoadAuto.setOnClickListener {
            confirmLoad(0)
        }

        btnRestart.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_RESTART, 0, "Restart emulator")
        }

        btnFullReset.setOnClickListener {
            confirmReset()
        }

        btnComboAb.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_PRESS_COMBO, EmulatorCommandBus.COMBO_AB, "Combo A+B")
        }

        btnComboAc.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_PRESS_COMBO, EmulatorCommandBus.COMBO_AC, "Combo A+C")
        }

        btnComboBc.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_PRESS_COMBO, EmulatorCommandBus.COMBO_BC, "Combo B+C")
        }

        btnRamProfile.setOnClickListener {
            ramWatchProfile = ramWatchProfile.next()
            updateRamProfileButton(btnRamProfile)
            renderDebugState()
        }

        btnRamBaseline.setOnClickListener {
            val snap = FrameDebugState.snapshot()
            val ram = snap.ram
            val vram = snap.vram
            if (ram == null || vram == null) {
                Toast.makeText(this, "No live emulator frame available for baseline", Toast.LENGTH_SHORT).show()
            } else {
                baselineRam = ram.copyOf()
                baselineVram = vram.copyOf()
                baselineAtMs = snap.updatedAtMs
                baselineRomLabel = readCurrentRomIdentity().first
                Toast.makeText(this, "RAM baseline marked", Toast.LENGTH_SHORT).show()
                renderDebugState()
            }
        }

        btnRamClearBaseline.setOnClickListener {
            clearRamBaseline()
            Toast.makeText(this, "RAM baseline cleared", Toast.LENGTH_SHORT).show()
            renderDebugState()
        }

        btnExportRawSnapshot.setOnClickListener {
            shareRawSnapshot()
        }

        btnToggleSave.setOnClickListener {
            val showing = layoutSaveTools.visibility == LinearLayout.VISIBLE
            layoutSaveTools.visibility = if (showing) LinearLayout.GONE else LinearLayout.VISIBLE
        }

        btnToggleBattle.setOnClickListener {
            val showing = layoutBattle.visibility == LinearLayout.VISIBLE
            if (!showing) {
                layoutBattle.visibility = LinearLayout.VISIBLE
                checkTamerNamePrompt()
            } else {
                layoutBattle.visibility = LinearLayout.GONE
            }
        }

        btnToggleAdvanced.setOnClickListener {
            val showing = layoutAdvanced.visibility == LinearLayout.VISIBLE
            layoutAdvanced.visibility = if (showing) LinearLayout.GONE else LinearLayout.VISIBLE
        }

        when (BattleTransportSettings.getTransportType()) {
            BattleTransportType.NEARBY -> rbNearby.isChecked = true
            BattleTransportType.INTERNET_RELAY -> rbInternet.isChecked = true
            BattleTransportType.SIMULATION -> rbSim.isChecked = true
        }

        when (BattleTransportSettings.getSimulationPreset()) {
            SimulationPreset.PURE_ECHO -> rbPureEcho.isChecked = true
            SimulationPreset.XOR_CHECKSUM -> rbXor.isChecked = true
            SimulationPreset.GLOBAL_CHECKSUM -> rbGlobal.isChecked = true
        }
        
        rgPreset.visibility = if (rbSim.isChecked) LinearLayout.VISIBLE else LinearLayout.GONE

        rgPreset.setOnCheckedChangeListener { _, checkedId ->
            val preset = when (checkedId) {
                R.id.rb_xor -> SimulationPreset.XOR_CHECKSUM
                R.id.rb_global -> SimulationPreset.GLOBAL_CHECKSUM
                else -> SimulationPreset.PURE_ECHO
            }
            BattleTransportSettings.setSimulationPreset(this@RomLoaderActivity, preset)
            sendCommand(EmulatorCommandBus.CMD_REFRESH_SETTINGS, 0, "Simulation preset")
        }

        rgTransport.setOnCheckedChangeListener { _, checkedId ->
            val type = when (checkedId) {
                R.id.rb_internet -> BattleTransportType.INTERNET_RELAY
                R.id.rb_sim -> BattleTransportType.SIMULATION
                else -> BattleTransportType.NEARBY
            }
            BattleTransportSettings.setTransportType(this@RomLoaderActivity, type)
            rgPreset.visibility = if (type == BattleTransportType.SIMULATION) LinearLayout.VISIBLE else LinearLayout.GONE
            sendCommand(EmulatorCommandBus.CMD_REFRESH_SETTINGS, 0, "Battle transport")
        }

        inputTamerName.setText(BattleTransportSettings.getTamerName())
        inputTamerName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                BattleTransportSettings.setTamerName(this@RomLoaderActivity, s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnTutorial.setOnClickListener {
            if (EmulatorStatus.snapshot().isDPower) { showDPowerControls(); return@setOnClickListener }
            val versionHint = readCurrentRomIdentity().first
            startActivity(
                Intent(this, TutorialActivity::class.java)
                    .putExtra(EvolutionGuideActivity.EXTRA_VERSION_HINT, versionHint)
            )
        }

        btnBattleHost.setOnClickListener {
            runWithBattlePermissions {
                sendCommand(EmulatorCommandBus.CMD_BATTLE_START_HOST, 0, "Battle host")
            }
        }

        btnBattleJoin.setOnClickListener {
            runWithBattlePermissions {
                sendCommand(EmulatorCommandBus.CMD_BATTLE_START_JOIN, 0, "Battle join")
            }
        }

        btnBattleStop.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_BATTLE_STOP, 0, "Battle stop")
        }

        btnShareDebug.setOnClickListener {
            shareDebugReport()
        }

        btnExportStates.setOnClickListener {
            exportSaveStateBundle()
        }

        switchAttentionNotifications.isChecked =
            DigimonAttentionSettings.isAttentionNotificationsEnabled() && hasNotificationPermission()
        switchAttentionNotifications.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasNotificationPermission()) {
                requestNotificationPermissionIfNeeded()
            } else {
                DigimonAttentionSettings.setAttentionNotificationsEnabled(this@RomLoaderActivity, isChecked)
            }
        }

        switchShowCareMistakes.isChecked = DigimonAttentionSettings.isShowCareMistakesEnabled()
        switchShowCareMistakes.setOnCheckedChangeListener { _, isChecked ->
            DigimonAttentionSettings.setShowCareMistakesEnabled(this@RomLoaderActivity, isChecked)
            renderDebugState()
        }

        switchDndEnabled.isChecked = DigimonDndSettings.isEnabled()
        switchDndFreezeMode.isChecked = DigimonDndSettings.isFreezeMode()
        switchDndAutoResume.isChecked = DigimonDndSettings.isAutoResumeEnabled()
        switchDndSuppressNotifications.isChecked = DigimonDndSettings.isSuppressNotificationsEnabled()
        btnDndStartTime.text = "START ${DigimonDndSettings.formatTime(DigimonDndSettings.getStartMinutes())}"
        btnDndEndTime.text = "END ${DigimonDndSettings.formatTime(DigimonDndSettings.getEndMinutes())}"

        switchDndEnabled.setOnCheckedChangeListener { _, isChecked ->
            DigimonDndSettings.setEnabled(this@RomLoaderActivity, isChecked)
            if (isChecked) {
                requestExactAlarmAccessIfNeeded()
            }
            DigimonDndScheduler.reschedule(this@RomLoaderActivity)
            refreshBackendSettingsSilently()
            updateDndStatus(textDndStatus, btnDndStartTime, btnDndEndTime)
            renderDebugState()
        }

        switchDndFreezeMode.setOnCheckedChangeListener { _, isChecked ->
            DigimonDndSettings.setMode(
                this@RomLoaderActivity,
                if (isChecked) DigimonDndSettings.Mode.FREEZE else DigimonDndSettings.Mode.SILENT
            )
            DigimonDndScheduler.reschedule(this@RomLoaderActivity)
            refreshBackendSettingsSilently()
            updateDndStatus(textDndStatus, btnDndStartTime, btnDndEndTime)
            renderDebugState()
        }

        btnDndStartTime.setOnClickListener {
            showDndTimePicker(isStart = true) {
                updateDndStatus(textDndStatus, btnDndStartTime, btnDndEndTime)
                renderDebugState()
            }
        }

        btnDndEndTime.setOnClickListener {
            showDndTimePicker(isStart = false) {
                updateDndStatus(textDndStatus, btnDndStartTime, btnDndEndTime)
                renderDebugState()
            }
        }

        switchDndAutoResume.setOnCheckedChangeListener { _, isChecked ->
            DigimonDndSettings.setAutoResumeEnabled(this@RomLoaderActivity, isChecked)
            refreshBackendSettingsSilently()
            updateDndStatus(textDndStatus, btnDndStartTime, btnDndEndTime)
        }

        switchDndSuppressNotifications.setOnCheckedChangeListener { _, isChecked ->
            DigimonDndSettings.setSuppressNotificationsEnabled(this@RomLoaderActivity, isChecked)
            refreshBackendSettingsSilently()
            updateDndStatus(textDndStatus, btnDndStartTime, btnDndEndTime)
        }

        switchDebugTelemetry.isChecked = EmulatorDebugSettings.isDebugEnabled()
        switchDebugTelemetry.setOnCheckedChangeListener { _, isChecked ->
            EmulatorDebugSettings.setDebugEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        loadInitialRomIdentity()
        updateStatus()
        updateDndStatus(textDndStatus, btnDndStartTime, btnDndEndTime)
        refreshSaveAndCommandInfo()
        renderDebugState()
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView?>(R.id.text_dnd_status)?.let { status ->
            updateDndStatus(
                status,
                findViewById(R.id.btn_dnd_start_time),
                findViewById(R.id.btn_dnd_end_time)
            )
        }
        mainHandler.removeCallbacks(debugRefresh)
        mainHandler.post(debugRefresh)
    }

    override fun onDestroy() {
        dpowerMenu?.dismiss()
        cancelImport()
        super.onDestroy()
    }

    private fun checkTamerNamePrompt() {
        if (BattleTransportSettings.getTamerName().isBlank()) {
            val input = EditText(this).apply {
                hint = "TAMER_404"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setPadding(50, 40, 50, 40)
                textSize = 16f
            }
            AlertDialog.Builder(this)
                .setTitle("IDENTIFY TAMER")
                .setMessage("Enter your Tamer Alias for the System Link Lobbies.")
                .setView(input)
                .setPositiveButton("INITIATE") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotBlank()) {
                        BattleTransportSettings.setTamerName(this, name)
                        findViewById<EditText>(R.id.input_tamer_name)?.setText(name)
                    } else {
                        // Keep blank, it will auto-gen later in relay.
                    }
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onPause() {
        nfcCardScanner.stop()
        super.onPause()
        mainHandler.removeCallbacks(debugRefresh)
    }

    private fun loadInitialRomIdentity() {
        val before = EmulatorStatus.snapshot()
        if (before.name != null || before.busy) return
        Thread({
            runCatching {
                val file = File(filesDir, "current_rom.bin")
                if (file.exists()) {
                    val bytes = file.inputStream().use { com.digimon.glyph.emulator.RomImport.readRomBytes(it) }
                    val device = com.digimon.glyph.emulator.RomCatalog.identify(bytes)
                    val name = if (device.id == "UnknownClassic") File(filesDir, "current_rom_name").takeIf { it.exists() }?.readText()?.trim() ?: device.id else device.id
                    EmulatorStatus.initialDevice(name, sha256(bytes), before.revision)
                }
            }.onFailure {
                if (EmulatorStatus.snapshot().revision == before.revision)
                    EmulatorStatus.runtime(EmulatorStatus.Run.ERROR, "Stored ROM could not be read: ${it.message}")
            }
        }, "ROM-identity").start()
    }

    private fun updateStatus() {
        val status = EmulatorStatus.snapshot()
        if (status.revision == lastStatusRevision) return
        lastStatusRevision = status.revision
        statusText.text = listOfNotNull(status.name?.let { "Selected device: $it" } ?: getString(R.string.rom_not_found),
            status.message.takeIf { it.isNotBlank() }, status.error).joinToString("\n")
        statRomValue.text = status.name ?: "—"
        statEmuValue.text = when (status.run) {
            EmulatorStatus.Run.RUNNING -> "RUNNING"
            EmulatorStatus.Run.FROZEN -> "PAUSED"
            EmulatorStatus.Run.STARTING -> "STARTING"
            EmulatorStatus.Run.ERROR -> "ERROR"
            EmulatorStatus.Run.STOPPED -> "STOPPED"
        }
        statEmuLabel.text = when (status.glyph) {
            EmulatorStatus.Glyph.CONNECTED -> getString(R.string.glyph_connected)
            EmulatorStatus.Glyph.DISCONNECTED -> getString(R.string.glyph_disconnected)
            EmulatorStatus.Glyph.OUTPUT_ERROR -> getString(R.string.glyph_output_error)
        }
        findViewById<Button>(R.id.btn_pick_rom).isEnabled = !status.busy
        findViewById<Button>(R.id.btn_start_emu).isEnabled = status.name != null && !status.busy
        findViewById<Button>(R.id.btn_stop_emu).isEnabled = status.run != EmulatorStatus.Run.STOPPED && !status.busy
        findViewById<Button>(R.id.btn_cancel_import).visibility =
            if (status.importing in listOf(EmulatorStatus.Import.SCANNING, EmulatorStatus.Import.CHOOSING)) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btn_dpower_controls).visibility = if (status.isDPower) View.VISIBLE else View.GONE
        findViewById<Switch>(R.id.switch_zoom).apply {
            isEnabled = true
            updatingRenderSwitch = true
            isChecked = if (status.isDPower) DisplayRenderSettings.isDPowerFitEnabled() else DisplayRenderSettings.isTextZoomOutEnabled()
            updatingRenderSwitch = false
            text = getString(if (status.isDPower) R.string.dpower_fit else R.string.auto_fit)
        }
        val classic = status.name != null && !status.isDPower
        findViewById<Button>(R.id.btn_toggle_battle).apply {
            isEnabled = classic
            text = if (status.isDPower) "Battles unavailable for D-Power" else "Battles"
        }
        if (!classic) findViewById<View>(R.id.layout_battle).visibility = View.GONE
        (findViewById<View>(R.id.btn_combo_ab).parent as? View)?.visibility = if (classic) View.VISIBLE else View.GONE
        for (id in listOf(R.id.btn_combo_ab, R.id.btn_combo_ac, R.id.btn_combo_bc)) {
            findViewById<View>(id).visibility = if (classic) View.VISIBLE else View.GONE
        }
        for (id in listOf(R.id.switch_attention_notifications, R.id.switch_show_care_mistakes)) {
            findViewById<View>(id).visibility = if (status.hasClassicMetadata) View.VISIBLE else View.GONE
        }
        findViewById<Button>(R.id.btn_tutorial).setText(if (status.isDPower) R.string.dpower_controls else R.string.classic_help)
    }

    private fun parseRomNameParts(name: String): Pair<String, String?> {
        val detectedVersion = DigimonDatabase.resolveVersion(name)
        if (detectedVersion != null) {
            val base = name
                .replace(Regex("(?i)digimon\\s*v?[123]"), "Digimon")
                .replace(Regex("(?i)v[123]"), "")
                .replace(Regex("[_\\-]+"), " ")
                .trim()
                .ifBlank { "Digimon" }
                .uppercase()
            return Pair(base, detectedVersion)
        }
        val match = Regex("(.*?)(v\\d+)$", RegexOption.IGNORE_CASE).find(name.trim())
        return if (match != null)
            Pair(match.groupValues[1].uppercase(), match.groupValues[2].uppercase())
        else
            Pair(name.uppercase(), null)
    }

    private fun renderDebugState() {
        val frameSnap = FrameDebugState.snapshot()
        if (frameSnap.updatedAtMs != lastFrameUpdateMs) {
            lastFrameUpdateMs = frameSnap.updatedAtMs
            val state = frameSnap.digimonState
            digimonStatsText.text = if (state != null && EmulatorStatus.snapshot().hasClassicMetadata) {
                val name = state.info?.name ?: "UNKNOWN"
                val stage = state.info?.stage ?: "UNKNOWN STAGE"
                val showCareMistakes = DigimonAttentionSettings.isShowCareMistakesEnabled()
                val dnd = when {
                    DigimonDndSettings.isFrozenNow() -> "SLEEP"
                    DigimonDndSettings.isDndActiveNow() -> if (DigimonDndSettings.isFreezeMode()) "WINDOW" else "QUIET"
                    else -> "OFF"
                }
                val frameAgeMs = if (frameSnap.updatedAtMs == 0L) null else (android.os.SystemClock.uptimeMillis() - frameSnap.updatedAtMs)
                buildString {
                    appendLine("SYS.MONITOR :: LIVE PET STREAM")
                    appendLine("FRAME ${formatFrameAge(frameAgeMs)}  ATTN ${formatFlag(state.needsAttention)}")
                    appendLine("DND   $dnd")
                    appendLine("================================")
                    appendLine("MON    $name")
                    appendLine("STAGE  $stage")
                    appendLine("AGE    ${state.age}D")
                    appendLine("WEIGHT ${state.weight}G")
                    appendLine("--------------------------------")
                    appendLine("HUNGER   ${formatMeterValue(state.hunger)}")
                    appendLine("PROTEIN  ${formatMeterValue(state.protein)}")
                    appendLine("OVERFD   ${formatCounterValue(state.overfeed)}")
                    appendLine("CARE     ${if (showCareMistakes) formatCounterValue(state.careMistakes) else "LOCKED"}")
                    appendLine("CARE TMR ${formatMinutesLeft(state.careTimerMinutesLeft)}")
                    appendLine("--------------------------------")
                    appendLine("TRAIN    ${formatCounterValue(state.training)}")
                    appendLine("SHITSU   --")
                    appendLine("SLEEP    --")
                    appendLine("POOP     --")
                    appendLine("WINS     ${formatCounterValue(state.wins)}   LOSES ${formatCounterValue(state.losses)}")
                    appendLine("SHOURI   ${formatPercentValue(state.winRate)}")
                    appendLine("--------------------------------")
                    append("RAW H=${formatNibble(state.hunger)} P=${formatNibble(state.protein)} O=${formatNibble(state.overfeed)}")
                    append(" TR=${formatCounterRaw(state.training)} C=${formatNibble(state.careMistakes)} T=${formatMinutesRaw(state.careTimerMinutesLeft)}")
                    append(" W=${formatCounterRaw(state.wins)} L=${formatCounterRaw(state.losses)} R=${formatCounterRaw(state.winRate)}")
                    append(" A=${if (state.needsAttention) "1" else "0"}")
                }
            } else if (frameSnap.vram?.size == 128) {
                "D-POWER EU 1.01\nPlay on the Glyph Matrix. Pet statistics are not decoded for this device."
            } else if (EmulatorStatus.snapshot().name != null) {
                "Pet statistics are not decoded for this ROM. Use the Glyph display to play."
            } else {
                "AWAITING SIGNAL..."
            }
        }
        refreshSaveAndCommandInfo()
        renderRamDebugState(frameSnap)

        if (!EmulatorDebugSettings.isDebugEnabled()) {
            setIndicatorState(indicatorA, "A", false, Color.parseColor("#00C853"))
            setIndicatorState(indicatorB, "B", false, Color.parseColor("#FFD600"))
            setIndicatorState(indicatorC, "C", false, Color.parseColor("#00B0FF"))
            debugText.text = "debug telemetry disabled (open Debug and enable it to view live input diagnostics)"
            return
        }

        val snap = InputDebugState.read(this)
        val ageMs = System.currentTimeMillis() - snap.timestampMs
        val frameAgeMs = if (frameSnap.updatedAtMs == 0L) Long.MAX_VALUE else (android.os.SystemClock.uptimeMillis() - frameSnap.updatedAtMs)
        val triggerAgeMs = if (snap.lastTriggerAtMs == 0L) Long.MAX_VALUE else (System.currentTimeMillis() - snap.lastTriggerAtMs)
        val streamState = if (snap.timestampMs == 0L || ageMs > 1500L) "inactive" else "live"
        val frameState = if (frameSnap.updatedAtMs == 0L || frameAgeMs > 1500L) "inactive" else "live"
        val flashWindowMs = 260L
        val aLit = snap.buttonAActive || (snap.lastTriggerButton == "A" && triggerAgeMs < flashWindowMs)
        val bLit = snap.buttonBActive || (snap.lastTriggerButton == "B" && triggerAgeMs < flashWindowMs)
        val cLit = snap.buttonCActive || (snap.lastTriggerButton == "C" && triggerAgeMs < flashWindowMs)
        setIndicatorState(indicatorA, "A", aLit, Color.parseColor("#00C853"))
        setIndicatorState(indicatorB, "B", bLit, Color.parseColor("#FFD600"))
        setIndicatorState(indicatorC, "C", cLit, Color.parseColor("#00B0FF"))

        debugText.text = buildString {
            appendLine("mode=${snap.mode}  stream=$streamState  age=${ageMs}ms")
            appendLine("frames=$frameState  frameAge=${if (frameAgeMs == Long.MAX_VALUE) "-" else "${frameAgeMs}ms"}")
            appendLine("pitch=${"%.1f".format(snap.pitchDeg)}  roll=${"%.1f".format(snap.rollDeg)}")
            appendLine("linear: x=${"%.2f".format(snap.linearX)}  y=${"%.2f".format(snap.linearY)}  z=${"%.2f".format(snap.linearZ)}")
            appendLine("filter: x=${"%.2f".format(snap.filteredX)}  y=${"%.2f".format(snap.filteredY)}  z=${"%.2f".format(snap.filteredZ)}")
            appendLine("pending: axis=${snap.pendingAxis}  dir=${snap.pendingDir}  age=${snap.pendingAgeMs}ms")
            appendLine("last trigger: ${snap.lastTriggerButton}  age=${if (triggerAgeMs == Long.MAX_VALUE) "-" else "${triggerAgeMs}ms"}")
            appendLine("states: A=${snap.buttonAActive}/${snap.buttonALatchedByB}  B=${snap.buttonBActive}/${snap.glyphPhysicalDown}  C=${snap.buttonCActive}/${snap.buttonCLatchedByB}")
            if (streamState == "inactive") {
                appendLine("open Glyph Toy and flick to see live values")
            }
        }
    }

    private fun formatFrameAge(ageMs: Long?): String {
        if (ageMs == null) return "--"
        return "${ageMs}MS"
    }

    private fun formatFlag(active: Boolean): String = if (active) "OPEN" else "IDLE"

    private fun formatCounterValue(value: Int?): String = value?.toString() ?: "--"

    private fun formatCounterRaw(value: Int?): String = value?.toString() ?: "-"

    private fun formatMinutesLeft(value: Int?): String = value?.let { "${it.toString().padStart(2, ' ')}M" } ?: "--"

    private fun formatPercentValue(value: Int?): String = value?.let { "${it}%" } ?: "--%"

    private fun formatNibble(value: Int?): String = value?.let { it.and(0xF).toString(16).uppercase() } ?: "-"

    private fun formatMinutesRaw(value: Int?): String = value?.toString() ?: "-"

    private fun formatMeterValue(value: Int?): String {
        if (value == null) return "--  [....]"
        val clamped = value.coerceIn(0, 4)
        return "${value.toString().padStart(2, ' ')}  [${"#".repeat(clamped)}${".".repeat(4 - clamped)}]"
    }

    private fun renderRamDebugState(frameSnap: FrameDebugState.Snapshot) {
        val ram = frameSnap.ram
        val vram = frameSnap.vram
        val (romName, _) = readCurrentRomIdentity()
        if (baselineRomLabel != null && baselineRomLabel != romName) {
            clearRamBaseline()
        }
        ramDebugText.text = if (ram == null || vram == null) {
            "RAW RAM WATCH\nawaiting live frame..."
        } else {
            buildRamDebugText(frameSnap, romName)
        }
    }

    private fun buildRamDebugText(frameSnap: FrameDebugState.Snapshot, romName: String?): String {
        val ram = frameSnap.ram ?: return "RAW RAM WATCH\nawaiting live frame..."
        val vram = frameSnap.vram ?: return "RAW RAM WATCH\nawaiting live frame..."
        if (EmulatorStatus.snapshot().isDPower) {
            return buildString {
                appendLine("D-POWER RAW RAM — first 256 bytes of ${ram.size}")
                appendLine("Classic watch profiles and decoded statistics do not apply.")
                ram.take(256).chunked(16).forEachIndexed { row, values ->
                    append("%04X: ".format(row * 16))
                    appendLine(values.joinToString(" ") { "%02X".format(it and 0xFF) })
                }
            }.trimEnd()
        }
        val profile = ramWatchProfile
        val baseline = baselineRam
        val baselineVramLocal = baselineVram
        val profileDiffs = if (baseline != null) collectRamDiffs(ram, baseline, profile.addresses.asIterable()) else emptyList()
        val globalDiffs = if (baseline != null) collectRamDiffs(ram, baseline, 0x00..0x9F) else emptyList()
        val vramDiffs = if (baselineVramLocal != null) collectRamDiffs(vram, baselineVramLocal, vram.indices) else emptyList()
        val frameAgeMs = if (frameSnap.updatedAtMs == 0L) null else (android.os.SystemClock.uptimeMillis() - frameSnap.updatedAtMs)
        val baselineAgeText = if (baselineAtMs == 0L) "--" else formatFrameAge(System.currentTimeMillis() - baselineAtMs)
        return buildString {
            appendLine("RAW RAM WATCH :: ${profile.label}")
            appendLine("ROM ${romName ?: "-"}  FRAME ${formatFrameAge(frameAgeMs)}  BASE ${if (baseline != null) baselineAgeText else "OFF"}")
            appendLine("--------------------------------")
            append(renderProfileValues(ram, profile))
            appendLine()
            appendLine("--------------------------------")
            appendLine("PROFILE Δ ${if (profileDiffs.isEmpty()) "-" else ""}")
            if (profileDiffs.isNotEmpty()) {
                appendLine(formatDiffList(profileDiffs.take(16)))
            }
            appendLine("GLOBAL Δ ${if (globalDiffs.isEmpty()) "-" else ""}")
            if (globalDiffs.isNotEmpty()) {
                appendLine(formatDiffList(globalDiffs.take(16)))
            }
            appendLine("VRAM Δ ${if (vramDiffs.isEmpty()) "-" else ""}")
            if (vramDiffs.isNotEmpty()) {
                appendLine(formatDiffList(vramDiffs.take(12)))
            }
        }.trimEnd()
    }

    private fun renderProfileValues(ram: IntArray, profile: RamWatchProfile): String {
        return if (profile.rowDump) {
            val rows = profile.addresses.map { it and 0xF0 }.distinct().sorted()
            buildString {
                for (row in rows) {
                    append(row.toString(16).uppercase().padStart(2, '0'))
                    append(": ")
                    for (col in 0..0xF) {
                        val addr = row + col
                        append((ram.getOrNull(addr) ?: 0).and(0xF).toString(16).uppercase())
                        if (col != 0xF) append(' ')
                    }
                    appendLine()
                }
            }.trimEnd()
        } else {
            buildString {
                profile.addresses.forEachIndexed { index, addr ->
                    append(addr.toString(16).uppercase().padStart(2, '0'))
                    append('=')
                    append((ram.getOrNull(addr) ?: 0).and(0xF).toString(16).uppercase())
                    if ((index + 1) % 6 == 0) appendLine() else append("  ")
                }
            }.trimEnd()
        }
    }

    private fun collectRamDiffs(current: IntArray, baseline: IntArray, addresses: Iterable<Int>): List<Triple<Int, Int, Int>> {
        val diffs = ArrayList<Triple<Int, Int, Int>>()
        for (addr in addresses) {
            val before = baseline.getOrNull(addr) ?: continue
            val after = current.getOrNull(addr) ?: continue
            if (before != after) {
                diffs += Triple(addr, before, after)
            }
        }
        return diffs
    }

    private fun formatDiffList(diffs: List<Triple<Int, Int, Int>>): String {
        return buildString {
            diffs.forEachIndexed { index, (addr, before, after) ->
                append(addr.toString(16).uppercase().padStart(2, '0'))
                append(':')
                append(before.and(0xF).toString(16).uppercase())
                append('>')
                append(after.and(0xF).toString(16).uppercase())
                if ((index + 1) % 4 == 0) appendLine() else append("  ")
            }
        }.trimEnd()
    }

    private fun updateRamProfileButton(button: Button) {
        button.text = "WATCH ${ramWatchProfile.label}"
    }

    private fun clearRamBaseline() {
        baselineRam = null
        baselineVram = null
        baselineAtMs = 0L
        baselineRomLabel = null
    }

    private fun shareRawSnapshot() {
        try {
            val report = buildRawSnapshotReport()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outDir = File(cacheDir, "reports")
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, "digimon_raw_snapshot_$stamp.txt")
            outFile.writeText(report)
            shareFile(
                file = outFile,
                mimeType = "text/plain",
                subject = "Digimon Glyph Raw Snapshot $stamp",
                chooserTitle = "Share raw snapshot"
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export raw snapshot: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildRawSnapshotReport(): String {
        val frame = FrameDebugState.snapshot()
        val (romName, romHash) = readCurrentRomIdentity()
        val ram = frame.ram
        val vram = frame.vram
        return buildString {
            appendLine("Digimon Glyph Raw Snapshot")
            appendLine("generated_at=${formatTime(System.currentTimeMillis())}")
            appendLine("rom_name=${romName ?: "-"}")
            appendLine("rom_sha256=${romHash ?: "-"}")
            appendLine("watch_profile=${ramWatchProfile.label}")
            appendLine("baseline_active=${baselineRam != null}")
            appendLine()
            if (frame.digimonState != null) {
                appendLine("[digimon]")
                appendLine("name=${frame.digimonState.info?.name ?: "-"}")
                appendLine("stage=${frame.digimonState.info?.stage ?: "-"}")
                appendLine("age=${frame.digimonState.age}")
                appendLine("weight=${frame.digimonState.weight}")
                appendLine("hunger=${frame.digimonState.hunger ?: "-"}")
                appendLine("protein=${frame.digimonState.protein ?: "-"}")
                appendLine("overfeed=${frame.digimonState.overfeed ?: "-"}")
                appendLine("training=${frame.digimonState.training ?: "-"}")
                appendLine("care=${frame.digimonState.careMistakes ?: "-"}")
                appendLine("wins=${frame.digimonState.wins ?: "-"}")
                appendLine("losses=${frame.digimonState.losses ?: "-"}")
                appendLine("win_rate=${frame.digimonState.winRate ?: "-"}")
                appendLine("attention=${frame.digimonState.needsAttention}")
                appendLine()
            }
            appendLine("[ram]")
            if (ram == null) {
                appendLine("unavailable")
            } else {
                appendLine(formatRamPages(ram))
            }
            appendLine()
            appendLine("[vram]")
            if (vram == null) {
                appendLine("unavailable")
            } else {
                appendLine(formatRamPages(vram, rowWidth = 0x10))
            }
            if (ram != null && baselineRam != null) {
                appendLine()
                appendLine("[baseline_diff]")
                appendLine(formatDiffList(collectRamDiffs(ram, baselineRam!!, 0x00..0x9F).take(64)))
            }
        }
    }

    private fun formatRamPages(values: IntArray, rowWidth: Int = 0x10): String {
        return buildString {
            var rowStart = 0
            while (rowStart < values.size) {
                append(rowStart.toString(16).uppercase().padStart(2, '0'))
                append(": ")
                val rowEnd = (rowStart + rowWidth).coerceAtMost(values.size)
                for (addr in rowStart until rowEnd) {
                    append(values[addr].and(if (EmulatorStatus.snapshot().isDPower) 0xFF else 0xF)
                        .toString(16).uppercase().padStart(if (EmulatorStatus.snapshot().isDPower) 2 else 1, '0'))
                    if (addr != rowEnd - 1) append(' ')
                }
                appendLine()
                rowStart += rowWidth
            }
        }.trimEnd()
    }

    private fun buildIndicator(label: String): TextView {
        val shareTech = Typeface.createFromAsset(assets, "fonts/ShareTechMono-Regular.ttf")
        return TextView(this).apply {
            text = "$label: OFF"
            textSize = 14f
            typeface = shareTech
            setTextColor(Color.WHITE)
            setPadding(18, 10, 18, 10)
            setBackgroundColor(Color.parseColor("#424242"))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 12
            layoutParams = lp
        }
    }

    private fun setIndicatorState(view: TextView, label: String, active: Boolean, activeColor: Int) {
        view.text = if (active) "$label: ON" else "$label: OFF"
        view.setBackgroundColor(if (active) activeColor else Color.parseColor("#424242"))
        view.setTextColor(if (active) Color.BLACK else Color.WHITE)
    }


    private fun addSlotRow(parent: LinearLayout, slot: Int): TextView {
        val shareTech = Typeface.createFromAsset(assets, "fonts/ShareTechMono-Regular.ttf")
        val teko = Typeface.createFromAsset(assets, "fonts/Teko.ttf")
        
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        val info = TextView(this).apply {
            textSize = 13f
            typeface = shareTech
            setTextColor(Color.WHITE)
            text = "Slot $slot: empty"
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setPadding(0, 8, 8, 8)
        }
        val saveBtn = Button(this).apply {
            text = "SAVE"
            typeface = teko
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@RomLoaderActivity, R.color.cyber_blue))
            setBackgroundResource(R.drawable.cyber_button_bg)
            val lp = LinearLayout.LayoutParams(dp(80), dp(48)) // approx 60dp wide, 40dp high
            lp.marginEnd = dp(8)
            layoutParams = lp
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_SAVE_SLOT, slot, "Save slot $slot")
            }
        }
        val loadBtn = Button(this).apply {
            text = "LOAD"
            typeface = teko
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@RomLoaderActivity, R.color.cyber_yellow))
            setBackgroundResource(R.drawable.cyber_button_bg)
            val lp = LinearLayout.LayoutParams(dp(80), dp(48))
            layoutParams = lp
            setOnClickListener {
                confirmLoad(slot)
            }
        }
        row.addView(info)
        row.addView(saveBtn)
        row.addView(loadBtn)
        parent.addView(row)
        return info
    }

    private fun sendCommand(type: String, arg: Int, label: String) {
        val status = EmulatorStatus.snapshot()
        if (status.busy || status.run == EmulatorStatus.Run.FROZEN) {
            Toast.makeText(this, if (status.busy) "Wait for ROM import to finish" else "Resume the pet before changing its saves", Toast.LENGTH_LONG).show()
            return
        }
        try {
            EmulatorCommandBus.post(this, type, arg)
            startBackendService()
            Toast.makeText(this, "$label requested", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "Command could not be queued", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshBackendSettingsSilently() {
        startBackendService()
        EmulatorCommandBus.post(this, EmulatorCommandBus.CMD_REFRESH_SETTINGS, 0)
    }

    private fun startBackendService() {
        try {
            // Ensure command poll loop is alive even when toy/widget binding is not active.
            val intent = Intent(this, DigimonGlyphToyService::class.java)
                .setAction(DigimonGlyphToyService.ACTION_START_WIDGET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
            // Best-effort bootstrap; command may still work if service is already active.
        }
    }

    private fun stopBackendService() {
        try {
            val intent = Intent(this, DigimonGlyphToyService::class.java)
                .setAction(DigimonGlyphToyService.ACTION_STOP)
            startService(intent)
        } catch (_: Exception) {
        }
    }

    private fun runWithNearbyPermissions(action: () -> Unit) {
        if (hasNearbyPermissions()) {
            action()
            return
        }
        pendingNearbyAction = action
        requestNearbyPermissions.launch(requiredNearbyPermissions())
    }

    private fun runWithBattlePermissions(action: () -> Unit) {
        if (BattleTransportSettings.getTransportType() == BattleTransportType.NEARBY) {
            runWithNearbyPermissions(action)
            return
        }
        action()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (hasNotificationPermission()) {
            DigimonAttentionSettings.setAttentionNotificationsEnabled(this, true)
            attentionNotificationsSwitch?.isChecked = true
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            DigimonAttentionSettings.setAttentionNotificationsEnabled(this, true)
            attentionNotificationsSwitch?.isChecked = true
        }
    }

    private fun requestExactAlarmAccessIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (DigimonDndScheduler.canScheduleExactAlarms(this)) return
        Toast.makeText(
            this,
            "Exact alarm access is off. Quiet hours still work, but wake timing may be less precise.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showDndTimePicker(isStart: Boolean, onUpdated: () -> Unit) {
        val current = if (isStart) DigimonDndSettings.getStartMinutes() else DigimonDndSettings.getEndMinutes()
        val hour = current / 60
        val minute = current % 60
        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                val total = selectedHour * 60 + selectedMinute
                if (isStart) {
                    DigimonDndSettings.setStartMinutes(this, total)
                } else {
                    DigimonDndSettings.setEndMinutes(this, total)
                }
                DigimonDndScheduler.reschedule(this)
                refreshBackendSettingsSilently()
                onUpdated()
            },
            hour,
            minute,
            true
        ).show()
    }

    private fun updateDndStatus(statusView: TextView, startButton: Button, endButton: Button) {
        DigimonDndSettings.init(this)
        startButton.text = "START ${DigimonDndSettings.formatTime(DigimonDndSettings.getStartMinutes())}"
        endButton.text = "END ${DigimonDndSettings.formatTime(DigimonDndSettings.getEndMinutes())}"
        val mode = if (DigimonDndSettings.isFreezeMode()) "FREEZE" else "SILENT"
        statusView.text = when {
            !DigimonDndSettings.isEnabled() -> "DND OFF"
            DigimonDndSettings.isFrozenNow() -> "DND ACTIVE  $mode  RESUME ${DigimonDndSettings.formatTime(DigimonDndSettings.getEndMinutes())}"
            DigimonDndSettings.isDndActiveNow() -> "DND ACTIVE  $mode"
            else -> "NEXT DND ${DigimonDndSettings.formatTime(DigimonDndSettings.getStartMinutes())} → ${DigimonDndSettings.formatTime(DigimonDndSettings.getEndMinutes())}  $mode"
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNearbyPermissions(): Boolean {
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!(coarseGranted || fineGranted)) return false
        return requiredNearbyCorePermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiredNearbyPermissions(): Array<String> {
        val core = requiredNearbyCorePermissions()
        val all = ArrayList<String>(core.size + 2)
        all.addAll(core)
        all.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        all.add(Manifest.permission.ACCESS_FINE_LOCATION)
        return all.toTypedArray()
    }

    private fun requiredNearbyCorePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    private fun refreshSaveAndCommandInfo() {
        val (currentRomName, currentRomHash) = readCurrentRomIdentity()
        val clockSupported = com.digimon.glyph.emulator.RomCatalog.isVerifiedClassicHash(currentRomHash)
        val runtime = com.digimon.glyph.emulator.EmulatorStatus.snapshot()
        val clockAvailable = clockSupported && runtime.hash == currentRomHash && !runtime.busy &&
            runtime.run == com.digimon.glyph.emulator.EmulatorStatus.Run.RUNNING
        for (id in listOf(R.id.btn_sync_firmware_clock, R.id.btn_set_firmware_clock)) {
            findViewById<Button>(id).apply { isEnabled = clockAvailable; alpha = if (clockAvailable) 1f else .45f }
        }
        findViewById<TextView>(R.id.clock_sync_info).text = if (clockSupported)
            "The Digimon clock and Glyph clock overlay are separate. Start the pet and finish clock setup before changing its time."
        else "Clock controls are available for supported classic Digimon ROMs. The Glyph clock overlay is separate."
        val auto = stateManager.getAutosaveInfo(currentRomName, currentRomHash)
        autosaveText.text = buildSaveLine("Autosave", auto, currentRomName, currentRomHash)
        slot1Text.text = buildSaveLine("Slot 1", stateManager.getSlotInfo(1, currentRomName, currentRomHash), currentRomName, currentRomHash)
        slot2Text.text = buildSaveLine("Slot 2", stateManager.getSlotInfo(2, currentRomName, currentRomHash), currentRomName, currentRomHash)
        slot3Text.text = buildSaveLine("Slot 3", stateManager.getSlotInfo(3, currentRomName, currentRomHash), currentRomName, currentRomHash)

        val ack = EmulatorCommandBus.readAck(this)
        if (ack != null && ack.id > lastAckId) {
            lastAckId = ack.id
            lastAckText = "${formatTime(ack.timestampMs)} - ${ack.status}"
        }
        val battle = BattleStateStore.read(this)
        val battleAgeMs = if (battle.updatedAtMs == 0L) "-" else "${System.currentTimeMillis() - battle.updatedAtMs}ms"
        val peer = battle.peerName
        val transport = BattleTransportSettings.getTransportType()
        battleStatusText.text = "[$transport] ${battle.status}  age=$battleAgeMs\n${battle.message}"
        // Update lobby-style peer name and badge
        battlePeerName.text = if (peer != null) peer.uppercase() else "NO ACTIVE LINK"
        val badgeText = when (battle.status) {
            BattleStateStore.Status.CONNECTED -> "BATTLING"
            BattleStateStore.Status.ADVERTISING,
            BattleStateStore.Status.DISCOVERING,
            BattleStateStore.Status.CONNECTING -> "SCANNING"
            else -> "WAITING"
        }
        battleBadge.text = badgeText
        commandStatusText.text = "Last command: $lastAckText"
    }

    private fun buildSaveLine(
        label: String,
        info: StateManager.SaveInfo,
        currentRomName: String?,
        currentRomHash: String?
    ): String {
        val slotId = label.filter { it.isDigit() }.ifEmpty { "AUTO" }
        if (!info.exists) return "SLT.$slotId  ──────  [EMPTY]"
        val compact = if (info.timestampMs <= 0L) "─────────────" else
            SimpleDateFormat("yy.MM.dd HH:mm", Locale.US).format(Date(info.timestampMs))
        val match = when {
            currentRomHash != null && info.romHash != null -> currentRomHash == info.romHash
            currentRomName != null && info.romName != null -> currentRomName == info.romName
            else -> true
        }
        val tag = if (match) "[OK]" else "[DIFF]"
        val romShort = (info.romName ?: "?").uppercase().take(8)
        return "SLT.$slotId  $compact  $tag · $romShort"
    }

    private fun readCurrentRomIdentity(): Pair<String?, String?> {
        val status = EmulatorStatus.snapshot()
        return Pair(status.name, status.hash)
    }

    private fun formatTime(ts: Long): String {
        if (ts <= 0L) return "-"
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return fmt.format(Date(ts))
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append("%02x".format(b))
        return sb.toString()
    }

    private fun shareDebugReport() {
        try {
            val report = buildDebugReport()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outDir = File(cacheDir, "reports")
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, "digimon_debug_report_$stamp.txt")
            outFile.writeText(report)
            shareFile(
                file = outFile,
                mimeType = "text/plain",
                subject = "Digimon Glyph Debug Report $stamp",
                chooserTitle = "Share debug report"
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share report: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportSaveStateBundle() {
        try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outDir = File(cacheDir, "reports")
            if (!outDir.exists()) outDir.mkdirs()
            val outFile = File(outDir, "digimon_state_bundle_$stamp.zip")
            val reportBytes = buildDebugReport().toByteArray(Charsets.UTF_8)
            val rawSnapshotBytes = buildRawSnapshotReport().toByteArray(Charsets.UTF_8)
            val dataDir = File(applicationInfo.dataDir)
            val sharedPrefsDir = File(dataDir, "shared_prefs")
            val filesDir = File(dataDir, "files")
            val exportedEntries = ArrayList<String>()

            ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
                fun addEntry(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                    exportedEntries.add(name)
                }

                addEntry("debug_report.txt", reportBytes)
                addEntry("raw_snapshot.txt", rawSnapshotBytes)

                listOf(
                    "digimon_state.xml",
                    "battle_transport_settings.xml",
                    "battle_state.xml",
                    "emulator_audio_settings.xml",
                    "emulator_debug_settings.xml",
                    "emulator_timing_settings.xml"
                ).forEach { name ->
                    val file = File(sharedPrefsDir, name)
                    if (file.exists()) {
                        addEntry("shared_prefs/$name", file.readBytes())
                    }
                }

                listOf("current_rom_name", "current_rom.bin").forEach { name ->
                    val file = File(filesDir, name)
                    if (file.exists()) {
                        addEntry("files/$name", file.readBytes())
                    }
                }
            }

            if (exportedEntries.isEmpty()) {
                Toast.makeText(this, "No save-state files found to export", Toast.LENGTH_LONG).show()
                return
            }

            shareFile(
                file = outFile,
                mimeType = "application/zip",
                subject = "Digimon Glyph Save State Bundle $stamp",
                chooserTitle = "Share save state bundle"
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export save states: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareFile(
        file: File,
        mimeType: String,
        subject: String,
        chooserTitle: String
    ) {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun buildDebugReport(): String {
        val now = System.currentTimeMillis()
        val (romName, romHash) = readCurrentRomIdentity()
        val battle = BattleStateStore.read(this)
        val battleAgeMs = if (battle.updatedAtMs == 0L) -1L else (now - battle.updatedAtMs)
        val ack = EmulatorCommandBus.readAck(this)
        val input = InputDebugState.read(this)
        val inputAgeMs = if (input.timestampMs == 0L) -1L else (now - input.timestampMs)
        val frame = FrameDebugState.snapshot()
        val frameAgeMs = if (frame.updatedAtMs == 0L) -1L else (android.os.SystemClock.uptimeMillis() - frame.updatedAtMs)
        val autosave = stateManager.getAutosaveInfo(romName, romHash)
        val slot1 = stateManager.getSlotInfo(1, romName, romHash)
        val slot2 = stateManager.getSlotInfo(2, romName, romHash)
        val slot3 = stateManager.getSlotInfo(3, romName, romHash)
        val transportType = BattleTransportSettings.getTransportType()
        val relayUrl = BattleTransportSettings.getRelayUrl()
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }
        return buildString {
            appendLine("Digimon Glyph Debug Report")
            appendLine("generated_at=${formatTime(now)}")
            appendLine()
            appendLine("[app]")
            appendLine("package=$packageName")
            appendLine("version_name=${pkgInfo.versionName}")
            appendLine("version_code=$versionCode")
            appendLine("pid=${Process.myPid()}")
            appendLine()
            appendLine("[device]")
            appendLine("manufacturer=${Build.MANUFACTURER}")
            appendLine("model=${Build.MODEL}")
            appendLine("device=${Build.DEVICE}")
            appendLine("product=${Build.PRODUCT}")
            appendLine("sdk_int=${Build.VERSION.SDK_INT}")
            appendLine("release=${Build.VERSION.RELEASE}")
            appendLine()
            appendLine("[rom]")
            appendLine("name=${romName ?: "-"}")
            appendLine("sha256=${romHash ?: "-"}")
            appendLine()
            appendLine("[settings]")
            appendLine("text_zoom_out=${DisplayRenderSettings.isTextZoomOutEnabled()}")
            appendLine("audio_enabled=${EmulatorAudioSettings.isAudioEnabled()}")
            appendLine("haptic_audio_enabled=${EmulatorAudioSettings.isHapticAudioEnabled()}")
            appendLine("debug_enabled=${EmulatorDebugSettings.isDebugEnabled()}")
            appendLine("full_accuracy_enabled=${EmulatorTimingSettings.isFullAccuracyEnabled()}")
            appendLine("clock_correction_enabled=${EmulatorTimingSettings.isClockCorrectionEnabled()}")
            appendLine("clock_correction_factor=${EmulatorTimingSettings.getClockCorrectionFactor()}")
            appendLine("battle_step_mode_enabled=${EmulatorTimingSettings.isBattleStepModeEnabled()}")
            appendLine("battle_step_slice_ms=${EmulatorTimingSettings.getBattleStepSliceMs()}")
            appendLine("battle_transport=$transportType")
            appendLine("battle_relay_url=${if (relayUrl.isBlank()) "-" else relayUrl}")
            appendLine()
            appendLine("[battle_state]")
            appendLine("status=${battle.status}")
            appendLine("role=${battle.role}")
            appendLine("peer=${battle.peerName ?: "-"}")
            appendLine("updated_at_ms=${battle.updatedAtMs}")
            appendLine("age_ms=$battleAgeMs")
            appendLine("message=${battle.message}")
            appendLine()
            appendLine("[command_ack]")
            appendLine("id=${ack?.id ?: 0L}")
            appendLine("timestamp_ms=${ack?.timestampMs ?: 0L}")
            appendLine("status=${ack?.status ?: "-"}")
            appendLine()
            appendLine("[saves]")
            appendLine("autosave_exists=${autosave.exists} ts=${autosave.timestampMs} rom=${autosave.romName ?: "-"} hash=${autosave.romHash ?: "-"} seq=${stateManager.getAutosaveSeq(romName, romHash)}")
            appendLine("slot1_exists=${slot1.exists} ts=${slot1.timestampMs} rom=${slot1.romName ?: "-"} hash=${slot1.romHash ?: "-"} seq=${stateManager.getSlotSeq(1, romName, romHash)}")
            appendLine("slot2_exists=${slot2.exists} ts=${slot2.timestampMs} rom=${slot2.romName ?: "-"} hash=${slot2.romHash ?: "-"} seq=${stateManager.getSlotSeq(2, romName, romHash)}")
            appendLine("slot3_exists=${slot3.exists} ts=${slot3.timestampMs} rom=${slot3.romName ?: "-"} hash=${slot3.romHash ?: "-"} seq=${stateManager.getSlotSeq(3, romName, romHash)}")
            appendLine()
            appendLine("[input_debug]")
            appendLine("snapshot_age_ms=$inputAgeMs")
            appendLine("mode=${input.mode}")
            appendLine("pitch=${input.pitchDeg} roll=${input.rollDeg}")
            appendLine("linear=${input.linearX},${input.linearY},${input.linearZ}")
            appendLine("filtered=${input.filteredX},${input.filteredY},${input.filteredZ}")
            appendLine("pending_axis=${input.pendingAxis} pending_dir=${input.pendingDir} pending_age_ms=${input.pendingAgeMs}")
            appendLine("last_trigger_button=${input.lastTriggerButton} last_trigger_age_ms=${if (input.lastTriggerAtMs == 0L) -1L else now - input.lastTriggerAtMs}")
            appendLine("buttonA=${input.buttonAActive} latchedByB=${input.buttonALatchedByB}")
            appendLine("buttonB=${input.buttonBActive} glyphPhysicalDown=${input.glyphPhysicalDown}")
            appendLine("buttonC=${input.buttonCActive} latchedByB=${input.buttonCLatchedByB}")
            appendLine()
            appendLine("[frame_debug]")
            appendLine("updated_at_ms=${frame.updatedAtMs}")
            appendLine("age_ms=$frameAgeMs")
            appendLine("has_glyph_frame=${frame.glyphFrame != null}")
            appendLine("has_full_frame=${frame.fullFrame != null}")
            appendLine()
            appendLine("[adb_hint]")
            appendLine("adb logcat -d | grep -E \"DigimonGlyphToy|BattleLinkManager|InternetBattleTransport|AndroidRuntime\"")
        }
    }

    private fun loadRomFromUri(uri: Uri) {
        val request = EmulatorStatus.beginImport() ?: return
        importRequest = request
        updateStatus()
        importWorker = Thread({
            try {
                val fileName = getFileName(uri)
                val candidates = contentResolver.openInputStream(uri)?.use {
                    com.digimon.glyph.emulator.RomImport.read(it, fileName)
                } ?: error("Cannot open ROM")
                runOnUiThread {
                    if (!canFinishImport(request)) return@runOnUiThread
                    if (candidates.size == 1) importCandidate(candidates.single(), request)
                    else {
                        EmulatorStatus.importResult(request, EmulatorStatus.Import.CHOOSING, "Choose a device from the archive")
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Choose a ROM")
                            .setItems(candidates.map { "${it.device.id} — ${it.name}" }.toTypedArray()) { _, index ->
                                if (canFinishImport(request)) importCandidate(candidates[index], request)
                            }
                            .setNegativeButton("Cancel") { _, _ -> cancelImport() }
                            .setOnCancelListener { cancelImport() }.show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (canFinishImport(request)) EmulatorStatus.importResult(request, EmulatorStatus.Import.FAILED, "Import failed: ${e.message}")
                }
            }
        }, "ROM-import").also { it.start() }
    }

    private fun canFinishImport(request: Long): Boolean {
        val state = EmulatorStatus.snapshot()
        return !isFinishing && !isDestroyed && state.request == request && state.busy
    }

    private fun cancelImport() {
        val status = EmulatorStatus.snapshot()
        if (status.request == importRequest && status.importing in listOf(EmulatorStatus.Import.SCANNING, EmulatorStatus.Import.CHOOSING)) {
            importWorker?.interrupt()
            EmulatorStatus.importResult(importRequest, EmulatorStatus.Import.CANCELLED, "Import cancelled; previous device kept")
        }
    }

    private fun importCandidate(candidate: com.digimon.glyph.emulator.RomImport.Candidate, request: Long) {
        var staged: File? = null
        try {
            EmulatorStatus.importResult(request, EmulatorStatus.Import.SWITCHING, "Saving current pet and loading ${candidate.device.id}…")
            staged = File.createTempFile("rom-import-", ".bin", filesDir)
            staged.writeBytes(candidate.bytes)
            val name = if (candidate.device.id == "UnknownClassic")
                candidate.name.substringAfterLast('/').substringBeforeLast('.') else candidate.device.id
            val intent = Intent(this, DigimonGlyphToyService::class.java)
                .setAction(DigimonGlyphToyService.ACTION_IMPORT_ROM)
                .putExtra("staged_rom", staged.name).putExtra("rom_name", name).putExtra("import_request", request)
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            staged?.delete()
            EmulatorStatus.importResult(request, EmulatorStatus.Import.FAILED, "Import failed: ${e.message}")
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun confirmReset() {
        val status = EmulatorStatus.snapshot()
        if (status.name == null) return
        AlertDialog.Builder(this).setTitle(getString(R.string.reset_pet))
            .setMessage(getString(R.string.reset_pet_message, status.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.back_up_first) { _, _ ->
                findViewById<View>(R.id.layout_save_tools).visibility = View.VISIBLE
                Toast.makeText(this, R.string.back_up_hint, Toast.LENGTH_LONG).show()
            }
            .setPositiveButton(R.string.reset_pet) { _, _ ->
                if (EmulatorStatus.snapshot().hash == status.hash) sendCommand(EmulatorCommandBus.CMD_FULL_RESET, 0, "Reset pet")
            }.show()
    }

    private fun confirmLoad(slot: Int) {
        val status = EmulatorStatus.snapshot()
        val info = if (slot == 0) stateManager.getAutosaveInfo(status.name, status.hash)
            else stateManager.getSlotInfo(slot, status.name, status.hash)
        if (!info.exists) { Toast.makeText(this, "No save in this slot", Toast.LENGTH_SHORT).show(); return }
        val label = if (slot == 0) "Autosave" else "Slot $slot"
        AlertDialog.Builder(this).setTitle("Load $label?")
            .setMessage("${status.name}\n${formatTime(info.timestampMs)}\nThis replaces current progress with this save.")
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Load") { _, _ ->
                if (EmulatorStatus.snapshot().hash == status.hash)
                    sendCommand(if (slot == 0) EmulatorCommandBus.CMD_LOAD_AUTOSAVE else EmulatorCommandBus.CMD_LOAD_SLOT, slot, "Load $label")
            }.show()
    }

    private fun showDPowerControls() {
        if (!EmulatorStatus.snapshot().isDPower) return
        dpowerMenu?.dismiss()
        val menu = com.digimon.glyph.ui.DPowerMenu(this,
            startPet = { if (EmulatorStatus.snapshot().isDPower && !EmulatorStatus.snapshot().busy) startBackendService() },
            stopPet = { if (EmulatorStatus.snapshot().isDPower && !EmulatorStatus.snapshot().busy) stopBackendService() },
            scanCard = { nfcCardScanner.scan() },
            press = { index ->
                val state = EmulatorStatus.snapshot()
                if (state.isDPower && !state.busy && state.run != EmulatorStatus.Run.FROZEN) {
                    val intent = Intent(this, DigimonGlyphToyService::class.java)
                        .setAction(DigimonGlyphToyService.ACTION_WIDGET_BUTTON)
                        .putExtra(DigimonGlyphToyService.EXTRA_WIDGET_BUTTON, 20 + index)
                    ContextCompat.startForegroundService(this, intent)
                }
            },
            walkingClick = {
                val walking = com.digimon.glyph.input.WalkingInput
                if (walking.enabled(this) && walking.permitted(this)) walking.setEnabled(this, false)
                else if (walking.permitted(this)) walking.setEnabled(this, true)
                else walkingPermission.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                updateWalkingButton()
            })
        dpowerMenu = menu
        walkingButton = menu.walkingButton
        updateWalkingButton()
        menu.show {
            nfcCardScanner.stop()
            dpowerMenu = null
            walkingButton = null
            findViewById<Switch>(R.id.switch_audio).isChecked = EmulatorAudioSettings.isAudioEnabled()
            findViewById<Switch>(R.id.switch_haptic).isChecked = EmulatorAudioSettings.isHapticAudioEnabled()
            findViewById<Switch>(R.id.switch_power_save).isChecked = EmulatorTimingSettings.isPowerSaveEnabled()
            findViewById<Switch>(R.id.switch_exact_timing).isChecked = EmulatorTimingSettings.isFullAccuracyEnabled()
            updateStatus()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "rom.bin"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
