package com.chloemlla.lumen.crash

import android.app.Application
import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

/**
 * Public entry for the Lumen Crash SDK.
 */
object LumenCrash {
    private val installedConfig = AtomicReference<LumenCrashConfig?>(null)
    private val storeRef = AtomicReference<CrashReportStore?>(null)
    private val handlerRef = AtomicReference<Thread.UncaughtExceptionHandler?>(null)
    private val watchdogRef = AtomicReference<LumenCrashWatchdog?>(null)
    private val installLock = Any()

    /** Host package name, captured at install time for the backend uploader. */
    @Volatile
    private var packageName: String = ""

    /** Application context, captured at install time for the default device ID. */
    @Volatile
    private var appContext: Context? = null

    /**
     * Tracks [reportId]s that have already been submitted for backend upload
     * within this process, so cold-start and saveReport paths do not duplicate
     * the upload.
     */
    private val uploadedReportIds = ConcurrentHashMap.newKeySet<String>()

    /** Last accepted non-fatal upload submission, used for client-side throttling. */
    private val lastNonFatalUploadAtMillis = AtomicLong(0L)

    /**
     * Single-thread executor for background crash-report uploads.
     *
     * Created lazily so the SDK does not allocate a thread when the host has
     * disabled the backend upload or not configured an access token.
     */
    @Volatile
    private var uploadExecutor: ExecutorService? = null

    @Volatile
    var startupCrashReport: CrashReport? = null
        private set

    fun install(application: Application, config: LumenCrashConfig) {
        AuthorIntegrity.verifyOrThrow("install")
        packageName = application.packageName
        appContext = application.applicationContext
        synchronized(installLock) {
            installedConfig.set(config)
            storeRef.set(CrashReportStore(application.applicationContext))
            installUncaughtExceptionHandler(application)
            restartWatchdog(application, config)
            CrashBreadcrumbs.record(
                "LumenCrash installed watchdog=${config.anrWatchdogEnabled || config.startupHangWatchdogEnabled}",
            )
        }
        submitInstallFollowUp(application, config)
    }

    /**
     * Runs the install work that must not extend `Application.onCreate`: a cross-process binder
     * call plus external-storage IO, and the flush of a report that is still on disk.
     *
     * A prior-exit report found here reaches the host through [LumenCrashConfig.onReportSaved]
     * instead of being guaranteed to be visible the moment [install] returns.
     */
    private fun submitInstallFollowUp(application: Application, config: LumenCrashConfig) {
        if (!config.priorExitCaptureEnabled && !config.crashReportBackendEnabled) return
        runCatching {
            executor().submit {
                runCatching { collectPriorExitReport(application, config) }
                // Capture-only hosts never call loadPendingReport(), so a stored report would
                // otherwise sit on disk until the next crash overwrote it.
                val pending = runCatching { store().load() }.getOrNull()
                if (pending != null) {
                    submitBackendUpload(pending, config)
                }
            }
        }
    }

    /**
     * Convenience install path.
     *
     * Auto-fills app metadata and defaults file-share authority to the SDK-owned
     * FileProvider. Hosts can still override any field through [configure].
     */
    fun install(
        application: Application,
        configure: LumenCrashConfigBuilder.() -> Unit = {},
    ) {
        val builder = LumenCrashConfigBuilder(application)
        builder.configure()
        install(application, builder.build())
    }

    /**
     * Host-safe install wrapper.
     *
     * Integrity remains fail-closed inside [install]; this only prevents one failed
     * install path from process-killing host startup.
     */
    fun installSafely(
        application: Application,
        configure: LumenCrashConfigBuilder.() -> Unit = {},
    ): Boolean {
        return runCatching {
            install(application, configure)
            true
        }.getOrDefault(false)
    }

    fun installSafely(
        application: Application,
        config: LumenCrashConfig,
    ): Boolean {
        return runCatching {
            install(application, config)
            true
        }.getOrDefault(false)
    }

    fun isInstalled(): Boolean = installedConfig.get() != null && handlerRef.get() != null

    fun configOrNull(): LumenCrashConfig? = installedConfig.get()

    fun store(): CrashReportStore {
        return storeRef.get()
            ?: throw IllegalStateException("LumenCrash.install() must be called before accessing the store.")
    }

    fun recordBreadcrumb(event: String) {
        CrashBreadcrumbs.record(event)
    }

