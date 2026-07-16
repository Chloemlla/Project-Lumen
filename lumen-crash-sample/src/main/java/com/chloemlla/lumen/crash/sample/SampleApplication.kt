package com.chloemlla.lumen.crash.sample

import android.app.Application
import android.content.Context
import com.chloemlla.lumen.crash.LumenCrash

class SampleApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Install as early as possible so the uncaught handler is ready before later startup work.
        LumenCrash.installSafely(this) {
            reportTitle = "Sample crash report"
            reportMessage = "This sample recovered from a forced crash."
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!LumenCrash.isInstalled()) {
            LumenCrash.installSafely(this) {
                reportTitle = "Sample crash report"
                reportMessage = "This sample recovered from a forced crash."
            }
        }
    }
}
