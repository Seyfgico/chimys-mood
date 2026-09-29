package com.seyfbk.dynamicnotify.data

enum class Mood {
    TOO_BAD, NORMAL, GOOD;

    val label: String
        get() = when (this) {
            TOO_BAD -> "Too Bad"
            NORMAL -> "Normal"
            GOOD -> "Good"
        }
}

/** Part of the day — flavors the wording of the mood check-in. */
enum class DaySlot(val greetings: List<String>) {
    MORNING(
        listOf(
            "Good morning Lina 🐱 — how are you feeling?",
            "Morning, Lina! How's your mood so far today?",
            "Rise and shine, Lina 🐱 How are you today?",
            "New day, Lina. How's your heart feeling?"
        )
    ),
    AFTERNOON(
        listOf(
            "Afternoon check-in, Lina — how are you feeling?",
            "Hey Lina 🐱 how's your day going so far?",
            "Midday mood check, Lina. How are you?",
            "Just checking in, Lina — how's your mood right now?"
        )
    ),
    EVENING(
        listOf(
            "Evening, Lina 🐱 how are you feeling?",
            "How's the evening treating you, Lina?",
            "Hey Lina, how's your mood this evening?",
            "Evening check-in — how are you, Lina?"
        )
    ),
    NIGHT(
        listOf(
            "Before you wind down, Lina — how are you feeling?",
            "Late check-in, Lina 🐱 how was today?",
            "Night mood check, Lina — how are you?",
            "How are you feeling tonight, Lina?"
        )
    );

    companion object {
        fun forHour(hour: Int): DaySlot = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}