    fun record(throwable: Throwable): CrashReport {
        AuthorIntegrity.verifyOrThrow("record")
        val config = installedConfig.get()
            ?: throw IllegalStateException("LumenCrash.install() must be called before record().")
        val appInfo = config.toAppInfo()
        CrashBreadcrumbs.record("Crash captured: ${throwable::class.java.name}")
        val report = buildReport(throwable, appInfo)
            ?: throw IllegalStateException("Unable to build a crash report for ${throwable::class.java.name}")
        saveReport(report, config)
        return report
    }

    /**
     * Records a throwable the host already caught and recovered from.
     *
     * Unlike [record], the report never claims the pending-report slot, so it neither blocks the
     * next launch with the crash screen nor hides a real crash that is still waiting to be shown.
     * The report is still uploaded to the backend and returned for host-side diagnostics.
     */
    fun recordNonFatal(throwable: Throwable): CrashReport {
        AuthorIntegrity.verifyOrThrow("record-non-fatal")
        val config = installedConfig.get()
            ?: throw IllegalStateException("LumenCrash.install() must be called before recordNonFatal().")
        val appInfo = config.toAppInfo()
        CrashBreadcrumbs.record("Handled failure captured: ${throwable::class.java.name}")
        val report = buildReport(throwable, appInfo, CrashReportKind.NON_FATAL)
            ?: throw IllegalStateException("Unable to build a crash report for ${throwable::class.java.name}")
        submitBackendUpload(report, config)
        return report
    }

    /**
     * Builds a report, falling back to the degraded constructor and finally to null.
     *
     * The fallback allocates a second stack-trace string, so on an OOM crash it can fail as
     * well; returning null keeps that failure from escaping the caller (in the uncaught handler
     * it would skip the chained system handler and erase the crash from platform statistics).
     */
    private fun buildReport(
        throwable: Throwable,
        appInfo: CrashAppInfo,
        kind: CrashReportKind = CrashReportKind.CRASH,
    ): CrashReport? {
        return runCatching { CrashReport.fromThrowable(throwable, appInfo, kind) }
            .getOrElse { failure ->
                runCatching {
                    CrashReport.fromThrowableFallback(throwable, failure, appInfo, kind)
                }.getOrNull()
            }
    }

    /**
     * Marks the first usable host frame as rendered and stops the optional startup timer.
     * Hosts that enable [LumenCrashConfig.startupHangWatchdogEnabled] should call this from
     * their first-frame callback, not from an early Application lifecycle method.
     */
    fun markStartupComplete() {
        watchdogRef.get()?.markStartupComplete()
    }

    /** Stops background detection. The uncaught exception handler remains installed. */
    fun stopWatchdog() {
        watchdogRef.getAndSet(null)?.stop()
    }

    fun loadPendingReport(): CrashReport? {
        AuthorIntegrity.verifyOrThrow("load-pending")
        val report = startupCrashReport ?: runCatching { store().load() }.getOrNull()
        if (report != null) {
            val config = installedConfig.get()
            if (config != null) {
                submitBackendUpload(report, config)
            }
        }
        return report
    }

    /**
     * Host-safe pending-report load. Returns null when install/integrity fails.
     */
    fun loadPendingReportSafely(): CrashReport? {
        return runCatching { loadPendingReport() }.getOrNull()
    }

    fun clearPendingReport() {
        runCatching { store().clear() }
        clearStartupCrashReport()
    }

    fun clearStartupCrashReport() {
        startupCrashReport = null
    }

    private fun installUncaughtExceptionHandler(application: Application) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val existing = handlerRef.get()
        if (existing != null && previousHandler === existing) return

