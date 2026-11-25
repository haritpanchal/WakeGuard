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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
    private var message by mutableStateOf("Alarm off")
    private var showTimePicker by mutableStateOf(false)
    private var stepChallengeEnabled by mutableStateOf(true)
    private var stepTarget by mutableIntStateOf(AlarmSettings.DEFAULT_STEP_TARGET)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        PocLog.append(this, "notification permission granted=$granted")
        if (!granted) {
            message = "Notifications denied"
        }
    }

    private val activityRecognitionPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        PocLog.append(this, "activity recognition permission granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enabled = settings.enabled
        hour = settings.hour
        minute = settings.minute
        stepChallengeEnabled = settings.stepChallengeEnabled
        stepTarget = settings.stepTarget
        message = describe()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            activityRecognitionPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        setContent {
            MaterialTheme {
                if (showTimePicker) {
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
                } else {
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Time display
                        item {
                            Text(
                                formatTime12(hour, minute),
                                style = MaterialTheme.typography.displayMedium,
                                modifier = Modifier.clickable { showTimePicker = true },
                            )
                        }
                        // Status
                        item {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        // Set / Cancel alarm
                        item {
                            Button(onClick = { toggle() }) {
                                Text(if (enabled) "Cancel alarm" else "Set alarm")
                            }
                        }
                        // Step challenge toggle
                        item {
                            Button(onClick = { toggleStepChallenge() }) {
                                Text(if (stepChallengeEnabled) "Steps: ON" else "Steps: OFF")
                            }
                        }
                        // Step target (only when steps enabled)
                        if (stepChallengeEnabled) {
                            item {
                                Button(onClick = { cycleStepTarget() }) {
                                    Text("Target: $stepTarget steps")
                                }
                            }
                        }
                        // Emergency stop — always visible
                        item {
                            Button(
                                onClick = { emergencyStop() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text("Emergency stop")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toggle() {
        enabled = !enabled
        save()
    }

    private fun toggleStepChallenge() {
        stepChallengeEnabled = !stepChallengeEnabled
        settings.stepChallengeEnabled = stepChallengeEnabled
        PocLog.append(this, "step challenge enabled=$stepChallengeEnabled")
    }

    private fun cycleStepTarget() {
        val options = AlarmSettings.STEP_TARGET_OPTIONS
        val currentIndex = options.indexOf(stepTarget)
        val nextIndex = if (currentIndex < 0 || currentIndex >= options.size - 1) 0 else currentIndex + 1
        stepTarget = options[nextIndex]
        settings.stepTarget = stepTarget
        PocLog.append(this, "step target changed to $stepTarget")
    }

    private fun emergencyStop() {
        PocLog.append(this, "emergency stop pressed")
        // Stop any running session
        sendBroadcast(Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_STOP))
        // Also directly stop service and cancel alarms
        stopService(Intent(this, AlarmSessionService::class.java))
        scheduler.cancel()
        val s = AlarmSettings(this)
        s.clearChain()
        s.sessionActive = false
        // Dismiss notifications
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.cancel(PocNotifications.MONITORING_ID)
        nm.cancel(PocNotifications.REPEAT_ID)
        // Re-schedule daily if alarm is still enabled
        if (enabled) {
            scheduler.scheduleDaily()
        }
        message = if (enabled) describe() else "Alarm off"
        PocLog.append(this, "emergency stop complete, alarm enabled=$enabled")
    }

    private fun save() {
        settings.enabled = enabled
        settings.hour = hour
        settings.minute = minute
        val scheduled = scheduler.scheduleDaily()
        message = when {
            !enabled -> "Alarm off"
            scheduled -> describe()
            else -> "Could not schedule alarm"
        }
        PocLog.append(this, "alarm saved enabled=$enabled time=%02d:%02d scheduled=$scheduled".format(hour, minute))
    }

    private fun describe(): String {
        if (!enabled) {
            return "Alarm off"
        }
        val now = System.currentTimeMillis()
        val trigger = AlarmSettings.nextTriggerMillis(hour, minute)
        val diffMin = ((trigger - now) / 60_000L).toInt()
        val h = diffMin / 60
        val m = diffMin % 60
        val relative = when {
            h == 0 -> "in $m min"
            m == 0 -> "in $h hr"
            else -> "in $h hr $m min"
        }
        return "Rings $relative"
    }

    companion object {
        fun formatTime12(hour: Int, minute: Int): String {
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            return "%d:%02d %s".format(h, minute, amPm)
        }
    }
}
