package com.diyf1wheel.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.diyf1wheel.settings.ControllerLayout

/** Miniature landscape phone showing where the big buttons are in a [ControllerLayout]. */
class LayoutPreview(context: Context, private val layout: ControllerLayout) : View(context) {
    private val dp = resources.displayMetrics.density
    private val painter = Theme.ButtonPainter(dp * 0.55f)
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val phone = RectF()
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val ph = h
        val pw = minOf(w, ph * 2.1f)
        phone.set((w - pw) / 2, 0f, (w + pw) / 2, ph)
        frame.color = Theme.BG
        c.drawRoundRect(phone, 10 * dp, 10 * dp, frame)

        val m = 5 * dp
        val colW = phone.width() * 0.27f
        val split = phone.top + phone.height() * 0.44f
        fun box(l: Float, t: Float, rr: Float, b: Float, color: Int, label: String) {
            r.set(l, t, rr, b)
            painter.draw(c, r, color, false, label, compact = true)
        }
        val (tl, tr) = if (layout == ControllerLayout.PADDLES)
            Pair(Pair(Theme.PURPLE, "−"), Pair(Theme.BLUE, "+")) else Pair(Pair(Theme.GREEN, "DRS"), Pair(Theme.ORANGE, "CLU"))
        box(phone.left + m, phone.top + m, phone.left + colW, split - m / 2, tl.first, tl.second)
        box(phone.right - colW, phone.top + m, phone.right - m, split - m / 2, tr.first, tr.second)
        box(phone.left + m, split + m / 2, phone.left + colW, phone.bottom - m, Theme.RED, "BRK")
        box(phone.right - colW, split + m / 2, phone.right - m, phone.bottom - m, Theme.GREEN, "THR")

        // steering arc in the middle
        val cx = phone.centerX()
        val rad = minOf(phone.width() * 0.16f, phone.height() * 0.32f)
        val cy = phone.centerY() + rad * 0.25f
        arc.strokeWidth = rad * 0.22f
        arc.color = Theme.TRACK
        r.set(cx - rad, cy - rad, cx + rad, cy + rad)
        c.drawArc(r, 160f, 220f, false, arc)
        arc.color = Theme.BLUE
        c.drawArc(r, 270f, 45f, false, arc)
    }
}
