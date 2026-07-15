package com.chloemlla.lumen.crash

import android.app.Application
import android.os.Process
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.exitProcess

/**
 * Public entry for the Lumen Crash SDK.
 */
object LumenCrash {
    private val installedConfig = AtomicReference<LumenCrashConfig?>(null)
    private val storeRef = AtomicReference<CrashReportStore?>(null)
    private val handlerRef = AtomicReference<Thread.UncaughtExceptionHandler?>(null)

    @Volatile
    var startupCrashReport: CrashReport? = null
        private set

    fun install(application: Application, config: LumenCrashConfig) {
        AuthorIntegrity.verifyOrThrow("install")
        installedConfig.set(config)
        storeRef.set(CrashReportStore(application.applicationContext))
        installUncaughtExceptionHandler(application)
        CrashBreadcrumbs.record("LumenCrash installed")
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
        val report = runCatching { CrashReport.fromThrowable(throwable, appInfo) }
            .getOrElse { CrashReport.fromThrowableFallback(throwable, it, appInfo) }
        startupCrashReport = report
        runCatching { store().save(report) }
            .onSuccess { config.onCrashSaved?.invoke(report) }
        return report
    }

    fun loadPendingReport(): CrashReport? {
        AuthorIntegrity.verifyOrThrow("load-pending")
        return startupCrashReport ?: runCatching { store().load() }.getOrNull()
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
            val config = installedConfig.get()
            val appInfo = config?.toAppInfo() ?: CrashAppInfo(
                appDisplayName = application.packageName,
                versionName = "unknown",
                versionCode = 0,
                commitHash = "unknown",
            )
            val report = runCatching { CrashReport.fromThrowable(throwable, appInfo) }
                .getOrElse { CrashReport.fromThrowableFallback(throwable, it, appInfo) }
            startupCrashReport = report
            runCatching { CrashReportStore(application.applicationContext).save(report) }
            runCatching { config?.onCrashSaved?.invoke(report) }
            val chained = previousHandler
            if (chained != null && chained !== handlerRef.get()) {
                chained.uncaughtException(thread, throwable)
            } else if (config?.killProcessWhenNoPreviousHandler != false) {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
        handlerRef.set(handler)
        Thread.setDefaultUncaughtExceptionHandler(handler)
        CrashBreadcrumbs.record("Crash reporter installed")
    }

    private fun LumenCrashConfig.toAppInfo(): CrashAppInfo = CrashAppInfo(
        appDisplayName = appDisplayName,
        versionName = versionName,
        versionCode = versionCode,
        commitHash = commitHash,
    )
}
