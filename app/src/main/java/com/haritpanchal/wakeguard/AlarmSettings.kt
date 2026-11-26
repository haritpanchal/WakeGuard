package com.haritpanchal.wakeguard

import android.content.Context
import java.time.ZonedDateTime

enum class VibrationIntensity(val key: String, val label: String) {
    GENTLE("gentle", "Gentle"),
    NORMAL("normal", "Normal"),
    AGGRESSIVE("aggressive", "Aggressive");

    companion object {
        fun fromKey(key: String): VibrationIntensity =
            entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

class AlarmSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ENABLED, value).commit()
        }

    var hour: Int
        get() = preferences.getInt(KEY_HOUR, DEFAULT_HOUR)
        set(value) {
            preferences.edit().putInt(KEY_HOUR, value).commit()
        }

    var minute: Int
        get() = preferences.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        set(value) {
            preferences.edit().putInt(KEY_MINUTE, value).commit()
        }

    var sessionActive: Boolean
        get() = preferences.getBoolean(KEY_SESSION_ACTIVE, false)
        set(value) {
            preferences.edit().putBoolean(KEY_SESSION_ACTIVE, value).commit()
        }

    val chainRings: Int
        get() = preferences.getInt(KEY_CHAIN_RINGS, 0)

    fun noteFirstRing() {
        preferences.edit()
            .putLong(KEY_CHAIN_START, System.currentTimeMillis())
            .putInt(KEY_CHAIN_RINGS, 1)
            .putInt(KEY_CHAIN_STEPS, 0)
            .commit()
    }

    fun noteRepeatRing() {
        preferences.edit().putInt(KEY_CHAIN_RINGS, chainRings + 1).commit()
    }

    fun clearChain() {
        preferences.edit()
            .remove(KEY_CHAIN_START)
            .remove(KEY_CHAIN_RINGS)
            .remove(KEY_CHAIN_STEPS)
            .commit()
    }

    var chainSteps: Int
        get() = preferences.getInt(KEY_CHAIN_STEPS, 0)
        set(value) {
            preferences.edit().putInt(KEY_CHAIN_STEPS, value).commit()
        }

    var stepChallengeEnabled: Boolean
        get() = preferences.getBoolean(KEY_STEP_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_STEP_ENABLED, value).commit()
        }

    var stepTarget: Int
        get() = preferences.getInt(KEY_STEP_TARGET, DEFAULT_STEP_TARGET)
        set(value) {
            preferences.edit().putInt(KEY_STEP_TARGET, value).commit()
        }

    var vibrationIntensity: VibrationIntensity
        get() = VibrationIntensity.fromKey(
            preferences.getString(KEY_VIBRATION_INTENSITY, VibrationIntensity.NORMAL.key)
                ?: VibrationIntensity.NORMAL.key
        )
        set(value) {
            preferences.edit().putString(KEY_VIBRATION_INTENSITY, value.key).commit()
        }

    fun shouldScheduleAnotherRing(): Boolean {
        if (!enabled) {
            return false
        }
        val started = preferences.getLong(KEY_CHAIN_START, 0L)
        if (started == 0L) {
            return true
        }
        val elapsed = System.currentTimeMillis() - started
        return chainRings < MAX_RINGS && elapsed < MAX_CHAIN_MS
    }

    companion object {
        const val PREFS = "wakeguard-poc"
        const val DEFAULT_HOUR = 7
        const val DEFAULT_MINUTE = 0
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_CHAIN_START = "chain_start_ms"
        private const val KEY_CHAIN_RINGS = "chain_rings"
        private const val KEY_CHAIN_STEPS = "chain_steps"
        private const val KEY_STEP_ENABLED = "step_challenge_enabled"
        private const val KEY_STEP_TARGET = "step_target"
        private const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
        const val DEFAULT_STEP_TARGET = 100
        val STEP_TARGET_OPTIONS = listOf(50, 100, 150, 200)
        private const val MAX_RINGS = 9
        private const val MAX_CHAIN_MS = 45 * 60 * 1000L

        fun nextTriggerMillis(hour: Int, minute: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
            var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) {
                next = next.plusDays(1)
            }
            return next.toInstant().toEpochMilli()
        }
    }
}
