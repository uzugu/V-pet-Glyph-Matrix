package com.digimon.glyph

import android.graphics.Typeface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        val teko = Typeface.createFromAsset(assets, "fonts/Teko.ttf")
        val shareTech = Typeface.createFromAsset(assets, "fonts/ShareTechMono-Regular.ttf")

        val mainTitle = findViewById<TextView>(R.id.main_title)
        val titleShadowCyan = findViewById<TextView>(R.id.title_shadow_cyan)
        val titleShadowPink = findViewById<TextView>(R.id.title_shadow_pink)
        val textStartHere = findViewById<TextView>(R.id.text_start_here)
        val textControls = findViewById<TextView>(R.id.text_controls)
        val textNeeds = findViewById<TextView>(R.id.text_needs)
        val textDailyCare = findViewById<TextView>(R.id.text_daily_care)
        val textMistakes = findViewById<TextView>(R.id.text_mistakes)
        val textLifeCycle = findViewById<TextView>(R.id.text_life_cycle)
        val textSpoilerIntro = findViewById<TextView>(R.id.text_spoiler_intro)
        val textSpoilerRules = findViewById<TextView>(R.id.text_spoiler_rules)
        val textSources = findViewById<TextView>(R.id.text_sources)
        val layoutEvolutionSpoilers = findViewById<LinearLayout>(R.id.layout_evolution_spoilers)
        val btnSpoilers = findViewById<Button>(R.id.btn_toggle_spoilers)
        val btnOpenEvolutionGuide = findViewById<Button>(R.id.btn_open_evolution_guide)
        val btnBack = findViewById<Button>(R.id.btn_back)

        mainTitle.typeface = teko
        titleShadowCyan.typeface = teko
        titleShadowPink.typeface = teko
        btnSpoilers.typeface = teko
        btnOpenEvolutionGuide.typeface = teko
        btnBack.typeface = teko

        listOf(
            textStartHere,
            textControls,
            textNeeds,
            textDailyCare,
            textMistakes,
            textLifeCycle,
            textSpoilerIntro,
            textSpoilerRules,
            textSources
        ).forEach { it.typeface = shareTech }

        btnSpoilers.setOnClickListener {
            if (layoutEvolutionSpoilers.visibility == View.VISIBLE) {
                layoutEvolutionSpoilers.visibility = View.GONE
                btnSpoilers.text = "SHOW EVOLUTION SPOILERS"
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Show Evolution Spoilers?")
                .setMessage("This section unlocks the full monster roster and route notes for V1, V2, and V3. Continue?")
                .setNegativeButton("Keep Hidden", null)
                .setPositiveButton("Show") { _, _ ->
                    layoutEvolutionSpoilers.visibility = View.VISIBLE
                    btnSpoilers.text = "HIDE EVOLUTION SPOILERS"
                }
                .show()
        }

        btnOpenEvolutionGuide.setOnClickListener {
            startActivity(
                Intent(this, EvolutionGuideActivity::class.java)
                    .putExtra(EvolutionGuideActivity.EXTRA_VERSION_HINT, intent.getStringExtra(EvolutionGuideActivity.EXTRA_VERSION_HINT))
            )
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}
