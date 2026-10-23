package com.digimon.glyph.input

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.digimon.glyph.R
import com.digimon.glyph.emulator.EmulatorStatus

/** Explicit foreground scan. No tag contents, identifiers or credentials are read or stored. */
class NfcCardScanner(private val activity: Activity, private val sendCard: (Long) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val session = NfcScanSession()
    private var adapter: NfcAdapter? = null
    private var dialog: AlertDialog? = null
    private val timeout = Runnable { stop() }

    fun scan() {
        stop()
        val state = EmulatorStatus.snapshot()
        if (!ready(state)) { toast(R.string.nfc_pet_not_ready); return }
        val nfc = NfcAdapter.getDefaultAdapter(activity)
        if (nfc == null) { toast(R.string.nfc_unavailable); return }
        if (!nfc.isEnabled) {
            dialog = AlertDialog.Builder(activity).setTitle(R.string.nfc_title)
                .setMessage(R.string.nfc_disabled)
                .setPositiveButton(R.string.nfc_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_NFC_SETTINGS)
                    if (intent.resolveActivity(activity.packageManager) != null) activity.startActivity(intent)
                }.setNegativeButton(android.R.string.cancel, null).create()
            dialog?.show()
            return
        }
        adapter = nfc
        val token = session.arm(state.revision)
        dialog = AlertDialog.Builder(activity).setTitle(R.string.nfc_title)
            .setMessage(R.string.nfc_scan_help).setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { disableReader() }.create()
        dialog?.show()
        try {
            nfc.enableReaderMode(activity, {
                // Android invokes this on a binder thread. Recheck lifecycle/runtime on the UI thread.
                handler.post {
                    val current = EmulatorStatus.snapshot()
                    if (session.consume(token, current.revision, ready(current))) {
                        stop()
                        sendCard(current.revision)
                        toast(R.string.nfc_card_sent)
                    }
                }
            }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
            handler.postDelayed(timeout, 30_000)
        } catch (_: IllegalStateException) {
            stop(); toast(R.string.nfc_scan_failed)
        } catch (_: SecurityException) {
            stop(); toast(R.string.nfc_scan_failed)
        } catch (_: UnsupportedOperationException) {
            stop(); toast(R.string.nfc_unavailable)
        }
    }

    /** Call before Activity.onPause, and when the D-Power console closes. */
    fun stop() {
        disableReader()
        dialog?.dismiss()
        dialog = null
    }
    private fun disableReader() {
        session.cancel()
        handler.removeCallbacks(timeout)
        adapter?.let { nfc ->
            try { nfc.disableReaderMode(activity) }
            catch (_: IllegalStateException) { }
            catch (_: SecurityException) { }
            catch (_: UnsupportedOperationException) { }
        }
        adapter = null
    }
    private fun ready(state: EmulatorStatus.Snapshot) = state.isDPower && !state.busy && state.run == EmulatorStatus.Run.RUNNING
    private fun toast(message: Int) { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
}
