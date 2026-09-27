package com.seyfbk.dynamicnotify.data

import okhttp3.Call
import okhttp3.EventSource
import okhttp3.EventSourceListener
import okhttp3.EventSources
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to a push-notifications-api server (see
 * github.com/viktorholk/push-notifications-api): POST /register to get a
 * device token, then GET /events?token=... as a long-lived SSE stream.
 */
class NotificationApiClient(private val serverUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE stream stays open indefinitely
        .retryOnConnectionFailure(true)
        .build()

    /** POST /register -> { "token": "..." } */
    fun register(onResult: (token: String?, error: String?) -> Unit) {
        val request = Request.Builder()
            .url("$serverUrl/register")
            .post("".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                onResult(null, e.message ?: "Could not reach server")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onResult(null, "Server returned ${it.code}")
                        return
                    }
                    val body = it.body?.string().orEmpty()
                    val token = try {
                        JSONObject(body).optString("token").takeIf { t -> t.isNotBlank() }
                    } catch (e: Exception) {
                        null
                    }
                    if (token != null) onResult(token, null)
                    else onResult(null, "Unexpected response from server")
                }
            }
        })
    }

    /** GET /events?token=... — calls back on every notification and on state changes. */
    fun connectEvents(
        token: String,
        onConnected: () -> Unit,
        onNotification: (PushNotification) -> Unit,
        onFailure: (String) -> Unit
    ): EventSource {
        val request = Request.Builder()
            .url("$serverUrl/events?token=$token")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                onConnected()
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "Connected") {
                    onConnected()
                    return
                }
                PushNotification.fromJson(data)?.let(onNotification)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                onFailure(t?.message ?: response?.message ?: "Connection lost")
            }
        }

        return EventSources.createFactory(client).newEventSource(request, listener)
    }
}
