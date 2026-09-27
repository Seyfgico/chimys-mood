package com.seyfbk.dynamicnotify.data

import android.content.Context
import org.json.JSONArray

/**
 * Persists everything the mood engine needs across process restarts:
 * the current mood + when it was picked, extra messages pulled in by
 * MessageSyncWorker (on WiFi), and a "shuffle bag" of remaining message
 * indices per mood so the same line doesn't repeat until the pool cycles.
 */
class MoodStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("dynamic_notify_mood", Context.MODE_PRIVATE)

    var syncUrl: String
        get() = sp.getString("sync_url", "") ?: ""
        set(value) = sp.edit().putString("sync_url", value.trim()).apply()

    var currentMood: Mood?
        get() = sp.getString("current_mood", null)?.let { runCatching { Mood.valueOf(it) }.getOrNull() }
        set(value) = sp.edit().putString("current_mood", value?.name).apply()

    var currentMoodTimestamp: Long
        get() = sp.getLong("current_mood_ts", 0L)
        set(value) = sp.edit().putLong("current_mood_ts", value).apply()

    private fun extraKey(mood: Mood) = "extra_${mood.name}"
    private fun queueKey(mood: Mood) = "queue_${mood.name}"

    // --- Mood history (for local "learning" + optional remote sync) ---

    data class HistoryEntry(val mood: Mood, val timestampMillis: Long)

    /** Appends a mood pick to the history log, trimmed to the most recent [MAX_HISTORY]. */
    fun addHistoryEntry(mood: Mood, timestampMillis: Long) {
        val current = history().toMutableList()
        current.add(HistoryEntry(mood, timestampMillis))
        val trimmed = if (current.size > MAX_HISTORY) current.takeLast(MAX_HISTORY) else current

        val arr = JSONArray()
        trimmed.forEach {
            val obj = org.json.JSONObject()
            obj.put("mood", it.mood.name)
            obj.put("ts", it.timestampMillis)
            arr.put(obj)
        }
        sp.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun history(): List<HistoryEntry> {
        val raw = sp.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val mood = runCatching { Mood.valueOf(obj.getString("mood")) }.getOrNull() ?: return@mapNotNull null
                HistoryEntry(mood, obj.optLong("ts"))
            }
        }.getOrDefault(emptyList())
    }

    fun extrasFor(mood: Mood): List<String> {
        val raw = sp.getString(extraKey(mood), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    /** Merges [messages] into the stored extras for [mood], de-duplicated. */
    fun mergeExtras(mood: Mood, messages: List<String>) {
        val merged = (extrasFor(mood) + messages).distinct()
        val arr = JSONArray()
        merged.forEach { arr.put(it) }
        sp.edit().putString(extraKey(mood), arr.toString()).apply()
        // Pool changed size — reset the shuffle bag so new lines enter rotation.
        sp.edit().remove(queueKey(mood)).apply()
    }

    fun fullPoolFor(mood: Mood): List<String> = MoodMessages.defaultsFor(mood) + extrasFor(mood)

    /** Remaining shuffled indices for [mood]'s current pool; empty means "needs reshuffle". */
    fun queueFor(mood: Mood): MutableList<Int> {
        val raw = sp.getString(queueKey(mood), null) ?: return mutableListOf()
        return raw.split(",").filter { it.isNotBlank() }.map { it.toInt() }.toMutableList()
    }

    fun saveQueue(mood: Mood, queue: List<Int>) {
        sp.edit().putString(queueKey(mood), queue.joinToString(",")).apply()
    }

    companion object {
        private const val KEY_HISTORY = "mood_history"
        private const val MAX_HISTORY = 300
    }
}
