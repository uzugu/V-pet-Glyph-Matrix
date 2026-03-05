package com.digimon.glyph

import android.Manifest
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
import android.os.Build
import android.os.Bundle
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
import com.digimon.glyph.emulator.EmulatorAudioSettings
import com.digimon.glyph.emulator.EmulatorCommandBus
import com.digimon.glyph.emulator.EmulatorDebugSettings
import com.digimon.glyph.emulator.EmulatorTimingSettings
import com.digimon.glyph.emulator.FrameDebugState
import com.digimon.glyph.emulator.DisplayRenderSettings
import com.digimon.glyph.emulator.StateManager
import com.digimon.glyph.input.InputDebugState
import java.io.File
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

    private lateinit var statusText: TextView
    private lateinit var autosaveText: TextView
    private lateinit var commandStatusText: TextView
    private lateinit var debugText: TextView
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
    private lateinit var inputClockFactor: EditText
    private var lastFrameUpdateMs: Long = -1L
    private var lastAckId: Long = 0L
    private var lastAckText: String = "-"
    private var pendingNearbyAction: (() -> Unit)? = null
    private val stateManager by lazy { StateManager(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val debugRefresh = object : Runnable {
        override fun run() {
            renderDebugState()
            mainHandler.postDelayed(this, 120L)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRenderSettings.init(this)
        EmulatorAudioSettings.init(this)
        EmulatorDebugSettings.init(this)
        EmulatorTimingSettings.init(this)
        BattleStateStore.init(this)
        BattleTransportSettings.init(this)

        setContentView(R.layout.activity_rom_loader)

        val teko = Typeface.createFromAsset(assets, "fonts/Teko.ttf")
        val shareTech = Typeface.createFromAsset(assets, "fonts/ShareTechMono-Regular.ttf")

        val mainTitle = findViewById<TextView>(R.id.main_title)
        statusText = findViewById(R.id.statusText)
        val btnPickRom = findViewById<Button>(R.id.btn_pick_rom)
        val btnStartEmu = findViewById<Button>(R.id.btn_start_emu)
        val btnStopEmu = findViewById<Button>(R.id.btn_stop_emu)
        digimonStatsText = findViewById(R.id.digimonStatsText)
        
        val switchZoom = findViewById<Switch>(R.id.switch_zoom)
        val switchAudio = findViewById<Switch>(R.id.switch_audio)
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
        val switchClockCorrection = findViewById<Switch>(R.id.switch_clock_correction)
        val inputClockFactor = findViewById<EditText>(R.id.input_clock_factor)
        commandStatusText = findViewById(R.id.commandStatusText)
        val btnShareDebug = findViewById<Button>(R.id.btn_share_debug)
        val switchDebugTelemetry = findViewById<Switch>(R.id.switch_debug_telemetry)
        
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
            setPadding(0, 0, 0, 32)
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
        statRomValue.typeface = teko
        statEmuValue.typeface = teko

        val shareTechViews = listOf(
            statusText, switchZoom, switchAudio, switchExactTiming, switchHaptic,
            autosaveText, btnSaveAuto, btnLoadAuto, btnRestart, btnFullReset,
            btnComboAb, btnComboAc, btnComboBc,
            rbNearby, rbInternet, rbSim, rbPureEcho, rbXor, rbGlobal,
            btnBattleHost, btnBattleJoin, btnBattleStop, battleStatusText,
            battlePeerName, battleBadge, statEmuLabel,
            switchClockCorrection, inputClockFactor, commandStatusText, btnShareDebug, switchDebugTelemetry,
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
            DisplayRenderSettings.setTextZoomOutEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        switchAudio.isChecked = EmulatorAudioSettings.isAudioEnabled()
        switchAudio.setOnCheckedChangeListener { _, isChecked ->
            EmulatorAudioSettings.setAudioEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        switchExactTiming.isChecked = EmulatorTimingSettings.isExactTimingEnabled()
        switchExactTiming.setOnCheckedChangeListener { _, isChecked ->
            EmulatorTimingSettings.setExactTimingEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        switchHaptic.isChecked = EmulatorAudioSettings.isHapticAudioEnabled()
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            EmulatorAudioSettings.setHapticAudioEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        btnSaveAuto.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_SAVE_AUTOSAVE, 0, "Save autosave")
        }

        btnLoadAuto.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_LOAD_AUTOSAVE, 0, "Load autosave")
        }

        btnRestart.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_RESTART, 0, "Restart emulator")
        }

        btnFullReset.setOnClickListener {
            sendCommand(EmulatorCommandBus.CMD_FULL_RESET, 0, "Full reset")
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
            startActivity(Intent(this, TutorialActivity::class.java))
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

        switchClockCorrection.isChecked = EmulatorTimingSettings.isClockCorrectionEnabled()
        switchClockCorrection.setOnCheckedChangeListener { _, isChecked ->
            EmulatorTimingSettings.setClockCorrectionEnabled(this@RomLoaderActivity, isChecked)
        }

        inputClockFactor.setText("%.2f".format(EmulatorTimingSettings.getClockCorrectionFactor()))
        inputClockFactor.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = inputClockFactor.text.toString().toFloatOrNull()
                if (value != null && value in 0.5f..3.0f) {
                    EmulatorTimingSettings.setClockCorrectionFactor(this@RomLoaderActivity, value)
                    Toast.makeText(this@RomLoaderActivity, "Clock factor set to %.2f".format(value), Toast.LENGTH_SHORT).show()
                } else {
                    inputClockFactor.setText("%.2f".format(EmulatorTimingSettings.getClockCorrectionFactor()))
                    Toast.makeText(this@RomLoaderActivity, "Value must be 0.50-3.00", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnShareDebug.setOnClickListener {
            shareDebugReport()
        }

        switchDebugTelemetry.isChecked = EmulatorDebugSettings.isDebugEnabled()
        switchDebugTelemetry.setOnCheckedChangeListener { _, isChecked ->
            EmulatorDebugSettings.setDebugEnabled(this@RomLoaderActivity, isChecked)
            EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        }

        updateStatus()
        refreshSaveAndCommandInfo()
        renderDebugState()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(debugRefresh)
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
        super.onPause()
        mainHandler.removeCallbacks(debugRefresh)
    }

    private fun updateStatus() {
        val romFile = File(filesDir, "current_rom.bin")
        val nameFile = File(filesDir, "current_rom_name")
        if (romFile.exists()) {
            val name = if (nameFile.exists()) nameFile.readText().trim() else "unknown"
            statusText.text = "ROM loaded: $name"
            val (base, version) = parseRomNameParts(name)
            if (version != null) {
                val display = "$base $version"
                val spannable = SpannableString(display)
                spannable.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.cyber_yellow)),
                    base.length + 1,
                    display.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                statRomValue.text = spannable
            } else {
                statRomValue.text = base
            }
        } else {
            statusText.text = getString(R.string.rom_not_found)
            statRomValue.text = "—"
        }
    }

    private fun parseRomNameParts(name: String): Pair<String, String?> {
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
            digimonStatsText.text = if (state != null) {
                val name = state.info?.name ?: "UNKNOWN"
                val stage = state.info?.stage ?: "UNKNOWN STAGE"
                """
                DIGIMON RAW DATA
                ----------------
                SPECIES : $name
                STAGE   : $stage
                AGE     : ${state.age}
                WEIGHT  : ${state.weight}g
                """.trimIndent()
            } else {
                "AWAITING SIGNAL..."
            }
        }
        // Update emulator state stat card
        val emuFrameAgeMs = if (frameSnap.updatedAtMs == 0L) Long.MAX_VALUE
                            else System.currentTimeMillis() - frameSnap.updatedAtMs
        statEmuValue.text = if (emuFrameAgeMs < 2000L) "ACTIVE" else "IDLE"
        statEmuLabel.text = when {
            emuFrameAgeMs == Long.MAX_VALUE -> "► NO SIGNAL"
            emuFrameAgeMs < 2000L          -> "► FRAME  ${emuFrameAgeMs}ms AGO"
            emuFrameAgeMs < 60_000L        -> "► LAST  ${emuFrameAgeMs / 1000}s AGO"
            else                           -> "► EMULATOR STATE"
        }

        refreshSaveAndCommandInfo()

        if (!EmulatorDebugSettings.isDebugEnabled()) {
            setIndicatorState(indicatorA, "A", false, Color.parseColor("#00C853"))
            setIndicatorState(indicatorB, "B", false, Color.parseColor("#FFD600"))
            setIndicatorState(indicatorC, "C", false, Color.parseColor("#00B0FF"))
            debugText.text = "debug telemetry disabled (open Debug and enable it to view live input diagnostics)"
            return
        }

        val snap = InputDebugState.read(this)
        val ageMs = System.currentTimeMillis() - snap.timestampMs
        val frameAgeMs = if (frameSnap.updatedAtMs == 0L) Long.MAX_VALUE else (System.currentTimeMillis() - frameSnap.updatedAtMs)
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
            val lp = LinearLayout.LayoutParams(200, 120) // approx 60dp wide, 40dp high
            lp.marginEnd = 16
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
            val lp = LinearLayout.LayoutParams(200, 120)
            layoutParams = lp
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_LOAD_SLOT, slot, "Load slot $slot")
            }
        }
        row.addView(info)
        row.addView(saveBtn)
        row.addView(loadBtn)
        parent.addView(row)
        return info
    }

    private fun sendCommand(type: String, arg: Int, label: String) {
        startBackendService()
        EmulatorCommandBus.post(this, type, arg)
        Toast.makeText(this, "$label requested", Toast.LENGTH_SHORT).show()
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
        val auto = stateManager.getAutosaveInfo()
        autosaveText.text = buildSaveLine("Autosave", auto, currentRomName, currentRomHash)
        slot1Text.text = buildSaveLine("Slot 1", stateManager.getSlotInfo(1), currentRomName, currentRomHash)
        slot2Text.text = buildSaveLine("Slot 2", stateManager.getSlotInfo(2), currentRomName, currentRomHash)
        slot3Text.text = buildSaveLine("Slot 3", stateManager.getSlotInfo(3), currentRomName, currentRomHash)

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
        val romNameFile = File(filesDir, "current_rom_name")
        val romFile = File(filesDir, "current_rom.bin")
        val name = if (romNameFile.exists()) romNameFile.readText().trim() else null
        if (!romFile.exists()) return Pair(name, null)
        val hash = try {
            sha256(romFile.readBytes())
        } catch (_: Exception) {
            null
        }
        return Pair(name, hash)
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

            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                outFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Digimon Glyph Debug Report $stamp")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share debug report"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share report: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
        val frameAgeMs = if (frame.updatedAtMs == 0L) -1L else (now - frame.updatedAtMs)
        val autosave = stateManager.getAutosaveInfo()
        val slot1 = stateManager.getSlotInfo(1)
        val slot2 = stateManager.getSlotInfo(2)
        val slot3 = stateManager.getSlotInfo(3)
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
            appendLine("exact_timing_enabled=${EmulatorTimingSettings.isExactTimingEnabled()}")
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
            appendLine("autosave_exists=${autosave.exists} ts=${autosave.timestampMs} rom=${autosave.romName ?: "-"} hash=${autosave.romHash ?: "-"} seq=${stateManager.getAutosaveSeq()}")
            appendLine("slot1_exists=${slot1.exists} ts=${slot1.timestampMs} rom=${slot1.romName ?: "-"} hash=${slot1.romHash ?: "-"} seq=${stateManager.getSlotSeq(1)}")
            appendLine("slot2_exists=${slot2.exists} ts=${slot2.timestampMs} rom=${slot2.romName ?: "-"} hash=${slot2.romHash ?: "-"} seq=${stateManager.getSlotSeq(2)}")
            appendLine("slot3_exists=${slot3.exists} ts=${slot3.timestampMs} rom=${slot3.romName ?: "-"} hash=${slot3.romHash ?: "-"} seq=${stateManager.getSlotSeq(3)}")
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
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val fileName = getFileName(uri)

            val romBytes: ByteArray
            val romName: String

            if (fileName.endsWith(".zip", ignoreCase = true)) {
                // Extract .bin from zip
                val result = extractBinFromZip(inputStream.readBytes())
                if (result == null) {
                    Toast.makeText(this, "No .bin file found in zip", Toast.LENGTH_LONG).show()
                    return
                }
                romBytes = result.first
                romName = result.second
            } else {
                romBytes = inputStream.readBytes()
                romName = fileName.removeSuffix(".bin")
            }
            inputStream.close()

            // Validate ROM size (8KB = 8192 or 16KB = 16384 bytes)
            if (romBytes.size != 8192 && romBytes.size != 16384) {
                Toast.makeText(this, getString(R.string.rom_invalid), Toast.LENGTH_LONG).show()
                return
            }

            // Save to internal storage
            File(filesDir, "current_rom.bin").writeBytes(romBytes)
            File(filesDir, "current_rom_name").writeText(romName)

            Toast.makeText(this, "${getString(R.string.rom_loaded)}: $romName", Toast.LENGTH_SHORT).show()
            startBackendService()
            updateStatus()
            refreshSaveAndCommandInfo()
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading ROM: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun extractBinFromZip(zipData: ByteArray): Pair<ByteArray, String>? {
        val zis = ZipInputStream(zipData.inputStream())
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name.endsWith(".bin", ignoreCase = true)) {
                val bytes = zis.readBytes()
                val name = entry.name.removeSuffix(".bin")
                zis.close()
                return Pair(bytes, name)
            }
            entry = zis.nextEntry
        }
        zis.close()
        return null
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
