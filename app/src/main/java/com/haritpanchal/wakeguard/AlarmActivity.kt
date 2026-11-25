package com.haritpanchal.wakeguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val watching = AlarmSettings(this).sessionActive
        PocLog.append(this, "activity created watching=$watching")
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(time)
                    Text(if (watching) "Watching movement" else "This ring already finished")
                    Button(onClick = { if (watching) stopAlarm() else finish() }) {
                        Text(if (watching) "Stop" else "Close")
                    }
                }
            }
        }
    }

    private fun stopAlarm() {
        sendBroadcast(Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_STOP))
        finish()
    }
}
