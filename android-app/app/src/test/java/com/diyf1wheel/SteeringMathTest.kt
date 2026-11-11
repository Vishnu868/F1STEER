package com.diyf1wheel

import com.diyf1wheel.sensor.OneEuroFilter
import com.diyf1wheel.sensor.Quat
import com.diyf1wheel.sensor.SteeringMath
import com.diyf1wheel.sensor.SteeringTuning
import com.diyf1wheel.sensor.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SteeringMathTest {
    private val zIn = SteeringMath.DEFAULT_AXIS
    private val eps = 0.05

    /** Arbitrary "how the phone happens to sit in the world" orientation. */
    private val mount = (Quat.axisAngle(Vec3(1.0, 0.0, 0.0), -70.0) * Quat.axisAngle(Vec3(0.0, 1.0, 0.0), 25.0)).normalized()

    private fun turned(deg: Double, axis: Vec3 = zIn) = (mount * Quat.axisAngle(axis, deg)).normalized()

    @Test fun zeroAtNeutral() {
        assertEquals(0.0, SteeringMath.twistDeg(SteeringMath.relative(mount, mount), zIn), eps)
    }

    @Test fun rightTurnIsPositive() {
        for (deg in listOf(5.0, 30.0, 90.0, 170.0)) {
            val rel = SteeringMath.relative(mount, turned(deg))
            assertEquals(deg, SteeringMath.twistDeg(rel, zIn), eps)
            assertEquals(-deg, SteeringMath.twistDeg(SteeringMath.relative(mount, turned(-deg)), zIn), eps)
        }
    }

    @Test fun ignoresWobbleOnOtherAxes() {
        // 40 degree steer plus 15 degree tilt of the wheel towards the driver
        val q = (mount * Quat.axisAngle(Vec3(1.0, 0.0, 0.0), 15.0) * Quat.axisAngle(zIn, 40.0)).normalized()
        val angle = SteeringMath.twistDeg(SteeringMath.relative(mount, q), zIn)
        assertEquals(40.0, angle, 1.0)
    }

    @Test fun independentOfWorldOrientation() {
        // Same physical wheel turn, but the user sits facing another direction.
        val other = (Quat.axisAngle(Vec3(0.0, 0.0, 1.0), 123.0) * mount).normalized()
        val q = (other * Quat.axisAngle(zIn, 33.0)).normalized()
        assertEquals(33.0, SteeringMath.twistDeg(SteeringMath.relative(other, q), zIn), eps)
    }

    @Test fun calibratedAxisForTiltedMount() {
        // phone mounted so the real steering axis is tilted 30 degrees from the screen normal
        val realAxis = Vec3(0.0, 0.5, -0.866)
        val rel = SteeringMath.relative(mount, turned(90.0, realAxis))
        val axis = SteeringMath.axisFromRightTurn(rel)
        assertNotNull(axis)
        assertEquals(1.0, axis!!.dot(realAxis.normalized()), 1e-6)
        // now steering 45 left about the real axis reads -45 with the calibrated axis
        val q = turned(-45.0, realAxis)
        assertEquals(-45.0, SteeringMath.twistDeg(SteeringMath.relative(mount, q), axis), eps)
    }

    @Test fun axisCalibrationNeedsARealTurn() {
        assertNull(SteeringMath.axisFromRightTurn(SteeringMath.relative(mount, turned(8.0))))
    }

    @Test fun gravityFallback() {
        val g0 = Vec3(0.0, 9.81, 0.0)          // phone upright, landscape-ish
        for (deg in listOf(-60.0, -10.0, 25.0, 90.0)) {
            // rotating the phone by +deg about the axis rotates gravity (in phone frame) by -deg
            val inv = Quat.axisAngle(zIn, -deg)
            val v = inv * Quat(0.0, g0.x, g0.y, g0.z) * inv.conj()
            val angle = SteeringMath.gravityTwistDeg(g0, Vec3(v.x, v.y, v.z), zIn)!!
            assertEquals(deg, angle, eps)
        }
        // wheel lying flat (axis vertical) -> not observable
        assertNull(SteeringMath.gravityTwistDeg(Vec3(0.0, 0.0, 9.81), Vec3(0.0, 0.0, 9.81), zIn))
    }

    @Test fun tuningOutput() {
        val t = SteeringTuning(maxLeftDeg = 90.0, maxRightDeg = 60.0, deadZone = 0.0, curve = 1.0)
        assertEquals(0.5, t.output(30.0), 1e-9)
        assertEquals(1.0, t.output(60.0), 1e-9)
        assertEquals(1.0, t.output(200.0), 1e-9)         // saturates
        assertEquals(-0.5, t.output(-45.0), 1e-9)
        assertEquals(0.0, t.copy(deadZone = 0.05).output(2.0), 1e-9)
        assertEquals(-0.5, t.copy(invert = true).output(30.0), 1e-9)
        assertTrue(t.copy(curve = 2.0).output(30.0) < 0.5) // finer near centre
    }

    @Test fun filterConverges() {
        val f = OneEuroFilter(1.5, 0.03)
        var y = 0.0
        var t = 0L
        repeat(400) { y = f.filter(30.0, t); t += 2_500_000L }   // 400 Hz for 1 s
        assertTrue(abs(y - 30.0) < 0.01)
    }
}
