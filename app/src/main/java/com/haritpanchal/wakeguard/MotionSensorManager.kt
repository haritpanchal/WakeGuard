package com.haritpanchal.wakeguard

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler

class MotionSensorManager(
    private val sensorManager: SensorManager,
    private val listener: SensorEventListener,
) {
    data class BoundSensor(val name: String, val wakeUp: Boolean)

    var bound: BoundSensor? = null
        private set

    fun start(handler: Handler): Boolean {
        val wakeUp = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        val sensor = wakeUp ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            return false
        }
        bound = BoundSensor(sensor.name, sensor.isWakeUpSensor)
        return sensorManager.registerListener(
            listener,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL,
            handler,
        )
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }
}
