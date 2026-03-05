package com.digimon.glyph

import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
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
        val textControls = findViewById<TextView>(R.id.text_controls)
        val textMechanics = findViewById<TextView>(R.id.text_mechanics)
        val btnBack = findViewById<Button>(R.id.btn_back)

        mainTitle.typeface = teko
        titleShadowCyan.typeface = teko
        titleShadowPink.typeface = teko
        btnBack.typeface = teko

        textControls.typeface = shareTech
        textMechanics.typeface = shareTech

        btnBack.setOnClickListener {
            finish()
        }
    }
}
