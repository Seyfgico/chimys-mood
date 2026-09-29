package com.seyfbk.dynamicnotify.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * One alarm chain drives everything:
 *  - a "tick" every 30 minutes, on the :00 and :30 marks
 *  - a tick that lands on an even hour (8:00, 10:00 ... 22:00) is a MOOD CHECK-IN
 *  - every other tick is a mood-related MESSAGE
 *  - quiet hours: no ticks from ACTIVE_END_HOUR until ACTIVE_START_HOUR
 *
 * Each tick re-arms the next one, so scheduling is idempotent and safe to
 * call from anywhere (app start, boot, the service, the receiver).
 */
object TickScheduler {

    /** First tick of the day (8:00). Keep this an even hour so check-ins land on 8, 10, 12... */
    const val ACTIVE_START_HOUR = 8

    /** Ticks stop at this hour; the last one of the day is 22:30. */
    const val ACTIVE_END_HOUR = 23

    private const val REQUEST_CODE = 4242
    const val EXTRA_SCHEDULED_AT = "scheduled_at"

    fun scheduleNext(context: Context) {
        val triggerAt = nextTickAfter(System.currentTimeMillis())
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntentFor(context, triggerAt)

        if (canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** True when the tick scheduled for [scheduledAt] is a mood check-in (even hour, on the hour). */
    fun isAskTick(scheduledAt: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = scheduledAt }
        return cal.get(Calendar.MINUTE) == 0 && cal.get(Calendar.HOUR_OF_DAY) % 2 == 0
    }

    /** The next :00 / :30 mark strictly after [afterMillis] that falls inside active hours. */
    fun nextTickAfter(afterMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = afterMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.get(Calendar.MINUTE) < 30) {
            cal.set(Calendar.MINUTE, 30)
        } else {
            cal.set(Calendar.MINUTE, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
        }
        while (cal.get(Calendar.HOUR_OF_DAY) !in ACTIVE_START_HOUR until ACTIVE_END_HOUR) {
            cal.add(Calendar.MINUTE, 30)
        }
        return cal.timeInMillis
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 31) alarmManager.canScheduleExactAlarms() else true

    private fun pendingIntentFor(context: Context, scheduledAt: Long): PendingIntent {
        val intent = Intent(context, TickReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULED_AT, scheduledAt)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
