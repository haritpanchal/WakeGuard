package com.haritpanchal.wakeguard

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AlarmActivity : ComponentActivity(), SensorEventListener {
    private var steps by mutableIntStateOf(0)
    private var muted by mutableStateOf(false)
    private val handler = Handler(Looper.getMainLooper())
    private var stepSensorRegistered = false
    private var stepChallengeEnabled = true
    private var stepTarget = AlarmSettings.DEFAULT_STEP_TARGET

    private val refreshState = object : Runnable {
        override fun run() {
            val settings = AlarmSettings(this@AlarmActivity)
            steps = settings.chainSteps
            if (stepChallengeEnabled && steps >= stepTarget) {
                finish()
                return
            }
            if (!settings.sessionActive) {
                finish()
                return
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val settings = AlarmSettings(this)
        val watching = settings.sessionActive
        AppLog.append(this, "activity created watching=$watching")
        if (!watching) {
            AppLog.append(this, "session not active, closing immediately")
            finish()
            return
        }
        stepChallengeEnabled = settings.stepChallengeEnabled
        stepTarget = settings.stepTarget
        steps = settings.chainSteps
        if (stepChallengeEnabled) {
            startStepListener()
        }
        handler.post(refreshState)

        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(time, style = MaterialTheme.typography.titleLarge)

                    if (stepChallengeEnabled) {
                        Text(
                            "$steps / $stepTarget steps",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            "Walk to dismiss alarm",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            "Alarm ringing",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (!muted) {
                        Button(onClick = { muteAlarm() }) {
                            Text("Mute")
                        }
                    }

                    if (!stepChallengeEnabled) {
                        Button(
                            onClick = { stopAlarm() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshState)
        stopStepListener()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            steps = AlarmSettings(this).chainSteps
            if (stepChallengeEnabled && steps >= stepTarget) {
                finish()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun muteAlarm() {
        muted = true
        val muteIntent = Intent(this, AlarmSessionService::class.java)
            .setAction(AlarmSessionService.ACTION_MUTE)
        startService(muteIntent)
    }

    private fun startStepListener() {
        val sensorManager = getSystemService(SensorManager::class.java)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepDetector != null) {
            stepSensorRegistered = sensorManager.registerListener(
                this, stepDetector, SensorManager.SENSOR_DELAY_FASTEST, handler,
            )
        }
    }

    private fun stopStepListener() {
        if (stepSensorRegistered) {
            getSystemService(SensorManager::class.java).unregisterListener(this)
            stepSensorRegistered = false
        }
    }

    private fun stopAlarm() {
        sendBroadcast(Intent(this, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_STOP))
        finish()
    }
}
