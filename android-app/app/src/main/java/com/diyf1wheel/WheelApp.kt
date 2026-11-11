package com.diyf1wheel

import android.app.Activity
import android.app.Application
import com.diyf1wheel.engine.WheelEngine

class WheelApp : Application() {
    lateinit var engine: WheelEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = WheelEngine(this)
    }
}

val Activity.engine: WheelEngine get() = (application as WheelApp).engine
