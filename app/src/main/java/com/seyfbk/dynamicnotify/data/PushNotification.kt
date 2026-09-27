package com.seyfbk.dynamicnotify.data

/** The content shown in the island pill: a title, a message line, and an accent color. */
data class PushNotification(
    val title: String,
    val message: String?,
    val color: String?
)
