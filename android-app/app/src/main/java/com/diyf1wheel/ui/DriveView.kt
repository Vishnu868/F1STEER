package com.diyf1wheel.ui

import java.util.Locale
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.diyf1wheel.engine.WheelEngine
import com.diyf1wheel.net.Protocol
import com.diyf1wheel.net.UdpLink
import com.diyf1wheel.settings.PedalMode
import kotlin.math.max
import kotlin.math.min

/**
 * The racing controller surface. One view owns every control so that multi-touch
 * is handled consistently:
 *  - each finger is bound to the control it first touched and can never "slide into"
 *    another control (prevents accidental activation);
 *  - a finger that slides well outside its control releases it;
 *  - paddles fire exactly one shift per touch-down (with a short debounce);
 *  - ACTION_CANCEL (e.g. a system gesture) releases everything.
 */
class DriveView(context: Context, private val engine: WheelEngine) : View(context) {

    private enum class Kind { PADDLE_UP, PADDLE_DOWN, THROTTLE, BRAKE, BUTTON, CENTRE }

    private inner class Control(val kind: Kind, val label: String, val color: Int, val button: Protocol.Button? = null) {
        val rect = RectF()
        var pressed = false
        var value = 0f
    }

    private val dp = resources.displayMetrics.density
    private val paddleUp = Control(Kind.PADDLE_UP, "UP  +", Color.rgb(0x2F, 0x8B, 0xFF))
    private val paddleDown = Control(Kind.PADDLE_DOWN, "−  DOWN", Color.rgb(0x9B, 0x5C, 0xFF))
    private val throttle = Control(Kind.THROTTLE, "THROTTLE", Color.rgb(0x1E, 0xD7, 0x60))
    private val brake = Control(Kind.BRAKE, "BRAKE", Color.rgb(0xE1, 0x06, 0x00))
    private val centre = Control(Kind.CENTRE, "HOLD TO CENTRE", Color.rgb(0x55, 0x5E, 0x70))
    private var extras: List<Control> = emptyList()
    private var controls: List<Control> = emptyList()
    private val dead = Control(Kind.BUTTON, "", 0)        // pointer that left its control

