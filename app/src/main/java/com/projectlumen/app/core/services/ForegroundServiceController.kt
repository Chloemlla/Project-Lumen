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
 * by catching [ForegroundServiceStartNotAllowedException] and re-posting the start once after a
 * short delay, without blocking the calling thread.
 */
internal object ForegroundServiceController {
    /**
     * Attempts to start a foreground service.
     *
     * On Android 12+, if the start fails because the platform refuses a background start, the
     * start is re-posted once after [RETRY_DELAY_MILLIS] and this method returns true, meaning
     * "accepted", not "already running".
     *
     * @return true if the service was started or a retry was queued, false otherwise.
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
            // Android 16: mAllowStartForeground may be false during cold start.
            // Re-post once instead of sleeping; callers can be on the main thread.
            if (isForegroundServiceStartNotAllowed(exception)) {
                Log.i(TAG, "Foreground service start refused (mAllowStartForeground false); " +
                    "retrying ${serviceName} in ${RETRY_DELAY_MILLIS}ms")
                scheduleStartRetry(
                    context = context.applicationContext,
                    intent = intent,
                    serviceName = serviceName,
                    eligibilityCheck = eligibilityCheck,
                )
                true
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

    private fun scheduleStartRetry(
        context: Context,
        intent: Intent,
        serviceName: String,
        eligibilityCheck: (() -> Boolean)?,
    ) {
        retryHandler.postDelayed(
            {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (retryException: Exception) {
                    handleFailure(
                        context = context,
                        serviceName = serviceName,
                        operation = "start",
                        throwable = retryException,
                        becameIneligible = becameIneligible(eligibilityCheck),
                    )
                }
            },
            RETRY_DELAY_MILLIS,
        )
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

    // Lazy: touching Looper during class init would break plain JVM unit tests.
    private val retryHandler by lazy { Handler(Looper.getMainLooper()) }

    private const val TAG = "ForegroundService"
    private const val EXPECTED_REFUSAL_LOG_WINDOW_MILLIS = 30_000L
    private const val FOREGROUND_SERVICE_START_NOT_ALLOWED_EXCEPTION =
        "android.app.ForegroundServiceStartNotAllowedException"
    private const val RETRY_DELAY_MILLIS = 2_000L
}