package com.projectlumen.app.core.services

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.projectlumen.app.ProjectLumenApplication
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

fun interface ForegroundServiceFailureReporter {
    fun recordForegroundServiceFailure(throwable: Throwable)
}

/**
 * Centralizes foreground-service creation and promotion failure handling.
 *
 * Android 12+ background-start refusals are an expected scheduling outcome. They must not be
 * persisted as crashes, while manifest, permission, notification, and service implementation
 * failures still need crash telemetry.
 *
 * Android 16 (SDK 36) introduced stricter [mAllowStartForeground] gating: the flag is reset on
 * process start and takes a short window before it becomes true. The [start] method handles this
 * by catching [ForegroundServiceStartNotAllowedException] and retrying once after a short delay.
 */
internal object ForegroundServiceController {
    /**
     * Attempts to start a foreground service.
     *
     * On Android 16+, if the start fails with [ForegroundServiceStartNotAllowedException]
     * (mAllowStartForeground is false), the method retries once after [RETRY_DELAY_MILLIS].
     * This handles the cold-start window where the platform has not yet set the flag.
     *
     * @return true if the service was started successfully, false otherwise.
     */
    fun start(
        context: Context,
        intent: Intent,
        eligibilityCheck: (() -> Boolean)? = null,
    ): Boolean {
        val serviceName = intent.component?.className ?: intent.action.orEmpty().ifBlank { "unknown" }
        val securityGate = (context.applicationContext as? ProjectLumenApplication)?.deviceSecurityGate
        if (securityGate != null && !securityGate.isServiceAllowed()) {
            recordExpectedRefusal(serviceName, operation = "start", reason = "device_security_blocked")
            return false
        }
        val eligible = evaluateEligibility(
            context = context,
            serviceName = serviceName,
            operation = "start",
            eligibilityCheck = eligibilityCheck,
        ) ?: return false
        if (!eligible) {
            recordExpectedRefusal(serviceName, operation = "start", reason = "process_not_foreground")
            return false
        }

        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (exception: Exception) {
            // Android 16: mAllowStartForeground may be false during cold start. Retry once after a
            // short delay on the main handler instead of blocking the calling thread.
            if (isForegroundServiceStartNotAllowed(exception)) {
                Log.i(TAG, "Foreground service start refused (mAllowStartForeground false); " +
                    "scheduling retry of ${serviceName} in ${RETRY_DELAY_MILLIS}ms")
                mainHandler.postDelayed({
                    runCatching {
                        ContextCompat.startForegroundService(context, intent)
                    }.onFailure { retryException ->
                        handleFailure(
                            context = context,
                            serviceName = serviceName,
                            operation = "start",
                            throwable = retryException,
                            becameIneligible = becameIneligible(eligibilityCheck),
                        )
                    }
                }, RETRY_DELAY_MILLIS)
                false
            } else {
                handleFailure(
                    context = context,
                    serviceName = serviceName,
                    operation = "start",
                    throwable = exception,
                    becameIneligible = becameIneligible(eligibilityCheck),
                )
            }
        }
    }

    fun promote(
        service: Service,
        notificationId: Int,
        notificationProvider: () -> Notification,
        foregroundServiceType: Int,
        eligibilityCheck: (() -> Boolean)? = null,
    ): Boolean {
        val serviceName = service.javaClass.name
        val securityGate = (service.applicationContext as? ProjectLumenApplication)?.deviceSecurityGate
        if (securityGate != null && !securityGate.isServiceAllowed()) {
            recordExpectedRefusal(serviceName, operation = "promote", reason = "device_security_blocked")
            return false
        }
        val eligible = evaluateEligibility(
            context = service,
            serviceName = serviceName,
            operation = "promote",
            eligibilityCheck = eligibilityCheck,
        ) ?: return false
        if (!eligible) {
            recordExpectedRefusal(serviceName, operation = "promote", reason = "process_not_foreground")
            return false
        }

        val notification = try {
            notificationProvider()
        } catch (exception: Exception) {
            reportUnexpectedFailure(
                context = service,
                serviceName = serviceName,
                operation = "build foreground notification",
                throwable = exception,
            )
            return false
        }

        return try {
            ServiceCompat.startForeground(
                service,
                notificationId,
                notification,
                foregroundServiceType,
            )
            true
        } catch (exception: Exception) {
            handleFailure(
                context = service,
                serviceName = serviceName,
                operation = "promote",
                throwable = exception,
                becameIneligible = becameIneligible(eligibilityCheck),
            )
        }
    }

