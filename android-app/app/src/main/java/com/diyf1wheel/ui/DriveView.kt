package com.diyf1wheel.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.diyf1wheel.engine.WheelEngine
import com.diyf1wheel.net.Protocol
import com.diyf1wheel.net.UdpLink
import com.diyf1wheel.settings.ControllerLayout
import com.diyf1wheel.settings.PedalMode
import com.diyf1wheel.settings.Settings
import java.util.Locale
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
 *
 * Two layouts ([ControllerLayout]): AUTO_GEARS puts DRS / CLUTCH in the top corners,
 * PADDLES puts gear down / gear up there. Pedals are always bottom-left / bottom-right.
 */
class DriveView(context: Context, private val engine: WheelEngine) : View(context) {

    private enum class Kind { PADDLE_UP, PADDLE_DOWN, THROTTLE, BRAKE, BUTTON, CENTRE }

    private class Control(
        val kind: Kind, val label: String, val color: Int,
        val button: Protocol.Button? = null, val sub: String = "",
        val icon: Theme.Icon = Theme.Icon.NONE, val compact: Boolean = false,
    ) {
        val rect = RectF()
        var pressed = false
        var value = 0f
    }

    private val dp = resources.displayMetrics.density

    private val paddleUp = Control(Kind.PADDLE_UP, "UP", Theme.BLUE, sub = "GEAR +", icon = Theme.Icon.CHEVRON_UP)
    private val paddleDown = Control(Kind.PADDLE_DOWN, "DOWN", Theme.PURPLE, sub = "GEAR −", icon = Theme.Icon.CHEVRON_DOWN)
    private val bigDrs = Control(Kind.BUTTON, "DRS", Theme.GREEN, Protocol.Button.DRS, sub = "TAP")
    private val bigClutch = Control(Kind.BUTTON, "CLUTCH", Theme.ORANGE, Protocol.Button.CLUTCH, sub = "HOLD")
    private val throttle = Control(Kind.THROTTLE, "THROTTLE", Theme.GREEN, icon = Theme.Icon.ARROW_UP)
    private val brake = Control(Kind.BRAKE, "BRAKE", Theme.RED, icon = Theme.Icon.ARROW_DOWN)
    private val centre = Control(Kind.CENTRE, "HOLD TO CENTRE", Theme.GREY, compact = true)

    private var layout = ControllerLayout.AUTO_GEARS
    private var topLeft = bigDrs
    private var topRight = bigClutch
    private var extras: List<Control> = emptyList()
    private var controls: List<Control> = emptyList()
    private val dead = Control(Kind.BUTTON, "", 0)        // pointer that left its control

    private val pointers = SparseArray<Control>()
    private var lastUp = 0L
    private var lastDown = 0L
    private var centreDownAt = 0L
    private var flashUntil = 0L

