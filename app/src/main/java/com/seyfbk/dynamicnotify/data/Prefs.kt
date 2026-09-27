package com.seyfbk.dynamicnotify.data

import android.content.Context

/**
 * Tiny wrapper around SharedPreferences for the server URL and the
 * device token returned by POST /register.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("dynamic_notify_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = sp.edit().putString(KEY_SERVER_URL, value.trimEnd('/')).apply()

    var deviceToken: String
        get() = sp.getString(KEY_TOKEN, "") ?: ""
        set(value) = sp.edit().putString(KEY_TOKEN, value).apply()

    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && deviceToken.isNotBlank()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "device_token"
    }
}
