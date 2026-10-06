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
            "Good morning Chimy 🐱 — how are you feeling?",
            "Morning, Chimy! How's your mood so far today?",
            "Rise and shine, Chimy 🐱 How are you today?",
            "New day, Chimy. How's your heart feeling?"
        )
    ),
    AFTERNOON(
        listOf(
            "Afternoon check-in, Chimy — how are you feeling?",
            "Hey Chimy 🐱 how's your day going so far?",
            "Midday mood check, Chimy. How are you?",
            "Just checking in, Chimy — how's your mood right now?"
        )
    ),
    EVENING(
        listOf(
            "Evening, Chimy 🐱 how are you feeling?",
            "How's the evening treating you, Chimy?",
            "Hey Chimy, how's your mood this evening?",
            "Evening check-in — how are you, Chimy?"
        )
    ),
    NIGHT(
        listOf(
            "Before you wind down, Chimy — how are you feeling?",
            "Late check-in, Chimy 🐱 how was today?",
            "Night mood check, Chimy — how are you?",
            "How are you feeling tonight, Chimy?"
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
