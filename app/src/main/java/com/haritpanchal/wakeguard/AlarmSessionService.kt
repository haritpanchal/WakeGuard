package com.haritpanchal.wakeguard

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.haritpanchal.wakeguard.wake.MotionSample
import com.haritpanchal.wakeguard.wake.WakefulnessDetector

class AlarmSessionService : Service(), SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val detector = WakefulnessDetector()
    private lateinit var sensors: MotionSensorManager
    private lateinit var scheduler: AlarmScheduler
    private var wakeLock: PowerManager.WakeLock? = null
    private var sampleCount = 0
    private var screenOffSamples = 0
    private var lastMinuteLogElapsed = 0L
    private var finished = false
    private var startedElapsed = 0L

    private val finishWindow = Runnable { finishWindow("timeout") }
    private val silenceWatch = Runnable {
        if (sampleCount == 0) {
            acquirePartialWakeLock("no-samples-30s")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensors = MotionSensorManager(getSystemService(SensorManager::class.java), this)
        scheduler = AlarmScheduler(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repeat = scheduler.nextIsRepeat()
        PocLog.append(this, "session started repeat=$repeat")
        try {
            startForeground(
                PocNotifications.MONITORING_ID,
                PocNotifications.monitoring(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            PocLog.append(this, "foreground service started type=specialUse")
        } catch (security: SecurityException) {
            PocLog.append(this, "foreground service rejected ${security.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        vibrate()
        openAlarmActivity()
        startMotion()
        startedElapsed = android.os.SystemClock.elapsedRealtime()
        lastMinuteLogElapsed = startedElapsed
        handler.postDelayed(finishWindow, WINDOW_MS)
        handler.postDelayed(silenceWatch, SILENCE_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sensors.stop()
        releaseWakeLock()
        vibrateCancel()
        PocLog.append(this, "service destroyed")
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || finished) {
            return
        }
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        sampleCount += 1
        if (!interactive) {
            screenOffSamples += 1
        }
        if (sampleCount == 1) {
            val bound = sensors.bound
            PocLog.append(
                this,
                "first sample name=${bound?.name} wakeUp=${bound?.wakeUp} " +
                    "x=${event.values[0]} y=${event.values[1]} z=${event.values[2]} " +
                    "screenInteractive=$interactive",
            )
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastMinuteLogElapsed >= 60_000L) {
            lastMinuteLogElapsed = now
            val snapshot = detector.snapshot()
            PocLog.append(
                this,
                "minute samples=$sampleCount screenOffSamples=$screenOffSamples " +
                    "bursts=${snapshot.validBurstCount} activeMs=${snapshot.totalActiveMs} " +
                    "awake=${snapshot.awake}",
            )
        }
        val decision = detector.add(
            MotionSample(
                timestampMs = System.currentTimeMillis(),
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
            ),
        )
        if (decision.awake) {
            PocLog.append(
                this,
                "awake decision reason=${decision.reason} bursts=${decision.validBurstCount} " +
                    "activeMs=${decision.totalActiveMs} spanMs=${decision.spanMs}",
            )
            finishWindow("awake")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startMotion() {
        val power = getSystemService(PowerManager::class.java)
        PocLog.append(this, "screen interactive=${power.isInteractive}")
        val registered = sensors.start(handler)
        val bound = sensors.bound
        if (!registered || bound == null) {
            PocLog.append(this, "accelerometer unavailable")
            acquirePartialWakeLock("no-sensor")
            return
        }
        PocLog.append(this, "sensor registered name=${bound.name} wakeUp=${bound.wakeUp}")
        if (!bound.wakeUp) {
            acquirePartialWakeLock("non-wake-up-sensor")
        }
    }

    private fun openAlarmActivity() {
        try {
            val launch = Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
            PocLog.append(this, "activity start requested")
        } catch (error: RuntimeException) {
            PocLog.append(this, "activity start threw ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private fun finishWindow(reason: String) {
        if (finished) {
            return
        }
        finished = true
        handler.removeCallbacks(finishWindow)
        handler.removeCallbacks(silenceWatch)
        val snapshot = detector.snapshot()
        PocLog.append(
            this,
            "window end reason=$reason awake=${snapshot.awake} bursts=${snapshot.validBurstCount} " +
                "activeMs=${snapshot.totalActiveMs} samples=$sampleCount screenOffSamples=$screenOffSamples",
        )
        sensors.stop()
        vibrateCancel()
        releaseWakeLock()
        if (snapshot.awake) {
            PocLog.append(this, "no repeat after awake decision")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val scheduled = scheduler.scheduleIn(REPEAT_DELAY_MS, repeat = true)
        PocLog.append(this, "repeat scheduled=$scheduled")
        getSystemService(NotificationManager::class.java)
            .notify(PocNotifications.REPEAT_ID, PocNotifications.repeatScheduled(this))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquirePartialWakeLock(reason: String) {
        if (wakeLock?.isHeld == true) {
            return
        }
        val power = getSystemService(PowerManager::class.java)
        val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeGuard:monitor")
        lock.setReferenceCounted(false)
        lock.acquire(WINDOW_MS + 30_000L)
        wakeLock = lock
        PocLog.append(this, "partial wake lock acquired reason=$reason")
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    private fun vibrate() {
        val vibrator = getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        vibrator.vibrate(
            android.os.VibrationEffect.createWaveform(
                longArrayOf(0, 500, 250, 500, 250, 900),
                0,
            ),
        )
        PocLog.append(this, "vibration started")
    }

    private fun vibrateCancel() {
        getSystemService(android.os.VibratorManager::class.java).defaultVibrator.cancel()
    }

    companion object {
        const val WINDOW_MS = 5 * 60 * 1000L
        const val REPEAT_DELAY_MS = 2 * 60 * 1000L
        private const val SILENCE_MS = 30_000L
    }
}