    private val gauge = SteeringGauge(dp)
    private val painter = Theme.ButtonPainter(dp)
    private val chip = Theme.Chip(dp)
    private val gaugeRect = RectF()
    private var chipTop = 0f
    private var infoY = 0f
    private val bg = Paint()
    private val info = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Theme.CONDENSED_BOLD; letterSpacing = 0.12f; color = Theme.MUTED
    }

    private val extraColors = mapOf(
        Protocol.Button.DRS to Theme.GREEN, Protocol.Button.ERS to Theme.YELLOW,
        Protocol.Button.CLUTCH to Theme.ORANGE, Protocol.Button.PAUSE to Theme.GREY,
        Protocol.Button.LOOK_BACK to Theme.CYAN, Protocol.Button.FLASHBACK to Theme.PINK,
        Protocol.Button.PIT_LIMITER to Theme.SALMON, Protocol.Button.MFD to Theme.SKY,
    )

    init {
        isHapticFeedbackEnabled = true
        reloadSettings()
    }

    /** Re-reads layout and extra buttons (called on every resume). */
    fun reloadSettings() {
        releaseAll()
        layout = engine.settings.layout
        if (layout == ControllerLayout.PADDLES) { topLeft = paddleDown; topRight = paddleUp }
        else { topLeft = bigDrs; topRight = bigClutch }
        val hidden = if (layout == ControllerLayout.AUTO_GEARS) Settings.AUTO_LAYOUT_BIG else emptySet()
        extras = Protocol.Button.entries
            .filter { it in engine.settings.extraButtons && it !in hidden }
            .map { Control(Kind.BUTTON, it.label, extraColors[it]!!, it, compact = true) }
        controls = listOf(topLeft, topRight, throttle, brake, centre) + extras
        if (width > 0 && height > 0) layoutControls(width, height)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bg.shader = RadialGradient(w / 2f, h / 2f, max(w, h) * 0.6f,
            Theme.SURFACE, Theme.BG, Shader.TileMode.CLAMP)
        layoutControls(w, h)
    }

    private fun layoutControls(w: Int, h: Int) {
        // Keep clear of the camera cut-out and rounded screen corners.
        val cut = rootWindowInsets?.displayCutout
        val base = 12 * dp
        // Same margin on both sides so the layout stays symmetric around the camera hole.
        val side = max(base, max(cut?.safeInsetLeft ?: 0, cut?.safeInsetRight ?: 0).toFloat())
        val left = side
        val right = w - side
        val top = max(base, (cut?.safeInsetTop ?: 0).toFloat())
        val bottom = h - max(base, (cut?.safeInsetBottom ?: 0).toFloat())
        val gap = 10 * dp

        val colW = (right - left) * 0.27f
        val split = top + (bottom - top) * 0.44f      // pedals get the larger, lower part
        topLeft.rect.set(left, top, left + colW, split - gap / 2)
        brake.rect.set(left, split + gap / 2, left + colW, bottom)
        topRight.rect.set(right - colW, top, right, split - gap / 2)
        throttle.rect.set(right - colW, split + gap / 2, right, bottom)

        val cl = left + colW + gap * 1.6f
        val cr = right - colW - gap * 1.6f
        val cw = cr - cl
        chipTop = top
        var y = top + 30 * dp
        if (extras.isNotEmpty()) {
            val perRow = if (extras.size > 4) (extras.size + 1) / 2 else extras.size
            val rows = (extras.size + perRow - 1) / perRow
            val bh = min((bottom - top) * 0.12f, 46 * dp)
            val bw = (cw - (perRow - 1) * gap * 0.8f) / perRow
            extras.forEachIndexed { i, c ->
                val x = cl + (i % perRow) * (bw + gap * 0.8f)
                val yy = y + (i / perRow) * (bh + gap * 0.8f)
                c.rect.set(x, yy, x + bw, yy + bh)
            }
            y += rows * (bh + gap * 0.8f)
        }
        val ch = min(40 * dp, (bottom - top) * 0.1f)
        centre.rect.set(cl + cw * 0.22f, bottom - ch, cr - cw * 0.22f, bottom)
        infoY = centre.rect.top - gap
        gaugeRect.set(cl, y + gap * 0.5f, cr, infoY - 16 * dp)
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
        canvas.drawPaint(bg)

        // paddles light up briefly on each shift, even for a very short tap
        val upFlash = now - lastUp < 150
        val downFlash = now - lastDown < 150
        for (c in controls) {
            val pressed = c.pressed || (c === paddleUp && upFlash) || (c === paddleDown && downFlash)
            val pedal = c.kind == Kind.THROTTLE || c.kind == Kind.BRAKE
            val progress = if (c.kind == Kind.CENTRE && centreDownAt != 0L)
                ((now - centreDownAt) / 600f).coerceIn(0f, 1f) else 0f
            painter.draw(canvas, c.rect, c.color, pressed && c.kind != Kind.CENTRE, c.label,
                subLabel = if (pedal) "${(c.value * 100).toInt()}%" else c.sub,
                icon = c.icon, level = if (pedal && c.pressed) c.value else -1f,
                progress = progress, compact = c.compact)
        }

        val link = engine.link
        val (text, color) = when {
            !engine.sensorHealthy() -> Pair("SENSOR STOPPED", Theme.RED)
            link.status == UdpLink.Status.CONNECTED && link.controllerOk -> Pair("LIVE", Theme.GREEN)
            link.status == UdpLink.Status.CONNECTED -> Pair("PC HAS NO CONTROLLER", Theme.AMBER)
            link.status == UdpLink.Status.STOPPED -> Pair("NOT CONNECTED", Theme.RED)
            else -> Pair("CONNECTING…", Theme.AMBER)
        }
        val rtt = link.rttMs?.let { String.format(Locale.US, "  ·  %.0f ms", it) } ?: ""
        chip.draw(canvas, gaugeRect.centerX(), chipTop, text + rtt, color)

        gauge.draw(canvas, gaugeRect, engine.filteredAngleDeg, engine.steer, flash = now < flashUntil)

        info.textSize = 11 * dp
        val hz = "${engine.sensor.rate.hz.toInt()} Hz"
        canvas.drawText(
            if (layout == ControllerLayout.PADDLES) "GEARS  ▲ ${engine.gearUp.get()}  ▼ ${engine.gearDown.get()}   ·   $hz"
            else "AUTO GEARS   ·   $hz",
            gaugeRect.centerX(), infoY, info)
        postInvalidateOnAnimation()
    }
}
