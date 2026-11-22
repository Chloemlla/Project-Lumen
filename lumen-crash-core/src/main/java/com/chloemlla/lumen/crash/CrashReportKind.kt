package com.chloemlla.lumen.crash

/**
 * Describes how a report was produced.
 *
 * The wire values are intentionally stable because reports can survive an SDK upgrade.
 */
enum class CrashReportKind(val wireValue: String) {
    CRASH("crash"),
    ANR("anr"),
    STARTUP_HANG("startup_hang"),
    FREEZE("freeze"),
    PRIOR_EXIT("prior_exit"),

    /** A throwable the host caught and recovered from; the process kept running. */
    NON_FATAL("non_fatal"),

    /**
     * A failure the host reported itself: a JS `window.onerror`, a Rust panic hook line, a
     * Flutter `FlutterError`, or a file dropped into [CrashExternalInbox].
     *
     * The SDK did not observe the death, so the report carries whatever the host supplied and
     * attributes it through [CrashReport.component].
     */
    EXTERNAL("external");

    companion object {
        fun fromWireValue(value: String?): CrashReportKind {
            return values().firstOrNull { it.wireValue == value } ?: CRASH
        }
    }
}

/** True for reports the watchdog produced rather than an uncaught throwable. */
internal fun CrashReportKind.isWatchdogKind(): Boolean =
    this == CrashReportKind.ANR ||
        this == CrashReportKind.STARTUP_HANG ||
        this == CrashReportKind.FREEZE
