package com.digimon.glyph.input

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat

/** Live steps only; never imports historical totals or counts while simulation is paused. */
class WalkingInput(private val context: Context, private val onStep: () -> Unit) : SensorEventListener {
    companion object {
        fun enabled(context: Context) = context.getSharedPreferences("walking_input", 0).getBoolean("enabled", false)
        fun setEnabled(context: Context, value: Boolean) { context.getSharedPreferences("walking_input", 0).edit().putBoolean("enabled", value).apply() }
        fun permitted(context: Context) = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        fun available(context: Context) = (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null
    }
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var active = false
    private var sinceNs = 0L
    private var lastNs = 0L
    fun update(runningDPower: Boolean) {
        val wanted = runningDPower && enabled(context) && permitted(context)
        if (!wanted) { stop(); return }
        if (active) return
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return
        sinceNs = SystemClock.elapsedRealtimeNanos()
        lastNs = 0
        active = try { manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
            catch (_: SecurityException) { false }
    }
    fun stop() {
        if (active) manager?.unregisterListener(this)
        active = false
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!active || !enabled(context) || !permitted(context) || event.sensor.type != Sensor.TYPE_STEP_DETECTOR) return
        if (event.timestamp < sinceNs || event.timestamp <= lastNs || event.values.firstOrNull() != 1f) return
        lastNs = event.timestamp
        onStep()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
