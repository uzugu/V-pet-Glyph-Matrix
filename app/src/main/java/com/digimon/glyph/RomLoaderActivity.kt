package com.digimon.glyph

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Launcher activity for loading Digimon ROM files.
 * Supports both raw .bin ROMs and .zip archives containing .bin files.
 * After loading, the ROM is saved to internal storage for the Glyph Toy service to use.
 */
class RomLoaderActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val pickRom = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            loadRomFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val infoText = TextView(this).apply {
            text = buildString {
                appendLine("How to use:")
                appendLine("1. Tap 'Select ROM File' and pick a Digimon ROM (.bin or .zip)")
                appendLine("2. Open Glyph Toys Manager on your phone")
                appendLine("3. Enable 'Digimon V3' toy")
                appendLine()
                appendLine("Controls:")
                appendLine("  Tilt left = Button A (cycle)")
                appendLine("  Glyph Button = Button B (confirm)")
                appendLine("  Tilt right = Button C (cancel)")
            }
            textSize = 14f
            setPadding(0, 32, 0, 0)
        }
        layout.addView(infoText)

        setContentView(layout)
        updateStatus()
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
