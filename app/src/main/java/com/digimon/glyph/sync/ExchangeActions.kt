package com.digimon.glyph.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.digimon.glyph.DigimonGlyphToyService
import java.util.UUID

/** The Glyph service is exported for Nothing; save replacement remains an in-process action. */
object ExchangeActions {
    private val capability = UUID.randomUUID().toString()
    fun request(context: Context, hash: String?, uri: Uri? = null): Intent =
        Intent(context, DigimonGlyphToyService::class.java)
            .setAction(if (uri == null) "com.digimon.glyph.EXCHANGE_EXPORT" else "com.digimon.glyph.EXCHANGE_IMPORT")
            .setData(uri).putExtra("rom_hash", hash).putExtra("exchange_capability", capability)
    fun authorized(intent: Intent) = intent.getStringExtra("exchange_capability") == capability
}
