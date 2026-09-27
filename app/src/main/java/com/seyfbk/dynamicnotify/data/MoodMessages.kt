package com.seyfbk.dynamicnotify.data

/**
 * Built-in messages — always available offline. MoodStore layers extra,
 * synced messages on top of these when the device has been on WiFi.
 * Emotional/supportive content only, as requested — no physical content.
 */
object MoodMessages {

    val TOO_BAD = listOf(
        "Chimy, come here. You don't have to face today alone.",
        "I wish I could be next to you right now, making everything a little better.",
        "Bad mood detected. SBK support system activated.",
        "Whatever happened, remember that you're loved more than you know.",
        "Chimy, today doesn't have to be perfect. Just take it easy, my love.",
        "I'm always with you even if I'm not with you.",
        "Your smile can wait. You'll always be loved even on your hardest days."
    )

    val NORMAL = listOf(
        "Chimy is feeling normal today. Nothing too exciting, but I'm here guarding the swing.",
        "Chimy is okay, but a little attention would be nice. 👀",
        "System status: Normal. Love status: Always active.",
        "Chimy is having an ordinary day. Everything about her is special for me.",
        "Mood stable. Missing you always.",
        "The mood is normal — you've never been normal, Treasure."
    )

    val GOOD = listOf(
        "Chimy is happy today, and knowing that makes my whole world brighter. ❤️",
        "Your smile is my favorite kind of happiness, Chimy.",
        "Chimy is feeling wonderful today. I hope this happiness stays with her all day.",
        "A little reminder that your happiness means everything to me.",
        "Chimy's heart is glowing today, and mine is smiling with hers.",
        "Today feels better because Chimy is happy. 🌷",
        "Chimy is in a beautiful mood. May every moment bring her another reason to smile.",
        "Your happiness is something I will always cherish, Chimy.",
        "Chimy is having a good day, and I hope she knows how deeply she is loved.",
        "When Chimy is happy, even ordinary moments feel special."
    )

    fun defaultsFor(mood: Mood): List<String> = when (mood) {
        Mood.TOO_BAD -> TOO_BAD
        Mood.NORMAL -> NORMAL
        Mood.GOOD -> GOOD
    }

    /** Accent color used by the pill for each mood. */
    fun colorFor(mood: Mood): String = when (mood) {
        Mood.TOO_BAD -> "#7C8CE0"
        Mood.NORMAL -> "#B0B0B0"
        Mood.GOOD -> "#FF9D3D"
    }

    /** Shown as a follow-up when the same mood streaks for a few days in a row. */
    val STREAK_TOO_BAD = listOf(
        "I've noticed you've had a few tough days in a row, Chimy. I see you, and I'm not going anywhere.",
        "A few hard days back to back — that's a lot to carry. You don't have to carry it alone."
    )

    val STREAK_GOOD = listOf(
        "You've been glowing for days now, Chimy — I hope you know how much that lights everything up.",
        "A streak of good days for you is the best kind of streak. Keep shining, Treasure."
    )

    fun streakBonusFor(mood: Mood): String? = when (mood) {
        Mood.TOO_BAD -> STREAK_TOO_BAD.random()
        Mood.GOOD -> STREAK_GOOD.random()
        Mood.NORMAL -> null
    }
}
