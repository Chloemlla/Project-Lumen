package com.projectlumen.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectlumen.app.app.ProjectLumenApp
import com.projectlumen.app.app.ProjectLumenViewModel
import com.projectlumen.app.openapi.LumenOpenIntents
import com.projectlumen.app.openapi.LumenOpenLaunchRequest
import com.projectlumen.app.openapi.LumenOpenLaunchTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class MainActivity : ComponentActivity() {
    private val openLaunchRequest = mutableStateOf<LumenOpenLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOpenIntent(intent)
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
                            apiClient = app.apiClient,
                            secureCredentials = app.secureCredentials,
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
                openLaunchRequest = openLaunchRequest.value,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        val request = LumenOpenIntents.parseLaunchRequest(intent, callingPackage) ?: return
        openLaunchRequest.value = request
        val app = application as ProjectLumenApplication
        lifecycleScope.launch(Dispatchers.IO) {
            when (request.target) {
                LumenOpenLaunchTarget.REST -> {
                    val durationSeconds = app.openApiController.triggerEyeRelaxation(
                        sourceApp = request.sourceApp,
                        requestedDurationSeconds = request.restDurationSeconds,
                    )
                    scheduleReturnToCaller(request, durationSeconds)
                }
                LumenOpenLaunchTarget.DASHBOARD,
                LumenOpenLaunchTarget.VISUAL_MONITOR -> app.openApiController.recordOpenLaunch(request.sourceApp)
            }
        }
    }

    private fun scheduleReturnToCaller(request: LumenOpenLaunchRequest, durationSeconds: Int) {
        val targetPackage = request.callerPackage.takeIf { it.isNotBlank() } ?: return
        if (targetPackage == packageName) return
        lifecycleScope.launch {
            delay(durationSeconds.coerceAtLeast(1) * 1000L + EXTERNAL_REST_RETURN_GRACE_MILLIS)
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                ?: return@launch
            runCatching { startActivity(launchIntent) }
        }
    }

    private companion object {
        private const val EXTERNAL_REST_RETURN_GRACE_MILLIS = 750L
    }
}