    internal fun isExpectedBackgroundStartRefusal(
        sdkInt: Int,
        throwableClassName: String,
        throwableMessage: String?,
    ): Boolean {
        if (sdkInt < Build.VERSION_CODES.S) return false
        return throwableClassName == FOREGROUND_SERVICE_START_NOT_ALLOWED_EXCEPTION ||
            (throwableClassName == IllegalStateException::class.java.name &&
                throwableMessage?.contains("startForegroundService() not allowed") == true)
    }

    internal fun shouldRecordFailure(
        sdkInt: Int,
        throwable: Throwable,
        becameIneligible: Boolean,
    ): Boolean {
        if (becameIneligible) return false
        return generateSequence(throwable) { it.cause }
            .none {
                isExpectedBackgroundStartRefusal(
                    sdkInt = sdkInt,
                    throwableClassName = it.javaClass.name,
                    throwableMessage = it.message,
                )
            }
    }

    /**
     * Checks if the throwable is a [ForegroundServiceStartNotAllowedException], either directly
     * or as a cause. Works across all Android versions where the exception class name differs.
     */
    private fun isForegroundServiceStartNotAllowed(throwable: Throwable): Boolean {
        return generateSequence(throwable) { it.cause }.any {
            isExpectedBackgroundStartRefusal(
                sdkInt = Build.VERSION.SDK_INT,
                throwableClassName = it.javaClass.name,
                throwableMessage = it.message,
            )
        }
    }

    private fun handleFailure(
        context: Context,
        serviceName: String,
        operation: String,
        throwable: Throwable,
        becameIneligible: Boolean,
    ): Boolean {
        if (shouldRecordFailure(Build.VERSION.SDK_INT, throwable, becameIneligible)) {
            reportUnexpectedFailure(context, serviceName, operation, throwable)
        } else {
            recordExpectedRefusal(serviceName, operation, reason = "platform_rejected_background_start")
        }
        return false
    }

    private fun evaluateEligibility(
        context: Context,
        serviceName: String,
        operation: String,
        eligibilityCheck: (() -> Boolean)?,
    ): Boolean? {
        if (eligibilityCheck == null) return true
        return try {
            eligibilityCheck()
        } catch (exception: Exception) {
            reportUnexpectedFailure(
                context = context,
                serviceName = serviceName,
                operation = "$operation eligibility check",
                throwable = exception,
            )
            null
        }
    }

    private fun becameIneligible(eligibilityCheck: (() -> Boolean)?): Boolean {
        if (eligibilityCheck == null) return false
        return try {
            !eligibilityCheck()
        } catch (exception: Exception) {
            Log.e(TAG, "Foreground service eligibility recheck failed", exception)
            false
        }
    }

    private fun reportUnexpectedFailure(
        context: Context,
        serviceName: String,
        operation: String,
        throwable: Throwable,
    ) {
        Log.e(TAG, "Foreground service $operation failed for $serviceName", throwable)
        runCatching {
            (context.applicationContext as? ForegroundServiceFailureReporter)
                ?.recordForegroundServiceFailure(throwable)
        }.onFailure { reportingFailure ->
            Log.e(TAG, "Foreground service failure reporting failed for $serviceName", reportingFailure)
        }
    }

    private fun recordExpectedRefusal(
        serviceName: String,
        operation: String,
        reason: String,
    ) {
        val refusalKey = "$operation:$serviceName:$reason"
        val nowElapsed = SystemClock.elapsedRealtime()
        val lastRecordedAt = expectedRefusalRecordedAt
            .computeIfAbsent(refusalKey) { AtomicLong(0L) }
            .getAndSet(nowElapsed)
        if (lastRecordedAt > 0L && nowElapsed - lastRecordedAt < EXPECTED_REFUSAL_LOG_WINDOW_MILLIS) {
            return
        }
        val message = "Foreground service $operation skipped service=$serviceName reason=$reason"
        Log.i(TAG, message)
        runCatching { CrashBreadcrumbs.record(message) }
    }

    private val expectedRefusalRecordedAt = ConcurrentHashMap<String, AtomicLong>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private const val TAG = "ForegroundService"
    private const val EXPECTED_REFUSAL_LOG_WINDOW_MILLIS = 30_000L
    private const val FOREGROUND_SERVICE_START_NOT_ALLOWED_EXCEPTION =
        "android.app.ForegroundServiceStartNotAllowedException"
    private const val RETRY_DELAY_MILLIS = 2_000L
}