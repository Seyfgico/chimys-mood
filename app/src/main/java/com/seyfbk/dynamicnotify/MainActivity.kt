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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seyfbk.dynamicnotify.data.CatPrefs
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.scheduler.TickScheduler
import com.seyfbk.dynamicnotify.service.MoodForegroundService

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Fully local — arm the tick schedule and start the mood engine as
        // soon as the app is opened. No server, no network, no setup.
        TickScheduler.scheduleNext(this)
        startForegroundServiceCompat()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        hasOverlayPermission = { hasOverlayPermission() },
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        needsExactAlarmPermission = { needsExactAlarmPermission() },
                        onRequestExactAlarmPermission = { requestExactAlarmPermission() },
                        onCatToggleChanged = { refreshCat() },
                        onTestMoodAsk = { testAskNow() },
                        onTestMessage = { mood -> testMessage(mood) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up an overlay-permission grant made while the app was backgrounded.
        refreshCat()
    }

    private fun startForegroundServiceCompat() {
        val intent = Intent(this, MoodForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun refreshCat() {
        startService(Intent(this, MoodForegroundService::class.java).apply {
            action = MoodForegroundService.ACTION_REFRESH_CAT
        })
    }

    private fun testAskNow() {
        startService(Intent(this, MoodForegroundService::class.java).apply {
            action = MoodForegroundService.ACTION_TICK
            putExtra(MoodForegroundService.EXTRA_IS_ASK, true)
        })
    }

    private fun testMessage(mood: Mood) {
        startService(Intent(this, MoodForegroundService::class.java).apply {
            action = MoodForegroundService.ACTION_TEST_MESSAGE
            putExtra(MoodForegroundService.EXTRA_MOOD, mood.name)
        })
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
    onCatToggleChanged: () -> Unit,
    onTestMoodAsk: () -> Unit,
    onTestMessage: (Mood) -> Unit
) {
    val context = LocalContext.current
    val catPrefs = remember { CatPrefs(context) }

    var overlayGranted by remember { mutableStateOf(hasOverlayPermission()) }
    var needsExactAlarm by remember { mutableStateOf(needsExactAlarmPermission()) }
    var catEnabled by remember { mutableStateOf(catPrefs.enabled) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("🐱 Lina Mood", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "A fully offline mood companion — no server, no account, no setup. A little cat " +
                "lives on your screen, checks in on Lina's mood every 2 hours, and pops in with " +
                "a message every half hour in between.",
            style = MaterialTheme.typography.bodyMedium
        )

        Card {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show the cat on screen", fontWeight = FontWeight.Medium)
                    Text(
                        "Turn this off to go back to plain notifications instead.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = catEnabled,
                    onCheckedChange = {
                        catEnabled = it
                        catPrefs.enabled = it
                        onCatToggleChanged()
                    }
                )
            }
        }

        if (!overlayGranted) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Overlay permission needed for the cat to appear on top of other apps.")
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
                    Text("For check-ins and messages to land on time, allow exact alarms.")
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
            "Drag the cat anywhere on screen — it remembers where you leave it. It asks about " +
                "Lina's mood every 2 hours, and shows a message from that mood's pool every 30 " +
                "minutes in between (occasionally just a silly \"I love you\" line for fun). Its " +
                "little animations — playing, jumping, stretching, napping — match the current mood. " +
                "Tapping elsewhere on the screen never makes it go away; only the switch above does.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onTestMoodAsk) { Text("Test: Ask now") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onTestMessage(Mood.TOO_BAD) }) { Text("Test: Too Bad") }
            OutlinedButton(onClick = { onTestMessage(Mood.NORMAL) }) { Text("Test: Normal") }
            OutlinedButton(onClick = { onTestMessage(Mood.GOOD) }) { Text("Test: Good") }
        }
    }
}
