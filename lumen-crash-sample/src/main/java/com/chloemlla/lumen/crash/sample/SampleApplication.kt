package com.chloemlla.lumen.crash.sample

import android.app.Application
import com.chloemlla.lumen.crash.LumenCrash

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Short path: metadata + default FileProvider authority are filled by the SDK.
        LumenCrash.installSafely(this) {
            reportTitle = "Sample crash report"
            reportMessage = "This sample recovered from a forced crash."
        }
    }
}
