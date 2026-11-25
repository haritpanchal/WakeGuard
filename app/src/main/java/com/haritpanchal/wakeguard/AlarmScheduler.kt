package com.haritpanchal.wakeguard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class AlarmScheduler(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun scheduleDaily(): Boolean {
        val settings = AlarmSettings(context)
        if (!settings.enabled) {
            cancel()
            return false
        }
        val triggerAt = AlarmSettings.nextTriggerMillis(settings.hour, settings.minute)
        return arm(triggerAt, repeat = false, delayMs = triggerAt - System.currentTimeMillis())
    }

    fun scheduleIn(delayMs: Long, repeat: Boolean): Boolean {
        return arm(System.currentTimeMillis() + delayMs, repeat, delayMs)
    }

    private fun arm(triggerAt: Long, repeat: Boolean, delayMs: Long): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (!alarmManager.canScheduleExactAlarms()) {
            AppLog.append(context, "exact alarm permission missing")
            return false
        }
        val requestCode = preferences.getInt(KEY_REQUEST, REQUEST_FIRE_LEGACY) + 1
        cancelArmed(alarmManager)
        preferences.edit()
            .putBoolean(KEY_NEXT_REPEAT, repeat)
            .putInt(KEY_REQUEST, requestCode)
            .commit()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            firePendingIntent(requestCode),
        )
        AppLog.append(
            context,
            "scheduled api=setExactAndAllowWhileIdle request=$requestCode " +
                "triggerAt=$triggerAt delayMs=$delayMs repeat=$repeat",
        )
        return true
    }

    fun cancel() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        cancelArmed(alarmManager)
        preferences.edit().putBoolean(KEY_NEXT_REPEAT, false).apply()
        AppLog.append(context, "alarm cancelled")
    }

    fun nextIsRepeat(): Boolean = preferences.getBoolean(KEY_NEXT_REPEAT, false)

    private fun cancelArmed(alarmManager: AlarmManager) {
        val current = preferences.getInt(KEY_REQUEST, REQUEST_FIRE_LEGACY)
        alarmManager.cancel(firePendingIntent(REQUEST_FIRE_LEGACY))
        if (current != REQUEST_FIRE_LEGACY) {
            alarmManager.cancel(firePendingIntent(current))
        }
    }

    private fun firePendingIntent(requestCode: Int): PendingIntent {
        val fire = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            fire,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val PREFS = "wakeguard-poc"
        const val KEY_NEXT_REPEAT = "next_is_repeat"
        const val KEY_REQUEST = "fire_request_code"
        const val REQUEST_FIRE_LEGACY = 42
    }
}
