package com.haritpanchal.wakeguard

import android.Manifest
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.TimePickerType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val settings by lazy { AlarmSettings(this) }
    private val scheduler by lazy { AlarmScheduler(this) }
    private var enabled by mutableStateOf(false)
    private var hour by mutableStateOf(AlarmSettings.DEFAULT_HOUR)
    private var minute by mutableStateOf(AlarmSettings.DEFAULT_MINUTE)
    private var message by mutableStateOf("Alarm off")
    private var showTimePicker by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        PocLog.append(this, "notification permission granted=$granted")
        if (!granted) {
            message = "Notifications denied"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enabled = settings.enabled
        hour = settings.hour
        minute = settings.minute
        message = describe()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                        item {
                            Text(
                                formatTime12(hour, minute),
                                style = MaterialTheme.typography.displayMedium,
                                modifier = Modifier.clickable { showTimePicker = true },
                            )
                        }
                        item {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        item {
                            Button(onClick = { toggle() }) {
                                Text(if (enabled) "Cancel alarm" else "Set alarm")
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
        val trigger = Instant.ofEpochMilli(AlarmSettings.nextTriggerMillis(hour, minute))
            .atZone(ZoneId.systemDefault())
        val label = trigger.format(NEXT_FORMAT)
        return "Next: $label"
    }

    companion object {
        private val NEXT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE hh:mm a")

        fun formatTime12(hour: Int, minute: Int): String {
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            return "%d:%02d %s".format(h, minute, amPm)
        }
    }
}
