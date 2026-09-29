package com.seyfbk.dynamicnotify.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seyfbk.dynamicnotify.service.MoodForegroundService

class TickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduledAt = intent.getLongExtra(TickScheduler.EXTRA_SCHEDULED_AT, System.currentTimeMillis())

        // Re-arm first, so a failure below can never break the chain.
        TickScheduler.scheduleNext(context)

        val serviceIntent = Intent(context, MoodForegroundService::class.java).apply {
            action = MoodForegroundService.ACTION_TICK
            putExtra(MoodForegroundService.EXTRA_IS_ASK, TickScheduler.isAskTick(scheduledAt))
        }
        runCatching { context.startForegroundService(serviceIntent) }
    }
}
