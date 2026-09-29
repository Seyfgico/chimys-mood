package com.seyfbk.dynamicnotify.engine

import com.seyfbk.dynamicnotify.data.FunMessages
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.MoodStore

/**
 * Picks the next message without repeating until every line in the pool
 * has been shown once (a "shuffle bag"). Each mood, plus the fun pool,
 * keeps its own bag.
 */
class MessagePicker(private val store: MoodStore) {

    fun next(mood: Mood): String = pick(mood.name, store.fullPoolFor(mood))

    fun nextFun(): String = pick("FUN", FunMessages.ALL)

    private fun pick(key: String, pool: List<String>): String {
        if (pool.isEmpty()) return "Lina 🐱"

        var queue = store.queueFor(key)
        if (queue.isEmpty()) {
            queue = pool.indices.shuffled().toMutableList()
        }

        val index = queue.removeAt(0)
        store.saveQueue(key, queue)
        return pool.getOrElse(index) { pool.first() }
    }
}
