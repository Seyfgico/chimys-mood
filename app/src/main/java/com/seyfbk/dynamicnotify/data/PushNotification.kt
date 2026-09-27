package com.seyfbk.dynamicnotify.data

import org.json.JSONObject

/**
 * Mirrors the JSON payload the push-notifications-api server sends over
 * the SSE stream: { title, message, url, icon, color, token, createdAt }
 */
data class PushNotification(
    val title: String,
    val message: String?,
    val url: String?,
    val color: String?
) {
    companion object {
        fun fromJson(json: String): PushNotification? {
            return try {
                val o = JSONObject(json)
                PushNotification(
                    title = o.optString("title", "Notification"),
                    message = o.optString("message").takeIf { it.isNotBlank() },
                    url = o.optString("url").takeIf { it.isNotBlank() },
                    color = o.optString("color").takeIf { it.isNotBlank() }
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
