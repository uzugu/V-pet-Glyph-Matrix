package com.digimon.glyph

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.digimon.glyph.emulator.DigimonDatabase
import com.digimon.glyph.emulator.DigimonInfo
import com.digimon.glyph.emulator.EvolutionDexEntry
import com.digimon.glyph.emulator.EvolutionDexRepository
import com.digimon.glyph.emulator.EvolutionGuideRepository
import com.digimon.glyph.emulator.EvolutionProfile
import com.digimon.glyph.emulator.EvolutionRoute
import kotlin.math.roundToInt

class EvolutionGuideActivity : AppCompatActivity() {

    private data class RouteCardModel(
        val title: String,
        val requirements: List<String>,
        val note: String? = null
    )

    private lateinit var teko: Typeface
    private lateinit var shareTech: Typeface
    private lateinit var btnV1: Button
    private lateinit var btnV2: Button
    private lateinit var btnV3: Button
    private lateinit var textGuideSubtitle: TextView
    private lateinit var textGuideRules: TextView
    private lateinit var layoutStageSections: LinearLayout

    private var currentVersion = "V1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_evolution_guide)

        teko = com.digimon.glyph.LocalizedText.typeface(this, "fonts/Teko.ttf")
        shareTech = com.digimon.glyph.LocalizedText.typeface(this, "fonts/ShareTechMono-Regular.ttf")

        val mainTitle = findViewById<TextView>(R.id.main_title)
        val titleShadowCyan = findViewById<TextView>(R.id.title_shadow_cyan)
        val titleShadowPink = findViewById<TextView>(R.id.title_shadow_pink)
        textGuideSubtitle = findViewById(R.id.text_guide_subtitle)
        textGuideRules = findViewById(R.id.text_guide_rules)
        layoutStageSections = findViewById(R.id.layout_stage_sections)
        btnV1 = findViewById(R.id.btn_v1)
        btnV2 = findViewById(R.id.btn_v2)
        btnV3 = findViewById(R.id.btn_v3)
        val btnBack = findViewById<Button>(R.id.btn_back)

        listOf(mainTitle, titleShadowCyan, titleShadowPink, btnV1, btnV2, btnV3, btnBack).forEach {
            it.typeface = teko
        }
        listOf(textGuideSubtitle, textGuideRules).forEach {
            it.typeface = shareTech
        }

        currentVersion = normalizeVersion(intent.getStringExtra(EXTRA_VERSION_HINT))
        renderCurrentVersion()

