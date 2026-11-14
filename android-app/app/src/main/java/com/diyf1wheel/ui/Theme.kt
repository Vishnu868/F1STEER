package com.diyf1wheel.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** Colours and drawing helpers shared by the driving screen and the setup screen previews. */
object Theme {
    val BG = Color.rgb(0x08, 0x09, 0x0C)
    val SURFACE = Color.rgb(0x12, 0x15, 0x1C)
    val TRACK = Color.rgb(0x1E, 0x23, 0x2D)
    val TEXT = Color.rgb(0xF2, 0xF4, 0xF8)
    val MUTED = Color.rgb(0x7C, 0x86, 0x99)

    val RED = Color.rgb(0xE1, 0x06, 0x00)
    val GREEN = Color.rgb(0x1E, 0xD7, 0x60)
    val BLUE = Color.rgb(0x2F, 0x8B, 0xFF)
    val PURPLE = Color.rgb(0x9B, 0x5C, 0xFF)
    val AMBER = Color.rgb(0xFF, 0xB0, 0x00)
    val ORANGE = Color.rgb(0xFF, 0x8A, 0x00)
    val YELLOW = Color.rgb(0xFF, 0xD0, 0x00)
    val CYAN = Color.rgb(0x00, 0xC8, 0xE6)
    val PINK = Color.rgb(0xE6, 0x4A, 0xFF)
    val SALMON = Color.rgb(0xFF, 0x5A, 0x5A)
    val SKY = Color.rgb(0x5A, 0xA0, 0xFF)
    val GREY = Color.rgb(0x8A, 0x93, 0xA6)

    val CONDENSED_BOLD: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    enum class Icon { NONE, CHEVRON_UP, CHEVRON_DOWN, ARROW_UP, ARROW_DOWN }

    fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    fun lighten(c: Int, f: Float) = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt())

    /** Paints reused by [drawButton]; one instance per view (not thread-safe). */
    class ButtonPainter(private val dp: Float) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = CONDENSED_BOLD; letterSpacing = 0.06f
        }
        private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = CONDENSED_BOLD; letterSpacing = 0.18f
        }
        private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()
        private val tmp = RectF()

        /**
         * Draws one control. [level] 0..1 draws a fill rising from the bottom (pedal meter);
         * [progress] 0..1 draws a left-to-right fill (hold-to-confirm).
         */
        fun draw(c: Canvas, r: RectF, color: Int, pressed: Boolean, label: String, subLabel: String = "",
                 icon: Icon = Icon.NONE, level: Float = -1f, progress: Float = 0f, compact: Boolean = false,
                 titleScale: Float = 1f) {
            val radius = if (compact) 14 * dp else 22 * dp
            if (pressed) {
                glow.strokeWidth = 10 * dp
                glow.color = withAlpha(color, 0x40)
                tmp.set(r); tmp.inset(-3 * dp, -3 * dp)
                c.drawRoundRect(tmp, radius + 3 * dp, radius + 3 * dp, glow)
            }
            fill.shader = LinearGradient(0f, r.top, 0f, r.bottom,
                if (pressed) lighten(color, 0.15f) else withAlpha(color, 0x38),
                if (pressed) color else withAlpha(color, 0x12), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, radius, radius, fill)
            fill.shader = null

            if (level in 0f..0.999f) {   // pedal level meter: darken the part above the level
                c.save()
                path.reset(); path.addRoundRect(r, radius, radius, Path.Direction.CW)
                c.clipPath(path)
                fill.color = withAlpha(Color.BLACK, 0x70)
                c.drawRect(r.left, r.top, r.right, r.bottom - r.height() * level.coerceIn(0f, 1f), fill)
                c.restore()
            }
            if (progress > 0f) {
                c.save()
                path.reset(); path.addRoundRect(r, radius, radius, Path.Direction.CW)
                c.clipPath(path)
                fill.color = BLUE
                c.drawRect(r.left, r.top, r.left + r.width() * progress.coerceIn(0f, 1f), r.bottom, fill)
                c.restore()
            }

            border.strokeWidth = (if (pressed) 2.5f else 1.5f) * dp
            border.color = if (pressed) lighten(color, 0.35f) else withAlpha(color, 0xC0)
            c.drawRoundRect(r, radius, radius, border)

            val ink = if (pressed) Color.BLACK else TEXT
            val h = r.height()
            var titleSize = if (compact) minOf(h * 0.36f, r.width() / maxOf(label.length, 3) * 1.3f, 17 * dp)
                            else minOf(h * 0.17f * titleScale, r.width() / maxOf(label.length, 4) * 1.45f * titleScale, 34 * dp * titleScale)
            // Wide letters (CLUTCH, MENU…) can still overflow: shrink until the label fits inside the border.
            title.textSize = titleSize
            val room = r.width() - 12 * dp
            val tw = title.measureText(label)
            if (tw > room && tw > 0f) {
                titleSize *= room / tw
                title.textSize = titleSize
            }
            title.color = ink
            val hasIcon = icon != Icon.NONE && !compact && h > 90 * dp
            val iconSize = if (hasIcon) minOf(h * 0.18f, r.width() * 0.22f) else 0f
            val subSize = if (subLabel.isNotEmpty() && !compact) minOf(titleSize * 0.45f, 13 * dp) else 0f
            val gap = 8 * dp
            val block = iconSize + (if (hasIcon) gap else 0f) + titleSize * 0.75f + (if (subSize > 0) gap + subSize else 0f)
            var y = r.centerY() - block / 2

            if (hasIcon) {
                drawIcon(c, icon, r.centerX(), y + iconSize / 2, iconSize, ink)
                y += iconSize + gap
            }
            c.drawText(label, r.centerX(), y + titleSize * 0.75f, title)
            y += titleSize * 0.75f
            if (subSize > 0) {
                sub.textSize = subSize
                sub.color = if (pressed) withAlpha(Color.BLACK, 0xB0) else withAlpha(color, 0xE0)
                c.drawText(subLabel, r.centerX(), y + gap + subSize * 0.8f, sub)
            }
        }

        private fun drawIcon(c: Canvas, which: Icon, cx: Float, cy: Float, size: Float, color: Int) {
            icon.color = color
            icon.strokeWidth = size * 0.16f
            val w = size * 0.55f
            val hh = size * 0.3f
            path.reset()
            when (which) {
                Icon.CHEVRON_UP -> { path.moveTo(cx - w, cy + hh); path.lineTo(cx, cy - hh); path.lineTo(cx + w, cy + hh) }
                Icon.CHEVRON_DOWN -> { path.moveTo(cx - w, cy - hh); path.lineTo(cx, cy + hh); path.lineTo(cx + w, cy - hh) }
                Icon.ARROW_UP -> {
                    path.moveTo(cx, cy + size / 2); path.lineTo(cx, cy - size / 2)
                    path.moveTo(cx - w * 0.7f, cy - size / 2 + w * 0.7f); path.lineTo(cx, cy - size / 2); path.lineTo(cx + w * 0.7f, cy - size / 2 + w * 0.7f)
                }
                Icon.ARROW_DOWN -> {
                    path.moveTo(cx, cy - size / 2); path.lineTo(cx, cy + size / 2)
                    path.moveTo(cx - w * 0.7f, cy + size / 2 - w * 0.7f); path.lineTo(cx, cy + size / 2); path.lineTo(cx + w * 0.7f, cy + size / 2 - w * 0.7f)
                }
                Icon.NONE -> return
            }
            c.drawPath(path, icon)
        }
    }

    /** Rounded status pill with a coloured dot. Returns its height. */
    class Chip(private val dp: Float) {
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = CONDENSED_BOLD; letterSpacing = 0.1f }
        private val r = RectF()

        fun draw(c: Canvas, cx: Float, top: Float, text: String, color: Int, size: Float = 13f): Float {
            txt.textSize = size * dp
            txt.color = TEXT
            val tw = txt.measureText(text)
            val h = size * dp * 2.1f
            val w = tw + h * 1.3f
            r.set(cx - w / 2, top, cx + w / 2, top + h)
            bg.color = withAlpha(color, 0x26)
            c.drawRoundRect(r, h / 2, h / 2, bg)
            dot.color = color
            c.drawCircle(r.left + h * 0.5f, r.centerY(), h * 0.16f, dot)
            c.drawText(text, r.left + h * 0.8f, r.centerY() + size * dp * 0.36f, txt)
            return h
        }
    }
}
