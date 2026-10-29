package com.digimon.glyph.sync

import com.digimon.glyph.R
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
            text = activity.getString(R.string.text_stream_to_even_g2); isChecked = EvenStreamSettings.enabled(activity)
            setOnCheckedChangeListener { _, value -> EvenStreamSettings.setEnabled(activity, value) }
        })
        text(activity.getString(R.string.text_start_your_pet_then_connect_in_the_v_pet_stream_ehpk_local_address_h, EvenStreamSettings.token(activity)))
        button(activity.getString(R.string.text_copy_g2_pairing_key)) { (activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText(activity.getString(R.string.text_v_pet_pairing_key), EvenStreamSettings.token(activity))) }
        text(activity.getString(R.string.text_save_exchange_syncthing_nchoose_the_folder_syncthing_shares_with_you))
        button(activity.getString(R.string.text_choose_exchange_folder), chooseFolder)
        button(activity.getString(R.string.text_publish_current_save)) { activity.startService(ExchangeActions.request(activity, EmulatorStatus.snapshot().hash)) }
        button(activity.getString(R.string.text_browse_synced_saves)) {
            Thread {
                try {
                    val records = ExchangeFolder(activity).records().filter { it.second.romHash == EmulatorStatus.snapshot().hash }
                    activity.runOnUiThread {
                        if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        val parents = records.mapNotNull { it.second.parent }.toSet()
                        val tips = records.filter { it.second.id !in parents }.size
                        val labels = records.map { (_, r) -> "${r.origin} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(r.createdMs))}${if (r.id !in parents) activity.getString(R.string.text_branch_tip) else ""}" }.toTypedArray()
                        AlertDialog.Builder(activity).setTitle(if(tips > 1) activity.getString(R.string.text_choose_save_value_branches, tips) else activity.getString(R.string.text_choose_synced_save))
                            .setItems(labels) { _, index ->
                                val (uri, r) = records[index]
                                AlertDialog.Builder(activity).setTitle(activity.getString(R.string.text_resume_this_save)).setMessage(activity.getString(R.string.text_value_from_value_nthis_replaces_the_current_pet_state_a_local_backup, r.profile, r.origin))
                                    .setPositiveButton(activity.getString(R.string.text_back_up_and_resume)) { _, _ -> activity.startService(ExchangeActions.request(activity, r.romHash, uri)) }
                                    .setNegativeButton(activity.getString(R.string.text_cancel_alt), null).show()
                            }.setNegativeButton(activity.getString(R.string.text_close), null).show()
                        if (records.isEmpty()) Toast.makeText(activity, activity.getString(R.string.text_no_valid_synced_saves_for_the_loaded_rom), Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) { activity.runOnUiThread { Toast.makeText(activity, e.message, Toast.LENGTH_LONG).show() } }
            }.start()
        }
        AlertDialog.Builder(activity).setTitle(activity.getString(R.string.text_even_g2_save_sync)).setView(android.widget.ScrollView(activity).apply { addView(panel) }).setPositiveButton(activity.getString(R.string.text_done), null).show()
    }
}
