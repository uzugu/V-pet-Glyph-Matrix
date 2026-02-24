package com.digimon.glyph.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.digimon.glyph.DigimonGlyphToyService
import com.digimon.glyph.R
import com.digimon.glyph.emulator.EmulatorCommandBus

class DigimonWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_BUTTON_A = "com.digimon.glyph.WIDGET_BUTTON_A"
        const val ACTION_BUTTON_B = "com.digimon.glyph.WIDGET_BUTTON_B"
        const val ACTION_BUTTON_C = "com.digimon.glyph.WIDGET_BUTTON_C"

        fun getWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(
                ComponentName(context, DigimonWidgetProvider::class.java)
            )
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        for (id in widgetIds) {
            val views = buildRemoteViews(context, id)
            manager.updateAppWidget(id, views)
        }
        ensureServiceRunning(context)
        WidgetFramePusher.ensureRunning(context)
    }

    override fun onEnabled(context: Context) {
        ensureServiceRunning(context)
        WidgetFramePusher.ensureRunning(context)
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        for (id in widgetIds) WidgetPrefs.clear(context, id)
    }

    override fun onDisabled(context: Context) {
        WidgetFramePusher.stop()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == ACTION_BUTTON_A || action == ACTION_BUTTON_B || action == ACTION_BUTTON_C) {
            val svcIntent = Intent(context, DigimonGlyphToyService::class.java).setAction(DigimonGlyphToyService.ACTION_START_WIDGET)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            } catch (_: Exception) {
                try { context.startService(svcIntent) } catch (_: Exception) {}
            }
        }
        when (intent.action) {
            ACTION_BUTTON_A -> {
                EmulatorCommandBus.post(context, EmulatorCommandBus.CMD_BUTTON_PRESS, EmulatorCommandBus.BUTTON_A)
            }
            ACTION_BUTTON_B -> {
                EmulatorCommandBus.post(context, EmulatorCommandBus.CMD_BUTTON_PRESS, EmulatorCommandBus.BUTTON_B)
            }
            ACTION_BUTTON_C -> {
                EmulatorCommandBus.post(context, EmulatorCommandBus.CMD_BUTTON_PRESS, EmulatorCommandBus.BUTTON_C)
            }
        }
    }

    private fun ensureServiceRunning(context: Context) {
        val intent = Intent(context, DigimonGlyphToyService::class.java)
            .setAction(DigimonGlyphToyService.ACTION_START_WIDGET)
        try {
            // Plain startService — the service calls startForeground() internally if it can.
            // We never use startForegroundService() from the widget because widget receivers
            // run in a background context where Android 12+ blocks foreground service starts.
            context.startService(intent)
        } catch (_: Exception) {
            // Background-restricted; frames won't push until user opens the app
        }
    }

    private fun buildRemoteViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val skin = WidgetPrefs.getSkin(context, widgetId)

        // Generic overlay zones (left/center/right).
        views.setOnClickPendingIntent(R.id.btn_a, makePendingIntent(context, ACTION_BUTTON_A, 0))
        views.setOnClickPendingIntent(R.id.btn_b, makePendingIntent(context, ACTION_BUTTON_B, 1))
        views.setOnClickPendingIntent(R.id.btn_c, makePendingIntent(context, ACTION_BUTTON_C, 2))

        // Digivice-specific right-side vertical buttons.
        views.setOnClickPendingIntent(R.id.btn_dv_a, makePendingIntent(context, ACTION_BUTTON_A, 10))
        views.setOnClickPendingIntent(R.id.btn_dv_b, makePendingIntent(context, ACTION_BUTTON_B, 11))
        views.setOnClickPendingIntent(R.id.btn_dv_c, makePendingIntent(context, ACTION_BUTTON_C, 12))

        val isDigivice = skin == WidgetPrefs.Skin.DIGIVICE
        views.setViewVisibility(R.id.zone_columns, if (isDigivice) android.view.View.GONE else android.view.View.VISIBLE)
        views.setViewVisibility(R.id.zone_digivice_buttons, if (isDigivice) android.view.View.VISIBLE else android.view.View.GONE)

        return views
    }

    private fun makePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, DigimonWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
