package com.diyf1wheel.sensor

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/** Unit quaternion (w, x, y, z) as delivered by Android's rotation-vector sensors. */
data class Quat(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(o: Quat) = Quat(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    fun conj() = Quat(w, -x, -y, -z)

    fun normalized(): Quat {
        val n = sqrt(w * w + x * x + y * y + z * z)
        return if (n < 1e-12) IDENTITY else Quat(w / n, x / n, y / n, z / n)
    }

    companion object {
        val IDENTITY = Quat(1.0, 0.0, 0.0, 0.0)
        fun axisAngle(ax: Vec3, deg: Double): Quat {
            val a = ax.normalized()
            val h = Math.toRadians(deg) / 2
            val s = kotlin.math.sin(h)
            return Quat(kotlin.math.cos(h), a.x * s, a.y * s, a.z * s)
        }
    }
}

data class Vec3(val x: Double, val y: Double, val z: Double) {
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun len() = sqrt(dot(this))
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    fun normalized(): Vec3 { val l = len(); return if (l < 1e-12) Vec3(0.0, 0.0, -1.0) else Vec3(x / l, y / l, z / l) }
}

object SteeringMath {
    /**
     * Default steering axis in the phone's own sensor frame: the screen normal,
     * pointing INTO the screen. With the phone facing the driver, turning the wheel
     * clockwise (to the right) is a positive rotation about this axis.
     */
    val DEFAULT_AXIS = Vec3(0.0, 0.0, -1.0)

    /** Relative rotation from the neutral pose to the current pose, in the neutral body frame. */
    fun relative(neutral: Quat, current: Quat): Quat = (neutral.conj() * current).normalized()

    /**
     * Swing-twist decomposition: the signed angle (degrees, -180..180) of [rel]'s
     * rotation component about [axis]. Wobble about the other two axes (the wheel
     * tilting, the phone flexing in the mount) is discarded.
     */
    fun twistDeg(rel: Quat, axis: Vec3): Double {
        var w = rel.w
        var p = rel.x * axis.x + rel.y * axis.y + rel.z * axis.z
        if (w < 0) { w = -w; p = -p }        // q and -q are the same rotation
        return Math.toDegrees(2 * atan2(p, w))
    }

    /**
     * Steering axis from a calibration where the user turned the wheel to the RIGHT.
     * Returns null when the turn was too small to be reliable (< 20 degrees).
     */
    fun axisFromRightTurn(rel: Quat): Vec3? {
        var r = rel
        if (r.w < 0) r = Quat(-r.w, -r.x, -r.y, -r.z)
        val v = Vec3(r.x, r.y, r.z)
        val angle = Math.toDegrees(2 * atan2(v.len(), r.w))
        if (angle < 20) return null
        return v.normalized()
    }

    /**
     * Fallback for phones without a gyroscope: steering angle from gravity alone.
     * [g0] and [g] are gravity vectors in the phone frame at neutral and now.
     * Rotating the phone by +θ about [axis] rotates gravity (seen from the phone) by -θ.
     */
    fun gravityTwistDeg(g0: Vec3, g: Vec3, axis: Vec3): Double? {
        val a = axis.normalized()
        val p0 = g0 - a * g0.dot(a)
        val p1 = g - a * g.dot(a)
        if (p0.len() < 2.0 || p1.len() < 2.0) return null   // axis nearly vertical: unobservable
        val sin = a.dot(p0.cross(p1))
        val cos = p0.dot(p1)
        return -Math.toDegrees(atan2(sin, cos))
    }
}

/** 1€ filter (Casiez et al. 2012): low jitter when still, low lag when moving fast. */
class OneEuroFilter(var minCutoff: Double = 1.5, var beta: Double = 0.02, private val dCutoff: Double = 1.0) {
    private var xPrev = 0.0
    private var dxPrev = 0.0
    private var tPrev = -1L

    private fun alpha(cutoff: Double, dt: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    fun reset() { tPrev = -1L }

    fun filter(x: Double, tNanos: Long): Double {
        if (tPrev < 0) { tPrev = tNanos; xPrev = x; dxPrev = 0.0; return x }
        val dt = ((tNanos - tPrev) / 1e9).coerceIn(1e-4, 0.1)
        tPrev = tNanos
        val dx = (x - xPrev) / dt
        val aD = alpha(dCutoff, dt)
        dxPrev += aD * (dx - dxPrev)
        val cutoff = minCutoff + beta * abs(dxPrev)
        val a = alpha(cutoff, dt)
        xPrev += a * (x - xPrev)
        return xPrev
    }
}

/** Tunable mapping from a physical wheel angle (degrees) to a -1..+1 steering output. */
data class SteeringTuning(
    val maxLeftDeg: Double = 90.0,    // wheel rotation that gives 100% left  (saturation point)
    val maxRightDeg: Double = 90.0,   // wheel rotation that gives 100% right
    val sensitivity: Double = 1.0,    // multiplier applied before saturation
    val deadZone: Double = 0.02,      // fraction of full lock ignored around centre
    val curve: Double = 1.0,          // 1 = linear, >1 = finer control near centre
    val smoothing: Double = 0.3,      // 0 = raw, 1 = heavy
    val invert: Boolean = false,
) {
    fun output(angleDeg: Double): Double {
        val max = if (angleDeg >= 0) maxRightDeg else maxLeftDeg
        var x = angleDeg / max.coerceAtLeast(5.0) * sensitivity
        val s = if (x < 0) -1.0 else 1.0
        var m = abs(x)
        m = if (m <= deadZone) 0.0 else (m - deadZone) / (1 - deadZone)
        m = m.coerceAtMost(1.0).pow(curve.coerceIn(0.3, 4.0))
        x = s * m
        return if (invert) -x else x
    }

    /** 1€ parameters for the smoothing slider. */
    fun minCutoffHz(): Double = 8.0 + (0.6 - 8.0) * smoothing.coerceIn(0.0, 1.0)

    companion object {
        val SENSITIVE = SteeringTuning(60.0, 60.0, 1.0, 0.01, 1.0, 0.15)
        val BALANCED = SteeringTuning(90.0, 90.0, 1.0, 0.02, 1.3, 0.3)
        val SMOOTH = SteeringTuning(120.0, 120.0, 1.0, 0.03, 1.6, 0.6)
    }
}

/** Exponential moving average for the sample-rate readout. */
class RateMeter {
    private var last = 0L
    var hz = 0.0
        private set
    fun tick(tNanos: Long) {
        if (last != 0L) {
            val dt = (tNanos - last) / 1e9
            if (dt > 0) hz += (1.0 / dt - hz) * (1 - exp(-dt / 0.5))
        }
        last = tNanos
    }
    fun reset() { last = 0L; hz = 0.0 }
}
