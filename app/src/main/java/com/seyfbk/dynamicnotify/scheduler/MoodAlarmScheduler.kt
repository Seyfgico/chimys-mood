package com.seyfbk.dynamicnotify.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.seyfbk.dynamicnotify.data.DaySlot
import java.util.Calendar

/**
 * Owns the 3-times-a-day mood-ask schedule (morning / evening / night).
 * Re-arming happens every time an alarm fires, so this never needs a
 * separate "daily repeat" primitive — each slot always points at its own
 * next future occurrence.
 */
object MoodAlarmScheduler {

    // Hour/minute (24h) for each slot — tweak to taste.
    private val SLOT_TIMES = mapOf(
        DaySlot.MORNING to (9 to 0),
        DaySlot.EVENING to (19 to 0),
        DaySlot.NIGHT to (22 to 30)
    )

    fun scheduleAll(context: Context) {
        DaySlot.values().forEach { scheduleSlot(context, it) }
    }

    fun scheduleSlot(context: Context, slot: DaySlot) {
        val (hour, minute) = SLOT_TIMES.getValue(slot)
        val triggerAt = nextOccurrence(hour, minute)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntentFor(context, slot)

        if (canScheduleExact(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** Soonest of the three slots that occurs strictly after [afterMillis]. */
    fun nextAskAfter(afterMillis: Long): Long =
        SLOT_TIMES.values.minOf { (h, m) -> nextOccurrence(h, m, afterMillis) }

    private fun nextOccurrence(hour: Int, minute: Int, after: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= after) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 31) alarmManager.canScheduleExactAlarms() else true

    private fun pendingIntentFor(context: Context, slot: DaySlot): PendingIntent {
        val intent = Intent(context, MoodAskReceiver::class.java).apply {
            putExtra(MoodAskReceiver.EXTRA_SLOT, slot.name)
        }
        return PendingIntent.getBroadcast(
            context,
            slot.ordinal, // stable per-slot request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
