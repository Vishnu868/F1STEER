package com.diyf1wheel.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.diyf1wheel.net.Protocol
import com.diyf1wheel.net.UdpLink
import com.diyf1wheel.sensor.OneEuroFilter
import com.diyf1wheel.sensor.Quat
import com.diyf1wheel.sensor.SteeringMath
import com.diyf1wheel.sensor.SteeringSensor
import com.diyf1wheel.sensor.SteeringTuning
import com.diyf1wheel.sensor.Vec3
import com.diyf1wheel.settings.Settings
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide controller state. Owned by [com.diyf1wheel.WheelApp].
 *
 * Safety rules enforced here:
 *  - The ACTIVE flag (which lets the PC drive the virtual controller) is only set
 *    while the driving screen is in the foreground AND the sensor is healthy.
 *  - When no screen is visible for 600 ms the sensor and link stop and the PC is
 *    told BYE, so it neutralises immediately.
 */
class WheelEngine(private val context: Context) {
    val settings = Settings(context)
    val sensor = SteeringSensor(context)
    val link = UdpLink(context) { phoneMs, rtt -> buildState(phoneMs, rtt) }

    @Volatile var tuning: SteeringTuning = settings.tuning
        private set
    @Volatile private var neutral: Quat? = settings.neutral
    @Volatile private var neutralGravity: Vec3? = settings.neutralGravity
    @Volatile var axis: Vec3 = settings.axis
        private set

    private val filter = OneEuroFilter(tuning.minCutoffHz(), beta = 0.03)

    // live values (written on the sensor thread / UI thread, read by the sender)
    @Volatile var rawAngleDeg = 0.0; private set
    @Volatile var filteredAngleDeg = 0.0; private set
    @Volatile var steer = 0.0; private set
    @Volatile var relative: Quat = Quat.IDENTITY; private set

    @Volatile var throttle = 0f
    @Volatile var brake = 0f
    private val buttons = AtomicInteger(0)
    val gearUp = AtomicInteger(0)
    val gearDown = AtomicInteger(0)

    @Volatile var driving = false
        private set

    val calibrated get() = when (sensor.mode) {
        SteeringSensor.Mode.GRAVITY -> neutralGravity != null
        else -> neutral != null
    }

    private val main = Handler(Looper.getMainLooper())
    private var users = 0
    private val stopRunnable = Runnable { stopAll() }

    init {
        sensor.onSample = { t -> onSample(t) }
    }

    // ------------------------------------------------------------ lifecycle
    /** Call from every Activity.onResume(). */
    fun acquire() {
        main.removeCallbacks(stopRunnable)
        users++
        sensor.start()
        if (settings.autoConnect && link.status == UdpLink.Status.STOPPED && settings.host.isNotEmpty()) connect()
    }

    /** Call from every Activity.onPause(). */
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) main.postDelayed(stopRunnable, 600)
    }

    private fun stopAll() {
        setDriving(false)
        link.stop()
        sensor.stop()
    }

    fun connect() {
        settings.autoConnect = true
        link.start(settings.host, settings.port, settings.pin, settings.sendRateHz)
    }

    fun disconnect() {
        settings.autoConnect = false
        link.stop()
    }

    fun setDriving(on: Boolean) {
        driving = on
        if (!on) clearInputs()
        else if (!calibrated) pendingAutoCentre = true  // first run: first sample = centre
    }

    @Volatile private var pendingAutoCentre = false

    fun clearInputs() {
        throttle = 0f
        brake = 0f
        buttons.set(0)
    }

    // ------------------------------------------------------------ controls
    fun setButton(b: Protocol.Button, down: Boolean) {
        while (true) {
            val old = buttons.get()
            val new = if (down) old or b.bit else old and b.bit.inv()
            if (buttons.compareAndSet(old, new)) return
        }
    }

    fun isButtonDown(b: Protocol.Button) = buttons.get() and b.bit != 0

    fun shiftUp() { gearUp.updateAndGet { (it + 1) and 0xFFFF } }
    fun shiftDown() { gearDown.updateAndGet { (it + 1) and 0xFFFF } }

    // ------------------------------------------------------------ calibration
    fun setCentre(save: Boolean = true) {
        neutral = sensor.quat
        neutralGravity = sensor.gravity
        filter.reset()
        if (save) {
            settings.neutral = neutral
            settings.neutralGravity = neutralGravity
        }
    }

    /** Step 2 of the axis wizard (step 1 = [setCentre]). Returns an error message or null. */
    fun finishAxisCalibration(): String? {
        if (sensor.mode == SteeringSensor.Mode.GRAVITY)
            return "Axis calibration needs a gyroscope. The default axis (screen normal) is used."
        val n = neutral ?: return "Set the centre first."
        val a = SteeringMath.axisFromRightTurn(SteeringMath.relative(n, sensor.quat))
            ?: return "Turn the wheel further to the right (at least 30°) and try again."
        axis = a
        settings.axis = a
        filter.reset()
        return null
    }

    fun resetAxis() {
        settings.resetAxis()
        axis = SteeringMath.DEFAULT_AXIS
        filter.reset()
    }

    fun updateTuning(t: SteeringTuning) {
        tuning = t
        settings.tuning = t
        filter.minCutoff = t.minCutoffHz()
    }

    // ------------------------------------------------------------ processing
    private fun onSample(tNanos: Long) {
        if (pendingAutoCentre) {
            pendingAutoCentre = false
            setCentre(save = false)
        }
        val angle: Double = when (sensor.mode) {
            SteeringSensor.Mode.GRAVITY -> {
                val g0 = neutralGravity ?: return
                SteeringMath.gravityTwistDeg(g0, sensor.gravity, axis) ?: rawAngleDeg
            }
            else -> {
                val n = neutral ?: return
                val rel = SteeringMath.relative(n, sensor.quat)
                relative = rel
                SteeringMath.twistDeg(rel, axis)
            }
        }
        rawAngleDeg = angle
        val f = filter.filter(angle, tNanos)
        filteredAngleDeg = f
        steer = tuning.output(f)
    }

    fun sensorHealthy(): Boolean = sensor.healthy(SystemClock.elapsedRealtimeNanos())

    private fun buildState(phoneMs: Long, rtt: Float?): Protocol.State {
        val healthy = sensorHealthy()
        val active = driving && healthy && calibrated
        var flags = 0
        if (active) flags = flags or Protocol.F_ACTIVE
        if (healthy) flags = flags or Protocol.F_SENSOR_OK
        if (calibrated) flags = flags or Protocol.F_CALIBRATED
        return Protocol.State(
            phoneMs = phoneMs,
            steer = if (active) steer.toFloat() else 0f,
            throttle = if (active) throttle else 0f,
            brake = if (active) brake else 0f,
            buttons = if (active) buttons.get() else 0,
            gearUp = gearUp.get(),
            gearDown = gearDown.get(),
            rawAngleDeg = rawAngleDeg.toFloat(),
            sensorHz = sensor.rate.hz.toInt(),
            rttMs = rtt,
            flags = flags,
        )
    }
}
