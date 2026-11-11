package com.diyf1wheel.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock

/**
 * Chooses the best orientation source and turns it into a relative steering angle.
 *
 * Preference order:
 *  1. GAME_ROTATION_VECTOR - gyro + accelerometer fusion, no magnetometer, so it is
 *     immune to magnets/metal near the phone. Rotation about any axis that is not
 *     vertical is corrected by gravity, so a near-upright wheel does not drift.
 *  2. ROTATION_VECTOR - same plus magnetometer (can be disturbed by magnets).
 *  3. GRAVITY / ACCELEROMETER only - for phones with no gyroscope. Works, but is
 *     noisier and reacts to shaking; the wheel must not be held flat.
 */
class SteeringSensor(context: Context) : SensorEventListener {
    enum class Mode(val label: String) { GAME_ROTATION("Game rotation vector (gyro+accel)"),
        ROTATION("Rotation vector (gyro+accel+mag)"), GRAVITY("Gravity only (no gyroscope)"), NONE("No usable sensor") }

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var thread: HandlerThread? = null

    val mode: Mode
    private val sensor: Sensor?
    val sensorName: String
    val hasGyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    @Volatile var quat: Quat = Quat.IDENTITY; private set
    @Volatile var gravity: Vec3 = Vec3(0.0, 9.81, 0.0); private set
    @Volatile var lastSampleNanos = 0L; private set
    val rate = RateMeter()

    /** Called on the sensor thread for every sample. */
    var onSample: ((tNanos: Long) -> Unit)? = null

    private val qBuf = FloatArray(4)
    private val gravityFilter = DoubleArray(3)

    init {
        val game = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val grav = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        when {
            game != null && hasGyroscope -> { mode = Mode.GAME_ROTATION; sensor = game }
            rot != null && hasGyroscope -> { mode = Mode.ROTATION; sensor = rot }
            grav != null -> { mode = Mode.GRAVITY; sensor = grav }
            else -> { mode = Mode.NONE; sensor = null }
        }
        sensorName = sensor?.let { "${it.name} (${it.vendor})" } ?: "none"
    }

    val isRunning get() = thread != null

    /** elapsedRealtimeNanos of the last sample (event timestamps are not guaranteed to share that base). */
    @Volatile private var lastSampleClock = 0L

    /** True when samples are arriving (no gap longer than 250 ms). */
    fun healthy(nowNanos: Long): Boolean =
        sensor != null && isRunning && lastSampleClock != 0L && nowNanos - lastSampleClock < 250_000_000L

    fun start() {
        if (thread != null || sensor == null) return
        val t = HandlerThread("steering-sensor", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
        thread = t
        rate.reset()
        lastSampleNanos = 0L
        lastSampleClock = 0L
        // SENSOR_DELAY_FASTEST: typically 200-500 Hz with HIGH_SAMPLING_RATE_SENSORS.
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, Handler(t.looper))
    }

    fun stop() {
        sm.unregisterListener(this)
        thread?.quitSafely()
        thread = null
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getQuaternionFromVector(qBuf, e.values)   // -> w, x, y, z
                quat = Quat(qBuf[0].toDouble(), qBuf[1].toDouble(), qBuf[2].toDouble(), qBuf[3].toDouble()).normalized()
            }
            Sensor.TYPE_GRAVITY -> gravity = Vec3(e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
            Sensor.TYPE_ACCELEROMETER -> {
                // crude low-pass to approximate gravity on phones without a gravity sensor
                val k = if (lastSampleNanos == 0L) 1.0 else 0.15
                for (i in 0..2) gravityFilter[i] += (e.values[i] - gravityFilter[i]) * k
                gravity = Vec3(gravityFilter[0], gravityFilter[1], gravityFilter[2])
            }
        }
        lastSampleNanos = e.timestamp
        lastSampleClock = SystemClock.elapsedRealtimeNanos()
        rate.tick(e.timestamp)
        onSample?.invoke(e.timestamp)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
