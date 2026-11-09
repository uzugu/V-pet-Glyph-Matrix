package com.digimon.glyph.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.digimon.glyph.R
import com.digimon.glyph.emulator.EmulatorCommandBus
import com.digimon.glyph.emulator.EmulatorDebugSettings
import com.digimon.glyph.emulator.StandbySettings

/** Shared by the main settings and D-Power console; uses existing preference keys. */
object StandbySettingsDialog {
    fun show(context: Context, isDPower: Boolean) {
        StandbySettings.init(context)
        val padding = (20 * context.resources.displayMetrics.density).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun help(id: Int) = TextView(context).also {
            it.setText(id)
            it.setPadding(0, padding / 2, 0, padding / 2)
            panel.addView(it)
        }
        fun refresh() = EmulatorCommandBus.post(context, EmulatorCommandBus.CMD_REFRESH_SETTINGS)
        help(R.string.standby_help)
        val alwaysLive = if (!isDPower) SwitchCompat(context).apply {
            setText(R.string.classic_always_live)
            isChecked = StandbySettings.classicAlwaysLive
            panel.addView(this)
        } else null
        val horizonLabel = if (!isDPower) TextView(context).also { panel.addView(it) } else null
        val horizon = if (!isDPower) SeekBar(context).apply {
            max = 15; progress = StandbySettings.classicHorizonMinutes / 15 - 1
            minimumHeight = (48 * context.resources.displayMetrics.density).toInt()
            panel.addView(this)
        } else null
        val warning = if (!isDPower) help(R.string.classic_exact_warning) else null
        val requestExact = if (!isDPower) android.widget.Button(context).apply {
            setText(R.string.classic_exact_allow)
            setOnClickListener {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:" + context.packageName)))
            }
            panel.addView(this)
        } else null
        if (!isDPower) help(R.string.classic_predictive_help)
        val freeze = if (isDPower) SwitchCompat(context).apply {
            setText(R.string.dp_freeze_standby)
            isChecked = StandbySettings.freezeDPower
            minHeight = (48 * context.resources.displayMetrics.density).toInt()
            panel.addView(this)
            help(R.string.dp_freeze_standby_help)
        } else null
        val intervalLabel = TextView(context)
        panel.addView(intervalLabel)
        val interval = SeekBar(context).apply {
            max = 7
            progress = StandbySettings.intervalMinutes / 15 - 1
            minimumHeight = (48 * context.resources.displayMetrics.density).toInt()
            panel.addView(this)
        }
        fun updateInterval() {
            intervalLabel.text = context.getString(if (isDPower) R.string.standby_interval else R.string.classic_fallback_interval, StandbySettings.intervalMinutes)
            interval.contentDescription = intervalLabel.text
            interval.isEnabled = freeze?.isChecked != true && alwaysLive?.isChecked != true
            horizon?.isEnabled = alwaysLive?.isChecked != true
            horizonLabel?.text = context.getString(R.string.classic_prediction_horizon, StandbySettings.classicHorizonMinutes)
            horizon?.contentDescription = horizonLabel?.text
            val exact = com.digimon.glyph.emulator.DigimonDndScheduler.canScheduleExactAlarms(context)
            val warningVisibility = if (exact || alwaysLive?.isChecked == true) android.view.View.GONE else android.view.View.VISIBLE
            warning?.visibility = warningVisibility
            requestExact?.visibility = warningVisibility
            intervalLabel.alpha = if (interval.isEnabled) 1f else .5f
        }
        updateInterval()
        interval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                if (user) {
                    StandbySettings.setIntervalMinutes(context, (value + 1) * 15)
                    updateInterval()
                    refresh()
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        freeze?.setOnCheckedChangeListener { _, enabled ->
            StandbySettings.setFreezeDPower(context, enabled)
            updateInterval()
            refresh()
        }
        alwaysLive?.setOnCheckedChangeListener { _, enabled ->
            StandbySettings.setClassicAlwaysLive(context, enabled)
            updateInterval(); refresh()
        }
        horizon?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                if (user) { StandbySettings.setClassicHorizonMinutes(context, (value + 1) * 15); updateInterval(); refresh() }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        help(R.string.standby_interval_help)
        if (isDPower && EmulatorDebugSettings.isDebugEnabled()) {
            panel.addView(SwitchCompat(context).apply {
                setText(R.string.dp_idle_skip)
                isChecked = EmulatorDebugSettings.isDPowerIdleSkippingEnabled()
                setOnCheckedChangeListener { _, enabled ->
                    EmulatorDebugSettings.setDPowerIdleSkippingEnabled(context, enabled)
                }
            })
            help(R.string.dp_idle_skip_help)
        }
        val dialog = AlertDialog.Builder(context).setTitle(R.string.standby_settings)
            .setView(ScrollView(context).apply { addView(panel) })
            .setPositiveButton(R.string.dp_close, null).show()
        val focus = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) { updateInterval(); refresh() }
        }
        dialog.window?.decorView?.viewTreeObserver?.addOnWindowFocusChangeListener(focus)
        dialog.setOnDismissListener { dialog.window?.decorView?.viewTreeObserver?.removeOnWindowFocusChangeListener(focus) }
    }
}
