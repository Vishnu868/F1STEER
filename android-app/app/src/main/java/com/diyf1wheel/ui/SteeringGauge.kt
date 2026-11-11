package com.diyf1wheel.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Steering read-out: a 220° arc that fills from 12 o'clock towards the side you
 * steer, a knob at the current position, the percentage and the physical angle.
 */
class SteeringGauge(private val dp: Float) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Theme.CONDENSED_BOLD; color = Theme.TEXT
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Theme.CONDENSED_BOLD; letterSpacing = 0.12f
    }
    private val oval = RectF()

    private companion object {
        const val HALF_SWEEP = 110f          // degrees each side of 12 o'clock
        const val TOP = 270f                 // 12 o'clock in Canvas angle terms
    }

    fun draw(c: Canvas, area: RectF, angleDeg: Double, steer: Double, flash: Boolean = false) {
        if (area.width() <= 0 || area.height() <= 0) return
        val stroke = min(area.width(), area.height()) * 0.075f
        // The arc's lowest points sit at 20° below horizontal: radius from the space available.
        val rFromH = (area.height() - stroke) / (1f + sin(Math.toRadians(20.0)).toFloat())
        val radius = min(area.width() / 2f - stroke, rFromH - stroke / 2)
        if (radius <= 10 * dp) return
        val cx = area.centerX()
        val cy = area.top + stroke / 2 + radius + 2 * dp
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        track.strokeWidth = stroke
        track.color = if (flash) Theme.withAlpha(Theme.BLUE, 0x90) else Theme.TRACK
        c.drawArc(oval, TOP - HALF_SWEEP, HALF_SWEEP * 2, false, track)

        // ticks at 0, ±25, ±50, ±75, ±100 %
        tick.strokeWidth = 2 * dp
        for (k in -4..4) {
            val a = Math.toRadians((TOP + k * HALF_SWEEP / 4).toDouble())
            val r1 = radius - stroke * 0.95f
            val r2 = radius - stroke * (if (k == 0) 1.9f else 1.45f)
            tick.color = if (k == 0) Theme.RED else Theme.withAlpha(Theme.MUTED, 0x90)
            c.drawLine(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(),
                       cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat(), tick)
        }

        val s = steer.toFloat().coerceIn(-1f, 1f)
        val col = if (abs(s) > 0.97f) Theme.RED else Theme.BLUE
        if (abs(s) > 0.003f) {
            fill.strokeWidth = stroke
            fill.color = col
            c.drawArc(oval, TOP, s * HALF_SWEEP, false, fill)
        }
        val ka = Math.toRadians((TOP + s * HALF_SWEEP).toDouble())
        val kx = cx + radius * cos(ka).toFloat()
        val ky = cy + radius * sin(ka).toFloat()
        knob.color = Color.WHITE
        c.drawCircle(kx, ky, stroke * 0.62f, knob)
        knob.color = col
        c.drawCircle(kx, ky, stroke * 0.32f, knob)

        val pct = (s * 100).toInt()
        big.textSize = radius * 0.42f
        c.drawText("${abs(pct)}", cx, cy + big.textSize * 0.2f, big)
        small.textSize = radius * 0.13f
        small.color = if (pct == 0) Theme.MUTED else col
        c.drawText(when { pct == 0 -> "CENTRE"; pct < 0 -> "LEFT %"; else -> "RIGHT %" },
            cx, cy + big.textSize * 0.2f + small.textSize * 1.6f, small)
        small.color = Theme.withAlpha(Theme.TEXT, 0xB0)
        c.drawText(String.format(Locale.US, "%+.0f°", angleDeg), cx, cy - big.textSize * 0.95f, small)
    }
}

/** Live gauge for the setup screen: status line, arc, and an info line. */
class GaugeView(context: Context, private val source: () -> Triple<Double, Double, Pair<String, Int>>) : View(context) {
    private val dp = resources.displayMetrics.density
    private val gauge = SteeringGauge(dp)
    private val chip = Theme.Chip(dp)
    private val area = RectF()

    override fun onDraw(canvas: Canvas) {
        val (angle, steer, status) = source()
        val h = chip.draw(canvas, width / 2f, 0f, status.first, status.second, 12f)
        area.set(0f, h + 10 * dp, width.toFloat(), height.toFloat())
        gauge.draw(canvas, area, angle, steer)
        postInvalidateOnAnimation()
    }
}
