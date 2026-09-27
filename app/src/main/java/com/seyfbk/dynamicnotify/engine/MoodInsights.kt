package com.seyfbk.dynamicnotify.engine

import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.MoodStore

/**
 * The one thing genuinely "learned" on-device from the mood history:
 * streak detection, so a run of the same mood can get an extra follow-up
 * line. No network, no external model — just pattern-matching over the
 * local log.
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
}
