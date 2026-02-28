package com.digimon.glyph.widget

import android.graphics.Bitmap
import com.digimon.glyph.widget.skins.CyberHudSkin
import com.digimon.glyph.widget.skins.DebugSkin
import com.digimon.glyph.widget.skins.DigiviceSkin
import com.digimon.glyph.widget.skins.GlyphMatrixSkin

/**
 * Dispatches rendering to the appropriate skin based on the user's choice.
 * Input: raw 25x25 glyph frame or 32x24 debug frame.
 * Output: styled Bitmap ready for RemoteViews (fixed 400x400 output).
 */
class WidgetSkinRenderer {

    companion object {
        const val OUTPUT_SIZE = 400  // px — fits well within RemoteViews bitmap limits
    }

    private val glyphMatrixSkin   = GlyphMatrixSkin()
    private val digiviceSkinV1    = DigiviceSkin.v1()
    private val digiviceSkinV2    = DigiviceSkin.v2()
    private val digiviceSkinV3    = DigiviceSkin.v3()
    private val digiviceSkinWhite = DigiviceSkin.white()
    private val cyberHudSkin      = CyberHudSkin()
    private val debugSkin         = DebugSkin()

    fun render(
        skin: WidgetPrefs.Skin,
        glyphFrame: Bitmap?,
        fullFrame: Bitmap?,
        outputWidth: Int = OUTPUT_SIZE,
        outputHeight: Int = OUTPUT_SIZE
    ): Bitmap? {
        return when (skin) {
            WidgetPrefs.Skin.GLYPH_MATRIX   -> glyphMatrixSkin.render(glyphFrame, outputWidth, outputHeight)
            WidgetPrefs.Skin.DIGIVICE_V1    -> digiviceSkinV1.render(fullFrame, outputWidth, outputHeight)
            WidgetPrefs.Skin.DIGIVICE_V2    -> digiviceSkinV2.render(fullFrame, outputWidth, outputHeight)
            WidgetPrefs.Skin.DIGIVICE_V3    -> digiviceSkinV3.render(fullFrame, outputWidth, outputHeight)
            WidgetPrefs.Skin.DIGIVICE_WHITE -> digiviceSkinWhite.render(fullFrame, outputWidth, outputHeight)
            WidgetPrefs.Skin.CYBER_HUD      -> cyberHudSkin.render(fullFrame, outputWidth, outputHeight)
            WidgetPrefs.Skin.DEBUG          -> debugSkin.render(fullFrame, outputWidth, outputHeight)
        }
    }
}
