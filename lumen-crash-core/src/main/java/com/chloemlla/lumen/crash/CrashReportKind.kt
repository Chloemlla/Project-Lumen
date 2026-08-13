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
    PRIOR_EXIT("prior_exit");

    companion object {
        fun fromWireValue(value: String?): CrashReportKind {
            return values().firstOrNull { it.wireValue == value } ?: CRASH
        }
    }
}
