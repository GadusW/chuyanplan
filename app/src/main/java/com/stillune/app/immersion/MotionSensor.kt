package com.stillune.app.immersion

import android.content.Context
import android.hardware.*
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.*

/** A gyro-fused rotation vector avoids the drift from integrating angular velocity alone. */
@Suppress("DEPRECATION")
fun motionSensor(context: Context) = callbackFlow {
    val manager = context.getSystemService(SensorManager::class.java)
    val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    if (sensor == null) {
        trySend(Tilt())
        close()
        return@callbackFlow
    }
    val xFilter = ResponsiveTilt()
    val yFilter = ResponsiveTilt()
    val motionFilter = LowPass(.08f)
    val movementSensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    val shakeFilter = LowPass(.10f)
    val shakeXFilter = LowPass(.045f)
    val shakeYFilter = LowPass(.045f)
    var shake = 0f
    var shakeX = 0f
    var shakeY = 0f
    var movementTime = 0L
    var gravity: FloatArray? = null
    var last = 0L
    var baselineX: Float? = null
    var baselineY: Float? = null
    var previousX = 0f
    var previousY = 0f
    val matrix = FloatArray(9)
    val remapped = FloatArray(9)
    val angles = FloatArray(3)
    val pose = PoseReference()
    var displayRotation = -1
    val listener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == movementSensor?.type) {
                val delta = if (movementTime == 0L) .02f else ((event.timestamp - movementTime) / 1e9f).coerceIn(.001f, .1f)
                movementTime = event.timestamp
                val linear = FloatArray(3)
                if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    for (i in 0..2) linear[i] = event.values[i]
                } else {
                    if (gravity == null) gravity = event.values.copyOf(3)
                    for (i in 0..2) {
                        gravity!![i] += (1f - exp(-delta / .65f)) * (event.values[i] - gravity!![i])
                        linear[i] = event.values[i] - gravity!![i]
                    }
                }
                val magnitude = sqrt(linear.sumOf { (it * it).toDouble() }).toFloat()
                shake = shakeFilter.update(((magnitude - .35f) / 6f).coerceIn(0f, 1f), delta)
                shakeX = shakeXFilter.update((linear[0] / 8f).coerceIn(-1f, 1f), delta)
                shakeY = shakeYFilter.update((linear[1] / 8f).coerceIn(-1f, 1f), delta)
                if (event.sensor.type != sensor.type) return
            }
            val dt = if (last == 0L) .02f else ((event.timestamp - last) / 1e9f).coerceIn(.001f, .1f)
            last = event.timestamp
            val rotation = context.getSystemService(WindowManager::class.java).defaultDisplay.rotation
            if (rotation != displayRotation) {
                pose.reset(); baselineX = null; baselineY = null; displayRotation = rotation
            }
            val rawX: Float
            val rawY: Float
            if (sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR || sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                val axes = when (rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, remapped)
                SensorManager.getOrientation(pose.rotation(remapped), angles)
                rawX = angles[2]
                rawY = angles[1]
            } else {
                val gx = event.values[0] / SensorManager.GRAVITY_EARTH
                val gy = event.values[1] / SensorManager.GRAVITY_EARTH
                val gz = event.values[2] / SensorManager.GRAVITY_EARTH
                val a = atan2(gx, sqrt(gy * gy + gz * gz))
                val b = atan2(gy, sqrt(gx * gx + gz * gz))
                when (rotation) {
                    Surface.ROTATION_90 -> { rawX = -b; rawY = a }
                    Surface.ROTATION_180 -> { rawX = -a; rawY = -b }
                    Surface.ROTATION_270 -> { rawX = b; rawY = -a }
                    else -> { rawX = a; rawY = b }
                }
            }
            if (baselineX == null) { baselineX = rawX; baselineY = rawY }
            fun relative(value: Float, base: Float): Float = atan2(sin(value - base), cos(value - base))
            val x = xFilter.update((relative(rawX, baselineX!!) / .42f).coerceIn(-1f, 1f), dt)
            val y = yFilter.update((relative(rawY, baselineY!!) / .42f).coerceIn(-1f, 1f), dt)
            val motion = motionFilter.update(maxOf(shake, (hypot(x - previousX, y - previousY) / dt / 1.5f).coerceIn(0f, 1f)), dt)
            previousX = x; previousY = y
            val rotationMotion = when (rotation) {
                Surface.ROTATION_90 -> -shakeY to shakeX
                Surface.ROTATION_180 -> -shakeX to -shakeY
                Surface.ROTATION_270 -> shakeY to -shakeX
                else -> shakeX to shakeY
            }
            trySend(Tilt(x, y, motion, true, rotationMotion.first, rotationMotion.second))
        }
    }
    val registered = manager.registerListener(listener, sensor, 20_000) // 50 Hz, no high-rate permission.
    if (movementSensor != null && movementSensor.type != sensor.type) manager.registerListener(listener, movementSensor, 20_000)
    if (!registered) trySend(Tilt())
    awaitClose { runCatching { manager.unregisterListener(listener) } }
}.conflate()
