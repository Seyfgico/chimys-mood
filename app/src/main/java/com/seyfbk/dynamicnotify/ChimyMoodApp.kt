package com.seyfbk.dynamicnotify

import android.app.Application

/**
 * Installs a global uncaught-exception handler as a last-resort safety
 * net: whatever broke gets written to CrashLog before the process dies,
 * so it's visible next time the app is opened, even if something crashes
 * before MainActivity itself can set up its own try/catch blocks.
 */
class ChimyMoodApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CrashLog.record(applicationContext, "UNCAUGHT (${thread.name})", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
