package com.digimon.glyph.even

import android.content.Context
import java.util.UUID

object EvenStreamSettings {
    fun enabled(context: Context) = context.getSharedPreferences("even_stream", 0).getBoolean("enabled", false)
    fun setEnabled(context: Context, value: Boolean) { context.getSharedPreferences("even_stream", 0).edit().putBoolean("enabled", value).apply() }
    fun token(context: Context): String {
        val prefs = context.getSharedPreferences("even_stream", 0)
        return prefs.getString("token", null) ?: UUID.randomUUID().toString().replace("-", "").also { prefs.edit().putString("token", it).commit() }
    }
}
