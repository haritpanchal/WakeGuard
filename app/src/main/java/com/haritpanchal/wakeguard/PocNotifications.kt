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
    const val MONITORING_ID = 1001
    const val REPEAT_ID = 1002

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WakeGuard alarm",
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = "Morning alarm proof of concept"
        channel.enableVibration(false)
        manager.createNotificationChannel(channel)
    }

    fun monitoring(context: Context): Notification {
        ensureChannel(context)
        return base(context)
            .setContentText("Monitoring movement")
            .setOngoing(true)
            .build()
    }

    fun repeatScheduled(context: Context): Notification {
        ensureChannel(context)
        return base(context)
            .setContentText("Repeat scheduled. Stop cancels it.")
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
