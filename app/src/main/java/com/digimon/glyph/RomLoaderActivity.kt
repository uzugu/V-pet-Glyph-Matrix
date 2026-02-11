package com.digimon.glyph

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.digimon.glyph.emulator.EmulatorAudioSettings
import com.digimon.glyph.emulator.EmulatorCommandBus
import com.digimon.glyph.emulator.EmulatorDebugSettings
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
    private var lastFrameUpdateMs: Long = -1L
    private var lastAckId: Long = 0L
    private var lastAckText: String = "-"
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRenderSettings.init(this)
        EmulatorAudioSettings.init(this)
        EmulatorDebugSettings.init(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Digimon Glyph Emulator"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, 0, 32)
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

        val zoomModeSwitch = Switch(this).apply {
            text = "Auto zoom-out for menu text"
            isChecked = DisplayRenderSettings.isTextZoomOutEnabled()
            setPadding(0, 16, 0, 0)
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

        val debugSwitch = Switch(this).apply {
            text = "Emulator debug telemetry"
            isChecked = EmulatorDebugSettings.isDebugEnabled()
            setPadding(0, 8, 0, 0)
            setOnCheckedChangeListener { _, isChecked ->
                EmulatorDebugSettings.setDebugEnabled(this@RomLoaderActivity, isChecked)
                EmulatorCommandBus.post(this@RomLoaderActivity, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
            }
        }
        layout.addView(debugSwitch)

        autosaveText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 20, 0, 4)
        }
        layout.addView(autosaveText)

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
        layout.addView(autosaveButtons)

        val slotTitle = TextView(this).apply {
            text = "Manual Save Slots"
            textSize = 15f
            setPadding(0, 4, 0, 8)
        }
        layout.addView(slotTitle)

        slot1Text = addSlotRow(layout, 1)
        slot2Text = addSlotRow(layout, 2)
        slot3Text = addSlotRow(layout, 3)

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
        layout.addView(controlRow)

        commandStatusText = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 14)
        }
        layout.addView(commandStatusText)

        val infoText = TextView(this).apply {
            text = buildString {
                appendLine("How to use:")
                appendLine("1. Tap 'Select ROM File' and pick a Digimon ROM (.bin or .zip)")
                appendLine("2. Open Glyph Toys Manager on your phone")
                appendLine("3. Enable 'Digimon V3' toy")
                appendLine()
                appendLine("Controls:")
                appendLine("  Flick = Button A/C (cycle/cancel by direction)")
                appendLine("  Glyph Button = Button B (confirm)")
                appendLine("  See Live Input Debug for trigger direction and states")
            }
            textSize = 14f
            setPadding(0, 32, 0, 0)
        }
        layout.addView(infoText)

        val debugTitle = TextView(this).apply {
            text = "Live Input Debug"
            textSize = 16f
            setPadding(0, 36, 0, 12)
        }
        layout.addView(debugTitle)

        val frameTitle = TextView(this).apply {
            text = "Live Display Debug"
            textSize = 16f
            setPadding(0, 12, 0, 8)
        }
        layout.addView(frameTitle)

        val frameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 16)
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
        layout.addView(indicatorRow)

        debugText = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 32)
        }
        layout.addView(debugText)

        setContentView(layout)
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
        if (!EmulatorDebugSettings.isDebugEnabled()) {
            setIndicatorState(indicatorA, "A", false, Color.parseColor("#00C853"))
            setIndicatorState(indicatorB, "B", false, Color.parseColor("#FFD600"))
            setIndicatorState(indicatorC, "C", false, Color.parseColor("#00B0FF"))
            if (lastFrameUpdateMs != -1L) {
                lastFrameUpdateMs = -1L
                fullDebugImage.setImageBitmap(null)
                glyphDebugImage.setImageBitmap(null)
            }
            refreshSaveAndCommandInfo()
            debugText.text = "debug telemetry disabled (enable the switch above to capture live input/frame diagnostics)"
            return
        }

        val snap = InputDebugState.read(this)
        val frameSnap = FrameDebugState.snapshot()
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

        if (frameSnap.updatedAtMs != lastFrameUpdateMs) {
            lastFrameUpdateMs = frameSnap.updatedAtMs
            fullDebugImage.setImageBitmap(frameSnap.fullFrame)
            glyphDebugImage.setImageBitmap(frameSnap.glyphFrame)
        }

        refreshSaveAndCommandInfo()

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
        EmulatorCommandBus.post(this, type, arg)
        Toast.makeText(this, "$label requested", Toast.LENGTH_SHORT).show()
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
