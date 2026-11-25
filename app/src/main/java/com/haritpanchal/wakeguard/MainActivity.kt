package com.haritpanchal.wakeguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val scheduler by lazy { AlarmScheduler(this) }
    private var status by mutableStateOf("No test alarm scheduled.")

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        PocLog.append(this, "notification permission granted=$granted")
        status = if (granted) {
            "Notifications allowed."
        } else {
            "Notifications denied. Stop will have no visible control."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("WakeGuard test")
                    Text(status)
                    Text("Swipe the app away after scheduling. Settings Force stop clears Android alarms.")
                    Button(onClick = { schedule() }) {
                        Text("Fire in 2 minutes")
                    }
                }
            }
        }
    }

    private fun schedule() {
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        PocLog.append(this, "schedule tapped notificationsGranted=$granted")
        val scheduled = scheduler.scheduleIn(INITIAL_DELAY_MS, repeat = false)
        val fireAt = LocalTime.now().plusMinutes(2).format(TIME)
        status = if (scheduled) {
            "Alarm set for $fireAt. Swipe away and turn the screen off."
        } else {
            "Exact alarm was not scheduled."
        }
    }

    companion object {
        const val INITIAL_DELAY_MS = 2 * 60 * 1000L
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