        val handler = Thread.UncaughtExceptionHandler { thread, throwable ->
            // Every step before the chain hand-off is best-effort: if report building or
            // persistence throws (an OOM crash re-throwing inside the fallback is the realistic
            // case), the system handler must still run or the crash disappears from platform
            // statistics entirely.
            runCatching {
                val config = installedConfig.get()
                val appInfo = config?.toAppInfo() ?: CrashAppInfo(
                    appDisplayName = application.packageName,
                    versionName = "unknown",
                    versionCode = 0,
                    commitHash = "unknown",
                )
                val report = buildReport(throwable, appInfo)
                if (report != null) {
                    if (config != null) {
                        saveReport(report, config, awaitUploadMillis = CRASH_UPLOAD_AWAIT_MILLIS)
                    } else {
                        startupCrashReport = report
                        runCatching { CrashReportStore(application.applicationContext).save(report) }
                    }
                }
            }
            val chained = previousHandler
            if (chained != null && chained !== handlerRef.get()) {
                chained.uncaughtException(thread, throwable)
            } else if (installedConfig.get()?.killProcessWhenNoPreviousHandler != false) {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
        handlerRef.set(handler)
        Thread.setDefaultUncaughtExceptionHandler(handler)
        CrashBreadcrumbs.record("Crash reporter installed")
    }

    private fun restartWatchdog(application: Application, config: LumenCrashConfig) {
        watchdogRef.getAndSet(null)?.stop()
        if (!config.anrWatchdogEnabled && !config.startupHangWatchdogEnabled) return

        val watchdog = LumenCrashWatchdog(application, config) { kind, durationMillis, threadDump ->
            recordWatchdogReport(kind, durationMillis, threadDump)
        }
        watchdogRef.set(watchdog)
        watchdog.start()
    }

    private fun recordWatchdogReport(
        kind: CrashReportKind,
        durationMillis: Long,
        threadDump: String,
    ) {
        val config = installedConfig.get() ?: return
        val report = runCatching {
            CrashReport.fromWatchdog(
                kind = kind,
                durationMillis = durationMillis,
                mainThread = android.os.Looper.getMainLooper().thread,
                threadDump = threadDump,
                appInfo = config.toAppInfo(),
            )
        }.getOrNull() ?: return
        // A freeze is a recovered stall, so it must not claim the single pending-report slot:
        // that would both overwrite a real crash nobody has seen yet and block the next launch
        // (or the next rotation) with the crash screen.
        if (kind == CrashReportKind.FREEZE || pendingReportIsRealCrash()) {
            runCatching { config.onAnrDetected?.invoke(report) }
            submitBackendUpload(report, config)
            return
        }
        saveReport(report, config)
    }

    private fun pendingReportIsRealCrash(): Boolean {
        if (startupCrashReport?.kind == CrashReportKind.CRASH) return true
        return runCatching { store().load()?.kind == CrashReportKind.CRASH }.getOrDefault(false)
    }

    private fun collectPriorExitReport(application: Application, config: LumenCrashConfig) {
        if (!config.priorExitCaptureEnabled) return
        if (!PriorExitCrashCollector.isSupported()) return
        val report = runCatching {
            PriorExitCrashCollector(application.applicationContext) { config.toAppInfo() }.collect()
        }.getOrNull() ?: return
        // Never clobber a pending real crash/watchdog report with a derived PRIOR_EXIT report.
        if (runCatching { store().load() != null }.getOrDefault(false)) return
        saveReport(report, config)
    }

    private fun saveReport(
        report: CrashReport,
        config: LumenCrashConfig,
        awaitUploadMillis: Long = 0L,
    ) {
        startupCrashReport = report
        runCatching { store().save(report) }
            .onSuccess {
                // Keep the legacy callback useful for hosts that already enqueue every
                // persisted diagnostic report through onCrashSaved.
                runCatching { config.onCrashSaved?.invoke(report) }
                runCatching { config.onReportSaved?.invoke(report) }
                if (report.kind != CrashReportKind.CRASH) {
                    runCatching { config.onAnrDetected?.invoke(report) }
                }
            }
        // Unconditional backend upload fires after persistence regardless of whether
        // the store save succeeded (best-effort).
        val upload = submitBackendUpload(report, config, dyingProcess = awaitUploadMillis > 0L)
        if (awaitUploadMillis > 0L && upload != null) {
            // The chained system handler kills the process as soon as this returns, so give the
            // POST a bounded chance to land instead of losing it with the daemon thread.
            runCatching { upload.get(awaitUploadMillis, TimeUnit.MILLISECONDS) }
        }
    }

    /**
     * Returns the shared background executor, creating it on first access.
     *
     * The queue is bounded and drops its oldest entry under pressure: a service loop calling
     * [recordNonFatal] can enqueue faster than a single upload thread (with network timeouts)
     * drains, and an unbounded queue would grow for the lifetime of the process.
     */
    private fun executor(): ExecutorService {
        val existing = uploadExecutor
        if (existing != null) return existing
        return synchronized(this) {
            uploadExecutor ?: ThreadPoolExecutor(
                1,
                1,
                UPLOAD_THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                ArrayBlockingQueue<Runnable>(MAX_QUEUED_UPLOADS),
                { runnable -> Thread(runnable, "lumen-crash-backend-upload").apply { isDaemon = true } },
                ThreadPoolExecutor.DiscardOldestPolicy(),
            ).apply { allowCoreThreadTimeOut(true) }.also { uploadExecutor = it }
        }
    }

    /**
     * Submits a background upload of [report] to the crash-report backend.
     *
     * The SDK uploads by default even without a configured token or device-ID
     * provider: the report is sent anonymously and tagged with the SDK's own
     * persistent per-install device ID. Guards against duplicate submissions
     * within one process by tracking [reportId] in [uploadedReportIds]; the guard
     * is released again when the backend reports a retryable outcome, so a
     * throttled or offline report is not lost for the rest of the process. All
     * failures are silently caught so the caller (crash handler / cold-start
     * loader) is never disrupted.
     *
     * [dyingProcess] shortens the network timeouts: a process that is about to be killed by the
     * system handler cannot wait 15 s for a connection.
     */
    private fun submitBackendUpload(
        report: CrashReport,
        config: LumenCrashConfig,
        dyingProcess: Boolean = false,
    ): Future<*>? {
        // Master switch.
        if (!config.crashReportBackendEnabled) return null
        // Client-side throttle so a failing service loop cannot saturate the upload queue.
        if (report.kind == CrashReportKind.NON_FATAL && !allowNonFatalUpload()) return null
        // Deduplicate: only submit once per process, with a bound on the tracked ids.
        if (uploadedReportIds.size >= MAX_TRACKED_REPORT_IDS) {
            uploadedReportIds.clear()
        }
        if (!uploadedReportIds.add(report.reportId)) return null
        // Package name must be known.
        val pkg = packageName.takeIf { it.isNotBlank() } ?: return null
        val context = appContext ?: return null
        val connectTimeoutMillis = if (dyingProcess) DYING_UPLOAD_TIMEOUT_MILLIS else UPLOAD_CONNECT_TIMEOUT_MILLIS
        val readTimeoutMillis = if (dyingProcess) DYING_UPLOAD_TIMEOUT_MILLIS else UPLOAD_READ_TIMEOUT_MILLIS

        return runCatching {
            executor().submit {
                val outcome = runCatching {
                    val deviceId = config.deviceInstallationIdProvider?.invoke()
                        ?.takeIf { it.isNotBlank() }
                        ?: CrashDeviceId.resolve(context)
                    CrashReportBackendUploader.upload(
                        report = report,
                        deviceInstallationId = deviceId,
                        packageName = pkg,
                        versionCode = config.versionCode,
                        accessToken = config.crashReportAccessToken,
                        baseUrl = config.crashReportBackendBaseUrl,
                        connectTimeoutMillis = connectTimeoutMillis,
                        readTimeoutMillis = readTimeoutMillis,
                    )
                }.getOrDefault(CrashUploadOutcome.RETRYABLE)
                if (outcome == CrashUploadOutcome.RETRYABLE) {
                    // Quota, server-side failure, or no network: release the in-process guard so
                    // the next loadPendingReport() can submit the same report again.
                    uploadedReportIds.remove(report.reportId)
                }
            }
        }.getOrNull()
    }

    private fun allowNonFatalUpload(): Boolean {
        val now = SystemClock.elapsedRealtime()
        while (true) {
            val last = lastNonFatalUploadAtMillis.get()
            if (last != 0L && now - last < NON_FATAL_UPLOAD_MIN_INTERVAL_MILLIS) return false
            if (lastNonFatalUploadAtMillis.compareAndSet(last, now)) return true
        }
    }

    private fun LumenCrashConfig.toAppInfo(): CrashAppInfo = CrashAppInfo(
        appDisplayName = appDisplayName,
        versionName = versionName,
        versionCode = versionCode,
        commitHash = commitHash,
    )

    private const val CRASH_UPLOAD_AWAIT_MILLIS = 1_500L
    private const val DYING_UPLOAD_TIMEOUT_MILLIS = 4_000
    private const val UPLOAD_CONNECT_TIMEOUT_MILLIS = 15_000
    private const val UPLOAD_READ_TIMEOUT_MILLIS = 30_000
    private const val UPLOAD_THREAD_KEEP_ALIVE_SECONDS = 30L
    private const val MAX_QUEUED_UPLOADS = 32
    private const val MAX_TRACKED_REPORT_IDS = 256
    private const val NON_FATAL_UPLOAD_MIN_INTERVAL_MILLIS = 60_000L
}
