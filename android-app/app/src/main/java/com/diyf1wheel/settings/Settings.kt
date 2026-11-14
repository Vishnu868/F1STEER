package com.diyf1wheel.settings

import android.content.Context
import com.diyf1wheel.net.Protocol
import com.diyf1wheel.sensor.Quat
import com.diyf1wheel.sensor.SteeringMath
import com.diyf1wheel.sensor.SteeringTuning
import com.diyf1wheel.sensor.Vec3

enum class Preset(val label: String, val tuning: SteeringTuning?) {
    SENSITIVE("Sensitive", SteeringTuning.SENSITIVE),
    BALANCED("Balanced", SteeringTuning.BALANCED),
    SMOOTH("Smooth", SteeringTuning.SMOOTH),
    CUSTOM("Custom", null),
}

/** Big buttons in the top corners of the driving screen. */
enum class ControllerLayout(val label: String, val description: String) {
    AUTO_GEARS("Auto gears", "DRS and clutch on top, no paddles. Use automatic gears in the game."),
    PADDLES("Paddle shift", "Gear down / gear up paddles on top. Use manual gears in the game."),
}

enum class PedalMode(val label: String) {
    DIGITAL("Digital - press = 100%"),
    ANALOG("Analog - slide thumb up for more"),
}

/** All persisted settings. Stored in SharedPreferences "wheel". */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("wheel", Context.MODE_PRIVATE)

    var host: String
        get() = p.getString("host", "") ?: ""
        set(v) = p.edit().putString("host", v.trim()).apply()
    var port: Int
        get() = p.getInt("port", Protocol.DEFAULT_PORT)
        set(v) = p.edit().putInt("port", v).apply()
    var pin: String
        get() = p.getString("pin", "") ?: ""
        set(v) = p.edit().putString("pin", v.trim()).apply()
    var autoConnect: Boolean
        get() = p.getBoolean("autoConnect", false)
        set(v) = p.edit().putBoolean("autoConnect", v).apply()

    var preset: Preset
        get() = runCatching { Preset.valueOf(p.getString("preset", "BALANCED")!!) }.getOrDefault(Preset.BALANCED)
        set(v) = p.edit().putString("preset", v.name).apply()

    var tuning: SteeringTuning
        get() {
            val d = SteeringTuning.BALANCED
            return SteeringTuning(
                maxLeftDeg = p.getFloat("maxLeft", d.maxLeftDeg.toFloat()).toDouble(),
                maxRightDeg = p.getFloat("maxRight", d.maxRightDeg.toFloat()).toDouble(),
                sensitivity = p.getFloat("sens", d.sensitivity.toFloat()).toDouble(),
                deadZone = p.getFloat("dead", d.deadZone.toFloat()).toDouble(),
                curve = p.getFloat("curve", d.curve.toFloat()).toDouble(),
                smoothing = p.getFloat("smooth", d.smoothing.toFloat()).toDouble(),
                invert = p.getBoolean("invert", false),
            )
        }
        set(t) = p.edit()
            .putFloat("maxLeft", t.maxLeftDeg.toFloat()).putFloat("maxRight", t.maxRightDeg.toFloat())
            .putFloat("sens", t.sensitivity.toFloat()).putFloat("dead", t.deadZone.toFloat())
            .putFloat("curve", t.curve.toFloat()).putFloat("smooth", t.smoothing.toFloat())
            .putBoolean("invert", t.invert).apply()

    var neutral: Quat?
        get() = if (!p.contains("nw")) null else Quat(
            p.getFloat("nw", 1f).toDouble(), p.getFloat("nx", 0f).toDouble(),
            p.getFloat("ny", 0f).toDouble(), p.getFloat("nz", 0f).toDouble())
        set(q) {
            val e = p.edit()
            if (q == null) e.remove("nw").remove("nx").remove("ny").remove("nz")
            else e.putFloat("nw", q.w.toFloat()).putFloat("nx", q.x.toFloat())
                .putFloat("ny", q.y.toFloat()).putFloat("nz", q.z.toFloat())
            e.apply()
        }

    var neutralGravity: Vec3?
        get() = if (!p.contains("gx")) null else Vec3(
            p.getFloat("gx", 0f).toDouble(), p.getFloat("gy", 0f).toDouble(), p.getFloat("gz", 0f).toDouble())
        set(g) {
            val e = p.edit()
            if (g == null) e.remove("gx").remove("gy").remove("gz")
            else e.putFloat("gx", g.x.toFloat()).putFloat("gy", g.y.toFloat()).putFloat("gz", g.z.toFloat())
            e.apply()
        }

    var axis: Vec3
        get() = if (!p.contains("ax")) SteeringMath.DEFAULT_AXIS else Vec3(
            p.getFloat("ax", 0f).toDouble(), p.getFloat("ay", 0f).toDouble(), p.getFloat("az", -1f).toDouble())
        set(a) = p.edit().putFloat("ax", a.x.toFloat()).putFloat("ay", a.y.toFloat()).putFloat("az", a.z.toFloat()).apply()

    fun resetAxis() = p.edit().remove("ax").remove("ay").remove("az").apply()
    val axisIsCustom get() = p.contains("ax")

    var layout: ControllerLayout
        get() = runCatching { ControllerLayout.valueOf(p.getString("layout", "AUTO_GEARS")!!) }
            .getOrDefault(ControllerLayout.AUTO_GEARS)
        set(v) = p.edit().putString("layout", v.name).apply()

    var pedalMode: PedalMode
        get() = runCatching { PedalMode.valueOf(p.getString("pedalMode", "DIGITAL")!!) }.getOrDefault(PedalMode.DIGITAL)
        set(v) = p.edit().putString("pedalMode", v.name).apply()

    /** Extra buttons shown on the driving screen. */
    var extraButtons: Set<Protocol.Button>
        get() {
            val raw = p.getStringSet("extras", null) ?: return DEFAULT_EXTRAS
            return raw.mapNotNull { runCatching { Protocol.Button.valueOf(it) }.getOrNull() }.filter { !it.pad }.toSet()
        }
        set(v) = p.edit().putStringSet("extras", v.map { it.name }.toSet()).apply()

    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit().putBoolean("haptics", v).apply()

    var sendRateHz: Int
        get() = p.getInt("rate", 200)
        set(v) = p.edit().putInt("rate", v.coerceIn(50, 250)).apply()

    var showDebug: Boolean
        get() = p.getBoolean("debug", false)
        set(v) = p.edit().putBoolean("debug", v).apply()

    companion object {
        val DEFAULT_EXTRAS = setOf(Protocol.Button.DRS, Protocol.Button.ERS, Protocol.Button.CLUTCH, Protocol.Button.PAUSE)

        /** Buttons that the Auto gears layout always shows as the big top-corner buttons. */
        val AUTO_LAYOUT_BIG = setOf(Protocol.Button.DRS, Protocol.Button.CLUTCH)
    }
}
