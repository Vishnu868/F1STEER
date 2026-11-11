package com.diyf1wheel.ui

import java.util.Locale
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/** Draws a rotating F1-style wheel, the -100..+100 % output bar and two status lines. */
class SteeringGauge(private val dp: Float) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val wheel = Path()
    private val tmp = RectF()

    fun draw(c: Canvas, area: RectF, angleDeg: Double, steer: Double, status: String, statusColor: Int,
             info: String, flash: Boolean = false) {
        if (area.width() <= 0 || area.height() <= 0) return
        val statusH = 18 * dp
        val barH = 14 * dp
        val textH = min(area.height() * 0.16f, 40 * dp)
        val wheelArea = area.height() - statusH * 2 - barH - textH - 16 * dp
        val size = min(area.width() * 0.8f, wheelArea)
        val cx = area.centerX()
        var y = area.top

        t.textSize = 13 * dp
        t.color = statusColor
        c.drawText(status, cx, y + statusH * 0.8f, t)
        y += statusH + 4 * dp

        // wheel
        if (size > 20 * dp) {
            val cy = y + size / 2
            c.save()
            c.rotate(angleDeg.toFloat().coerceIn(-540f, 540f), cx, cy)
            val w = size * 1.35f
            val h = size * 0.8f
            tmp.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
            wheel.reset()
            wheel.addRoundRect(tmp, h * 0.45f, h * 0.45f, Path.Direction.CW)
            p.style = Paint.Style.FILL
            p.color = Color.rgb(0x1C, 0x21, 0x2B)
            c.drawPath(wheel, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3 * dp
            p.color = if (flash) Color.rgb(0x2F, 0x8B, 0xFF) else Color.rgb(0x3A, 0x42, 0x55)
            c.drawPath(wheel, p)
            // grips
            p.style = Paint.Style.FILL
            p.color = Color.rgb(0x2A, 0x30, 0x3C)
            c.drawRoundRect(tmp.left, tmp.top + h * 0.1f, tmp.left + w * 0.16f, tmp.bottom - h * 0.1f, 8 * dp, 8 * dp, p)
            c.drawRoundRect(tmp.right - w * 0.16f, tmp.top + h * 0.1f, tmp.right, tmp.bottom - h * 0.1f, 8 * dp, 8 * dp, p)
            // centre marker (12 o'clock)
            p.color = Color.rgb(0xE1, 0x06, 0x00)
            c.drawRect(cx - 3 * dp, tmp.top, cx + 3 * dp, tmp.top + h * 0.22f, p)
            // "screen"
            p.color = Color.rgb(0x0A, 0x0C, 0x10)
            c.drawRoundRect(cx - w * 0.2f, cy - h * 0.18f, cx + w * 0.2f, cy + h * 0.22f, 6 * dp, 6 * dp, p)
            t.textSize = h * 0.16f
            t.color = Color.WHITE
            c.drawText("${"%+.0f".format(Locale.US, angleDeg)}°", cx, cy + h * 0.1f, t)
            c.restore()
            y += size + 8 * dp
        }

        // percentage
        t.textSize = textH * 0.8f
        t.color = Color.WHITE
        val pct = (steer * 100).toInt()
        c.drawText(if (pct == 0) "CENTRE" else if (pct < 0) "L ${-pct}%" else "R $pct%", cx, y + textH * 0.8f, t)
        y += textH + 4 * dp

        // bar
        val bw = area.width() * 0.9f
        val left = cx - bw / 2
        p.style = Paint.Style.FILL
        p.color = Color.rgb(0x1C, 0x21, 0x2B)
        c.drawRoundRect(left, y, left + bw, y + barH, barH / 2, barH / 2, p)
        p.color = Color.rgb(0x2F, 0x8B, 0xFF)
        val x = cx + (bw / 2) * steer.toFloat().coerceIn(-1f, 1f)
        c.drawRoundRect(min(cx, x), y, maxOf(cx, x), y + barH, barH / 2, barH / 2, p)
        p.color = Color.WHITE
        c.drawRect(cx - 1 * dp, y - 3 * dp, cx + 1 * dp, y + barH + 3 * dp, p)
        y += barH + 4 * dp

        t.textSize = 12 * dp
        t.color = Color.rgb(0x8A, 0x93, 0xA6)
        c.drawText(info, cx, y + statusH * 0.8f, t)
    }
}

/** Small live gauge for the setup screen. */
class GaugeView(context: Context, private val source: () -> Triple<Double, Double, Pair<String, Int>>) : View(context) {
    private val gauge = SteeringGauge(resources.displayMetrics.density)
    private val area = RectF()
    var info = ""

    override fun onDraw(canvas: Canvas) {
        val (angle, steer, status) = source()
        area.set(0f, 0f, width.toFloat(), height.toFloat())
        gauge.draw(canvas, area, angle, steer, status.first, status.second, info)
        postInvalidateOnAnimation()
    }
}
