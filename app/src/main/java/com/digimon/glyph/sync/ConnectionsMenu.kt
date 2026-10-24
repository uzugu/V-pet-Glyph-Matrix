package com.digimon.glyph.sync

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.digimon.glyph.RomLoaderActivity
import com.digimon.glyph.emulator.EmulatorStatus
import com.digimon.glyph.even.EvenStreamSettings

object ConnectionsMenu {
    fun show(activity: RomLoaderActivity, chooseFolder: () -> Unit) {
        val panel = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 16, 32, 16) }
        fun text(value: String) { panel.addView(TextView(activity).apply { text = value; setPadding(0, 12, 0, 16) }) }
        fun button(label: String, action: () -> Unit) { panel.addView(Button(activity).apply { text = label; setOnClickListener { action() } }) }
        panel.addView(androidx.appcompat.widget.SwitchCompat(activity).apply {
            text = "Stream to Even G2"; isChecked = EvenStreamSettings.enabled(activity)
            setOnCheckedChangeListener { _, value -> EvenStreamSettings.setEnabled(activity, value) }
        })
        text("Start your pet, then connect in the V-pet Stream EHPK. Local address: http://127.0.0.1:8766\nPairing key: ${EvenStreamSettings.token(activity)}")
        button("Copy G2 pairing key") { (activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("V-pet pairing key", EvenStreamSettings.token(activity))) }
        text("Save exchange / Syncthing\nChoose the folder Syncthing shares with your Frame. Completed snapshots appear on both devices. Import explicitly after switching devices; stop the old pet to avoid divergent progress.")
        button("Choose exchange folder", chooseFolder)
        button("Publish current save") { activity.startService(ExchangeActions.request(activity, EmulatorStatus.snapshot().hash)) }
        button("Browse synced saves") {
            Thread {
                try {
                    val records = ExchangeFolder(activity).records().filter { it.second.romHash == EmulatorStatus.snapshot().hash }
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        val parents = records.mapNotNull { it.second.parent }.toSet()
                        val tips = records.filter { it.second.id !in parents }.size
                        val labels = records.map { (_, r) -> "${r.origin} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(r.createdMs))}${if (r.id !in parents) " · branch tip" else ""}" }.toTypedArray()
                        AlertDialog.Builder(activity).setTitle(if(tips > 1) "Choose save — $tips branches" else "Choose synced save")
                            .setItems(labels) { _, index ->
                                val (uri, r) = records[index]
                                AlertDialog.Builder(activity).setTitle("Resume this save?").setMessage("${r.profile} from ${r.origin}\nThis replaces the current pet state. A local backup is saved first. Separate branches are never merged automatically.")
                                    .setPositiveButton("Back up and resume") { _, _ -> activity.startService(ExchangeActions.request(activity, r.romHash, uri)) }
                                    .setNegativeButton("Cancel", null).show()
                            }.setNegativeButton("Close", null).show()
                        if (records.isEmpty()) Toast.makeText(activity, "No valid synced saves for the loaded ROM", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) { activity.runOnUiThread { Toast.makeText(activity, e.message, Toast.LENGTH_LONG).show() } }
            }.start()
        }
        AlertDialog.Builder(activity).setTitle("Even G2 & save sync").setView(android.widget.ScrollView(activity).apply { addView(panel) }).setPositiveButton("Done", null).show()
    }
}