    private val pointers = SparseArray<Control>()
    private var lastUp = 0L
    private var lastDown = 0L
    private var centreDownAt = 0L
    private var flashUntil = 0L
    private val gauge = SteeringGauge(dp)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * dp }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true; letterSpacing = 0.08f
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x8A, 0x93, 0xA6); textAlign = Paint.Align.CENTER }

    init {
        setBackgroundColor(Color.rgb(0x0D, 0x0F, 0x14))
        isHapticFeedbackEnabled = true
        reloadExtras()
    }

    fun reloadExtras() {
        val enabled = engine.settings.extraButtons
        val colors = mapOf(
            Protocol.Button.DRS to Color.rgb(0x1E, 0xD7, 0x60), Protocol.Button.ERS to Color.rgb(0xFF, 0xD0, 0x00),
            Protocol.Button.CLUTCH to Color.rgb(0xFF, 0x8A, 0x00), Protocol.Button.PAUSE to Color.rgb(0x8A, 0x93, 0xA6),
            Protocol.Button.LOOK_BACK to Color.rgb(0x00, 0xC8, 0xE6), Protocol.Button.FLASHBACK to Color.rgb(0xE6, 0x4A, 0xFF),
            Protocol.Button.PIT_LIMITER to Color.rgb(0xFF, 0x5A, 0x5A), Protocol.Button.MFD to Color.rgb(0x5A, 0xA0, 0xFF),
        )
        extras = Protocol.Button.entries.filter { it in enabled }.map { Control(Kind.BUTTON, it.label, colors[it]!!, it) }
        controls = listOf(paddleUp, paddleDown, throttle, brake, centre) + extras
        if (width > 0 && height > 0) layoutControls(width, height)
        invalidate()
    }

    private var gaugeTop = 0f
    private val gaugeRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = layoutControls(w, h)

    private fun layoutControls(w: Int, h: Int) {
        val m = 8 * dp
        val colW = w * 0.25f
        val split = h * 0.50f
        paddleDown.rect.set(m, m, colW, split - m / 2)
        brake.rect.set(m, split + m / 2, colW, h - m)
        paddleUp.rect.set(w - colW, m, w - m, split - m / 2)
        throttle.rect.set(w - colW, split + m / 2, w - m, h - m)

        val cl = colW + m * 1.5f
        val cr = w - colW - m * 1.5f
        val cw = cr - cl
        if (extras.isNotEmpty()) {
            val perRow = if (extras.size > 4) (extras.size + 1) / 2 else extras.size
            val rows = (extras.size + perRow - 1) / perRow
            val bh = min(h * 0.13f, 56 * dp)
            val bw = (cw - (perRow - 1) * m) / perRow
            extras.forEachIndexed { i, c ->
                val r = i / perRow
                val col = i % perRow
                val x = cl + col * (bw + m)
                val y = m + r * (bh + m)
                c.rect.set(x, y, x + bw, y + bh)
            }
            gaugeTop = m + rows * (bh + m) + m
        } else gaugeTop = m
        val ch = min(44 * dp, h * 0.11f)
        centre.rect.set(cl + cw * 0.25f, h - m - ch, cr - cw * 0.25f, h - m)
        gaugeRect.set(cl, gaugeTop, cr, centre.rect.top - m)
    }

    // ------------------------------------------------------------------ touch
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val x = e.getX(i)
                val y = e.getY(i)
                val c = controls.firstOrNull { it.rect.contains(x, y) && !isHeld(it) }
                pointers.put(e.getPointerId(i), c ?: dead)
                if (c != null) press(c, y)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val c = pointers[id] ?: continue
                    if (c === dead) continue
                    val x = e.getX(i)
                    val y = e.getY(i)
                    val slop = 28 * dp
                    if (x < c.rect.left - slop || x > c.rect.right + slop ||
                        y < c.rect.top - slop || y > c.rect.bottom + slop) {
                        release(c)
                        pointers.put(id, dead)
                    } else if (c.kind == Kind.THROTTLE || c.kind == Kind.BRAKE) {
                        setPedal(c, y)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                pointers[id]?.let { if (it !== dead) release(it) }
                pointers.remove(id)
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        invalidate()
        return true
    }

    private fun isHeld(c: Control): Boolean {
        for (i in 0 until pointers.size()) if (pointers.valueAt(i) === c) return true
        return false
    }

    private fun haptic() {
        if (engine.settings.haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun press(c: Control, y: Float) {
        c.pressed = true
        val now = SystemClock.uptimeMillis()
        when (c.kind) {
            Kind.PADDLE_UP -> if (now - lastUp > 70) { lastUp = now; engine.shiftUp(); haptic() }
            Kind.PADDLE_DOWN -> if (now - lastDown > 70) { lastDown = now; engine.shiftDown(); haptic() }
            Kind.THROTTLE, Kind.BRAKE -> setPedal(c, y)
            Kind.BUTTON -> { engine.setButton(c.button!!, true); haptic() }
            Kind.CENTRE -> centreDownAt = now
        }
    }

    private fun setPedal(c: Control, y: Float) {
        val v = if (engine.settings.pedalMode == PedalMode.DIGITAL) 1f else {
            // top of the pedal = 100 %, bottom = 15 %
            val rel = ((c.rect.bottom - y) / c.rect.height()).coerceIn(0f, 1f)
            0.15f + 0.85f * rel
        }
        c.value = v
        if (c.kind == Kind.THROTTLE) engine.throttle = v else engine.brake = v
    }

    private fun release(c: Control) {
        c.pressed = false
        c.value = 0f
        when (c.kind) {
            Kind.THROTTLE -> engine.throttle = 0f
            Kind.BRAKE -> engine.brake = 0f
            Kind.BUTTON -> engine.setButton(c.button!!, false)
            Kind.CENTRE -> centreDownAt = 0L
            else -> {}
        }
    }

    fun releaseAll() {
        controls.forEach { if (it.pressed) release(it) }
        pointers.clear()
        engine.clearInputs()
    }

    // ------------------------------------------------------------------ draw
    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (centreDownAt != 0L && now - centreDownAt > 600) {
            engine.setCentre()
            centreDownAt = 0L
            flashUntil = now + 800
            haptic()
        }
        // paddles flash briefly on each shift
        paddleUp.value = if (now - lastUp < 150) 1f else 0f
        paddleDown.value = if (now - lastDown < 150) 1f else 0f

        for (c in controls) drawControl(canvas, c, now)

        val link = engine.link
        val status = when {
            !engine.sensorHealthy() -> Pair("SENSOR STOPPED", Color.RED)
            link.status == UdpLink.Status.CONNECTED && link.controllerOk -> Pair("LIVE", Color.rgb(0x1E, 0xD7, 0x60))
            link.status == UdpLink.Status.CONNECTED -> Pair("PC HAS NO CONTROLLER", Color.rgb(0xFF, 0xB0, 0x00))
            link.status == UdpLink.Status.STOPPED -> Pair("NOT CONNECTED", Color.RED)
            else -> Pair("CONNECTING…", Color.rgb(0xFF, 0xB0, 0x00))
        }
        val rtt = link.rttMs?.let { "  ·  ${"%.0f".format(Locale.US, it)} ms" } ?: ""
        gauge.draw(canvas, gaugeRect, engine.filteredAngleDeg, engine.steer,
            "${status.first}$rtt", status.second,
            "▲ ${engine.gearUp.get()}   ▼ ${engine.gearDown.get()}   ·   ${engine.sensor.rate.hz.toInt()} Hz",
            flash = now < flashUntil)
        postInvalidateOnAnimation()
    }

    private fun drawControl(canvas: Canvas, c: Control, now: Long) {
        val r = 18 * dp
        val active = c.pressed || c.value > 0f
        fill.color = if (active) c.color else Color.argb(0x33, Color.red(c.color), Color.green(c.color), Color.blue(c.color))
        canvas.drawRoundRect(c.rect, r, r, fill)
        stroke.color = c.color
        canvas.drawRoundRect(c.rect, r, r, stroke)
        if ((c.kind == Kind.THROTTLE || c.kind == Kind.BRAKE) && c.pressed &&
            engine.settings.pedalMode == PedalMode.ANALOG) {
            fill.color = Color.argb(0x55, 0, 0, 0)
            val top = c.rect.bottom - c.rect.height() * c.value
            canvas.drawRoundRect(c.rect.left, c.rect.top, c.rect.right, top, r, r, fill)
        }
        if (c.kind == Kind.CENTRE && centreDownAt != 0L) {
            val p = ((now - centreDownAt) / 600f).coerceIn(0f, 1f)
            fill.color = Color.rgb(0x2F, 0x8B, 0xFF)
            canvas.drawRoundRect(c.rect.left, c.rect.top, c.rect.left + c.rect.width() * p, c.rect.bottom, r, r, fill)
        }
        val size = when (c.kind) {
            Kind.BUTTON, Kind.CENTRE -> min(c.rect.height() * 0.32f, c.rect.width() / max(c.label.length, 4) * 1.5f)
            else -> min(c.rect.width() / 7f, 26 * dp)
        }
        text.textSize = size
        text.color = if (active && c.kind != Kind.CENTRE) Color.BLACK else Color.WHITE
        canvas.drawText(c.label, c.rect.centerX(), c.rect.centerY() + size / 3, text)
        if (c.kind == Kind.THROTTLE || c.kind == Kind.BRAKE) {
            small.textSize = 13 * dp
            small.color = if (active) Color.BLACK else Color.rgb(0x8A, 0x93, 0xA6)
            canvas.drawText("${(c.value * 100).toInt()}%", c.rect.centerX(), c.rect.centerY() + size / 3 + 22 * dp, small)
        }
    }
}
