package com.digimon.glyph

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.digimon.glyph.emulator.DigimonDndScheduler
import com.digimon.glyph.emulator.DigimonDndSettings

class DigimonDndReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DND_START = "com.digimon.glyph.DND_START"
        const val ACTION_DND_END = "com.digimon.glyph.DND_END"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        DigimonDndSettings.init(context)
        when (intent?.action) {
            ACTION_DND_START,
            ACTION_DND_END -> {
                val serviceIntent = Intent(context, DigimonGlyphToyService::class.java)
                    .setAction(intent.action)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                DigimonDndScheduler.reschedule(context)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                DigimonDndScheduler.reschedule(context)
                if (DigimonDndSettings.isEnabled() && DigimonDndSettings.isFreezeMode()) {
                    val action = if (DigimonDndSettings.isDndActiveNow()) ACTION_DND_START else ACTION_DND_END
                    val serviceIntent = Intent(context, DigimonGlyphToyService::class.java).setAction(action)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }
        }
    }
}
