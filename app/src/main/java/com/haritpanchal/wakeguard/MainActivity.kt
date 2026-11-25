package com.haritpanchal.wakeguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val settings by lazy { AlarmSettings(this) }
    private val scheduler by lazy { AlarmScheduler(this) }
    private var enabled by mutableStateOf(false)
    private var hour by mutableStateOf(AlarmSettings.DEFAULT_HOUR)
    private var minute by mutableStateOf(AlarmSettings.DEFAULT_MINUTE)
    private var message by mutableStateOf("Off.")

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        PocLog.append(this, "notification permission granted=$granted")
        if (!granted) {
            message = "Notifications denied. Stop will have no visible control."
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
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item { Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.displayMedium) }
                    item { Text(message) }
                    item {
                        Button(onClick = { toggle() }) {
                            Text(if (enabled) "Turn off" else "Turn on")
                        }
                    }
                    item {
                        Button(onClick = { changeHour(-1) }) { Text("Hour earlier") }
                    }
                    item {
                        Button(onClick = { changeHour(1) }) { Text("Hour later") }
                    }
                    item {
                        Button(onClick = { changeMinute(-5) }) { Text("5 min earlier") }
                    }
                    item {
                        Button(onClick = { changeMinute(5) }) { Text("5 min later") }
                    }
                    item {
                        Button(onClick = { changeMinute(-1) }) { Text("1 min earlier") }
                    }
                    item {
                        Button(onClick = { changeMinute(1) }) { Text("1 min later") }
                    }
                }
            }
        }
    }

    private fun toggle() {
        enabled = !enabled
        save()
    }

    private fun changeHour(delta: Int) {
        hour = (hour + delta).mod(24)
        save()
    }

    private fun changeMinute(delta: Int) {
        val total = hour * 60 + minute + delta
        val wrapped = total.mod(24 * 60)
        hour = wrapped / 60
        minute = wrapped % 60
        save()
    }

    private fun save() {
        settings.enabled = enabled
        settings.hour = hour
        settings.minute = minute
        val scheduled = scheduler.scheduleDaily()
        message = when {
            !enabled -> "Off. Nothing is scheduled."
            scheduled -> describe()
            else -> "Exact alarm was not scheduled."
        }
        PocLog.append(this, "alarm saved enabled=$enabled time=%02d:%02d scheduled=$scheduled".format(hour, minute))
    }

    private fun describe(): String {
        if (!enabled) {
            return "Off. Nothing is scheduled."
        }
        val trigger = AlarmSettings.nextTriggerMillis(hour, minute)
        val label = Instant.ofEpochMilli(trigger).atZone(ZoneId.systemDefault()).format(NEXT)
        return "Next $label"
    }

    companion object {
        private val NEXT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm")
    }
}
