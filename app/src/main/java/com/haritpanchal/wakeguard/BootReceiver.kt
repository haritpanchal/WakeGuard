package com.haritpanchal.wakeguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val scheduled = AlarmScheduler(context).scheduleDaily()
        PocLog.append(context, "clock restored action=${intent?.action} scheduled=$scheduled")
    }
}
