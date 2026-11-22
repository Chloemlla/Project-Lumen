package com.chloemlla.lumen.crash.sample

import android.app.Application
import android.content.Context
import com.chloemlla.lumen.crash.LumenCrash
import java.io.File

class SampleApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Install as early as possible so the uncaught handler is ready before later startup work.
        LumenCrash.installSafely(this) {
            reportTitle = "Sample crash report"
            reportMessage = "This sample recovered from a forced crash."
            startupHangWatchdogEnabled = true
            startupHangTimeoutMillis = 15_000L
            // Demonstrates the WebView-shell channel: drop a *.json / *.txt / *.log file here
            // (for example from a Rust panic hook) and the SDK turns it into a report.
            externalInboxDirectory = File(filesDir, "crash-inbox")
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!LumenCrash.isInstalled()) {
            LumenCrash.installSafely(this) {
                reportTitle = "Sample crash report"
                reportMessage = "This sample recovered from a forced crash."
                startupHangWatchdogEnabled = true
                startupHangTimeoutMillis = 15_000L
                externalInboxDirectory = File(filesDir, "crash-inbox")
            }
        }
    }
}
