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

enum class DaySlot {
    MORNING, EVENING, NIGHT;

    val greeting: String
        get() = when (this) {
            MORNING -> "Good morning Chimy 🐱 — how are you today?"
            EVENING -> "Evening check-in — how are you feeling, Chimy?"
            NIGHT -> "Before you sleep, Chimy — how was your day?"
        }
}
