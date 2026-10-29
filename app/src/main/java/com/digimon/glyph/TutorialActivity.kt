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

        val teko = com.digimon.glyph.LocalizedText.typeface(this, "fonts/Teko.ttf")
        val shareTech = com.digimon.glyph.LocalizedText.typeface(this, "fonts/ShareTechMono-Regular.ttf")

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
                btnSpoilers.text = getString(R.string.text_show_evolution_spoilers)
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.text_show_evolution_spoilers_alt))
                .setMessage(getString(R.string.text_this_section_unlocks_the_full_monster_roster_and_route_notes_for_v1_))
                .setNegativeButton(getString(R.string.text_keep_hidden), null)
                .setPositiveButton(getString(R.string.text_show)) { _, _ ->
                    layoutEvolutionSpoilers.visibility = View.VISIBLE
                    btnSpoilers.text = getString(R.string.text_hide_evolution_spoilers)
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