        btnV1.setOnClickListener { switchVersion("V1") }
        btnV2.setOnClickListener { switchVersion("V2") }
        btnV3.setOnClickListener { switchVersion("V3") }
        btnBack.setOnClickListener { finish() }
    }

    private fun switchVersion(version: String) {
        if (currentVersion == version) return
        currentVersion = version
        renderCurrentVersion()
    }

    private fun renderCurrentVersion() {
        val roster = DigimonDatabase.getVersionRoster(currentVersion)
        val guide = EvolutionGuideRepository.getGuide(currentVersion)
        val dexEntries = EvolutionDexRepository.getVersionEntries(this, currentVersion)
            .associateBy { it.speciesId }
        val rosterMap = roster.toMap()

        textGuideSubtitle.text = getString(R.string.text_digital_monster_value, currentVersion)
        textGuideRules.text = buildString {
            append(getString(R.string.text_data_view_tap_a_digimon_for_exact_stats_and_route_numbers))
            append(getString(R.string.text_ncare_training_overfeed_sleep_and_battle_targets_are_shown_per_route))
            if (currentVersion in setOf("V1", "V2", "V3")) {
                append(getString(R.string.text_nperfect_ultimate_routes_show_the_humulos_high_chance_battle_targets))
            }
        }

        updateVersionButtons()
        layoutStageSections.removeAllViews()

        val stageOrder = listOf("Baby I", "Baby II", "Rookie", "Champion", "Ultimate")
        stageOrder.forEach { stage ->
            val stageEntries = roster
                .filter { (_, info) -> info.stage == stage }
                .mapNotNull { (speciesId, info) ->
                    dexEntries[speciesId]?.let { Triple(speciesId, info, it) }
                }

            if (stageEntries.isEmpty()) return@forEach
            layoutStageSections.addView(createStageSection(stage, stageEntries, rosterMap, guide.profiles))
        }
    }

    private fun updateVersionButtons() {
        val activeBg = ContextCompat.getDrawable(this, R.drawable.guide_selector_active_bg)
        val inactiveBg = ContextCompat.getDrawable(this, R.drawable.cyber_button_bg)
        val activeText = ContextCompat.getColor(this, R.color.bg_base)
        val inactiveText = ContextCompat.getColor(this, R.color.cyber_blue)

        listOf("V1" to btnV1, "V2" to btnV2, "V3" to btnV3).forEach { (code, button) ->
            val selected = currentVersion == code
            button.background = if (selected) activeBg else inactiveBg
            button.setTextColor(if (selected) activeText else inactiveText)
        }
    }

    private fun createStageSection(
        stage: String,
        entries: List<Triple<Int, DigimonInfo, EvolutionDexEntry>>,
        rosterMap: Map<Int, DigimonInfo>,
        profiles: Map<Int, EvolutionProfile>
    ): View {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@EvolutionGuideActivity, R.drawable.guide_stage_section_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }

        section.addView(TextView(this).apply {
            text = LocalizedText.display(this@EvolutionGuideActivity, stage).uppercase()
            setTextAppearance(R.style.TextAppearance_Cyber_SectionHeader)
            typeface = teko
        })

        entries.forEachIndexed { index, (speciesId, info, dex) ->
            section.addView(createDigimonCard(currentVersion, speciesId, info, dex, rosterMap, profiles))
            if (index != entries.lastIndex) {
                section.addView(space(12))
            }
        }
        return section
    }

    private fun createDigimonCard(
        version: String,
        speciesId: Int,
        info: DigimonInfo,
        dex: EvolutionDexEntry,
        rosterMap: Map<Int, DigimonInfo>,
        profiles: Map<Int, EvolutionProfile>
    ): View {
        val profile = profiles[speciesId]
        val incomingRoutes = buildIncomingRoutes(speciesId, rosterMap, profiles)
        val outgoingRoutes = buildOutgoingRoutes(profile, rosterMap)
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }

        details.addView(createStatGrid(dex))
        details.addView(
            createRouteSection(
                getString(R.string.text_get_this_form),
                incomingRoutes,
                dex.conditionText.ifBlank { getString(R.string.text_no_incoming_route_data) }
            )
        )
        details.addView(
            createRouteSection(
                getString(R.string.text_next_forms),
                outgoingRoutes,
                if (dex.evolvesToText.isEmpty()) getString(R.string.text_final_stage) else dex.evolvesToText.joinToString("\n")
            )
        )

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@EvolutionGuideActivity, R.drawable.guide_dex_card_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val sprite = PixelArtImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(124), dp(124))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val drawableName = "guide_${version.lowercase()}_${normalizeName(info.name)}"
        val drawableId = resources.getIdentifier(drawableName, "drawable", packageName)
        if (drawableId != 0) {
            sprite.setImageResource(drawableId)
        }

        val metaColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(14)
            }
            addView(TextView(this@EvolutionGuideActivity).apply {
                text = LocalizedText.display(this@EvolutionGuideActivity, info.name)
                setTextAppearance(R.style.TextAppearance_Cyber_SectionHeader)
                typeface = teko
                setTextColor(ContextCompat.getColor(this@EvolutionGuideActivity, R.color.cyber_blue))
            })
            addView(TextView(this@EvolutionGuideActivity).apply {
                text = dex.header
                setTextAppearance(R.style.TextAppearance_Cyber_Body)
                typeface = shareTech
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            })
            addView(TextView(this@EvolutionGuideActivity).apply {
                text = if (details.visibility == View.VISIBLE) getString(R.string.text_hide_data) else getString(R.string.text_show_data)
                tag = "toggle"
                setTextAppearance(R.style.TextAppearance_Cyber_Muted)
                typeface = shareTech
                setTextColor(ContextCompat.getColor(this@EvolutionGuideActivity, R.color.cyber_yellow))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            })
        }

        header.addView(sprite)
        header.addView(metaColumn)
        card.addView(header)
        card.addView(details)

        card.setOnClickListener {
            details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            val toggle = metaColumn.findViewWithTag<TextView>("toggle")
            toggle.text = if (details.visibility == View.VISIBLE) getString(R.string.text_hide_data) else getString(R.string.text_show_data)
        }
        return card
    }

    private fun buildIncomingRoutes(
        speciesId: Int,
        rosterMap: Map<Int, DigimonInfo>,
        profiles: Map<Int, EvolutionProfile>
    ): List<RouteCardModel> {
        val incoming = mutableListOf<RouteCardModel>()
        profiles.forEach { (sourceId, profile) ->
            val sourceName = rosterMap[sourceId]?.name?.let { LocalizedText.display(this, it) } ?: return@forEach
            profile.evolvesTo
                .filter { it.targetSpeciesId == speciesId }
                .forEach { route ->
                    incoming += RouteCardModel(
                        title = getString(R.string.text_from_value, sourceName.uppercase()),
                        requirements = route.requirements.map { LocalizedText.display(this, it) },
                        note = route.note?.let { LocalizedText.display(this, it) }
                    )
                }
        }
        return incoming
    }

    private fun buildOutgoingRoutes(
        profile: EvolutionProfile?,
        rosterMap: Map<Int, DigimonInfo>
    ): List<RouteCardModel> {
        if (profile == null) return emptyList()
        return profile.evolvesTo.map { route ->
            val targetName = LocalizedText.display(this,
                rosterMap[route.targetSpeciesId]?.name ?: route.title.removeSuffix(" route"))
            RouteCardModel(
                title = getString(R.string.text_to_value, targetName.uppercase()),
                requirements = route.requirements.map { LocalizedText.display(this, it) },
                note = route.note?.let { LocalizedText.display(this, it) }
            )
        }
    }

    private fun createStatGrid(dex: EvolutionDexEntry): View {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val rows = listOf(
            getString(R.string.text_activity) to dex.activity,
            getString(R.string.text_lifespan) to dex.lifespan,
            getString(R.string.text_weight) to dex.weight,
            getString(R.string.text_fullness) to dex.fullness,
            getString(R.string.text_cycle) to dex.cycle,
            getString(R.string.text_stamina) to dex.stamina,
            getString(R.string.text_injury) to dex.injury
        )
        rows.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(6)
                }
            }
            pair.forEachIndexed { index, (label, value) ->
                row.addView(createStatCell(label, value))
                if (index != pair.lastIndex) {
                    row.addView(space(8, horizontal = true))
                }
            }
            if (pair.size == 1) {
                row.addView(LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
            grid.addView(row)
        }
        return grid
    }

    private fun createStatCell(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@EvolutionGuideActivity, R.drawable.guide_route_card_bg)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(TextView(this@EvolutionGuideActivity).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_Cyber_Muted)
                typeface = shareTech
            })
            addView(TextView(this@EvolutionGuideActivity).apply {
                text = value
                setTextAppearance(R.style.TextAppearance_Cyber_Body)
                typeface = shareTech
                setTextColor(ContextCompat.getColor(this@EvolutionGuideActivity, R.color.cyber_blue))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }
    }

    private fun createLabelBodyBlock(label: String, body: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
            addView(TextView(this@EvolutionGuideActivity).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_Cyber_Muted)
                typeface = shareTech
            })
            addView(TextView(this@EvolutionGuideActivity).apply {
                text = body.ifBlank { "-" }
                setTextAppearance(R.style.TextAppearance_Cyber_Body)
                typeface = shareTech
                setLineSpacing(dp(3).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(4)
                }
            })
        }
    }

    private fun createRouteSection(label: String, routes: List<RouteCardModel>, fallback: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
            addView(TextView(this@EvolutionGuideActivity).apply {
                text = label
                setTextAppearance(R.style.TextAppearance_Cyber_Muted)
                typeface = shareTech
            })

            if (routes.isEmpty()) {
                addView(TextView(this@EvolutionGuideActivity).apply {
                    text = fallback
                    setTextAppearance(R.style.TextAppearance_Cyber_Body)
                    typeface = shareTech
                    setLineSpacing(dp(3).toFloat(), 1f)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(4)
                    }
                })
            } else {
                routes.forEachIndexed { index, route ->
                    addView(createRouteCard(route))
                    if (index != routes.lastIndex) {
                        addView(space(8))
                    }
                }
            }
        }
    }

    private fun createRouteCard(route: RouteCardModel): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@EvolutionGuideActivity, R.drawable.guide_route_card_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(TextView(this@EvolutionGuideActivity).apply {
                text = route.title
                setTextAppearance(R.style.TextAppearance_Cyber_Muted)
                typeface = shareTech
                setTextColor(ContextCompat.getColor(this@EvolutionGuideActivity, R.color.cyber_yellow))
            })
            route.requirements.forEach { requirement ->
                addView(TextView(this@EvolutionGuideActivity).apply {
                    text = requirement
                    setTextAppearance(R.style.TextAppearance_Cyber_Body)
                    typeface = shareTech
                    setLineSpacing(dp(2).toFloat(), 1f)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(4)
                    }
                })
            }
            route.note?.takeIf { it.isNotBlank() }?.let { note ->
                addView(TextView(this@EvolutionGuideActivity).apply {
                    text = note
                    setTextAppearance(R.style.TextAppearance_Cyber_Muted)
                    typeface = shareTech
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(6)
                    }
                })
            }
        }
    }

    private fun normalizeVersion(raw: String?): String {
        val upper = raw?.uppercase() ?: return "V1"
        return when {
            upper.contains("V2") -> "V2"
            upper.contains("V3") -> "V3"
            else -> "V1"
        }
    }

    private fun normalizeName(name: String): String {
        return name.lowercase().filter { it.isLetterOrDigit() }
    }

    private fun space(size: Int, horizontal: Boolean = false): View {
        return View(this).apply {
            layoutParams = if (horizontal) {
                LinearLayout.LayoutParams(dp(size), 1)
            } else {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(size))
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_VERSION_HINT = "version_hint"
    }
}
