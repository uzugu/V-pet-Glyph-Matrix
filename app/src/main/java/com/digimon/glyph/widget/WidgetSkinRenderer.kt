package com.digimon.glyph.widget

import android.graphics.Bitmap
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

    private val glyphMatrixSkin = GlyphMatrixSkin()
    private val debugSkin = DebugSkin()
    private val digiviceSkin = DigiviceSkin()

    fun render(
        skin: WidgetPrefs.Skin,
        glyphFrame: Bitmap?,
        fullFrame: Bitmap?
    ): Bitmap? {
        return when (skin) {
            WidgetPrefs.Skin.GLYPH_MATRIX -> glyphMatrixSkin.render(glyphFrame, OUTPUT_SIZE)
            WidgetPrefs.Skin.DIGIVICE -> digiviceSkin.render(fullFrame, OUTPUT_SIZE)
            WidgetPrefs.Skin.DEBUG -> debugSkin.render(fullFrame, OUTPUT_SIZE)
        }
    }
}
