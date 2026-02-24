package com.digimon.glyph.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Configuration activity shown when the user adds a widget.
 * Lets them pick a display skin (Glyph Matrix, Digivice, Debug).
 */
class WidgetConfigActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the user backs out, the widget should not be created.
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(buildUI())
    }

    private fun buildUI(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "Choose Display Style"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        root.addView(title)

        val skins = listOf(
            Triple(WidgetPrefs.Skin.GLYPH_MATRIX, "Glyph Matrix", "Grid of white dots — Nothing Phone LED style"),
            Triple(WidgetPrefs.Skin.DIGIVICE, "Digivice", "Classic virtual pet device shell"),
            Triple(WidgetPrefs.Skin.DEBUG, "Debug", "Full 32x24 LCD — uncropped green pixels"),
        )

        for ((skin, name, desc) in skins) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#1E1E1E"))
                setPadding(32, 24, 32, 24)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 24 }
                layoutParams = lp
            }

            val nameView = TextView(this).apply {
                text = name
                textSize = 18f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }
            card.addView(nameView)

            val descView = TextView(this).apply {
                text = desc
                textSize = 14f
                setTextColor(Color.parseColor("#AAAAAA"))
                setPadding(0, 8, 0, 16)
            }
            card.addView(descView)

            val btn = Button(this).apply {
                text = "Select"
                setOnClickListener { selectSkin(skin) }
            }
            card.addView(btn)

            root.addView(card)
        }

        return scroll
    }

    private fun selectSkin(skin: WidgetPrefs.Skin) {
        WidgetPrefs.setSkin(this, widgetId, skin)

        // Return RESULT_OK — the launcher will call DigimonWidgetProvider.onUpdate() itself.
        // Do NOT call onUpdate() here; it tries to startService() which can crash the activity
        // before RESULT_OK is delivered, leaving the widget in "can't load" state.
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}
