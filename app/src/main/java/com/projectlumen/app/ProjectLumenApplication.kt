package com.projectlumen.app

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.projectlumen.app.core.api.ProjectLumenApiClient
import com.projectlumen.app.core.crash.CrashReport
import com.projectlumen.app.core.crash.CrashReportStore
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.debug.DeveloperDebugOverlayService
import com.projectlumen.app.core.lifecycle.AppLifecycleCoordinator
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.security.AppIntegrityGuard
import com.projectlumen.app.core.security.SecureCredentialStore
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.DataBackupService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.services.ShizukuResilienceWorker
import com.projectlumen.app.core.services.TimerForegroundService
import com.projectlumen.app.core.services.TimerReconciliationWorker
import com.projectlumen.app.core.light.LightMonitorService
import com.projectlumen.app.core.proximity.ProximityDetectionWorker
import com.projectlumen.app.core.shizuku.ShizukuCapabilityManager
import com.projectlumen.app.core.telemetry.EyeCareTelemetryReporter
import com.projectlumen.app.core.toast.LumenToast
import com.projectlumen.app.openapi.LumenOpenRuntimeController

class ProjectLumenApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val eyeCarePreferences: EyeCarePreferencesDataStore by lazy { EyeCarePreferencesDataStore(this) }
    val notifications: NotificationService by lazy { NotificationService(this) }
    val audio: AudioService by lazy { AudioService(this) }
    val export: ExportService by lazy { ExportService(this) }
    val backup: DataBackupService by lazy { DataBackupService(this, database, eyeCarePreferences) }
    val apiClient: ProjectLumenApiClient by lazy { ProjectLumenApiClient(this) }
    val crashReports: CrashReportStore by lazy { CrashReportStore(this) }
    val secureCredentials: SecureCredentialStore by lazy { SecureCredentialStore(this) }
    val openApiController: LumenOpenRuntimeController by lazy { LumenOpenRuntimeController(this) }
    val telemetry: EyeCareTelemetryReporter by lazy {
        EyeCareTelemetryReporter(
            context = this,
            database = database,
            apiClient = apiClient,
            shizuku = shizuku,
            accessTokenProvider = {
                secureCredentials.load()?.accessToken
                    ?: com.projectlumen.app.core.api.ProjectLumenApiConfig.telemetryAccessToken.takeIf { it.isNotBlank() }
            },
        )
    }
    val shizuku: ShizukuCapabilityManager by lazy { ShizukuCapabilityManager(this) }
    private val lifecycleCoordinator: AppLifecycleCoordinator by lazy { AppLifecycleCoordinator(this) }
    private var crashExceptionHandler: Thread.UncaughtExceptionHandler? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        installCrashReporter()
    }

    override fun onCreate() {
        super.onCreate()
        installCrashReporter()
        AppIntegrityGuard.enforce(this)
        notifications.ensureChannels()
        LumenToast.install(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleCoordinator)
    }

    private fun installCrashReporter() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (defaultExceptionHandler === crashExceptionHandler) return

        lateinit var handler: Thread.UncaughtExceptionHandler
        handler = Thread.UncaughtExceptionHandler { thread, throwable ->
            val report = runCatching { CrashReport.fromThrowable(throwable) }
                .getOrElse { CrashReport.fromThrowableFallback(throwable, it) }
            runCatching { crashReports.save(report) }
            if (defaultExceptionHandler != null && defaultExceptionHandler !== handler) {
                defaultExceptionHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
        crashExceptionHandler = handler
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    fun startTimerService() {
        ContextCompat.startForegroundService(this, Intent(this, TimerForegroundService::class.java))
        TimerReconciliationWorker.enqueue(this)
    }

    fun stopTimerService() {
        stopService(Intent(this, TimerForegroundService::class.java))
        TimerReconciliationWorker.cancel(this)
    }

    fun scheduleProximityMonitoring() {
        ProximityDetectionWorker.enqueueNext(this)
    }

    fun cancelProximityMonitoring() {
        ProximityDetectionWorker.cancel(this)
    }

    fun calibrateProximityMonitoring() {
        ProximityDetectionWorker.enqueueCalibration(this)
    }

    fun startLightMonitoring() {
        LightMonitorService.start(this)
    }

    fun stopLightMonitoring() {
        LightMonitorService.stop(this)
    }

    fun startDeveloperDebugService() {
        DeveloperDebugOverlayService.start(this)
    }

    fun stopDeveloperDebugService() {
        DeveloperDebugOverlayService.stop(this)
    }

    fun simulateDeveloperLowMemory() {
        DeveloperDebugOverlayService.simulateLowMemory(this)
    }

    fun startShizukuResilience() {
        ShizukuResilienceWorker.enqueue(this)
    }

    fun stopShizukuResilience() {
        ShizukuResilienceWorker.cancel(this)
    }
}
