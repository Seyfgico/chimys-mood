package com.seyfbk.dynamicnotify.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seyfbk.dynamicnotify.service.MoodForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TickScheduler.scheduleNext(context)
            // Bring the cat back after a restart.
            runCatching {
                context.startForegroundService(Intent(context, MoodForegroundService::class.java))
            }
        }
    }
}
