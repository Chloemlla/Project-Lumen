package com.projectlumen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectlumen.app.app.ProjectLumenApp
import com.projectlumen.app.app.ProjectLumenViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application as ProjectLumenApplication
            val viewModel: ProjectLumenViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return ProjectLumenViewModel(
                            database = app.database,
                            notifications = app.notifications,
                            audio = app.audio,
                            export = app.export,
                            backup = app.backup,
                            eyeCarePreferences = app.eyeCarePreferences,
                            startTimerService = app::startTimerService,
                            stopTimerService = app::stopTimerService,
                            scheduleProximityMonitoring = app::scheduleProximityMonitoring,
                            cancelProximityMonitoring = app::cancelProximityMonitoring,
                            calibrateProximityMonitoring = app::calibrateProximityMonitoring,
                            startLightMonitoring = app::startLightMonitoring,
                            stopLightMonitoring = app::stopLightMonitoring,
                            startDeveloperDebugService = app::startDeveloperDebugService,
                            stopDeveloperDebugService = app::stopDeveloperDebugService,
                            startShizukuResilience = app::startShizukuResilience,
                            stopShizukuResilience = app::stopShizukuResilience,
                            shizuku = app.shizuku,
                            simulateDeveloperLowMemory = app::simulateDeveloperLowMemory,
                            uploadTelemetrySnapshot = { app.telemetry.uploadCurrentSnapshot(force = true) },
                        ) as T
                    }
                },
            )
            ProjectLumenApp(
                viewModel = viewModel,
                crashReport = app.crashReports.load(),
            )
        }
    }
}
