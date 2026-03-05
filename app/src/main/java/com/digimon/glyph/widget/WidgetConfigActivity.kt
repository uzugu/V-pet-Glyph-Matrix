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
 * Lets them pick a display skin. Styled to match RomLoaderActivity's cyber terminal aesthetic.
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
        val teko = Typeface.createFromAsset(assets, "fonts/Teko.ttf")
        val shareTech = Typeface.createFromAsset(assets, "fonts/ShareTechMono-Regular.ttf")

        val COLOR_BG       = Color.parseColor("#0A0D0F")
        val COLOR_CARD     = Color.parseColor("#111418")
        val COLOR_CYAN     = Color.parseColor("#00E5FF")
        val COLOR_YELLOW   = Color.parseColor("#FFD600")
        val COLOR_WHITE    = Color.parseColor("#E8EEF2")
        val COLOR_DIM      = Color.parseColor("#556070")
        val COLOR_ACCENT   = Color.parseColor("#1A2228")

        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BG)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 48)
        }
        scroll.addView(root)

        // ── Header ──────────────────────────────────────────────────────
        val headerFrame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 32 }
            layoutParams = lp
        }

        val titleLabel = TextView(this).apply {
            text = "[ SELECT DISPLAY SKIN ]"
            textSize = 26f
            typeface = teko
            gravity = Gravity.CENTER
            setTextColor(COLOR_CYAN)
            letterSpacing = 0.05f
        }
        headerFrame.addView(titleLabel)

        val subtitleLabel = TextView(this).apply {
            text = "◈ WIDGET CONFIGURATION INTERFACE"
            textSize = 12f
            typeface = shareTech
            gravity = Gravity.CENTER
            setTextColor(COLOR_DIM)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
            layoutParams = lp
        }
        headerFrame.addView(subtitleLabel)

        // Cyan divider
        val divider = View(this).apply {
            setBackgroundColor(COLOR_CYAN)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
            ).apply { topMargin = 16 }
            layoutParams = lp
        }
        headerFrame.addView(divider)

        root.addView(headerFrame)

        // ── Skin cards ──────────────────────────────────────────────────
        data class SkinEntry(
            val skin: WidgetPrefs.Skin,
            val name: String,
            val desc: String,
            val accentColor: Int = COLOR_CYAN
        )

        val skins = listOf(
            SkinEntry(WidgetPrefs.Skin.GLYPH_MATRIX,   "GLYPH MATRIX",   "Nothing Phone LED dot grid — white on black",        COLOR_CYAN),
            SkinEntry(WidgetPrefs.Skin.DIGIVICE_V3,    "DIGIVICE  V3",   "Orange shell — classic V3 colorway",                  Color.parseColor("#F35C00")),
            SkinEntry(WidgetPrefs.Skin.DIGIVICE_V1,    "DIGIVICE  V1",   "Gray/blue shell — 1997 original colorway",            Color.parseColor("#5A6070")),
            SkinEntry(WidgetPrefs.Skin.DIGIVICE_V2,    "DIGIVICE  V2",   "Black/red shell — V2 colorway",                       Color.parseColor("#CC2200")),
            SkinEntry(WidgetPrefs.Skin.DIGIVICE_WHITE, "DIGIVICE  WHITE","White/gold shell — V-Tamer colorway",                 Color.parseColor("#D4A800")),
            SkinEntry(WidgetPrefs.Skin.BRICK_V1,       "BRICK  V-PET V1","Red brick shell — faithful SVG replica of 1997 V-Pet",Color.parseColor("#B24A4A")),
            SkinEntry(WidgetPrefs.Skin.BRICK_V3,       "BRICK  V-PET V3","Orange brick shell — SVG replica of V3 device",       Color.parseColor("#F35C00")),
            SkinEntry(WidgetPrefs.Skin.CYBER_HUD,      "CYBER HUD",      "Terminal panel — cyber aesthetic with LCD overlay",   COLOR_CYAN),
            SkinEntry(WidgetPrefs.Skin.DEBUG,          "DEBUG",          "Raw 32×24 LCD — uncropped pixel dump",                COLOR_DIM),
        )

        for (entry in skins) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(COLOR_CARD)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }
                layoutParams = lp
            }

            // Left accent strip (skin-colored)
            val strip = View(this).apply {
                setBackgroundColor(entry.accentColor)
                layoutParams = LinearLayout.LayoutParams(4, LinearLayout.LayoutParams.MATCH_PARENT)
            }
            card.addView(strip)

            // Text content
            val textBlock = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(16, 16, 8, 16)
                }
                layoutParams = lp
            }

            val nameView = TextView(this).apply {
                text = entry.name
                textSize = 20f
                typeface = teko
                setTextColor(COLOR_WHITE)
                letterSpacing = 0.05f
            }
            textBlock.addView(nameView)

            val descView = TextView(this).apply {
                text = entry.desc
                textSize = 12f
                typeface = shareTech
                setTextColor(COLOR_DIM)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4 }
                layoutParams = lp
            }
            textBlock.addView(descView)

            card.addView(textBlock)

            // Select button
            val btn = Button(this).apply {
                text = "[ INIT ]"
                textSize = 14f
                typeface = teko
                setTextColor(COLOR_CYAN)
                setBackgroundColor(COLOR_ACCENT)
                letterSpacing = 0.05f
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(4, 12, 12, 12)
                    gravity = Gravity.CENTER_VERTICAL
                }
                layoutParams = lp
                setOnClickListener { selectSkin(entry.skin) }
            }
            card.addView(btn)

            root.addView(card)
        }

        // ── Footer ──────────────────────────────────────────────────────
        val footerDivider = View(this).apply {
            setBackgroundColor(COLOR_CYAN)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { topMargin = 8; bottomMargin = 16 }
            layoutParams = lp
        }
        root.addView(footerDivider)

        val footerLabel = TextView(this).apply {
            text = "► SELECTION PERSISTS PER-WIDGET"
            textSize = 11f
            typeface = shareTech
            gravity = Gravity.CENTER
            setTextColor(COLOR_DIM)
        }
        root.addView(footerLabel)

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
