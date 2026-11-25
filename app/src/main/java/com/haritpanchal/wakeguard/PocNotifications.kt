package com.haritpanchal.wakeguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object PocNotifications {
    const val CHANNEL_ID = "wakeguard-alarm"
    const val CHANNEL_UPDATE_ID = "wakeguard-progress"
    const val MONITORING_ID = 1001
    const val REPEAT_ID = 1002

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WakeGuard alarm",
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = "Morning alarm"
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)

        val updateChannel = NotificationChannel(
            CHANNEL_UPDATE_ID,
            "Step progress",
            NotificationManager.IMPORTANCE_LOW,
        )
        updateChannel.description = "Silent step progress updates"
        updateChannel.enableVibration(false)
        updateChannel.setSound(null, null)
        manager.createNotificationChannel(updateChannel)
    }

    fun monitoring(context: Context, steps: Int = 0, fullScreen: Boolean = false): Notification {
        ensureChannel(context)
        val settings = AlarmSettings(context)
        val target = settings.stepTarget
        val stepsEnabled = settings.stepChallengeEnabled
        val remaining = target - steps
        val text = when {
            !stepsEnabled -> "Alarm active"
            steps >= target -> "Done! $steps steps"
            steps > 0 -> "$steps / $target steps — $remaining to go"
            else -> "Walk $target steps to dismiss"
        }
        if (fullScreen) {
            return base(context)
                .setContentText(text)
                .setOngoing(true)
                .setFullScreenIntent(alarmActivityIntent(context), true)
                .build()
        }
        // Updates: use low-priority builder so the full-screen intent is not re-triggered
        return updateBase(context)
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    fun repeatScheduled(context: Context): Notification {
        ensureChannel(context)
        return base(context)
            .setContentText("Steps incomplete — ringing again soon")
            .setOngoing(false)
            .build()
    }

    private fun base(context: Context): Notification.Builder {
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_stat))
            .setContentTitle("WakeGuard")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .addAction(stopAction(context))
    }

    private fun updateBase(context: Context): Notification.Builder {
        return Notification.Builder(context, CHANNEL_UPDATE_ID)
            .setSmallIcon(Icon.createWithResource(context, R.drawable.ic_stat))
            .setContentTitle("WakeGuard")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .addAction(stopAction(context))
    }

    private fun alarmActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            8,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopAction(context: Context): Notification.Action {
        val stop = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_STOP)
        val pending = PendingIntent.getBroadcast(
            context,
            7,
            stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_stat),
            "Stop",
            pending,
        ).build()
    }
}
