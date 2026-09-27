package com.seyfbk.dynamicnotify.data

import android.content.Context
import org.json.JSONArray

/**
 * Persists everything the mood engine needs across process restarts:
 * the current mood + when it was picked, the mood history log, and a
 * "shuffle bag" of remaining message indices per mood so the same line
 * doesn't repeat until the whole pool cycles. Fully local — no network.
 */
class MoodStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("dynamic_notify_mood", Context.MODE_PRIVATE)

    var currentMood: Mood?
        get() = sp.getString("current_mood", null)?.let { runCatching { Mood.valueOf(it) }.getOrNull() }
        set(value) = sp.edit().putString("current_mood", value?.name).apply()

    var currentMoodTimestamp: Long
        get() = sp.getLong("current_mood_ts", 0L)
        set(value) = sp.edit().putLong("current_mood_ts", value).apply()

    private fun queueKey(mood: Mood) = "queue_${mood.name}"

    fun fullPoolFor(mood: Mood): List<String> = MoodMessages.defaultsFor(mood)

    /** Remaining shuffled indices for [mood]'s current pool; empty means "needs reshuffle". */
    fun queueFor(mood: Mood): MutableList<Int> {
        val raw = sp.getString(queueKey(mood), null) ?: return mutableListOf()
        return raw.split(",").filter { it.isNotBlank() }.map { it.toInt() }.toMutableList()
    }

    fun saveQueue(mood: Mood, queue: List<Int>) {
        sp.edit().putString(queueKey(mood), queue.joinToString(",")).apply()
    }

    // --- Mood history (for the local streak/"learning" signal) ---

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

    companion object {
        private const val KEY_HISTORY = "mood_history"
        private const val MAX_HISTORY = 300
    }
}
