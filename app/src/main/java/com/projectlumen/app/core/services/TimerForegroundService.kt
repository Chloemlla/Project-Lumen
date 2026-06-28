package com.projectlumen.app.core.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.projectlumen.app.core.constants.NotificationIds

class TimerForegroundService : Service() {
    private lateinit var notifications: NotificationService

    override fun onCreate() {
        super.onCreate()
        notifications = NotificationService(this)
        notifications.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NotificationIds.FOREGROUND_TIMER,
            notifications.buildOngoingStatusNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
