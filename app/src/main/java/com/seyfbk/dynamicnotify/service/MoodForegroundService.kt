package com.seyfbk.dynamicnotify.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.seyfbk.dynamicnotify.data.CatPrefs
import com.seyfbk.dynamicnotify.data.DaySlot
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.MoodMessages
import com.seyfbk.dynamicnotify.data.MoodStore
import com.seyfbk.dynamicnotify.engine.MessagePicker
import com.seyfbk.dynamicnotify.engine.MoodInsights
import com.seyfbk.dynamicnotify.overlay.CatOverlay
import com.seyfbk.dynamicnotify.scheduler.TickScheduler
import java.util.Calendar
import kotlin.random.Random

/**
 * Fully local: no server, no network. Drives the on-screen cat (or, if
 * the cat is switched off / overlay permission is missing, falls back to
 * plain notifications) and answers the tick schedule from TickScheduler.
 */
class MoodForegroundService : Service() {

    private var overlay: CatOverlay? = null
    private lateinit var moodStore: MoodStore
    private lateinit var catPrefs: CatPrefs
    private lateinit var messagePicker: MessagePicker
    private lateinit var moodInsights: MoodInsights
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()
            startForeground(NOTIF_ID, buildStatusNotification())
        } catch (t: Throwable) {
            com.seyfbk.dynamicnotify.CrashLog.record(applicationContext, "startForeground", t)
            stopSelf()
            return
        }

        try {
            moodStore = MoodStore(applicationContext)
            catPrefs = CatPrefs(applicationContext)
            messagePicker = MessagePicker(moodStore)
            moodInsights = MoodInsights(moodStore)
        } catch (t: Throwable) {
            com.seyfbk.dynamicnotify.CrashLog.record(applicationContext, "MoodForegroundService.init-state", t)
            stopSelf()
            return
        }

        runCatching {
            overlay = CatOverlay(applicationContext)
            overlay?.updateMood(moodStore.currentMood)
            overlay?.refresh()
        }.onFailure {
            com.seyfbk.dynamicnotify.CrashLog.record(applicationContext, "CatOverlay.refresh", it)
        }

        runCatching {
            TickScheduler.scheduleNext(applicationContext)
        }.onFailure {
            com.seyfbk.dynamicnotify.CrashLog.record(applicationContext, "TickScheduler.scheduleNext", it)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            when (intent?.action) {
                ACTION_TICK -> {
                    if (intent.getBooleanExtra(EXTRA_IS_ASK, false)) askForMood() else pushRandomMessage()
                }
                ACTION_MOOD_PICKED -> {
                    intent.getStringExtra(EXTRA_MOOD)
                        ?.let { runCatching { Mood.valueOf(it) }.getOrNull() }
                        ?.let { onMoodPicked(it) }
                }
                ACTION_REFRESH_CAT -> overlay?.refresh()
                ACTION_TEST_MESSAGE -> {
                    intent.getStringExtra(EXTRA_MOOD)
                        ?.let { runCatching { Mood.valueOf(it) }.getOrNull() }
                        ?.let { showMessageFor(it) }
                }
            }
        }.onFailure {
            com.seyfbk.dynamicnotify.CrashLog.record(applicationContext, "onStartCommand(${intent?.action})", it)
        }
        return START_STICKY
    }

    // --------------------------------------------------------------- ticks

    private fun askForMood() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = DaySlot.forHour(hour).greetings.random()
        if (catUsable()) {
            overlay?.showAsk(greeting) { mood -> onMoodPicked(mood) }
        } else {
            NotificationHelper.postMoodAsk(applicationContext, greeting)
        }
    }

    private fun pushRandomMessage() {
        val mood = moodStore.currentMood ?: Mood.NORMAL
        // Every so often, send a just-for-fun line instead of a mood message.
        val useFun = Random.nextInt(100) < 20
        val text = if (useFun) messagePicker.nextFun() else messagePicker.next(mood)
        val color = if (useFun) MoodMessages.FUN_COLOR else MoodMessages.colorFor(mood)
        deliver(text, color)
    }

    private fun onMoodPicked(mood: Mood) {
        val now = System.currentTimeMillis()
        moodStore.currentMood = mood
        moodStore.currentMoodTimestamp = now
        moodStore.addHistoryEntry(mood, now)
        overlay?.updateMood(mood)

        showMessageFor(mood) // immediate feedback

        // If the same mood has streaked for a few check-ins, follow up with
        // an extra line a little later — the one bit of local "learning"
        // from the history log.
        val (streakMood, streakCount) = moodInsights.currentStreak() ?: (mood to 1)
        if (streakMood == mood && streakCount >= 3) {
            MoodMessages.streakBonusFor(mood)?.let { bonus ->
                mainHandler.postDelayed({ deliver(bonus, MoodMessages.colorFor(mood)) }, STREAK_BONUS_DELAY_MS)
            }
        }
    }

    private fun showMessageFor(mood: Mood) {
        deliver(messagePicker.next(mood), MoodMessages.colorFor(mood))
    }

    private fun deliver(text: String, colorHex: String) {
        if (catUsable()) {
            overlay?.showMessage(text, colorHex)
        } else {
            NotificationHelper.postMessage(applicationContext, text)
        }
    }

    private fun catUsable(): Boolean {
        val hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(applicationContext)
        return catPrefs.enabled && hasOverlayPermission
    }

    // --------------------------------------------------------------- boilerplate

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Lina Mood service", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildStatusNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lina Mood")
            .setContentText("Watching over Lina — fully offline")
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

        const val ACTION_TICK = "com.seyfbk.dynamicnotify.TICK"
        const val ACTION_MOOD_PICKED = "com.seyfbk.dynamicnotify.MOOD_PICKED"
        const val ACTION_REFRESH_CAT = "com.seyfbk.dynamicnotify.REFRESH_CAT"
        const val ACTION_TEST_MESSAGE = "com.seyfbk.dynamicnotify.TEST_MESSAGE"
        const val EXTRA_IS_ASK = "is_ask"
        const val EXTRA_MOOD = "mood"
        private const val STREAK_BONUS_DELAY_MS = 60_000L
    }
}
