package com.projectlumen.app

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.services.TimerForegroundService

class ProjectLumenApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val notifications: NotificationService by lazy { NotificationService(this) }
    val audio: AudioService by lazy { AudioService(this) }
    val export: ExportService by lazy { ExportService(this) }

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
    }

    fun startTimerService() {
        ContextCompat.startForegroundService(this, Intent(this, TimerForegroundService::class.java))
    }

    fun stopTimerService() {
        stopService(Intent(this, TimerForegroundService::class.java))
    }
}
