package com.digimon.glyph

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Empty application locales mean follow the system, including future language changes. */
object LanguageSettings {
    private val tags = listOf("", "en", "ja")

    private fun labels(context: Context) = arrayOf(
        context.getString(R.string.language_follow_system), "English", "日本語"
    )

    private fun selectedIndex(): Int {
        val language = AppCompatDelegate.getApplicationLocales()[0]?.language.orEmpty()
        return tags.indexOf(language)
    }

    fun currentLabel(context: Context): String =
        labels(context).getOrNull(selectedIndex())
            ?: AppCompatDelegate.getApplicationLocales().toLanguageTags()

    fun showPicker(context: Context) {
        val selected = selectedIndex()
        AlertDialog.Builder(context)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(labels(context), selected) { dialog, index ->
                dialog.dismiss()
                if (index != selected) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(tags[index])
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
