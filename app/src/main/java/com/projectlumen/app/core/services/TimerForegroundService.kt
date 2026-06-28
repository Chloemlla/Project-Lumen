package com.projectlumen.app.core.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds

class TimerForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        NotificationService(this).ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, NotificationChannels.REMINDER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.ongoing_timer_title))
            .setContentText(getString(R.string.ongoing_timer_message))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this,
            NotificationIds.FOREGROUND_TIMER,
            notification,
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
