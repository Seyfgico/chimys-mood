package com.seyfbk.dynamicnotify.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.MoodMessages
import com.seyfbk.dynamicnotify.data.MoodStore
import com.seyfbk.dynamicnotify.data.PushNotification
import com.seyfbk.dynamicnotify.engine.MessagePicker
import com.seyfbk.dynamicnotify.engine.MoodInsights
import com.seyfbk.dynamicnotify.overlay.DynamicIslandOverlay
import com.seyfbk.dynamicnotify.scheduler.MoodAlarmScheduler
import com.seyfbk.dynamicnotify.scheduler.RandomMessageScheduler

/**
 * Fully local mood-companion service: no server, no network. It arms the
 * 3x/day mood-ask schedule, drives the Dynamic Island overlay, and picks
 * messages purely from the offline pools in MoodMessages.
 */
class MoodForegroundService : Service() {

    private var overlay: DynamicIslandOverlay? = null
    private lateinit var moodStore: MoodStore
    private lateinit var messagePicker: MessagePicker
    private lateinit var moodInsights: MoodInsights
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildStatusNotification())
        overlay = DynamicIslandOverlay(applicationContext)
        moodStore = MoodStore(applicationContext)
        messagePicker = MessagePicker(moodStore)
        moodInsights = MoodInsights(moodStore)
        MoodAlarmScheduler.scheduleAll(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_MOOD_ASK -> {
                val greeting = intent.getStringExtra(EXTRA_GREETING).orEmpty()
                overlay?.showMoodAsk(greeting) { mood -> onMoodPicked(mood) }
            }
            ACTION_SHOW_RANDOM_MESSAGE -> {
                intent.getStringExtra(EXTRA_MOOD)
                    ?.let { runCatching { Mood.valueOf(it) }.getOrNull() }
                    ?.let { mood -> showMessageFor(mood) }
            }
        }
        return START_STICKY
    }

    private fun onMoodPicked(mood: Mood) {
        val now = System.currentTimeMillis()
        moodStore.currentMood = mood
        moodStore.currentMoodTimestamp = now
        moodStore.addHistoryEntry(mood, now)

        showMessageFor(mood) // immediate feedback

        // If the same mood has streaked for a few check-ins, follow up with
        // an extra line a little later — the one bit of local "learning"
        // from the history log.
        val (streakMood, streakCount) = moodInsights.currentStreak() ?: (mood to 1)
        if (streakMood == mood && streakCount >= 3) {
            MoodMessages.streakBonusFor(mood)?.let { bonus ->
                mainHandler.postDelayed({
                    overlay?.show(
                        PushNotification(title = "Chimy 🐱", message = bonus, color = MoodMessages.colorFor(mood))
                    )
                }, STREAK_BONUS_DELAY_MS)
            }
        }

        val nextAsk = MoodAlarmScheduler.nextAskAfter(now)
        RandomMessageScheduler.scheduleRandomPushes(applicationContext, mood, nextAsk)
    }

    private fun showMessageFor(mood: Mood) {
        val text = messagePicker.next(mood)
        overlay?.show(
            PushNotification(title = "Chimy 🐱", message = text, color = MoodMessages.colorFor(mood))
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Chimy's Mood service", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildStatusNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chimy's Mood")
            .setContentText("Watching over Chimy's mood — fully offline")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        overlay?.detach()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "dynamic_notify_service"
        private const val NOTIF_ID = 1

        const val ACTION_SHOW_MOOD_ASK = "com.seyfbk.dynamicnotify.SHOW_MOOD_ASK"
        const val ACTION_SHOW_RANDOM_MESSAGE = "com.seyfbk.dynamicnotify.SHOW_RANDOM_MESSAGE"
        const val EXTRA_GREETING = "greeting"
        const val EXTRA_MOOD = "mood"
        private const val STREAK_BONUS_DELAY_MS = 60_000L
    }
}
