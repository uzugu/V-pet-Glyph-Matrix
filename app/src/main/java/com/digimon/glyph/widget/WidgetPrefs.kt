package com.digimon.glyph.widget

import android.content.Context

/**
 * Per-widget-instance settings stored in SharedPreferences.
 */
object WidgetPrefs {

    enum class Skin {
        GLYPH_MATRIX,
        DIGIVICE_V1,
        DIGIVICE_V2,
        DIGIVICE_V3,
        DIGIVICE_WHITE,
        BRICK_V1,
        BRICK_V3,
        CYBER_HUD,
        DEBUG
    }

    private const val PREFS = "widget_prefs"

    fun getSkin(context: Context, widgetId: Int): Skin {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString("skin_$widgetId", Skin.GLYPH_MATRIX.name)
        return try {
            when (name) {
                "DIGIVICE" -> Skin.DIGIVICE_V3  // legacy migration: old saves → V3
                else -> Skin.valueOf(name!!)
            }
        } catch (_: Exception) { Skin.GLYPH_MATRIX }
    }

    fun setSkin(context: Context, widgetId: Int, skin: Skin) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("skin_$widgetId", skin.name)
            .apply()
    }

    fun clear(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("skin_$widgetId")
            .apply()
    }
}
