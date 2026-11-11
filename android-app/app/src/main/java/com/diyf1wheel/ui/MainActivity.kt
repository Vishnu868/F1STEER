package com.diyf1wheel.ui

import java.util.Locale
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.diyf1wheel.BuildConfig
import com.diyf1wheel.engine
import com.diyf1wheel.net.Discovery
import com.diyf1wheel.net.Protocol
import com.diyf1wheel.net.UdpLink
import com.diyf1wheel.sensor.SteeringSensor
import com.diyf1wheel.sensor.SteeringTuning
import com.diyf1wheel.settings.PedalMode
import com.diyf1wheel.settings.Preset
import kotlin.concurrent.thread

/** Setup screen: connection, calibration, tuning, controls and live diagnostics. */
class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val dp by lazy { resources.displayMetrics.density }

    private lateinit var statusText: TextView
    private lateinit var linkInfo: TextView
    private lateinit var sensorInfo: TextView
    private lateinit var debugText: TextView
    private lateinit var connectBtn: Button
    private lateinit var hostEdit: EditText
    private lateinit var pinEdit: EditText
    private lateinit var portEdit: EditText
    private lateinit var gauge: GaugeView
    private val sliders = mutableMapOf<String, Pair<SeekBar, TextView>>()
    private val presetButtons = mutableMapOf<Preset, Button>()
    private var suppressSliderEvents = false

    companion object {
        const val RED = 0xFFE10600.toInt()
        const val GREEN = 0xFF1ED760.toInt()
        const val AMBER = 0xFFFFB000.toInt()
        const val BG = 0xFF0D0F14.toInt()
        const val PANEL = 0xFF161A22.toInt()
        const val MUTED = 0xFF8A93A6.toInt()
        const val FIELD = 0xFF0A0C10.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(build())
        loadSettingsIntoUi()
    }

    override fun onResume() {
        super.onResume()
        engine.acquire()
        ui.post(refresh)
    }

    override fun onPause() {
        ui.removeCallbacks(refresh)
        engine.release()
        super.onPause()
    }

    // ================================================================= layout
    private fun build(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16), px(16), px(16), px(24)) }
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); addView(root) }
        // Android 15+ draws apps edge-to-edge: keep content clear of the status/navigation bars.
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }

        // header
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(View(context).apply { setBackgroundColor(RED) }, LinearLayout.LayoutParams(px(6), px(30)))
            addView(TextView(context).apply {
                text = "DIY F1 WHEEL"; setTextColor(Color.WHITE); textSize = 24f; typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.08f; setPadding(px(10), 0, 0, 0)
            })
            addView(TextView(context).apply { text = "  v${BuildConfig.VERSION_NAME}"; setTextColor(MUTED); textSize = 12f })
        })

        // big drive button
        root.addView(Button(this).apply {
            text = "START DRIVING"
            textSize = 20f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            background = rounded(RED, 14f)
            setOnClickListener { startActivity(Intent(this@MainActivity, DriveActivity::class.java)) }
        }, lp(top = 14, height = 64))

        // ---- connection
        val conn = card(root, "Connection to laptop")
        statusText = TextView(this).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        conn.addView(statusText)
        linkInfo = muted("")
        conn.addView(linkInfo)
        hostEdit = field("Laptop IP (e.g. 192.168.1.20)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        pinEdit = field("6-digit PIN shown on the laptop", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        portEdit = field("Port", InputType.TYPE_CLASS_NUMBER)
        conn.addView(label("Laptop IP address")); conn.addView(hostEdit)
        conn.addView(label("Pairing PIN")); conn.addView(pinEdit)
        conn.addView(label("UDP port")); conn.addView(portEdit)
        conn.addView(row(
            button("Find laptop", Color.rgb(0x23, 0x29, 0x38)) { findLaptop() },
            Button(this).also { connectBtn = it; style(it, RED); it.setOnClickListener { toggleConnect() } },
        ))

        // ---- live
        val live = card(root, "Live steering")
        gauge = GaugeView(this) {
            val s = when {
                engine.sensor.mode == SteeringSensor.Mode.NONE -> Pair("NO MOTION SENSOR", RED)
                !engine.sensorHealthy() -> Pair("SENSOR STARTING…", AMBER)
                !engine.calibrated -> Pair("NOT CALIBRATED - set centre", AMBER)
                else -> Pair("SENSOR OK", GREEN)
            }
            Triple(engine.filteredAngleDeg, engine.steer, s)
        }
        live.addView(gauge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(230)))
        sensorInfo = muted("")
        live.addView(sensorInfo)

        // ---- calibration
        val cal = card(root, "Calibration")
        cal.addView(muted("Hold the wheel (or phone) level, exactly how you will drive, then set the centre. " +
                "Use axis calibration if your phone is mounted at an angle on the wheel."))
        cal.addView(row(
            button("Set centre", Color.rgb(0x2F, 0x8B, 0xFF)) {
                engine.setCentre(); toast("Centre saved")
            },
            button("Calibrate axis", Color.rgb(0x23, 0x29, 0x38)) { axisWizard() },
        ))
        cal.addView(row(button("Reset axis", Color.rgb(0x23, 0x29, 0x38)) { engine.resetAxis(); toast("Axis reset to screen normal") }))

        // ---- tuning
        val tune = card(root, "Steering response")
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (p in Preset.entries) {
            val b = Button(this).apply {
                text = p.label; textSize = 12f; isAllCaps = false
                setOnClickListener { applyPreset(p) }
            }
            presetButtons[p] = b
            presets.addView(b, LinearLayout.LayoutParams(0, px(44), 1f).apply { marginEnd = px(4) })
        }
        tune.addView(presets)
        slider(tune, "maxLeft", "Full lock LEFT at", 20, 270) { "${it}° of wheel rotation" }
        slider(tune, "maxRight", "Full lock RIGHT at", 20, 270) { "${it}° of wheel rotation" }
        slider(tune, "sens", "Sensitivity", 50, 200) { "×${"%.2f".format(Locale.US, it / 100f)}" }
        slider(tune, "dead", "Centre dead zone", 0, 15) { "$it %" }
        slider(tune, "curve", "Response curve", 50, 300) {
            val g = it / 100f
            "${"%.2f".format(Locale.US, g)}  " + when { g < 0.95f -> "(quicker near centre)"; g > 1.05f -> "(finer near centre)"; else -> "(linear)" }
        }
        slider(tune, "smooth", "Smoothing", 0, 100) { "$it %" + if (it > 70) "  (adds lag)" else "" }
        tune.addView(Switch(this).apply {
            text = "Invert steering direction"; setTextColor(Color.WHITE)
            isChecked = engine.tuning.invert
            setOnCheckedChangeListener { _, c -> engine.updateTuning(engine.tuning.copy(invert = c)) }
        })

        // ---- controls
        val ctl = card(root, "Driving screen")
        ctl.addView(label("Throttle / brake"))
        for (m in PedalMode.entries) {
            ctl.addView(CheckBox(this).apply {
                text = m.label; setTextColor(Color.WHITE)
                isChecked = engine.settings.pedalMode == m
                tag = "pedal_$m"
                setOnClickListener {
                    engine.settings.pedalMode = m
                    for (o in PedalMode.entries) (ctl.findViewWithTag<CheckBox>("pedal_$o")).isChecked = o == m
                }
            })
        }
        ctl.addView(label("Extra buttons on the driving screen"))
        val extras = engine.settings.extraButtons.toMutableSet()
        for (b in Protocol.Button.entries) {
            ctl.addView(CheckBox(this).apply {
                text = b.label; setTextColor(Color.WHITE); isChecked = b in extras
                setOnCheckedChangeListener { _, c ->
                    if (c) extras.add(b) else extras.remove(b)
                    engine.settings.extraButtons = extras.toSet()
                }
            })
        }
        ctl.addView(Switch(this).apply {
            text = "Vibrate on paddle / button press"; setTextColor(Color.WHITE)
            isChecked = engine.settings.haptics
            setOnCheckedChangeListener { _, c -> engine.settings.haptics = c }
        })
        ctl.addView(label("Send rate (higher = smoother, more battery)"))
        val rates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (r in listOf(100, 200, 250)) {
            rates.addView(Button(this).apply {
                text = "$r Hz"; tag = "rate_$r"
                setOnClickListener {
                    engine.settings.sendRateHz = r
                    refreshRateButtons(rates)
                    if (engine.link.status != UdpLink.Status.STOPPED) engine.connect()
                }
            }, LinearLayout.LayoutParams(0, px(44), 1f).apply { marginEnd = px(4) })
        }
        ctl.addView(rates)
        refreshRateButtons(rates)

        // ---- diagnostics
        val dbg = card(root, "Sensor debug")
        dbg.addView(Switch(this).apply {
            text = "Show raw sensor data"; setTextColor(Color.WHITE)
            isChecked = engine.settings.showDebug
            setOnCheckedChangeListener { _, c -> engine.settings.showDebug = c; debugText.visibility = if (c) View.VISIBLE else View.GONE }
        })
        debugText = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 11f; setTextColor(MUTED)
            visibility = if (engine.settings.showDebug) View.VISIBLE else View.GONE
        }
        dbg.addView(debugText)

        root.addView(muted("No accounts, no internet, no telemetry. Data only goes to the laptop you pair with."),
            lp(top = 16))
        return scroll
    }

    // ================================================================= behaviour
    private fun loadSettingsIntoUi() {
        val s = engine.settings
        hostEdit.setText(s.host)
        pinEdit.setText(s.pin)
        portEdit.setText(s.port.toString())
        loadTuningIntoSliders(engine.tuning)
        highlightPreset(s.preset)
    }

    private fun loadTuningIntoSliders(t: SteeringTuning) {
        suppressSliderEvents = true
        sliders["maxLeft"]!!.first.progress = t.maxLeftDeg.toInt() - 20
        sliders["maxRight"]!!.first.progress = t.maxRightDeg.toInt() - 20
        sliders["sens"]!!.first.progress = (t.sensitivity * 100).toInt() - 50
        sliders["dead"]!!.first.progress = (t.deadZone * 100).toInt()
        sliders["curve"]!!.first.progress = (t.curve * 100).toInt() - 50
        sliders["smooth"]!!.first.progress = (t.smoothing * 100).toInt()
        suppressSliderEvents = false
    }

    private fun tuningFromSliders(): SteeringTuning {
        fun v(k: String, min: Int) = sliders[k]!!.first.progress + min
        return SteeringTuning(
            maxLeftDeg = v("maxLeft", 20).toDouble(), maxRightDeg = v("maxRight", 20).toDouble(),
            sensitivity = v("sens", 50) / 100.0, deadZone = v("dead", 0) / 100.0,
            curve = v("curve", 50) / 100.0, smoothing = v("smooth", 0) / 100.0,
            invert = engine.tuning.invert)
    }

    private fun applyPreset(p: Preset) {
        engine.settings.preset = p
        p.tuning?.let {
            val t = it.copy(invert = engine.tuning.invert)
            engine.updateTuning(t)
            loadTuningIntoSliders(t)
        }
        highlightPreset(p)
    }

    private fun highlightPreset(p: Preset) {
        presetButtons.forEach { (k, b) -> style(b, if (k == p) RED else Color.rgb(0x23, 0x29, 0x38)) }
    }

    private fun refreshRateButtons(row: LinearLayout) {
        for (r in listOf(100, 200, 250)) row.findViewWithTag<Button>("rate_$r")?.let {
            style(it, if (engine.settings.sendRateHz == r) RED else Color.rgb(0x23, 0x29, 0x38))
        }
    }

    private fun saveConnectionFields(): Boolean {
        val host = hostEdit.text.toString().trim()
        val pin = pinEdit.text.toString().trim()
        val port = portEdit.text.toString().trim().toIntOrNull()
        if (host.isEmpty()) { toast("Enter the laptop IP (shown in the receiver) or tap Find laptop"); return false }
        if (!(pin.length == 6 && pin.all { it.isDigit() })) { toast("The PIN is the 6 digits shown on the laptop"); return false }
        if (port == null || port !in 1024..65535) { toast("Port must be 1024-65535"); return false }
        engine.settings.host = host
        engine.settings.pin = pin
        engine.settings.port = port
        return true
    }

    private fun toggleConnect() {
        if (engine.link.status == UdpLink.Status.STOPPED) {
            if (saveConnectionFields()) engine.connect()
        } else engine.disconnect()
    }

    private fun findLaptop() {
        toast("Searching the local network…")
        thread(name = "discovery") {
            val found = runCatching { Discovery.find(this) }.getOrDefault(emptyList())
            ui.post {
                when {
                    found.isEmpty() -> AlertDialog.Builder(this)
                        .setTitle("Laptop not found")
                        .setMessage("Make sure the receiver is running and both devices are on the same Wi-Fi. " +
                            "Some routers block discovery (\"AP / client isolation\") - type the IP shown in the receiver instead.")
                        .setPositiveButton("OK", null).show()
                    found.size == 1 -> useFound(found[0])
                    else -> AlertDialog.Builder(this).setTitle("Choose laptop")
                        .setItems(found.map { "${it.name}  (${it.ip})" }.toTypedArray()) { _, i -> useFound(found[i]) }
                        .show()
                }
            }
        }
    }

    private fun useFound(f: Discovery.Found) {
        hostEdit.setText(f.ip)
        portEdit.setText(f.port.toString())
        toast("Found ${f.name} at ${f.ip}")
        if (pinEdit.text.isEmpty()) pinEdit.requestFocus()
    }

    private fun axisWizard() {
        if (engine.sensor.mode == SteeringSensor.Mode.GRAVITY) {
            toast("This phone has no gyroscope - the default axis is used"); return
        }
        AlertDialog.Builder(this)
            .setTitle("Axis calibration  1/2")
            .setMessage("Hold the wheel in its straight-ahead (centre) position, then tap Next.")
            .setPositiveButton("Next") { _, _ ->
                engine.setCentre()
                AlertDialog.Builder(this)
                    .setTitle("Axis calibration  2/2")
                    .setMessage("Turn the wheel to the RIGHT by about 90° (a quarter turn) and hold it there, then tap Finish.")
                    .setPositiveButton("Finish") { _, _ ->
                        val err = engine.finishAxisCalibration()
                        if (err == null) {
                            toast("Axis saved. Return the wheel to centre.")
                        } else {
                            AlertDialog.Builder(this).setTitle("Calibration failed").setMessage(err)
                                .setPositiveButton("OK", null).show()
                        }
                    }
                    .setNegativeButton("Cancel", null).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private val refresh = object : Runnable {
        override fun run() {
            val link = engine.link
            val (txt, col) = when (link.status) {
                UdpLink.Status.STOPPED -> Pair("● Not connected", MUTED)
                UdpLink.Status.CONNECTING -> Pair("● Connecting… (check IP / PIN / firewall)", AMBER)
                UdpLink.Status.CONNECTED -> if (link.controllerOk) Pair("● Connected - controller ready", GREEN)
                                            else Pair("● Connected - laptop has no virtual controller", AMBER)
                UdpLink.Status.ERROR -> Pair("● Network error - retrying", RED)
            }
            statusText.text = txt
            statusText.setTextColor(col)
            connectBtn.text = if (link.status == UdpLink.Status.STOPPED) "CONNECT" else "DISCONNECT"
            val rtt = link.rttMs?.let { "%.1f ms round trip (~%.1f ms one way)".format(Locale.US, it, it / 2) } ?: "latency: -"
            linkInfo.text = buildString {
                append(rtt)
                if (link.status == UdpLink.Status.CONNECTED) append("\nLaptop receives ${link.pcRxHz} packets/s · ${link.pcDropped} dropped")
                link.error?.let { append("\n$it") }
            }
            val sensor = engine.sensor
            sensorInfo.text = "${sensor.mode.label}\n${sensor.sensorName}\n" +
                "Sample rate: ${sensor.rate.hz.toInt()} Hz · gyroscope: ${if (sensor.hasGyroscope) "yes" else "NO"}" +
                "\nAxis: ${if (engine.settings.axisIsCustom) "calibrated" else "default (screen normal)"}"
            if (debugText.visibility == View.VISIBLE) {
                val q = sensor.quat
                val r = engine.relative
                val g = sensor.gravity
                val a = engine.axis
                debugText.text = "quat     w=%+.4f x=%+.4f y=%+.4f z=%+.4f\n".format(Locale.US, q.w, q.x, q.y, q.z) +
                    "relative w=%+.4f x=%+.4f y=%+.4f z=%+.4f\n".format(Locale.US, r.w, r.x, r.y, r.z) +
                    "gravity  %+.2f %+.2f %+.2f m/s²\n".format(Locale.US, g.x, g.y, g.z) +
                    "axis     %+.3f %+.3f %+.3f\n".format(Locale.US, a.x, a.y, a.z) +
                    "angle raw %+.2f°  filtered %+.2f°  out %+.3f\n".format(Locale.US, engine.rawAngleDeg, engine.filteredAngleDeg, engine.steer) +
                    "packets sent ${link.sentPackets} · reconnects ${link.reconnects}"
            }
            ui.postDelayed(this, 100)
        }
    }

    // ================================================================= helpers
    private fun px(v: Int) = (v * dp).toInt()

    private fun lp(top: Int = 0, height: Int = -2) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, if (height > 0) px(height) else ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = px(top) }

    private fun rounded(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r * dp }

    private fun style(b: Button, color: Int) {
        b.background = rounded(color, 10f)
        b.setTextColor(Color.WHITE)
        b.isAllCaps = false
    }

    private fun button(text: String, color: Int, onClick: () -> Unit) = Button(this).apply {
        this.text = text; style(this, color); setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, px(8), 0, 0)
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, px(48), 1f).apply { if (i > 0) marginStart = px(8) })
        }
    }

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(PANEL, 14f)
            setPadding(px(14), px(12), px(14), px(14))
        }
        c.addView(TextView(this).apply {
            text = title.uppercase(); setTextColor(MUTED); textSize = 12f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f; setPadding(0, 0, 0, px(6))
        })
        parent.addView(c, lp(top = 14))
        return c
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 14f; setPadding(0, px(10), 0, px(2))
    }

    private fun muted(t: String) = TextView(this).apply { text = t; setTextColor(MUTED); textSize = 13f }

    private fun field(hint: String, type: Int) = EditText(this).apply {
        this.hint = hint; inputType = type; setTextColor(Color.WHITE); setHintTextColor(0xFF5A6275.toInt())
        background = rounded(FIELD, 8f); setPadding(px(12), px(10), px(12), px(10)); isSingleLine = true
    }

    private fun slider(parent: LinearLayout, key: String, title: String, min: Int, max: Int, fmt: (Int) -> String) {
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(10), 0, 0) }
        head.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 14f },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val value = TextView(this).apply { setTextColor(AMBER); textSize = 13f }
        head.addView(value)
        val bar = SeekBar(this).apply {
            this.max = max - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    value.text = fmt(p + min)
                    if (!suppressSliderEvents && fromUser) {
                        engine.updateTuning(tuningFromSliders())
                        if (engine.settings.preset != Preset.CUSTOM) {
                            engine.settings.preset = Preset.CUSTOM
                            highlightPreset(Preset.CUSTOM)
                        }
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        parent.addView(head)
        parent.addView(bar)
        sliders[key] = Pair(bar, value)
        value.text = fmt(min)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
