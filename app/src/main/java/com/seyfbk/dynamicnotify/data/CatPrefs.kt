package com.seyfbk.dynamicnotify.data

import android.content.Context

/** Whether the on-screen cat is switched on, and where the user last left it. */
class CatPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("lina_cat", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(value) = sp.edit().putBoolean("enabled", value).apply()

    /** -1 means "never moved yet" — the overlay picks a default spot. */
    val posX: Int get() = sp.getInt("x", -1)
    val posY: Int get() = sp.getInt("y", -1)

    fun savePosition(x: Int, y: Int) {
        sp.edit().putInt("x", x).putInt("y", y).apply()
    }
}
