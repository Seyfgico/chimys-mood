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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seyfbk.dynamicnotify.data.Mood
import com.seyfbk.dynamicnotify.data.MoodStore
import com.seyfbk.dynamicnotify.data.NotificationApiClient
import com.seyfbk.dynamicnotify.data.Prefs
import com.seyfbk.dynamicnotify.scheduler.MoodAlarmScheduler
import com.seyfbk.dynamicnotify.service.SseForegroundService
import com.seyfbk.dynamicnotify.worker.MessageSyncWorker

class MainActivity : ComponentActivity() {

    private lateinit var prefs: Prefs

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // The mood engine runs independently of the push server — start it
        // and arm the 3x/day schedule as soon as the app is opened.
        MoodAlarmScheduler.scheduleAll(this)
        startForegroundServiceCompat()
        MessageSyncWorker.schedulePeriodic(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConnectScreen(
                        prefs = prefs,
                        hasOverlayPermission = { hasOverlayPermission() },
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        needsExactAlarmPermission = { needsExactAlarmPermission() },
                        onRequestExactAlarmPermission = { requestExactAlarmPermission() },
                        onConnected = { startForegroundServiceCompat() },
                        onTestMoodAsk = { mood ->
                            startService(Intent(this, SseForegroundService::class.java).apply {
                                action = SseForegroundService.ACTION_SHOW_RANDOM_MESSAGE
                                putExtra(SseForegroundService.EXTRA_MOOD, mood.name)
                            })
                        }
                    )
                }
            }
        }
    }

    private fun startForegroundServiceCompat() {
        val intent = Intent(this, SseForegroundService::class.java)
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
private fun ConnectScreen(
    prefs: Prefs,
    hasOverlayPermission: () -> Boolean,
    onRequestOverlayPermission: () -> Unit,
    needsExactAlarmPermission: () -> Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    onConnected: () -> Unit,
    onTestMoodAsk: (Mood) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val moodStore = remember { MoodStore(context) }

    var serverUrl by remember { mutableStateOf(prefs.serverUrl.ifBlank { "http://" }) }
    var syncUrl by remember { mutableStateOf(moodStore.syncUrl) }
    var status by remember { mutableStateOf(if (prefs.isConfigured) "Connected as ${prefs.deviceToken.take(8)}…" else "") }
    var isLoading by remember { mutableStateOf(false) }
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
            "Connects to your push-notifications-api server and shows incoming pushes as a Dynamic Island at the top of the screen.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server address") },
            placeholder = { Text("http://192.168.1.10:3000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
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
        Text("Chimy's mood 🐱", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Mood check-ins pop up 3x a day (morning, evening, night) as an interactive island. " +
                "Between check-ins, random supportive messages show up on their own — all offline by default.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onTestMoodAsk(Mood.TOO_BAD) }) { Text("Test: Too Bad") }
            OutlinedButton(onClick = { onTestMoodAsk(Mood.NORMAL) }) { Text("Test: Normal") }
            OutlinedButton(onClick = { onTestMoodAsk(Mood.GOOD) }) { Text("Test: Good") }
        }

        OutlinedTextField(
            value = syncUrl,
            onValueChange = {
                syncUrl = it
                moodStore.syncUrl = it
            },
            label = { Text("Message sync URL (optional)") },
            placeholder = { Text("https://your-server/messages") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "When set, DynamicNotify pulls extra emotional messages over WiFi and adds them " +
                "to the local pool per mood — the app never needs this to function.",
            style = MaterialTheme.typography.labelSmall
        )

        HorizontalDivider()

        Button(
            enabled = !isLoading && serverUrl.length > 8,
            onClick = {
                isLoading = true
                status = "Registering…"
                val client = NotificationApiClient(serverUrl.trimEnd('/'))
                client.register { token, error ->
                    isLoading = false
                    if (token != null) {
                        prefs.serverUrl = serverUrl
                        prefs.deviceToken = token
                        status = "Connected as ${token.take(8)}…"
                        onConnected()
                    } else {
                        status = "Failed: ${error ?: "unknown error"}"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Connecting…" else "Register & Connect")
        }

        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Server API is compatible with viktorholk/push-notifications-api (POST /register, GET /events).",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
