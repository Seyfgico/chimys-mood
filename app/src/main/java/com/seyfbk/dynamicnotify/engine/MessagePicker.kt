package com.seyfbk.dynamicnotify.engine

import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.MoodStore

/**
 * Picks the next message for a mood without repeating until every line in
 * the pool has been shown once.
 */
class MessagePicker(private val store: MoodStore) {

    fun next(mood: Mood): String {
        val pool = store.fullPoolFor(mood)
        if (pool.isEmpty()) return "Chimy 🐱"

        var queue = store.queueFor(mood)
        if (queue.isEmpty()) {
            queue = pool.indices.shuffled().toMutableList()
        }

        val index = queue.removeAt(0)
        store.saveQueue(mood, queue)
        return pool.getOrElse(index) { pool.first() }
    }
}
