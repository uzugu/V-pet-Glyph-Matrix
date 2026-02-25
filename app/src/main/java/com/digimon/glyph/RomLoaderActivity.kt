package com.digimon.glyph

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.digimon.glyph.battle.BattleStateStore
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
    private lateinit var fullDebugImage: ImageView
    private lateinit var glyphDebugImage: ImageView
    private lateinit var slot1Text: TextView
    private lateinit var slot2Text: TextView
    private lateinit var slot3Text: TextView
    private lateinit var battleStatusText: TextView
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

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        scrollView.addView(layout)

        val title = TextView(this).apply {
            text = "Digimon Glyph Emulator"
            textSize = 24f
            setPadding(0, 0, 0, 20)
        }
        layout.addView(title)

        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(statusText)

        val loadButton = Button(this).apply {
            text = getString(R.string.pick_rom)
            setOnClickListener {
                pickRom.launch(arrayOf(
                    "application/zip",
                    "application/octet-stream",
                    "application/x-zip-compressed",
                    "*/*"
                ))
            }
        }
        layout.addView(loadButton)

        val infoText = TextView(this).apply {
            text = buildString {
                appendLine("Quick start:")
                appendLine("1. Load a Digimon ROM (.bin or .zip)")
                appendLine("2. Start Digimon V3 from Glyph Toys (Nothing) or use widget mode")
                appendLine()
                appendLine("Controls:")
                appendLine("  Flick left/right = A/C")
                appendLine("  Flick toward/away = quick B")
                appendLine("  Hold Glyph button = B hold")
            }
            textSize = 14f
            setPadding(0, 18, 0, 18)
        }
        layout.addView(infoText)

        val screenTitle = TextView(this).apply {
            text = "Live Screens"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }
        layout.addView(screenTitle)

        val frameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 18)
        }
        val fullCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        val glyphCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        fullCol.addView(TextView(this).apply {
            text = "Full Digivice"
            textSize = 13f
            setPadding(0, 0, 0, 6)
        })
        glyphCol.addView(TextView(this).apply {
            text = "Glyph + Overlay"
            textSize = 13f
            setPadding(0, 0, 0, 6)
        })

        fullDebugImage = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            adjustViewBounds = true
            minimumHeight = 170
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        glyphDebugImage = ImageView(this).apply {
            setBackgroundColor(Color.BLACK)
            adjustViewBounds = true
            minimumHeight = 170
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        fullCol.addView(fullDebugImage)
        glyphCol.addView(glyphDebugImage)
        frameRow.addView(fullCol)
        frameRow.addView(glyphCol)
        layout.addView(frameRow)

        val zoomModeSwitch = Switch(this).apply {
            text = "Auto zoom-out for menu text"
            isChecked = DisplayRenderSettings.isTextZoomOutEnabled()
            setPadding(0, 0, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                DisplayRenderSettings.setTextZoomOutEnabled(this@RomLoaderActivity, isChecked)
                EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
            }
        }
        layout.addView(zoomModeSwitch)

        val audioSwitch = Switch(this).apply {
            text = "Emulator audio"
            isChecked = EmulatorAudioSettings.isAudioEnabled()
            setPadding(0, 8, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                EmulatorAudioSettings.setAudioEnabled(this@RomLoaderActivity, isChecked)
                EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
            }
        }
        layout.addView(audioSwitch)

        val exactTimingSwitch = Switch(this).apply {
            text = "Exact timing (higher battery)"
            isChecked = EmulatorTimingSettings.isExactTimingEnabled()
            setPadding(0, 8, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                EmulatorTimingSettings.setExactTimingEnabled(this@RomLoaderActivity, isChecked)
                EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
            }
        }
        layout.addView(exactTimingSwitch)

        val saveToolsToggle = Button(this).apply {
            text = "Show Save and Controls"
            setPadding(0, 10, 0, 8)
        }
        layout.addView(saveToolsToggle)

        val saveToolsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
        }
        layout.addView(saveToolsContainer)

        val battleToggle = Button(this).apply {
            text = "Show Battle (Beta)"
            setPadding(0, 8, 0, 8)
        }
        layout.addView(battleToggle)

        val battleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
        }
        layout.addView(battleContainer)

        val advancedToggle = Button(this).apply {
            text = "Show Advanced and Debug"
            setPadding(0, 8, 0, 8)
        }
        layout.addView(advancedToggle)

        val advancedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
        }
        layout.addView(advancedContainer)

        // Clock correction: toggle + editable speed factor
        val clockCorrectionSwitch = Switch(this).apply {
            text = "Clock speed correction"
            isChecked = EmulatorTimingSettings.isClockCorrectionEnabled()
            setPadding(0, 8, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                EmulatorTimingSettings.setClockCorrectionEnabled(this@RomLoaderActivity, isChecked)
            }
        }
        advancedContainer.addView(clockCorrectionSwitch)

        val clockFactorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 0)
        }
        val clockFactorLabel = TextView(this).apply {
            text = "Speed factor: "
            textSize = 14f
        }
        val clockFactorInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText("%.2f".format(EmulatorTimingSettings.getClockCorrectionFactor()))
            textSize = 14f
            setEms(5)
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val value = text.toString().toFloatOrNull()
                    if (value != null && value in 0.5f..3.0f) {
                        EmulatorTimingSettings.setClockCorrectionFactor(this@RomLoaderActivity, value)
                        Toast.makeText(this@RomLoaderActivity, "Clock factor set to %.2f".format(value), Toast.LENGTH_SHORT).show()
                    } else {
                        setText("%.2f".format(EmulatorTimingSettings.getClockCorrectionFactor()))
                        Toast.makeText(this@RomLoaderActivity, "Value must be 0.50-3.00", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        clockFactorLayout.addView(clockFactorLabel)
        clockFactorLayout.addView(clockFactorInput)
        advancedContainer.addView(clockFactorLayout)

        val hapticSwitch = Switch(this).apply {
            text = "Vibrate from emulator sound"
            isChecked = EmulatorAudioSettings.isHapticAudioEnabled()
            setPadding(0, 8, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                EmulatorAudioSettings.setHapticAudioEnabled(this@RomLoaderActivity, isChecked)
                EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
            }
        }
        layout.addView(hapticSwitch)

        autosaveText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 20, 0, 4)
        }
        saveToolsContainer.addView(autosaveText)

        val autosaveButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 10)
        }
        autosaveButtons.addView(Button(this).apply {
            text = "Save Now"
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_SAVE_AUTOSAVE, 0, "Save autosave")
            }
        })
        autosaveButtons.addView(Button(this).apply {
            text = "Load Auto"
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_LOAD_AUTOSAVE, 0, "Load autosave")
            }
        })
        saveToolsContainer.addView(autosaveButtons)

        val slotTitle = TextView(this).apply {
            text = "Manual Save Slots"
            textSize = 15f
            setPadding(0, 4, 0, 8)
        }
        saveToolsContainer.addView(slotTitle)

        slot1Text = addSlotRow(saveToolsContainer, 1)
        slot2Text = addSlotRow(saveToolsContainer, 2)
        slot3Text = addSlotRow(saveToolsContainer, 3)

        val controlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        controlRow.addView(Button(this).apply {
            text = "Restart"
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_RESTART, 0, "Restart emulator")
            }
        })
        controlRow.addView(Button(this).apply {
            text = "Full Reset"
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_FULL_RESET, 0, "Full reset")
            }
        })
        saveToolsContainer.addView(controlRow)

        val comboTitle = TextView(this).apply {
            text = "Combo Buttons"
            textSize = 15f
            setPadding(0, 8, 0, 8)
        }
        saveToolsContainer.addView(comboTitle)

        val comboRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        comboRow.addView(Button(this).apply {
            text = "A+B"
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_PRESS_COMBO, EmulatorCommandBus.COMBO_AB, "Combo A+B")
            }
        })
        comboRow.addView(Button(this).apply {
            text = "A+C"
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_PRESS_COMBO, EmulatorCommandBus.COMBO_AC, "Combo A+C")
            }
        })
        comboRow.addView(Button(this).apply {
            text = "B+C"
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_PRESS_COMBO, EmulatorCommandBus.COMBO_BC, "Combo B+C")
            }
        })
        saveToolsContainer.addView(comboRow)

        val battleTitle = TextView(this).apply {
            text = "Battle Mode (Beta)"
            textSize = 15f
            setPadding(0, 8, 0, 8)
        }
        battleContainer.addView(battleTitle)

        val battleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }
        battleRow.addView(Button(this).apply {
            text = "Host"
            setOnClickListener {
                runWithNearbyPermissions {
                    sendCommand(EmulatorCommandBus.CMD_BATTLE_START_HOST, 0, "Battle host")
                }
            }
        })
        battleRow.addView(Button(this).apply {
            text = "Join"
            setOnClickListener {
                runWithNearbyPermissions {
                    sendCommand(EmulatorCommandBus.CMD_BATTLE_START_JOIN, 0, "Battle join")
                }
            }
        })
        battleRow.addView(Button(this).apply {
            text = "Stop"
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_BATTLE_STOP, 0, "Battle stop")
            }
        })
        battleContainer.addView(battleRow)

        battleStatusText = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 12)
        }
        battleContainer.addView(battleStatusText)

        commandStatusText = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 14)
        }
        advancedContainer.addView(commandStatusText)

        val debugToggle = Button(this).apply {
            text = "Show Debug Telemetry"
            setPadding(0, 8, 0, 8)
        }
        advancedContainer.addView(debugToggle)

        val debugContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = LinearLayout.GONE
        }
        advancedContainer.addView(debugContainer)

        val debugSwitch = Switch(this).apply {
            text = "Emulator debug telemetry"
            isChecked = EmulatorDebugSettings.isDebugEnabled()
            setPadding(0, 0, 0, 8)
            setOnCheckedChangeListener { _, isChecked ->
                EmulatorDebugSettings.setDebugEnabled(this@RomLoaderActivity, isChecked)
                EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
            }
        }
        debugContainer.addView(debugSwitch)

        val debugTitle = TextView(this).apply {
            text = "Live Input Debug"
            textSize = 16f
            setPadding(0, 8, 0, 10)
        }
        debugContainer.addView(debugTitle)

        val indicatorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        indicatorA = buildIndicator("A")
        indicatorB = buildIndicator("B")
        indicatorC = buildIndicator("C")
        indicatorRow.addView(indicatorA)
        indicatorRow.addView(indicatorB)
        indicatorRow.addView(indicatorC)
        debugContainer.addView(indicatorRow)

        debugText = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 32)
        }
        debugContainer.addView(debugText)

        debugToggle.setOnClickListener {
            val showing = debugContainer.visibility == LinearLayout.VISIBLE
            debugContainer.visibility = if (showing) LinearLayout.GONE else LinearLayout.VISIBLE
            debugToggle.text = if (showing) "Show Debug Telemetry" else "Hide Debug Telemetry"
        }

        saveToolsToggle.setOnClickListener {
            val showing = saveToolsContainer.visibility == LinearLayout.VISIBLE
            saveToolsContainer.visibility = if (showing) LinearLayout.GONE else LinearLayout.VISIBLE
            saveToolsToggle.text = if (showing) "Show Save and Controls" else "Hide Save and Controls"
        }

        battleToggle.setOnClickListener {
            val showing = battleContainer.visibility == LinearLayout.VISIBLE
            battleContainer.visibility = if (showing) LinearLayout.GONE else LinearLayout.VISIBLE
            battleToggle.text = if (showing) "Show Battle (Beta)" else "Hide Battle (Beta)"
        }

        advancedToggle.setOnClickListener {
            val showing = advancedContainer.visibility == LinearLayout.VISIBLE
            advancedContainer.visibility = if (showing) LinearLayout.GONE else LinearLayout.VISIBLE
            advancedToggle.text = if (showing) "Show Advanced and Debug" else "Hide Advanced and Debug"
        }

        setContentView(scrollView)
        updateStatus()
        refreshSaveAndCommandInfo()
        renderDebugState()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.post(debugRefresh)
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
            statusText.text = "Current ROM: $name (${romFile.length()} bytes)"
        } else {
            statusText.text = getString(R.string.rom_not_found)
        }
    }

    private fun renderDebugState() {
        val frameSnap = FrameDebugState.snapshot()
        if (frameSnap.updatedAtMs != lastFrameUpdateMs) {
            lastFrameUpdateMs = frameSnap.updatedAtMs
            setPixelPreview(fullDebugImage, frameSnap.fullFrame)
            setPixelPreview(glyphDebugImage, frameSnap.glyphFrame)
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
        return TextView(this).apply {
            text = "$label: OFF"
            textSize = 14f
            typeface = Typeface.MONOSPACE
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

    private fun setPixelPreview(imageView: ImageView, bitmap: Bitmap?) {
        if (bitmap == null) {
            imageView.setImageDrawable(null)
            return
        }
        val drawable = BitmapDrawable(resources, bitmap)
        drawable.setFilterBitmap(false)
        drawable.setDither(false)
        imageView.setImageDrawable(drawable)
    }

    private fun addSlotRow(parent: LinearLayout, slot: Int): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 6)
        }
        val info = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            text = "Slot $slot: empty"
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
            setPadding(0, 8, 8, 8)
        }
        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener {
                sendCommand(EmulatorCommandBus.CMD_SAVE_SLOT, slot, "Save slot $slot")
            }
        }
        val loadBtn = Button(this).apply {
            text = "Load"
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
        ensureBackendServiceRunning()
        EmulatorCommandBus.post(this, type, arg)
        Toast.makeText(this, "$label requested", Toast.LENGTH_SHORT).show()
    }

    private fun ensureBackendServiceRunning() {
        try {
            // Ensure command poll loop is alive even when toy/widget binding is not active.
            val intent = Intent(this, DigimonGlyphToyService::class.java)
                .setAction(DigimonGlyphToyService.ACTION_START_WIDGET)
            startService(intent)
        } catch (_: Exception) {
            // Best-effort bootstrap; command may still work if service is already active.
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
        val peer = battle.peerName ?: "-"
        battleStatusText.text = "Battle: ${battle.status} (${battle.role})  peer=$peer  age=$battleAgeMs\n${battle.message}"
        commandStatusText.text = "Last command: $lastAckText"
    }

    private fun buildSaveLine(
        label: String,
        info: StateManager.SaveInfo,
        currentRomName: String?,
        currentRomHash: String?
    ): String {
        if (!info.exists) return "$label: empty"
        val ts = formatTime(info.timestampMs)
        val saveRom = info.romName ?: "unknown"
        val match = when {
            currentRomHash != null && info.romHash != null -> currentRomHash == info.romHash
            currentRomName != null && info.romName != null -> currentRomName == info.romName
            else -> true
        }
        val tag = if (match) "match" else "other rom"
        return "$label: $ts  [$tag]  ($saveRom)"
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
