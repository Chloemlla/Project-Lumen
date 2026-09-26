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
import com.projectlumen.app.core.api.ProjectLumenClientIdentity
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.CrashReport
import com.chloemlla.lumen.crash.CrashReportPasteUploader
import com.chloemlla.lumen.crash.CrashReportStore
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.debug.DeveloperDebugOverlayService
import com.projectlumen.app.core.debug.MemoryHealthMonitor
import com.projectlumen.app.core.insights.AndroidDeviceInsightDataSource
import com.projectlumen.app.core.devicecontrol.PrivilegedDeviceControlCoordinator
import com.projectlumen.app.core.lifecycle.AppLifecycleCoordinator
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.network.ClashPartnerCompat
import com.projectlumen.app.core.mmkv.ProjectLumenMmkv
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperLauncher
import com.projectlumen.app.core.security.AppIntegrityGuard
import com.projectlumen.app.core.security.DeviceSecurityGate
import com.projectlumen.app.core.security.SecureCredentialStore
import com.projectlumen.app.core.services.AuraAudioService
import com.projectlumen.app.core.services.DataBackupService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.ForegroundServiceController
import com.projectlumen.app.core.services.ForegroundServiceFailureReporter
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.services.QuarkKeeperCoordinator
import com.projectlumen.app.core.services.ScheduleAlarmRestore
import com.projectlumen.app.core.services.ScheduleOverdueNagScheduler
import com.projectlumen.app.core.services.ShizukuResilienceWorker
import com.projectlumen.app.core.services.TimerForegroundService
import com.projectlumen.app.core.services.TimerReconciliationWorker
import com.projectlumen.app.core.light.LightMonitorService
import com.projectlumen.app.core.proximity.ProximityDetectionWorker
import com.projectlumen.app.core.repositories.DeviceInsightsRepository
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.ScheduleRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.schedule.ScheduleMaterializer
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
    val audio: AuraAudioService by lazy { AuraAudioService(this) }
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
    val deviceSecurityGate: DeviceSecurityGate by lazy {
        DeviceSecurityGate(
            this,
            // CRooot's native probes die in fork()ed helper processes, so neither the uncaught
            // exception handler nor the process-exit history can see them; the gate hands every
            // CRooot failure here instead of letting it stay in logcat.
            crashReporter = { throwable -> recordHandledFailure(throwable) },
            breadcrumbRecorder = { event -> CrashBreadcrumbs.record(event) },
        )
    }
    val openApiController: LumenOpenRuntimeController by lazy { LumenOpenRuntimeController(this) }
    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(
            database.scheduleSeriesDao(),
            database.scheduleOccurrencesDao(),
            ScheduleMaterializer(
                database.scheduleSeriesDao(),
                database.scheduleOccurrencesDao(),
            ),
        )
    }

    /**
     * Every path that can invalidate the schedule occurrence window or the alarms that point into it
     * goes through here: boot, an exact-alarm permission change, a schedule edit, and a change to the
     * overdue-nag settings. It covers both chains — the pre-start reminders and the overdue nag.
     */
    suspend fun rescheduleScheduleReminders() {
        scheduleRepository.refreshWindow()
        ScheduleAlarmRestore.rearm(this, scheduleRepository)
        ScheduleOverdueNagScheduler.rearmAll(this)
    }

    /**
     * The Quark guard's reconciliation point, in the same shape as [rescheduleScheduleReminders].
     *
     * Boot, an exact-alarm permission change, a cold start and every in-app action funnel through
     * here so no caller has to know which alarm slot its change affected. Idempotent, and safe with
     * the guard turned off — that is the case it uses to cancel the whole day's chain.
     */
    suspend fun reconcileQuarkKeeper() {
        QuarkKeeperCoordinator.reconcile(this)
    }

    /**
     * Re-raises one round of the deadline alert outside reconciliation.
     *
     * Only the paths whose day is still open but whose escalation has already been spent need this:
     * [QuarkKeeperCoordinator.reconcile] stays deliberately silent once the day's nag round is above
     * zero, so an undo after the deadline would otherwise leave the day unanswered and unannounced.
     */
    suspend fun raiseQuarkKeeperAlert() {
        QuarkKeeperCoordinator.fireForceAlert(this)
    }

    /**
     * Fires one of the guard's day nodes on the spot, for the developer-mode buttons on the guard's
     * screen.
     *
     * Both are the very entry points the alarm receiver uses, the guard's own conditions included —
     * there is no shortcut past them here. They are reached directly rather than by broadcasting the
     * alarms' own actions, because the receiver hands its work to a detached scope and answers with
     * `goAsync()`: correct for a fire nobody is waiting on, wrong for one the user just asked for and
     * is watching.
     */
    suspend fun fireQuarkKeeperReminderNode() {
        QuarkKeeperCoordinator.fireReminderNode(this)
    }

    suspend fun fireQuarkKeeperDeadlineNode() {
        QuarkKeeperCoordinator.fireDeadlineNode(this)
    }

    /**
     * Opens Quark's check-in board. True when it opened, false when Quark is not installed or has no
     * launchable entry point — the in-app caller answers false with the install / web dialog, while
     * the notification's own "go" button answers it by opening the store listing directly, because a
     * broadcast receiver has no window to ask the question in.
     */
    fun openQuarkKeeperCheckIn(): Boolean = QuarkKeeperLauncher.launchCheckIn(this)

    fun openQuarkKeeperStoreListing() {
        QuarkKeeperLauncher.openStoreListing(this)
    }

    fun openQuarkKeeperWebCheckIn() {
        QuarkKeeperLauncher.openWebCheckIn(this)
    }

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
    // Every caller reads and writes the same process-wide stores, so handing out a fresh repository
    // per call only hid that fact.
    private val settingsRepositoryInstance: SettingsRepository by lazy {
        SettingsRepository(
            database.appSettingsDao(),
            eyeCarePreferences,
            { secureCredentials.deviceInstallationId() },
        )
    }
    private val runtimeRepositoryInstance: RuntimeRepository by lazy {
        RuntimeRepository(database.runtimeStateDao())
    }
    private val lifecycleCoordinator: AppLifecycleCoordinator by lazy { AppLifecycleCoordinator(this) }
    val deviceControl: PrivilegedDeviceControlCoordinator by lazy { PrivilegedDeviceControlCoordinator(this) }
    private val crashReportUploadInFlight = AtomicBoolean(false)
    @Volatile
    private var crashReportUploadsReady = false

    /** False once MMKV initialization failed: local runtime state cannot be persisted at all. */
    @Volatile
    var localStorageAvailable: Boolean = true
        private set
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
        // ClashPartnerCompat.start does cross-process ContentProvider queries + process VPN
        // binding — moved to the IO startup work below, never on the cold-start main thread.
        // Keep paste-upload HttpURLConnection off stacked system proxies while Clash VPN
        // process binding is active (module-safe hook, no hard dep).
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
            initializeMmkvOrRecordCrash()
            // 官方客户端身份与首访闸门共用一个安装标识。这里只存下一步取数 lambda：
            // deviceInstallationId() 会碰 MMKV + Keystore，不能拉上冷启动主线程（见
            // startBackgroundStartupWork 里的预热）。
            ProjectLumenClientIdentity.installDeviceIdProvider {
                runCatching { secureCredentials.deviceInstallationId() }.getOrNull()
            }
            startBackgroundStartupWork()
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
        super.onTrimMemory(level)
        // Debug.getMemoryInfo walks /proc/self/smaps; the main thread must not pay for it while
        // the system is already under memory pressure.
        applicationScope.launch {
            runCatching { MemoryHealthMonitor.recordTrim(this@ProjectLumenApplication, level) }
        }
    }

    /**
     * Cold-start work that does not gate the first frame: binder queries, /proc sampling, the
     * Keystore-backed credential stores and the native integrity/CRooot scans.
     */
    private fun startBackgroundStartupWork() {
        applicationScope.launch {
            // Clash VPN binding must run before OkHttp clients are built so the first client
            // picks up the matching skip-proxy decision; here it also runs off the main thread
            // (cross-process ContentProvider query + process network binding).
            runCatching { ClashPartnerCompat.start(this@ProjectLumenApplication) }
            // Warm the Keystore/EncryptedSharedPreferences lazies before the ViewModel reads them
            // on the main thread, so that read finds an initialized store instead of building one.
            runCatching { secureCredentials.installProfile() }
            runCatching { secureCredentials.deviceInstallationId() }
            runCatching { recordRecentProcessExitReason() }
            runCatching { MemoryHealthMonitor.sample(this@ProjectLumenApplication) }
            // Integrity remains enforced for real release builds that configure the cert fingerprint,
            // but must not process-kill managed-emulator boots when the native bridge fails.
            runCatching { AppIntegrityGuard.enforce(this@ProjectLumenApplication) }
                .onFailure { throwable ->
                    Log.e(TAG, "App integrity enforcement failed", throwable)
                    recordHandledFailure(throwable)
                }
            runCatching { deviceSecurityGate.startStartupScan(applicationScope) }
                .onFailure { Log.e(TAG, "Device security startup scan failed to start", it) }
            // The guard's alarms are re-armed from here as well as from boot: an update or a
            // force-stop drops them without a BOOT_COMPLETED ever arriving, and a cold start after
            // the deadline is where the day's catch-up alert has to be raised. Runs after
            // initializeMmkvOrRecordCrash() in onCreate, which the guard's store reads through.
            // Skipped outright when that initialization failed: every later MMKV access throws, so
            // there is no stored decision to read and nowhere to persist one — and a reconcile against
            // the default, disabled snapshot would cancel the day's chain instead of restoring it.
            if (localStorageAvailable) {
                runCatching { reconcileQuarkKeeper() }
                    .onFailure { Log.w(TAG, "Quark Keeper startup reconcile failed", it) }
            }
        }
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
            .onFailure { throwable ->
                // Every later MMKV access throws, so services that persist runtime state would only
                // produce an exception storm; remember the outcome instead of retrying blindly.
                localStorageAvailable = false
                recordHandledFailure(throwable)
            }
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
            recordSuppressedBackendRequest(throwable)
            return null
        }
        return runCatching { LumenCrash.record(throwable) }
            .onFailure { Log.e(TAG, "Failed to record crash", it) }
            .getOrNull()
    }

    /**
     * Records a failure the caller already caught and recovered from. The report never claims the
     * pending-report slot, so a handled failure cannot block the next launch with the crash screen.
     */
    fun recordHandledFailure(throwable: Throwable): CrashReport? {
        if (throwable is BackendCommunicationBlockedException) {
            recordSuppressedBackendRequest(throwable)
            return null
        }
        return runCatching { LumenCrash.recordNonFatal(throwable) }
            .onFailure { Log.e(TAG, "Failed to record handled failure", it) }
            .getOrNull()
    }

    private fun recordSuppressedBackendRequest(throwable: BackendCommunicationBlockedException) {
        runCatching {
            CrashBreadcrumbs.record(
                "Backend request suppressed capability=${throwable.capability.name.lowercase()} reason=${throwable.reasonCode}",
            )
        }
    }

    override fun recordForegroundServiceFailure(throwable: Throwable) {
        recordHandledFailure(throwable)
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
        // The reconciliation net is plain WorkManager bookkeeping, not a foreground service, so it
        // must be enqueued even when the security gate refuses the service start below.
        TimerReconciliationWorker.enqueue(this)
        if (refuseWithoutLocalStorage("timer service")) return
        if (!deviceSecurityGate.isServiceAllowed()) {
            Log.w(TAG, "Timer service refused by device security gate")
            return
        }
        ForegroundServiceController.start(
            context = this,
            intent = Intent(this, TimerForegroundService::class.java),
        )
    }

    private fun refuseWithoutLocalStorage(what: String): Boolean {
        if (localStorageAvailable) return false
        Log.w(TAG, "Local storage unavailable; skipping $what")
        return true
    }

    fun settingsRepository(): SettingsRepository = settingsRepositoryInstance

    fun nativeProtectionSummary(): String {
        return AppIntegrityGuard.nativeProtectionSummary(this)
    }

    fun runtimeRepository(): RuntimeRepository = runtimeRepositoryInstance

    fun stopTimerService() {
        stopService(Intent(this, TimerForegroundService::class.java))
        TimerReconciliationWorker.cancel(this)
    }

    fun scheduleProximityMonitoring() {
        if (refuseWithoutLocalStorage("proximity monitoring")) return
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
        if (refuseWithoutLocalStorage("light monitoring")) return
        if (!deviceSecurityGate.isServiceAllowed()) return
        LightMonitorService.start(this)
    }

    fun stopLightMonitoring() {
        LightMonitorService.stop(this)
    }

    fun startDeveloperDebugService() {
        if (refuseWithoutLocalStorage("developer debug overlay")) return
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
        if (refuseWithoutLocalStorage("Shizuku resilience worker")) return
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
