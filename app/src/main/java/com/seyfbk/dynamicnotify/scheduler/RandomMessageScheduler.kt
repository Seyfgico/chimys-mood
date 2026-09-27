package com.seyfbk.dynamicnotify.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.seyfbk.dynamicnotify.data.Mood
import kotlin.random.Random

/**
 * After the user answers a mood-ask, this fills the gap until the *next*
 * ask with a handful of randomly-timed messages from that mood's pool —
 * "pushes notifications randomly till the next mood ask".
 */
object RandomMessageScheduler {

    private const val MIN_GAP_MS = 5 * 60 * 1000L   // don't fire in the first 5 min
    private const val END_BUFFER_MS = 2 * 60 * 1000L // leave the last 2 min quiet

    fun scheduleRandomPushes(context: Context, mood: Mood, windowEndMillis: Long) {
        cancelPending(context)

        val now = System.currentTimeMillis()
        val start = now + MIN_GAP_MS
        val end = windowEndMillis - END_BUFFER_MS
        if (end <= start) return // window too short (e.g. testing) — skip

        val count = Random.nextInt(2, 5) // 2..4 random pushes
        val times = (1..count).map { Random.nextLong(start, end) }.sorted()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        times.forEachIndexed { index, triggerAt ->
            val intent = Intent(context, RandomMessageReceiver::class.java).apply {
                putExtra(RandomMessageReceiver.EXTRA_MOOD, mood.name)
            }
            val pi = PendingIntent.getBroadcast(
                context, REQUEST_CODE_BASE + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    /** Clears any not-yet-fired random pushes from a previous mood pick. */
    fun cancelPending(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (index in 0 until MAX_SLOTS) {
            val pi = pendingIntentFor(context, index)
            alarmManager.cancel(pi)
        }
    }

    private fun pendingIntentFor(context: Context, index: Int): PendingIntent {
        val intent = Intent(context, RandomMessageReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_BASE + index, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val REQUEST_CODE_BASE = 1000
    private const val MAX_SLOTS = 4
}
