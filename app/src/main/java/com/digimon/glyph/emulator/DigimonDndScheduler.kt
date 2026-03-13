package com.digimon.glyph.emulator

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.digimon.glyph.DigimonDndReceiver
import java.util.Calendar

object DigimonDndScheduler {

    private const val TAG = "DigimonDndScheduler"
    private const val REQ_START = 4101
    private const val REQ_END = 4102

    fun reschedule(context: Context) {
        try {
            cancel(context)
            DigimonDndSettings.init(context)
            if (!DigimonDndSettings.isEnabled()) return
            val now = System.currentTimeMillis()
            scheduleAlarm(context, DigimonDndReceiver.ACTION_DND_START, REQ_START, computeNextStart(now))
            scheduleAlarm(context, DigimonDndReceiver.ACTION_DND_END, REQ_END, computeNextEnd(now))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to reschedule DND alarms", t)
        }
    }

    fun cancel(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            alarmManager.cancel(pendingIntent(context, DigimonDndReceiver.ACTION_DND_START, REQ_START))
            alarmManager.cancel(pendingIntent(context, DigimonDndReceiver.ACTION_DND_END, REQ_END))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to cancel DND alarms", t)
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            alarmManager.canScheduleExactAlarms()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to query exact alarm access", t)
            false
        }
    }

    fun computeNextStart(nowMs: Long): Long {
        val nowMinutes = DigimonDndSettings.getMinutesOfDay(nowMs)
        val start = DigimonDndSettings.getStartMinutes()
        val cal = calendarForMinutes(nowMs, start)
        if (DigimonDndSettings.isDndActiveNow(nowMs) || nowMinutes >= start) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun computeNextEnd(nowMs: Long): Long {
        val start = DigimonDndSettings.getStartMinutes()
        val end = DigimonDndSettings.getEndMinutes()
        val nowMinutes = DigimonDndSettings.getMinutesOfDay(nowMs)
        val crossMidnight = start >= end
        val cal = calendarForMinutes(nowMs, end)

        if (DigimonDndSettings.isDndActiveNow(nowMs)) {
            if (crossMidnight && nowMinutes >= start) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            if (!crossMidnight) {
                if (nowMinutes >= end) cal.add(Calendar.DAY_OF_YEAR, 1)
            } else {
                if (nowMinutes >= end && nowMinutes < start) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        return cal.timeInMillis
    }

    private fun scheduleAlarm(context: Context, action: String, requestCode: Int, triggerAtMs: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pendingIntent = pendingIntent(context, action, requestCode)
            if (canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to schedule DND alarm action=$action requestCode=$requestCode", t)
        }
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, DigimonDndReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun calendarForMinutes(baseMs: Long, minutesOfDay: Int): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = baseMs
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
        }
    }
}
