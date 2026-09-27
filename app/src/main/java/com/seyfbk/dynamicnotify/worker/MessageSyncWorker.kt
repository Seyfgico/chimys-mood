package com.seyfbk.dynamicnotify.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.MoodStore
import com.seyfbk.dynamicnotify.engine.MoodInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * "The app evolves through internet": whenever there's a connection at all
 * (WiFi or mobile data — not WiFi-only anymore), this uploads Chimy's
 * recent mood counts and pulls back extra messages per mood, merging them
 * into the local pool. The app never requires this to function — it only
 * ever *adds* to what's built in, and everything still works fully offline.
 *
 * POSTs to the configured URL:
 * { "recentMoodCounts": { "TOO_BAD": 2, "NORMAL": 5, "GOOD": 9 } }
 *
 * Expects back:
 * { "too_bad": ["..."], "normal": ["..."], "good": ["..."] }
 *
 * That response shape is a contract you'd implement server-side (e.g. an
 * endpoint that feeds the counts to a language model and returns new,
 * on-theme lines) — this worker only handles the upload/merge plumbing.
 */
class MessageSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = MoodStore(applicationContext)
        val url = store.syncUrl
        if (url.isBlank()) return@withContext Result.success()

        try {
            val counts = MoodInsights(store).recentCounts()
            val payload = JSONObject().apply {
                put("recentMoodCounts", JSONObject().apply {
                    counts.forEach { (mood, count) -> put(mood.name, count) }
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.retry()
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)

                mergeArray(store, Mood.TOO_BAD, json.optJSONArray("too_bad"))
                mergeArray(store, Mood.NORMAL, json.optJSONArray("normal"))
                mergeArray(store, Mood.GOOD, json.optJSONArray("good"))
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun mergeArray(store: MoodStore, mood: Mood, array: org.json.JSONArray?) {
        if (array == null) return
        val messages = (0 until array.length()).map { array.getString(it) }
        if (messages.isNotEmpty()) store.mergeExtras(mood, messages)
    }

    companion object {
        private const val PERIODIC_NAME = "message_sync_periodic"
        private const val ONE_OFF_NAME = "message_sync_now"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // any internet, not just WiFi
            .build()

        /** Call once (e.g. from MainActivity.onCreate) — safe to call repeatedly. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<MessageSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Kicks off an immediate sync attempt right after a mood is picked. */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<MessageSyncWorker>()
                .setConstraints(constraints())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_OFF_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
