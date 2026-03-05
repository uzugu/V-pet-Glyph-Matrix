package com.digimon.glyph.widget

import android.content.Context
import android.graphics.Bitmap
import com.digimon.glyph.widget.skins.BrickVpetSkin
import com.digimon.glyph.widget.skins.CyberHudSkin
import com.digimon.glyph.widget.skins.DebugSkin
import com.digimon.glyph.widget.skins.DigiviceSkin
import com.digimon.glyph.widget.skins.GlyphMatrixSkin
import com.digimon.glyph.widget.skins.VpetIconAtlas

/**
 * Dispatches rendering to the appropriate skin based on the user's choice.
 * Input: raw 25x25 glyph frame or 32x24 debug frame.
 * Output: styled Bitmap ready for RemoteViews (fixed 400x400 output).
 */
class WidgetSkinRenderer(context: Context) {

    companion object {
        const val OUTPUT_SIZE = 400  // px — fits well within RemoteViews bitmap limits
    }

    private val iconAtlas         = VpetIconAtlas(context)
    private val glyphMatrixSkin   = GlyphMatrixSkin()
    private val digiviceSkinV1    = DigiviceSkin.v1(iconAtlas)
    private val digiviceSkinV2    = DigiviceSkin.v2(iconAtlas)
    private val digiviceSkinV3    = DigiviceSkin.v3(iconAtlas)
    private val digiviceSkinWhite = DigiviceSkin.white(iconAtlas)
    private val brickSkinV1      = BrickVpetSkin(context, "skin_brick_v1.svg", iconAtlas)
    private val brickSkinV3      = BrickVpetSkin(context, "skin_brick_v3.svg", iconAtlas)
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
            WidgetPrefs.Skin.BRICK_V1       -> brickSkinV1.render(fullFrame, outputWidth, outputHeight)
            WidgetPrefs.Skin.BRICK_V3       -> brickSkinV3.render(fullFrame, outputWidth, outputHeight)
            WidgetPrefs.Skin.CYBER_HUD      -> cyberHudSkin.render(fullFrame, outputWidth, outputHeight)
            WidgetPrefs.Skin.DEBUG          -> debugSkin.render(fullFrame, outputWidth, outputHeight)
        }
    }
}
