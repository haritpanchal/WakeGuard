package com.haritpanchal.wakeguard

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class AlarmSessionService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var scheduler: AlarmScheduler
    private var wakeLock: PowerManager.WakeLock? = null
    private var finished = false

    private var ringing = false
    private var alarmPlayer: MediaPlayer? = null
    private var screenLock: PowerManager.WakeLock? = null

    private var stepCount = 0
    private var stepTarget = AlarmSettings.DEFAULT_STEP_TARGET
    private var stepChallengeEnabled = true
    private var stepSensorRegistered = false
    private var vibrationIntensity = VibrationIntensity.NORMAL

    private val endRing = Runnable { endRing() }
    private val finishWindow = Runnable { finishWindow("timeout") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        scheduler = AlarmScheduler(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle mute action
        if (intent?.action == ACTION_MUTE) {
            muteTone()
            return START_NOT_STICKY
        }

        val repeat = scheduler.nextIsRepeat()
        val settings = AlarmSettings(this)
        if (repeat) settings.noteRepeatRing() else settings.noteFirstRing()
        settings.sessionActive = true

        stepChallengeEnabled = settings.stepChallengeEnabled
        stepTarget = settings.stepTarget
        stepCount = settings.chainSteps
        vibrationIntensity = settings.vibrationIntensity
        AppLog.append(
            this,
            "session started repeat=$repeat ring=${settings.chainRings} " +
                "carrySteps=$stepCount stepsEnabled=$stepChallengeEnabled target=$stepTarget " +
                "vibration=${vibrationIntensity.key}",
        )

        try {
            startForeground(
                Notifications.MONITORING_ID,
                Notifications.monitoring(this, stepCount, fullScreen = true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            AppLog.append(this, "foreground service started type=specialUse")
        } catch (security: SecurityException) {
            AppLog.append(this, "foreground service rejected ${security.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        acquirePartialWakeLock()
        vibrate()
        if (stepChallengeEnabled) {
            startStepDetector()
        }
        handler.postDelayed(endRing, RING_MS)
        if (stepChallengeEnabled) {
            handler.postDelayed(finishWindow, WINDOW_MS)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopStepDetector()
        releaseWakeLock()
        vibrateCancel()
        val settings = AlarmSettings(this)
        settings.sessionActive = false
        // Only save steps if chain is still active (not already cleared by Stop)
        if (settings.chainRings > 0) {
            settings.chainSteps = stepCount
        }
        AppLog.append(this, "service destroyed steps=$stepCount")
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (finished) return
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            onStepDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onStepDetected() {
        stepCount += 1
        val settings = AlarmSettings(this)
        settings.chainSteps = stepCount
        AppLog.append(this, "step total=$stepCount/$stepTarget")

        try {
            getSystemService(NotificationManager::class.java)
                .notify(Notifications.MONITORING_ID, Notifications.monitoring(this, stepCount))
        } catch (_: Exception) {}

        if (stepCount >= stepTarget) {
            AppLog.append(this, "step target reached! steps=$stepCount")
            finishWindow("steps_complete")
        }
    }

    private fun muteTone() {
        stopAlarmTone()
        AppLog.append(this, "tone muted by user, vibration continues")
    }

    private fun startStepDetector() {
        val sensorManager = getSystemService(SensorManager::class.java)
        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepDetector == null) {
            AppLog.append(this, "step detector sensor unavailable")
            return
        }
        stepSensorRegistered = sensorManager.registerListener(
            this, stepDetector, SensorManager.SENSOR_DELAY_FASTEST, handler,
        )
        AppLog.append(
            this,
            "step detector registered=$stepSensorRegistered name=${stepDetector.name} wakeUp=${stepDetector.isWakeUpSensor}",
        )
    }

    private fun stopStepDetector() {
        if (stepSensorRegistered) {
            getSystemService(SensorManager::class.java).unregisterListener(this)
            stepSensorRegistered = false
        }
    }

    private fun finishWindow(reason: String) {
        if (finished) return
        finished = true
        handler.removeCallbacks(finishWindow)
        handler.removeCallbacks(endRing)
        AppLog.append(this, "window end reason=$reason steps=$stepCount")

        stopStepDetector()
        vibrateCancel()
        releaseWakeLock()

        val settings = AlarmSettings(this)
        val stepsComplete = stepChallengeEnabled && stepCount >= stepTarget

        if (stepsComplete || !stepChallengeEnabled || !settings.shouldScheduleAnotherRing()) {
            val why = when {
                stepsComplete -> "steps_complete"
                !stepChallengeEnabled -> "no_step_challenge"
                else -> "chain cap"
            }
            AppLog.append(this, "no repeat after $why steps=$stepCount")
            settings.clearChain()
            scheduler.scheduleDaily()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        settings.chainSteps = stepCount
        val scheduled = scheduler.scheduleIn(NEXT_RING_DELAY_MS, repeat = true)
        AppLog.append(this, "steps incomplete, next ring in ${NEXT_RING_DELAY_MS}ms scheduled=$scheduled steps=$stepCount")
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.REPEAT_ID, Notifications.repeatScheduled(this))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquirePartialWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java)
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeGuard:monitor")
        lock.setReferenceCounted(false)
        lock.acquire(WINDOW_MS + 30_000L)
        wakeLock = lock
        AppLog.append(this, "partial wake lock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun vibrate() {
        wakeScreen()
        val vibrator = getSystemService(android.os.VibratorManager::class.java).defaultVibrator

        val (pattern, amplitudes) = when (vibrationIntensity) {
            VibrationIntensity.GENTLE -> Pair(
                longArrayOf(0, 200, 500, 200, 500, 300),
                intArrayOf(0, 80, 0, 80, 0, 100),
            )
            VibrationIntensity.NORMAL -> Pair(
                longArrayOf(0, 500, 250, 500, 250, 900),
                intArrayOf(0, 180, 0, 180, 0, 255),
            )
            VibrationIntensity.AGGRESSIVE -> Pair(
                longArrayOf(0, 800, 100, 800, 100, 1200),
                intArrayOf(0, 255, 0, 255, 0, 255),
            )
        }

        val effect = if (vibrator.hasAmplitudeControl()) {
            android.os.VibrationEffect.createWaveform(pattern, amplitudes, 0)
        } else {
            android.os.VibrationEffect.createWaveform(pattern, 0)
        }
        vibrator.vibrate(effect)

        startAlarmTone()
        ringing = true
        AppLog.append(this, "vibration(${vibrationIntensity.key}) + tone started for ${RING_MS}ms")
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val power = getSystemService(PowerManager::class.java)
        val lock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WakeGuard:screen",
        )
        lock.acquire(RING_MS + 5_000L)
        screenLock = lock
        AppLog.append(this, "screen wake lock acquired")
    }

    private fun releaseScreenLock() {
        screenLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        screenLock = null
    }

    private fun startAlarmTone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            player.setDataSource(this, uri)
            player.isLooping = true
            player.prepare()
            player.start()
            alarmPlayer = player
            AppLog.append(this, "alarm tone playing uri=$uri")
        } catch (e: Exception) {
            AppLog.append(this, "alarm tone failed ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun stopAlarmTone() {
        alarmPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
                player.release()
            } catch (_: Exception) {}
        }
        alarmPlayer = null
    }

    private fun endRing() {
        if (finished || !ringing) return
        ringing = false
        getSystemService(android.os.VibratorManager::class.java).defaultVibrator.cancel()
        stopAlarmTone()
        releaseScreenLock()
        if (stepChallengeEnabled) {
            AppLog.append(this, "ring stopped, waiting for steps ($stepCount/$stepTarget)")
        } else {
            AppLog.append(this, "ring stopped, no step challenge")
            finishWindow("ring_ended")
        }
    }

    private fun vibrateCancel() {
        getSystemService(android.os.VibratorManager::class.java).defaultVibrator.cancel()
        stopAlarmTone()
        releaseScreenLock()
    }

    companion object {
        const val ACTION_MUTE = "com.haritpanchal.wakeguard.MUTE"
        const val WINDOW_MS = 5 * 60 * 1000L
        const val RING_MS = 45 * 1000L
        private const val NEXT_RING_DELAY_MS = 1_000L
    }
}
