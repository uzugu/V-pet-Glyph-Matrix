package com.digimon.glyph.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.digimon.glyph.R
import com.digimon.glyph.emulator.FrameDebugState

/**
 * Periodically reads the latest frame from FrameDebugState, renders a styled
 * bitmap for each widget instance, and pushes it via AppWidgetManager.
 *
 * Runs on the main looper — rendering is lightweight (small bitmaps).
 */
object WidgetFramePusher {

    private const val UPDATE_INTERVAL_MS = 250L  // ~4 FPS
    val isRunning: Boolean get() = handler != null
    private var handler: Handler? = null
    private var renderer: WidgetSkinRenderer? = null
    private var lastFrameTs: Long = 0L
    private var appContext: Context? = null

    private val pushRunnable = object : Runnable {
        override fun run() {
            pushFrame()
            handler?.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    fun ensureRunning(context: Context) {
        if (handler != null) return
        appContext = context.applicationContext
        renderer = WidgetSkinRenderer()
        handler = Handler(Looper.getMainLooper())
        handler?.post(pushRunnable)
    }

    fun stop() {
        handler?.removeCallbacks(pushRunnable)
        handler = null
        renderer = null
        appContext = null
        lastFrameTs = 0L
    }

    private fun pushFrame() {
        val ctx = appContext ?: return
        val snap = FrameDebugState.snapshot()
        if (snap.updatedAtMs == lastFrameTs) return  // No new frame
        lastFrameTs = snap.updatedAtMs

        val manager = AppWidgetManager.getInstance(ctx)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(ctx, DigimonWidgetProvider::class.java)
        )
        if (widgetIds.isEmpty()) return

        for (id in widgetIds) {
            val skin = WidgetPrefs.getSkin(ctx, id)
            val outputBitmap = renderer?.render(skin, snap.glyphFrame, snap.fullFrame) ?: continue
            val views = RemoteViews(ctx.packageName, R.layout.widget_layout)
            views.setImageViewBitmap(R.id.widget_frame, outputBitmap)
            // Re-set click intents (they persist but safer to always set)
            setClickIntents(ctx, views, skin)
            manager.updateAppWidget(id, views)
        }
    }

    private fun setClickIntents(context: Context, views: RemoteViews, skin: WidgetPrefs.Skin) {
        val makeIntent = { action: String, code: Int ->
            val intent = android.content.Intent(context, DigimonWidgetProvider::class.java)
                .setAction(action)
            android.app.PendingIntent.getBroadcast(
                context, code, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        val isDigivice = skin == WidgetPrefs.Skin.DIGIVICE
        views.setViewVisibility(R.id.zone_columns, if (isDigivice) android.view.View.GONE else android.view.View.VISIBLE)
        views.setViewVisibility(R.id.zone_digivice_buttons, if (isDigivice) android.view.View.VISIBLE else android.view.View.GONE)

        views.setOnClickPendingIntent(R.id.btn_a, makeIntent(DigimonWidgetProvider.ACTION_BUTTON_A, 0))
        views.setOnClickPendingIntent(R.id.btn_b, makeIntent(DigimonWidgetProvider.ACTION_BUTTON_B, 1))
        views.setOnClickPendingIntent(R.id.btn_c, makeIntent(DigimonWidgetProvider.ACTION_BUTTON_C, 2))
        views.setOnClickPendingIntent(R.id.btn_dv_a, makeIntent(DigimonWidgetProvider.ACTION_BUTTON_A, 10))
        views.setOnClickPendingIntent(R.id.btn_dv_b, makeIntent(DigimonWidgetProvider.ACTION_BUTTON_B, 11))
        views.setOnClickPendingIntent(R.id.btn_dv_c, makeIntent(DigimonWidgetProvider.ACTION_BUTTON_C, 12))
    }
}
