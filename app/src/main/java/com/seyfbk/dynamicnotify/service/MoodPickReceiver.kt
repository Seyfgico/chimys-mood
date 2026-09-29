package com.seyfbk.dynamicnotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seyfbk.dynamicnotify.data.Mood

/** Handles a tap on one of the mood buttons in the check-in notification. */
class MoodPickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mood = intent.getStringExtra(EXTRA_MOOD)
            ?.let { runCatching { Mood.valueOf(it) }.getOrNull() } ?: return

        NotificationHelper.cancelAsk(context)

        val serviceIntent = Intent(context, MoodForegroundService::class.java).apply {
            action = MoodForegroundService.ACTION_MOOD_PICKED
            putExtra(MoodForegroundService.EXTRA_MOOD, mood.name)
        }
        runCatching { context.startForegroundService(serviceIntent) }
    }

    companion object {
        const val EXTRA_MOOD = "mood"
    }
}
