package com.projectlumen.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.chloemlla.lumen.crash.ui.LumenCrashReportScreen
import com.projectlumen.app.app.ProjectLumenApp
import com.projectlumen.app.app.ProjectLumenViewModel
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.LumenCrash
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.openapi.LumenOpenIntents
import com.projectlumen.app.openapi.LumenOpenLaunchRequest
import com.projectlumen.app.openapi.LumenOpenLaunchTarget
import com.projectlumen.app.ui.theme.ProjectLumenTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class MainActivity : ComponentActivity() {
    private val openLaunchRequest = mutableStateOf<LumenOpenLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Baseline-profile / managed emulators must keep the process alive even when startup
        // dependencies fail. Never let onCreate throw after Application install.
        runCatching {
            CrashBreadcrumbs.record("MainActivity.onCreate")
            enableEdgeToEdge()
            handleOpenIntent(intent)
            val app = application as ProjectLumenApplication
            var initialStartupReport = app.startupCrashReport
                ?: runCatching { if (LumenCrash.isInstalled()) app.crashReports.load() else null }.getOrNull()
            val initialViewModel = if (initialStartupReport == null) {
                createProjectLumenViewModel(app)
            } else {
                null
            }
            if (initialStartupReport == null && initialViewModel == null) {
                initialStartupReport = app.startupCrashReport
                    ?: runCatching { if (LumenCrash.isInstalled()) app.crashReports.load() else null }.getOrNull()
            }
            setContent {
                var startupReport by remember { mutableStateOf(initialStartupReport) }
                startupReport?.let { report ->
                    ProjectLumenTheme(themeMode = AppThemeMode.SYSTEM, useDynamicColors = false) {
                        LumenCrashReportScreen(
                            report = report,
                            onContinue = {
                                app.clearStartupCrashReport()
                                startupReport = null
                                if (initialViewModel == null) recreate()
                            },
                            clearStoredReportOnContinue = true,
                            onClearStoredReport = {
                                app.scheduleCrashReportUpload(report)
                                runCatching { app.crashReports.clear() }
                                app.clearStartupCrashReport()
                            },
                        )
                    }
                    return@setContent
                }
                val viewModel = initialViewModel
                if (viewModel == null) {
                    // Keep a non-empty surface so process stays visibly alive for macrobenchmark.
                    ProjectLumenTheme(themeMode = AppThemeMode.SYSTEM, useDynamicColors = false) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                    return@setContent
                }
                ProjectLumenApp(
                    viewModel = viewModel,
                    crashReport = runCatching {
                        if (LumenCrash.isInstalled()) app.crashReports.load() else null
                    }.getOrNull(),
                    openLaunchRequest = openLaunchRequest.value,
                )
            }
        }.onFailure { error ->
            Log.e(TAG, "MainActivity.onCreate failed", error)
            runCatching {
                setContent {
                    ProjectLumenTheme(themeMode = AppThemeMode.SYSTEM, useDynamicColors = false) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    private fun createProjectLumenViewModel(app: ProjectLumenApplication): ProjectLumenViewModel? {
        return try {
            ViewModelProvider(
                this,
                object : ViewModelProvider.Factory {
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
                            recordCrashReport = app::recordCrash,
                        ) as T
                    }
                },
            )[ProjectLumenViewModel::class.java]
        } catch (throwable: Throwable) {
            app.recordCrash(throwable)
            null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runCatching { CrashBreadcrumbs.record("MainActivity.onNewIntent") }
        setIntent(intent)
        handleOpenIntent(intent)
    }

    private fun handleOpenIntent(intent: Intent?) {
        val request = LumenOpenIntents.parseLaunchRequest(intent, callingPackage) ?: return
        runCatching { CrashBreadcrumbs.record("Open API launch target=${request.target}") }
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
        private const val TAG = "MainActivity"
    }
}
