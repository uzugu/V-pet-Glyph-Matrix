package com.digimon.glyph.emulator

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build

/** Does not change the independent D-Power maintenance alarm. */
class ClassicWakeAlarm(private val manager: AlarmManager, private val pending: PendingIntent,
                       private val api: Int = Build.VERSION.SDK_INT) {
    // The injected API level defaults to SDK_INT; lint cannot follow that constructor argument.
    @android.annotation.SuppressLint("NewApi")
    fun exactAllowed(): Boolean = api < 31 || try { manager.canScheduleExactAlarms() } catch (_: SecurityException) { false }
    fun cancel() = manager.cancel(pending)
    fun schedule(nowMs: Long, firmwareAtMs: Long, prediction: ClassicAttentionPredictor.Prediction,
                 intervalMinutes: Int): StandbyWakePolicy.Wake {
        val wake = StandbyWakePolicy.classic(nowMs, firmwareAtMs, prediction, exactAllowed(), intervalMinutes)
        cancel()
        if (wake.exact) {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, wake.atMs, pending) }
            catch (_: SecurityException) { return scheduleInexact(nowMs, intervalMinutes) }
        } else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, wake.atMs, pending)
        return wake
    }
    private fun scheduleInexact(nowMs: Long, intervalMinutes: Int): StandbyWakePolicy.Wake {
        val wake = StandbyWakePolicy.periodic(nowMs, intervalMinutes)
        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, wake.atMs, pending)
        return wake
    }
}
