package com.seyfbk.dynamicnotify.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.seyfbk.dynamicnotify.data.DaySlot
import com.seyfbk.dynamicnotify.service.SseForegroundService

class MoodAskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(EXTRA_SLOT)?.let {
            runCatching { DaySlot.valueOf(it) }.getOrNull()
        } ?: DaySlot.MORNING

        // Re-arm this slot for its next occurrence (tomorrow).
        MoodAlarmScheduler.scheduleSlot(context, slot)

        val serviceIntent = Intent(context, SseForegroundService::class.java).apply {
            action = SseForegroundService.ACTION_SHOW_MOOD_ASK
            putExtra(SseForegroundService.EXTRA_GREETING, slot.greeting)
        }
        context.startForegroundService(serviceIntent)
    }

    companion object {
        const val EXTRA_SLOT = "slot"
    }
}
