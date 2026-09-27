package com.seyfbk.dynamicnotify

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.scheduler.MoodAlarmScheduler
import com.seyfbk.dynamicnotify.service.MoodForegroundService

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Fully local — arm the 3x/day schedule and start the mood engine
        // as soon as the app is opened. No server, no network, no setup.
        MoodAlarmScheduler.scheduleAll(this)
        startForegroundServiceCompat()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        hasOverlayPermission = { hasOverlayPermission() },
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        needsExactAlarmPermission = { needsExactAlarmPermission() },
                        onRequestExactAlarmPermission = { requestExactAlarmPermission() },
                        onTestMoodAsk = { mood ->
                            startService(Intent(this, MoodForegroundService::class.java).apply {
                                action = MoodForegroundService.ACTION_SHOW_RANDOM_MESSAGE
                                putExtra(MoodForegroundService.EXTRA_MOOD, mood.name)
                            })
                        }
                    )
                }
            }
        }
    }

    private fun startForegroundServiceCompat() {
        val intent = Intent(this, MoodForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun needsExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val alarmManager = getSystemService(android.app.AlarmManager::class.java)
        return !alarmManager.canScheduleExactAlarms()
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
    }
}

@Composable
private fun HomeScreen(
    hasOverlayPermission: () -> Boolean,
    onRequestOverlayPermission: () -> Unit,
    needsExactAlarmPermission: () -> Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onTestMoodAsk: (Mood) -> Unit
) {
    var overlayGranted by remember { mutableStateOf(hasOverlayPermission()) }
    var needsExactAlarm by remember { mutableStateOf(needsExactAlarmPermission()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🐱 Chimy's Mood", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "A fully offline mood companion — no server, no account, no setup. " +
                "Chimy checks in 3 times a day as a Dynamic Island at the top of the screen.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (!overlayGranted) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Overlay permission needed so the island can draw on top of other apps.")
                    Button(onClick = {
                        onRequestOverlayPermission()
                        overlayGranted = hasOverlayPermission()
                    }) { Text("Grant overlay permission") }
                }
            }
        }

        if (needsExactAlarm) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("For the 3x/day mood check-ins to be on time, allow exact alarms.")
                    Button(onClick = {
                        onRequestExactAlarmPermission()
                        needsExactAlarm = needsExactAlarmPermission()
                    }) { Text("Grant alarm permission") }
                }
            }
        }

        HorizontalDivider()
        Text("How it works", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Mood check-ins pop up 3x a day (morning, evening, night) as an interactive island — " +
                "tap a mood to answer. Between check-ins, messages from that mood's pool show up on " +
                "their own: tap the island to open one, tap anywhere else to dismiss it. " +
                "Every message lives in the app itself — nothing is downloaded.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onTestMoodAsk(Mood.TOO_BAD) }) { Text("Test: Too Bad") }
            OutlinedButton(onClick = { onTestMoodAsk(Mood.NORMAL) }) { Text("Test: Normal") }
            OutlinedButton(onClick = { onTestMoodAsk(Mood.GOOD) }) { Text("Test: Good") }
        }
    }
}
