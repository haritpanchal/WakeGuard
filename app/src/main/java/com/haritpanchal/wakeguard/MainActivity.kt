package com.haritpanchal.wakeguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.TimePickerType
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private val settings by lazy { AlarmSettings(this) }
    private val scheduler by lazy { AlarmScheduler(this) }
    private var enabled by mutableStateOf(false)
    private var hour by mutableIntStateOf(AlarmSettings.DEFAULT_HOUR)
    private var minute by mutableIntStateOf(AlarmSettings.DEFAULT_MINUTE)
    private var dayLabel by mutableStateOf("")
    private var ringsIn by mutableStateOf("Alarm off")
    private var showTimePicker by mutableStateOf(false)
    private var showStepPicker by mutableStateOf(false)
    private var stepChallengeEnabled by mutableStateOf(true)
    private var stepTarget by mutableIntStateOf(AlarmSettings.DEFAULT_STEP_TARGET)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        AppLog.append(this, "notification permission granted=$granted")
        if (!granted) {
            ringsIn = "Notifications denied"
        }
    }

    private val activityRecognitionPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        AppLog.append(this, "activity recognition permission granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enabled = settings.enabled
        hour = settings.hour
        minute = settings.minute
        stepChallengeEnabled = settings.stepChallengeEnabled
        stepTarget = settings.stepTarget
        updateStatus()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            activityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        setContent {
            MaterialTheme {
                when {
                    showTimePicker -> {
                        TimePicker(
                            initialTime = LocalTime.of(hour, minute),
                            onTimePicked = { picked ->
                                hour = picked.hour
                                minute = picked.minute
                                showTimePicker = false
                                save()
                            },
                            timePickerType = TimePickerType.HoursMinutesAmPm12H,
                        )
                    }
                    showStepPicker -> {
                        val options = listOf("OFF") +
                            AlarmSettings.STEP_TARGET_OPTIONS.map { "$it steps" }
                        val currentIndex = if (!stepChallengeEnabled) {
                            0
                        } else {
                            val idx = AlarmSettings.STEP_TARGET_OPTIONS.indexOf(stepTarget)
                            if (idx >= 0) idx + 1 else 2
                        }
                        ScalingLazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            item {
                                Text(
                                    "\uD83D\uDEB6 Walk to dismiss",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            items(options.size) { index ->
                                val label = options[index]
                                val selected = index == currentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (selected)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.surfaceContainer,
                                            RoundedCornerShape(16.dp),
                                        )
                                        .clickable {
                                            if (index == 0) {
                                                stepChallengeEnabled = false
                                                settings.stepChallengeEnabled = false
                                            } else {
                                                stepChallengeEnabled = true
                                                settings.stepChallengeEnabled = true
                                                val target = AlarmSettings.STEP_TARGET_OPTIONS[index - 1]
                                                stepTarget = target
                                                settings.stepTarget = target
                                            }
                                            AppLog.append(
                                                this@MainActivity,
                                                "steps set enabled=$stepChallengeEnabled target=$stepTarget",
                                            )
                                            showStepPicker = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (selected) {
                                        Text(
                                            "✓",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        ScalingLazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Day label (only when alarm is on)
                            if (enabled) {
                                item {
                                    Text(
                                        dayLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            // Time — tap to toggle on/off, long-press to change time
                            item {
                                Text(
                                    formatTime12(hour, minute),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = if (enabled)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.clickable { toggleAlarm() },
                                )
                            }
                            // Status below time
                            item {
                                Text(
                                    if (enabled) ringsIn else "Tap time to set alarm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            // Change time link
                            if (enabled) {
                                item {
                                    Text(
                                        "Change time",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { showTimePicker = true },
                                    )
                                }
                            }
                            // Card-style rows (Samsung settings style)
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainer,
                                            RoundedCornerShape(16.dp),
                                        )
                                        .clickable { showStepPicker = true }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Steps",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            if (stepChallengeEnabled) "Walk to dismiss" else "Disabled",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        if (stepChallengeEnabled) "$stepTarget" else "OFF",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                            RoundedCornerShape(16.dp),
                                        )
                                        .clickable { emergencyStop() }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Emergency stop",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            "Stops alarm completely",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toggleAlarm() {
        enabled = !enabled
        save()
    }

    private fun emergencyStop() {
        AppLog.append(this, "emergency stop pressed")
        sendBroadcast(Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_STOP))
        stopService(Intent(this, AlarmSessionService::class.java))
        scheduler.cancel()
        val s = AlarmSettings(this)
        s.clearChain()
        s.sessionActive = false
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(Notifications.MONITORING_ID)
        nm.cancel(Notifications.REPEAT_ID)
        if (enabled) {
            scheduler.scheduleDaily()
        }
        updateStatus()
        AppLog.append(this, "emergency stop complete, alarm enabled=$enabled")
    }

    private fun save() {
        settings.enabled = enabled
        settings.hour = hour
        settings.minute = minute
        val scheduled = scheduler.scheduleDaily()
        updateStatus()
        if (!enabled) {
            ringsIn = "Alarm off"
        } else if (!scheduled) {
            ringsIn = "Could not schedule alarm"
        }
        AppLog.append(this, "alarm saved enabled=$enabled time=%02d:%02d scheduled=$scheduled".format(hour, minute))
    }

    private fun updateStatus() {
        if (!enabled) {
            dayLabel = ""
            ringsIn = "Alarm off"
            return
        }
        val now = java.time.ZonedDateTime.now()
        val triggerMs = AlarmSettings.nextTriggerMillis(hour, minute)
        val trigger = java.time.Instant.ofEpochMilli(triggerMs).atZone(now.zone)
        val today = now.toLocalDate()
        val triggerDate = trigger.toLocalDate()

        dayLabel = when {
            triggerDate == today -> "Today"
            triggerDate == today.plusDays(1) -> "Tomorrow"
            else -> trigger.format(java.time.format.DateTimeFormatter.ofPattern("EEE"))
        }

        val diffMin = ((triggerMs - System.currentTimeMillis()) / 60_000L).toInt()
        val h = diffMin / 60
        val m = diffMin % 60
        ringsIn = when {
            h == 0 -> "Rings in $m min"
            m == 0 -> "Rings in $h hr"
            else -> "Rings in $h hr $m min"
        }
    }

    companion object {
        fun formatTime12(hour: Int, minute: Int): String {
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            return "%d:%02d %s".format(h, minute, amPm)
        }
    }
}
