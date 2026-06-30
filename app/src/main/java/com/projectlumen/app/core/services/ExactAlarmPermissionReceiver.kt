package com.projectlumen.app.core.services

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.repositories.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val app = context.applicationContext as ProjectLumenApplication
                val settings = SettingsRepository(app.database.appSettingsDao(), app.eyeCarePreferences).getOrDefault()
                val runtime = app.database.runtimeStateDao().get() ?: return@runCatching
                app.notifications.syncRuntimeAlarms(settings, runtime)
                if (settings.keepAliveEnabled && runtime.activeEngine != ActiveEngine.IDLE.name) {
                    app.startTimerService()
                }
            }
            pendingResult.finish()
        }
    }
}
