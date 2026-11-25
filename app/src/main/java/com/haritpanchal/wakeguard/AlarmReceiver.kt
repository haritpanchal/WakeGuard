package com.haritpanchal.wakeguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP -> {
                PocLog.append(context, "stop requested")
                val scheduler = AlarmScheduler(context)
                scheduler.cancel()
                AlarmSettings(context).clearChain()
                scheduler.scheduleDaily()
                context.stopService(Intent(context, AlarmSessionService::class.java))
                val manager = context.getSystemService(android.app.NotificationManager::class.java)
                manager.cancel(PocNotifications.MONITORING_ID)
                manager.cancel(PocNotifications.REPEAT_ID)
            }
            else -> {
                PocLog.append(context, "alarm delivered to receiver")
                val service = Intent(context, AlarmSessionService::class.java)
                context.startForegroundService(service)
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.haritpanchal.wakeguard.FIRE"
        const val ACTION_STOP = "com.haritpanchal.wakeguard.STOP"
    }
}
