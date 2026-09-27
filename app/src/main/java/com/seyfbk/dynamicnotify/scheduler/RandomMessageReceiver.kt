package com.seyfbk.dynamicnotify.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.service.MoodForegroundService

class RandomMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mood = intent.getStringExtra(EXTRA_MOOD)?.let {
            runCatching { Mood.valueOf(it) }.getOrNull()
        } ?: return

        val serviceIntent = Intent(context, MoodForegroundService::class.java).apply {
            action = MoodForegroundService.ACTION_SHOW_RANDOM_MESSAGE
            putExtra(MoodForegroundService.EXTRA_MOOD, mood.name)
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        const val EXTRA_MOOD = "mood"
    }
}
