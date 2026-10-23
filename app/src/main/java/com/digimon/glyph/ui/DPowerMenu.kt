package com.digimon.glyph.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.res.ResourcesCompat
import com.digimon.glyph.R
import com.digimon.glyph.emulator.*
import com.digimon.glyph.input.WalkingInput

/** Digivice-style control console; settings share the existing runtime stores. */
class DPowerMenu(
    private val context: Context,
    private val startPet: () -> Unit,
    private val stopPet: () -> Unit,
    private val press: (Int) -> Unit,
    private val scanCard: () -> Unit,
    private val walkingClick: () -> Unit,
) {
    private val cyan = Color.rgb(66, 221, 227)
    private val amber = Color.rgb(255, 196, 74)
    private val ink = Color.rgb(230, 241, 245)
    private val muted = Color.rgb(156, 178, 190)
    private val bgColor = Color.rgb(9, 18, 26)
    private val handler = Handler(Looper.getMainLooper())
    private val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(12)); setBackgroundColor(bgColor) }
    private val controls = mutableListOf<Button>()
    private val status = text("", 14f, amber)
    private val rateLabel = text("", 15f)
    val walkingButton: Button
    private var dialog: AlertDialog? = null
    private val refresh = object : Runnable {
        override fun run() {
            val state = EmulatorStatus.snapshot()
            status.text = context.getString(R.string.dp_runtime, state.run.name.lowercase().replaceFirstChar { it.uppercase() })
            val canPress = state.isDPower && !state.busy && state.run != EmulatorStatus.Run.FROZEN
            controls.forEach { it.isEnabled = canPress; it.alpha = if (canPress) 1f else .4f }
            handler.postDelayed(this, 500)
        }
    }

    init {
        panel.addView(text(context.getString(R.string.dp_console_kicker), 12f, amber).apply {
            typeface = ResourcesCompat.getFont(context, R.font.share_tech_mono)
            letterSpacing = .12f
        })
        panel.addView(text(context.getString(R.string.dp_console_title), 34f).apply {
            typeface = ResourcesCompat.getFont(context, R.font.teko)
        })
        panel.addView(status)

        val device = section(R.string.dp_device_section)
        row(device, listOf(context.getString(R.string.dp_start) to startPet, context.getString(R.string.dp_stop) to stopPet))
        val labels = context.resources.getStringArray(R.array.dpower_buttons)
        for (pair in listOf(0 to 1, 2 to 3, 4 to 5)) {
            row(device, listOf(labels[pair.first] to { press(pair.first) }, (if (pair.second == 5) context.getString(R.string.nfc_scan_card) else labels[pair.second]) to { if (pair.second == 5) scanCard() else press(pair.second) }), manual = true)
        }
        device.addView(text(context.getString(R.string.nfc_menu_help), 14f, muted))
        val help = text(context.getString(R.string.dpower_gesture_help), 14f, muted).apply { visibility = View.GONE }
        device.addView(button(context.getString(R.string.dp_gesture_toggle)) { help.visibility = if (help.visibility == View.VISIBLE) View.GONE else View.VISIBLE })
        device.addView(help)

        val steps = section(R.string.dp_steps_section)
        toggle(steps, R.string.dp_auto_step, DPowerOptions.autoStepEnabled) { DPowerOptions.setAutoStep(context, it) }
        steps.addView(rateLabel)
        fun updateRate() { rateLabel.text = context.getString(R.string.dp_step_rate, DPowerOptions.halfStepsPerSecond / 2.0) }
        updateRate()
        steps.addView(SeekBar(context).apply {
            max = 7; progress = DPowerOptions.halfStepsPerSecond - 1
            contentDescription = context.getString(R.string.dp_rate_accessibility)
            progressTintList = ColorStateList.valueOf(cyan); thumbTintList = ColorStateList.valueOf(amber)
            minimumHeight = dp(48)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, user: Boolean) {
                    if (user) { DPowerOptions.setStepRate(context, value + 1); updateRate() }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        })
        steps.addView(text(context.getString(R.string.dp_auto_help), 14f, muted))
        walkingButton = button("") { walkingClick() }.apply { isEnabled = WalkingInput.available(context) }
        steps.addView(walkingButton)
        steps.addView(text(context.getString(if (WalkingInput.available(context)) R.string.walking_help else R.string.walking_unavailable), 14f, muted))

        val display = section(R.string.dp_display_section)
        toggle(display, R.string.dpower_clock, DisplayRenderSettings.isDPowerClockEnabled()) { DisplayRenderSettings.setDPowerClockEnabled(context, it) }
        display.addView(text(context.getString(R.string.dpower_clock_help), 14f, muted))
        toggle(display, R.string.dpower_fit, DisplayRenderSettings.isDPowerFitEnabled()) { DisplayRenderSettings.setDPowerFitEnabled(context, it) }
        display.addView(text(context.getString(R.string.dp_fit_help), 14f, muted))
        display.addView(text(context.getString(R.string.dp_brightness), 16f, amber))
        val preview = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(Color.BLACK) }
        val petSample = text(context.getString(R.string.dp_pet_preview), 17f).apply { typeface = Typeface.MONOSPACE }
        val clockSample = text("12 / 34", 17f).apply { typeface = Typeface.MONOSPACE; gravity = android.view.Gravity.END }
        preview.addView(petSample, LinearLayout.LayoutParams(0, -2, 1f)); preview.addView(clockSample, LinearLayout.LayoutParams(0, -2, 1f))
        fun previewBrightness() { petSample.setTextColor(DPowerOptions.brightness.petColor); clockSample.setTextColor(DPowerOptions.brightness.clockColor) }
        previewBrightness()
        display.addView(preview)
        val brightnessLabels = context.resources.getStringArray(R.array.dp_brightness_presets)
        val group = RadioGroup(context)
        DPowerBrightness.entries.forEachIndexed { index, profile ->
            group.addView(RadioButton(context).apply {
                id = View.generateViewId(); text = brightnessLabels[index]; setTextColor(ink); textSize = 15f
                minHeight = dp(48)
                buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(cyan, muted))
                isChecked = DPowerOptions.brightness == profile
                setOnCheckedChangeListener { _, checked -> if (checked) { DPowerOptions.setBrightness(context, profile); previewBrightness() } }
            })
        }
        display.addView(group)
        toggle(display, R.string.dp_smooth_display, EmulatorTimingSettings.isFullAccuracyEnabled()) { EmulatorTimingSettings.setFullAccuracyEnabled(context, it) }
        display.addView(text(context.getString(R.string.dp_smooth_help), 14f, muted))

        val sound = section(R.string.dp_sound_section)
        toggle(sound, R.string.dp_audio, EmulatorAudioSettings.isAudioEnabled()) { EmulatorAudioSettings.setAudioEnabled(context, it) }
        toggle(sound, R.string.dp_haptics, EmulatorAudioSettings.isHapticAudioEnabled()) { EmulatorAudioSettings.setHapticAudioEnabled(context, it) }
        sound.addView(text(context.getString(R.string.dp_saved_help), 14f, muted))
    }

    fun dismiss() { dialog?.dismiss() }

    fun show(onDismiss: () -> Unit) {
        val scroll = ScrollView(context).apply { isFillViewport = true; addView(panel) }
        dialog = AlertDialog.Builder(context).setView(scroll).setPositiveButton(R.string.dp_close, null)
            .setOnDismissListener { handler.removeCallbacks(refresh); onDismiss(); dialog = null }.create()
        dialog!!.show()
        dialog!!.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
            setLayout(context.resources.displayMetrics.widthPixels - dp(24), (context.resources.displayMetrics.heightPixels * .88).toInt())
        }
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(cyan)
        handler.post(refresh)
    }
    private fun section(title: Int): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            background = GradientDrawable().apply { setColor(Color.rgb(15, 32, 43)); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.rgb(39, 83, 96)) }
        }
        panel.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        card.addView(text(context.getString(title), 22f, cyan).apply { typeface = ResourcesCompat.getFont(context, R.font.teko) })
        return card
    }
    private fun toggle(parent: LinearLayout, label: Int, initial: Boolean, changed: (Boolean) -> Unit) {
        parent.addView(SwitchCompat(context).apply {
            text = context.getString(label); textSize = 15f; setTextColor(ink); minHeight = dp(52)
            isChecked = initial; setPadding(0, dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, checked -> changed(checked) }
        })
    }
    private fun row(parent: LinearLayout, actions: List<Pair<String, () -> Unit>>, manual: Boolean = false) {
        val row = LinearLayout(context)
        actions.forEachIndexed { index, (label, action) ->
            val button = button(label, action)
            if (manual) controls.add(button)
            row.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = dp(8); bottomMargin = dp(8) })
        }
        parent.addView(row)
    }
    private fun button(label: String, action: () -> Unit) = androidx.appcompat.widget.AppCompatButton(context).apply {
        text = label; isAllCaps = false; textSize = 15f; setTextColor(cyan); minHeight = dp(48)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        backgroundTintList = null
        background = GradientDrawable().apply { setColor(Color.rgb(12, 45, 57)); cornerRadius = dp(6).toFloat(); setStroke(dp(1), Color.rgb(50, 115, 130)) }
        setOnClickListener { action() }
    }
    private fun text(value: String, size: Float, color: Int = ink) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(0, dp(4), 0, dp(6))
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
