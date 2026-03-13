package com.digimon.glyph.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.widget.RemoteViews
import com.digimon.glyph.R
import com.digimon.glyph.emulator.DigimonDndSettings
import com.digimon.glyph.emulator.FrameDebugState
import com.digimon.glyph.emulator.EmulatorCommandBus

/**
 * Periodically reads the latest frame from FrameDebugState, renders a styled
 * bitmap for each widget instance, and pushes it via AppWidgetManager.
 *
 * Runs on a dedicated background looper so widget bitmap work does not contend
 * with the service main thread.
 */
object WidgetFramePusher {

    private const val UPDATE_INTERVAL_MS = 120L
    val isRunning: Boolean get() = handler != null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var renderer: WidgetSkinRenderer? = null
    private var lastFrameTs: Long = 0L
    private var lastFrozenState: Boolean? = null
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
        renderer = WidgetSkinRenderer(appContext!!)
        thread = HandlerThread("WidgetFramePusher").apply { start() }
        handler = Handler(thread!!.looper)
        handler?.post(pushRunnable)
    }

    fun stop() {
        handler?.removeCallbacks(pushRunnable)
        handler = null
        thread?.quitSafely()
        thread = null
        renderer = null
        appContext = null
        lastFrameTs = 0L
        lastFrozenState = null
    }

    fun refreshNow(context: Context) {
        if (handler == null) {
            ensureRunning(context)
            return
        }
        appContext = context.applicationContext
        handler?.post { pushFrame(force = true) }
    }

    private fun pushFrame(force: Boolean = false) {
        val ctx = appContext ?: return
        DigimonDndSettings.init(ctx)
        val frozen = DigimonDndSettings.isFrozenNow()
        val snap = FrameDebugState.snapshot()
        if (!force && snap.updatedAtMs == lastFrameTs && frozen == lastFrozenState) return
        lastFrameTs = snap.updatedAtMs
        lastFrozenState = frozen

        val manager = AppWidgetManager.getInstance(ctx)
        val widgetIds = manager.getAppWidgetIds(
            ComponentName(ctx, DigimonWidgetProvider::class.java)
        )
        if (widgetIds.isEmpty()) return

        val density = ctx.resources.displayMetrics.density

        for (id in widgetIds) {
            val skin = WidgetPrefs.getSkin(ctx, id)

            // Render at the widget's actual pixel dimensions — no square-bitmap distortion
            val options = manager.getAppWidgetOptions(id)
            val maxWdp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            val maxHdp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val outW = if (maxWdp > 0) (maxWdp * density).toInt().coerceIn(100, 1200)
                       else WidgetSkinRenderer.OUTPUT_SIZE
            val outH = if (maxHdp > 0) (maxHdp * density).toInt().coerceIn(100, 1200)
                       else WidgetSkinRenderer.OUTPUT_SIZE

            val outputBitmap = renderer?.render(skin, snap.glyphFrame, snap.fullFrame, outW, outH) ?: continue
            val views = RemoteViews(ctx.packageName, R.layout.widget_layout)
            views.setImageViewBitmap(R.id.widget_frame, outputBitmap)
            views.setViewVisibility(R.id.widget_dnd_badge, if (frozen) android.view.View.VISIBLE else android.view.View.GONE)
            // Re-set click intents (they persist but safer to always set)
            setClickIntents(ctx, views, skin, frozen)
            manager.updateAppWidget(id, views)
        }
    }

    private fun setClickIntents(context: Context, views: RemoteViews, skin: WidgetPrefs.Skin, frozen: Boolean) {
        val makeIntent = { button: Int, code: Int ->
            val intent = android.content.Intent(context, com.digimon.glyph.DigimonGlyphToyService::class.java)
                .setAction(com.digimon.glyph.DigimonGlyphToyService.ACTION_WIDGET_BUTTON)
                .putExtra(com.digimon.glyph.DigimonGlyphToyService.EXTRA_WIDGET_BUTTON, button)
            android.app.PendingIntent.getService(
                context, code, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        val isDeviceSkin = skin == WidgetPrefs.Skin.DIGIVICE_V1 || skin == WidgetPrefs.Skin.DIGIVICE_V2
                       || skin == WidgetPrefs.Skin.DIGIVICE_V3 || skin == WidgetPrefs.Skin.DIGIVICE_WHITE
                       || skin == WidgetPrefs.Skin.BRICK_V1    || skin == WidgetPrefs.Skin.BRICK_V3
        if (frozen) {
            views.setViewVisibility(R.id.zone_columns, android.view.View.GONE)
            views.setViewVisibility(R.id.zone_digivice_buttons, android.view.View.GONE)
            return
        }
        views.setViewVisibility(R.id.zone_columns, if (isDeviceSkin) android.view.View.GONE else android.view.View.VISIBLE)
        views.setViewVisibility(R.id.zone_digivice_buttons, if (isDeviceSkin) android.view.View.VISIBLE else android.view.View.GONE)

        views.setOnClickPendingIntent(R.id.btn_a, makeIntent(EmulatorCommandBus.BUTTON_A, 0))
        views.setOnClickPendingIntent(R.id.btn_b, makeIntent(EmulatorCommandBus.BUTTON_B, 1))
        views.setOnClickPendingIntent(R.id.btn_c, makeIntent(EmulatorCommandBus.BUTTON_C, 2))
        views.setOnClickPendingIntent(R.id.btn_dv_a, makeIntent(EmulatorCommandBus.BUTTON_A, 10))
        views.setOnClickPendingIntent(R.id.btn_dv_b, makeIntent(EmulatorCommandBus.BUTTON_B, 11))
        views.setOnClickPendingIntent(R.id.btn_dv_c, makeIntent(EmulatorCommandBus.BUTTON_C, 12))
    }
}
