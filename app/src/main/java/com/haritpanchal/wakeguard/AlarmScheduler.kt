package com.haritpanchal.wakeguard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class AlarmScheduler(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun scheduleIn(delayMs: Long, repeat: Boolean): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (!alarmManager.canScheduleExactAlarms()) {
            PocLog.append(context, "exact alarm permission missing")
            return false
        }
        preferences.edit().putBoolean(KEY_NEXT_REPEAT, repeat).apply()
        val triggerAt = System.currentTimeMillis() + delayMs
        val fire = firePendingIntent()
        alarmManager.cancel(fire)
        val show = PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fire)
        PocLog.append(
            context,
            "scheduled triggerAt=$triggerAt delayMs=$delayMs repeat=$repeat",
        )
        return true
    }

    fun cancel() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(firePendingIntent())
        preferences.edit().putBoolean(KEY_NEXT_REPEAT, false).apply()
        PocLog.append(context, "alarm cancelled")
    }

    fun nextIsRepeat(): Boolean = preferences.getBoolean(KEY_NEXT_REPEAT, false)

    private fun firePendingIntent(): PendingIntent {
        val fire = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_FIRE,
            fire,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val PREFS = "wakeguard-poc"
        const val KEY_NEXT_REPEAT = "next_is_repeat"
        const val REQUEST_FIRE = 42
        const val REQUEST_SHOW = 43
    }
}
