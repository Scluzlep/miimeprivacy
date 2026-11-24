package com.scluzlep.miimeprivacy

import android.app.Application
import com.scluzlep.miimeprivacy.bridge.XposedServiceManager

class MiImeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedServiceManager.init(this)
    }
}
