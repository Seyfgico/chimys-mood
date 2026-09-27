package com.seyfbk.dynamicnotify.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.MoodStore
import com.seyfbk.dynamicnotify.data.NotificationApiClient
import com.seyfbk.dynamicnotify.data.Prefs
import com.seyfbk.dynamicnotify.data.PushNotification
import com.seyfbk.dynamicnotify.data.MoodMessages
import com.seyfbk.dynamicnotify.engine.MessagePicker
import com.seyfbk.dynamicnotify.engine.MoodInsights
import com.seyfbk.dynamicnotify.overlay.DynamicIslandOverlay
import com.seyfbk.dynamicnotify.scheduler.MoodAlarmScheduler
import com.seyfbk.dynamicnotify.scheduler.RandomMessageScheduler
import com.seyfbk.dynamicnotify.worker.MessageSyncWorker
import okhttp3.sse.EventSource

/**
 * Keeps a persistent SSE connection to the server (as in the original
 * push-notifications-api Android app) and forwards every notification to
 * the DynamicIslandOverlay instead of a normal system notification.
 */
class SseForegroundService : Service() {

    private var eventSource: EventSource? = null
    private var overlay: DynamicIslandOverlay? = null
    private lateinit var moodStore: MoodStore
    private lateinit var messagePicker: MessagePicker
    private lateinit var moodInsights: MoodInsights
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildStatusNotification("Connecting…"))
        overlay = DynamicIslandOverlay(applicationContext)
        moodStore = MoodStore(applicationContext)
        messagePicker = MessagePicker(moodStore)
        moodInsights = MoodInsights(moodStore)
        MoodAlarmScheduler.scheduleAll(applicationContext)
        connect()
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
                        PushNotification(
                            title = "Chimy 🐱",
                            message = bonus,
                            url = null,
                            color = MoodMessages.colorFor(mood)
                        )
                    )
                }, STREAK_BONUS_DELAY_MS)
            }
        }

        val nextAsk = MoodAlarmScheduler.nextAskAfter(now)
        RandomMessageScheduler.scheduleRandomPushes(applicationContext, mood, nextAsk)

        // Any internet connection (not just WiFi) can trigger a sync/learn pass.
        MessageSyncWorker.runOnce(applicationContext)
    }

    private fun showMessageFor(mood: Mood) {
        val text = messagePicker.next(mood)
        overlay?.show(
            PushNotification(
                title = "Chimy 🐱",
                message = text,
                url = null,
                color = MoodMessages.colorFor(mood)
            )
        )
    }

    private fun connect() {
        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured) {
            // No push-notifications-api server configured — that's fine, the
            // mood check-ins and offline messages still run on their own.
            updateStatusNotification("Mood check-ins active (no server connected)")
            return
        }

        val api = NotificationApiClient(prefs.serverUrl)
        eventSource = api.connectEvents(
            token = prefs.deviceToken,
            onConnected = {
                updateStatusNotification("Connected — listening for pushes")
            },
            onNotification = { notification ->
                overlay?.show(notification)
            },
            onFailure = {
                updateStatusNotification("Reconnecting…")
                eventSource?.cancel()
                connect()
            }
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Dynamic Island service", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildStatusNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DynamicNotify")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

    private fun updateStatusNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildStatusNotification(text))
    }

    override fun onDestroy() {
        eventSource?.cancel()
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
