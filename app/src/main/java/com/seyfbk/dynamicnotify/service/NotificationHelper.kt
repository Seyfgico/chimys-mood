package com.seyfbk.dynamicnotify.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.seyfbk.dynamicnotify.MainActivity
import com.seyfbk.dynamicnotify.data.Mood
import java.util.concurrent.atomic.AtomicInteger

/**
 * Used only when the cat isn't on screen (switched off, or the overlay
 * permission is missing): messages become normal notifications, and the
 * mood check-in becomes a notification with three answer buttons.
 */
object NotificationHelper {

    private const val CHANNEL_ID = "lina_messages"
    private const val ASK_ID = 2001
    private val nextMessageId = AtomicInteger(3000)

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Lina's messages", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun postMessage(context: Context, text: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Lina 🐱")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(nextMessageId.getAndIncrement(), notification)
    }

    fun postMoodAsk(context: Context, greeting: String) {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Lina 🐱")
            .setContentText(greeting)
            .setStyle(NotificationCompat.BigTextStyle().bigText(greeting))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)

        Mood.values().forEach { mood ->
            val intent = Intent(context, MoodPickReceiver::class.java).apply {
                putExtra(MoodPickReceiver.EXTRA_MOOD, mood.name)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                10 + mood.ordinal,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, mood.label, pending)
        }

        context.getSystemService(NotificationManager::class.java).notify(ASK_ID, builder.build())
    }

    fun cancelAsk(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ASK_ID)
    }
}
