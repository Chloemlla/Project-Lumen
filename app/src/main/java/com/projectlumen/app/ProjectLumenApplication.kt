package com.projectlumen.app

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import com.projectlumen.app.core.api.BackendCapability
import com.projectlumen.app.core.api.BackendCommunicationBlockedException
import com.projectlumen.app.core.api.BackendConnectivityController
import com.projectlumen.app.core.api.MmkvBackendConnectivityPersistence
import com.projectlumen.app.core.api.ProjectLumenApiClient
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.CrashReport
import com.chloemlla.lumen.crash.CrashReportPasteUploader
import com.chloemlla.lumen.crash.CrashReportStore
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.debug.DeveloperDebugOverlayService
import com.projectlumen.app.core.debug.MemoryHealthMonitor
import com.projectlumen.app.core.haptics.HapticPlaybackService
import com.projectlumen.app.core.insights.AndroidDeviceInsightDataSource
import com.projectlumen.app.core.devicecontrol.PrivilegedDeviceControlCoordinator
import com.projectlumen.app.core.lifecycle.AppLifecycleCoordinator
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.network.ClashPartnerCompat
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import com.projectlumen.app.core.security.AppIntegrityGuard
import com.projectlumen.app.core.security.DeviceSecurityGate
import com.projectlumen.app.core.security.SecureCredentialStore
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.DataBackupService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.ForegroundServiceController
import com.projectlumen.app.core.services.ForegroundServiceFailureReporter
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.services.ShizukuResilienceWorker
import com.projectlumen.app.core.services.TimerForegroundService
import com.projectlumen.app.core.services.TimerReconciliationWorker
import com.projectlumen.app.core.light.LightMonitorService
import com.projectlumen.app.core.proximity.ProximityDetectionWorker
import com.projectlumen.app.core.repositories.DeviceInsightsRepository
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.shizuku.ShizukuCapabilityManager
import com.projectlumen.app.core.telemetry.EyeCareTelemetryReporter
import com.projectlumen.app.core.toast.LumenToast
import com.projectlumen.app.openapi.LumenOpenRuntimeController
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ProjectLumenApplication : Application(), ForegroundServiceFailureReporter {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val eyeCarePreferences: EyeCarePreferencesDataStore by lazy { EyeCarePreferencesDataStore(this) }
    val notifications: NotificationService by lazy { NotificationService(this) }
    val haptics: HapticPlaybackService by lazy { HapticPlaybackService() }
    val audio: AudioService by lazy { AudioService(this, haptics) }
    val export: ExportService by lazy { ExportService(this) }
    val backup: DataBackupService by lazy {
        DataBackupService(this, database, eyeCarePreferences) { secureCredentials.deviceInstallationId() }
    }
    val backendConnectivity: BackendConnectivityController by lazy {
        BackendConnectivityController(
            scope = applicationScope,
            persistence = MmkvBackendConnectivityPersistence(),
            healthProbe = { apiClient.health() },
        )
    }
    val apiClient: ProjectLumenApiClient by lazy {
        ProjectLumenApiClient(
            backendGate = backendConnectivity,
            deviceSecurityGate = deviceSecurityGate,
        )
    }
    val crashReports: CrashReportStore
        get() {
            if (!LumenCrash.isInstalled()) {
                runCatching { installLumenCrashSdk() }
            }
            // Never throw from property access during cold start / baseline launches.
            return runCatching { LumenCrash.store() }.getOrElse {
                // Last-resort local store if SDK install failed closed.
                CrashReportStore(this)
            }
        }
    val secureCredentials: SecureCredentialStore by lazy { SecureCredentialStore(this) }
    val deviceSecurityGate: DeviceSecurityGate by lazy { DeviceSecurityGate(this) }
    val openApiController: LumenOpenRuntimeController by lazy { LumenOpenRuntimeController(this) }
    val telemetry: EyeCareTelemetryReporter by lazy {
        EyeCareTelemetryReporter(
            context = this,
            database = database,
            apiClient = apiClient,
            backendGate = backendConnectivity,
            shizuku = shizuku,
            accessTokenProvider = {
                secureCredentials.load()?.accessToken
                    ?: com.projectlumen.app.core.api.ProjectLumenApiConfig.telemetryAccessToken.takeIf { it.isNotBlank() }
            },
        )
    }
    val shizuku: ShizukuCapabilityManager by lazy { ShizukuCapabilityManager(this) }
    val deviceInsights: DeviceInsightsRepository by lazy {
        DeviceInsightsRepository(AndroidDeviceInsightDataSource(this))
    }
    private val lifecycleCoordinator: AppLifecycleCoordinator by lazy { AppLifecycleCoordinator(this) }
    val deviceControl: PrivilegedDeviceControlCoordinator by lazy { PrivilegedDeviceControlCoordinator(this) }
    private val crashReportUploadInFlight = AtomicBoolean(false)
    @Volatile
    private var crashReportUploadsReady = false
    val startupCrashReport: CrashReport?
        get() = runCatching { LumenCrash.startupCrashReport }.getOrNull()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        runCatching {
            installLumenCrashSdk()
            CrashBreadcrumbs.record("Application.attachBaseContext")
        }.onFailure { Log.e(TAG, "LumenCrash install failed in attachBaseContext", it) }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
        ClashPartnerCompat.start(this)
        // Keep paste-upload HttpURLConnection off stacked system proxies while
        // Clash VPN process binding is active (module-safe hook, no hard dep).
        runCatching {
            CrashReportPasteUploader.shouldSkipManualProxy = {
                ClashPartnerCompat.shouldSkipManualProxy()
            }
        }
        // Keep cold start non-fatal for managed-device baseline profile generation.
        runCatching {
            runCatching {
                installLumenCrashSdk()
                CrashBreadcrumbs.record("Application.onCreate")
            }.onFailure { Log.e(TAG, "LumenCrash install failed in onCreate", it) }
            runCatching { recordRecentProcessExitReason() }
            initializeMmkvOrRecordCrash()
            runCatching { MemoryHealthMonitor.sample(this) }
            // Integrity remains enforced for real release builds that configure the cert fingerprint,
            // but must not process-kill managed-emulator boots when the native bridge fails.
            runCatching { AppIntegrityGuard.enforce(this) }
                .onFailure { throwable ->
                    Log.e(TAG, "App integrity enforcement failed", throwable)
                    recordCrash(throwable)
                }
            deviceSecurityGate.startStartupScan(applicationScope)
            runCatching { notifications.ensureChannels() }
            runCatching { LumenToast.install(this) }
            runCatching { backendConnectivity.start() }
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleCoordinator)
            }
            runCatching { deviceControl.start() }
            observeBackendAvailability()
            crashReportUploadsReady = true
            scheduleStoredCrashReportUpload()
        }.onFailure { error ->
            Log.e(TAG, "Application.onCreate failed", error)
            crashReportUploadsReady = true
        }
    }

    override fun onTrimMemory(level: Int) {
        MemoryHealthMonitor.recordTrim(this, level)
        super.onTrimMemory(level)
    }

    private fun installLumenCrashSdk() {
        if (LumenCrash.isInstalled()) return
        val appName = runCatching { getString(R.string.app_name) }.getOrDefault("Project Lumen")
        // Keep product copy + telemetry hook; metadata can still be explicit for BuildConfig.
        LumenCrash.install(this) {
            appDisplayName = appName
            versionName = BuildConfig.VERSION_NAME
            versionCode = BuildConfig.VERSION_CODE
            commitHash = BuildConfig.SHORT_HASH
            // Prefer existing host provider so app file-share paths stay unchanged.
            fileProviderAuthority = "${packageName}.fileprovider"
            shareSubject = runCatching { getString(R.string.crash_report_share_subject) }.getOrNull()
            reportTitle = runCatching { getString(R.string.crash_report_title) }.getOrNull()
            reportMessage = runCatching { getString(R.string.crash_report_message) }.getOrNull()
            onCrashSaved = { report -> scheduleCrashReportUpload(report) }
            // Unconditional backend crash upload: the SDK fires a POST to the
            // crash-report endpoint for every persisted report. The access token
            // and device installation ID are supplied at crash time, not install
            // time, so they remain safe even if the crash happens during early
            // Application startup.
            crashReportAccessToken = com.projectlumen.app.core.api.ProjectLumenApiConfig.telemetryAccessToken
                .takeIf { it.isNotBlank() }
            deviceInstallationIdProvider = {
                runCatching<String?> { secureCredentials.deviceInstallationId() }.getOrNull()
            }
        }
    }

    private fun initializeMmkvOrRecordCrash() {
        runCatching { ProjectLumenMmkv.initialize(this) }
            .onSuccess { CrashBreadcrumbs.record("MMKV initialized") }
            .onFailure(::recordCrash)
    }

    /**
     * Android 11+ exposes historical process exit reasons. Capture the newest entry as a breadcrumb
     * so cold-start diagnostics can distinguish ANR / low-memory / force-stop from pure crashes.
     */
    private fun recordRecentProcessExitReason() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activityManager = getSystemService(ActivityManager::class.java) ?: return
        val exit = activityManager.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()
            ?: return
        val reason = when (exit.reason) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
            else -> "UNKNOWN(${exit.reason})"
        }
        CrashBreadcrumbs.record(
            "Last process exit reason=$reason status=${exit.status} importance=${exit.importance} pss=${exit.pss}",
        )
    }

    fun recordStartupCrash(throwable: Throwable): CrashReport? {
        return recordCrash(throwable)
    }

    /**
     * Never throws: baseline-profile / managed-emulator boots must survive even when
     * LumenCrash is unavailable or integrity checks fail closed.
     */
    fun recordCrash(throwable: Throwable): CrashReport? {
        if (throwable is BackendCommunicationBlockedException) {
            runCatching {
                CrashBreadcrumbs.record(
                    "Backend request suppressed capability=${throwable.capability.name.lowercase()} reason=${throwable.reasonCode}",
                )
            }
            return null
        }
        return runCatching { LumenCrash.record(throwable) }
            .onFailure { Log.e(TAG, "Failed to record crash", it) }
            .getOrNull()
    }

    override fun recordForegroundServiceFailure(throwable: Throwable) {
        recordCrash(throwable)
    }

    fun clearStartupCrashReport() {
        runCatching { LumenCrash.clearStartupCrashReport() }
    }

    fun scheduleStoredCrashReportUpload() {
        scheduleCrashReportUpload()
    }

    fun scheduleCrashReportUpload(report: CrashReport? = null) {
        if (!crashReportUploadsReady) return
        if (!crashReportUploadInFlight.compareAndSet(false, true)) return
        applicationScope.launch {
            try {
                val reportToUpload = report
                    ?: runCatching { crashReports.load() }.getOrNull()
                    ?: return@launch
                val result = runCatching { telemetry.uploadCrashReport(reportToUpload, force = true) }.getOrNull()
                if (result?.accepted == true) {
                    clearUploadedCrashReport(reportToUpload)
                    runCatching { CrashBreadcrumbs.record("Crash report uploaded") }
                }
            } finally {
                crashReportUploadInFlight.set(false)
            }
        }
    }

    private fun observeBackendAvailability() {
        applicationScope.launch {
            backendConnectivity.state
                .map { backendConnectivity.decision(BackendCapability.TELEMETRY).executable }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        scheduleStoredCrashReportUpload()
                        deviceControl.onBackendAvailable()
                    } else {
                        deviceControl.onBackendUnavailable()
                    }
                }
        }
    }

    private fun clearUploadedCrashReport(report: CrashReport) {
        val storedReport = runCatching { crashReports.load() }.getOrNull()
        if (storedReport?.reportId == report.reportId) {
            runCatching { crashReports.clear() }
        }
        if (startupCrashReport?.reportId == report.reportId) {
            clearStartupCrashReport()
        }
    }

    fun startTimerService() {
        if (!deviceSecurityGate.isServiceAllowed()) {
            Log.w(TAG, "Timer service refused by device security gate")
            return
        }
        // Enqueue the reconciliation safety net first so it survives even when the
        // foreground-service start below is refused (background start on Android 12+).
        TimerReconciliationWorker.enqueue(this)
        ForegroundServiceController.start(
            context = this,
            intent = Intent(this, TimerForegroundService::class.java),
        )
    }

    fun settingsRepository(): SettingsRepository {
        return SettingsRepository(
            database.appSettingsDao(),
            eyeCarePreferences,
            { secureCredentials.deviceInstallationId() },
        )
    }

    fun nativeProtectionSummary(): String {
        return AppIntegrityGuard.nativeProtectionSummary(this)
    }

    fun runtimeRepository(): RuntimeRepository {
        return RuntimeRepository(database.runtimeStateDao())
    }

    fun stopTimerService() {
        stopService(Intent(this, TimerForegroundService::class.java))
        TimerReconciliationWorker.cancel(this)
    }

    fun scheduleProximityMonitoring() {
        if (!deviceSecurityGate.isServiceAllowed()) return
        ProximityDetectionWorker.enqueueNext(this)
    }

    fun cancelProximityMonitoring() {
        ProximityDetectionWorker.cancel(this)
    }

    fun calibrateProximityMonitoring() {
        ProximityDetectionWorker.enqueueCalibration(this)
    }

    fun startLightMonitoring() {
        if (!deviceSecurityGate.isServiceAllowed()) return
        LightMonitorService.start(this)
    }

    fun stopLightMonitoring() {
        LightMonitorService.stop(this)
    }

    fun startDeveloperDebugService() {
        if (!deviceSecurityGate.isServiceAllowed()) return
        DeveloperDebugOverlayService.start(this)
    }

    fun stopDeveloperDebugService() {
        DeveloperDebugOverlayService.stop(this)
    }

    fun simulateDeveloperLowMemory() {
        DeveloperDebugOverlayService.simulateLowMemory(this)
    }

    fun startShizukuResilience() {
        if (!deviceSecurityGate.isServiceAllowed()) return
        ShizukuResilienceWorker.enqueue(this)
    }

    fun stopShizukuResilience() {
        ShizukuResilienceWorker.cancel(this)
    }

    companion object {
        private const val TAG = "ProjectLumenApp"
        @Volatile
        private var appContext: Context? = null

        /** Returns the application context, or null if the Application has not been created yet. */
        fun applicationContext(): Context? = appContext
    }
}
