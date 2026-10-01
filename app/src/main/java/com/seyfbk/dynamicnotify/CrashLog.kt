package com.seyfbk.dynamicnotify

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A tiny in-app crash/error log. Written to app-private storage so it
 * survives without needing a PC, ADB, or a file manager — it's readable
 * straight from the home screen (see MainActivity's "Diagnostics" card).
 */
object CrashLog {

    private fun logFile(context: Context): File = File(context.filesDir, "crash_log.txt")

    fun record(context: Context, tag: String, throwable: Throwable) {
        runCatching {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val entry = buildString {
                appendLine("===== $stamp — $tag =====")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                append(sw.toString())
                appendLine()
            }
            logFile(context).appendText(entry)
        }
    }

    fun readAll(context: Context): String =
        runCatching { logFile(context).readText() }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { logFile(context).writeText("") }
    }
}
