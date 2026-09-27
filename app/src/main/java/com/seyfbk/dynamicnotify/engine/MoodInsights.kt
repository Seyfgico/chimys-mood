package com.seyfbk.dynamicnotify.engine

import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.MoodStore

/**
 * Everything genuinely "learned" on-device from the mood history, without
 * pretending this is a trained model: streak detection and recent
 * frequency. Real generative learning (new phrasing) happens server-side
 * via MessageSyncWorker, which uploads these same counts.
 */
class MoodInsights(private val store: MoodStore) {

    /** How many of the most recent entries in a row share the same mood. */
    fun currentStreak(): Pair<Mood, Int>? {
        val history = store.history()
        if (history.isEmpty()) return null
        val last = history.last().mood
        var count = 0
        for (entry in history.asReversed()) {
            if (entry.mood == last) count++ else break
        }
        return last to count
    }

    /** Mood counts within the last [days] days — used for the sync payload. */
    fun recentCounts(days: Int = 30): Map<Mood, Int> {
        val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        val counts = mutableMapOf(Mood.TOO_BAD to 0, Mood.NORMAL to 0, Mood.GOOD to 0)
        store.history().filter { it.timestampMillis >= cutoff }.forEach {
            counts[it.mood] = (counts[it.mood] ?: 0) + 1
        }
        return counts
    }
}
