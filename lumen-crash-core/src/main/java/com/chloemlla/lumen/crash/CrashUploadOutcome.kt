package com.chloemlla.lumen.crash

/**
 * Outcome of a single [CrashReportBackendUploader.upload] attempt.
 *
 * The crash backend answers with more than "worked / did not work": it enforces a per-device
 * hourly quota, de-duplicates by report id, and reports its own internal failures as
 * `HTTP 200 {"accepted": false}`. Collapsing all of that into a boolean made a throttled or
 * server-side-failed report indistinguishable from a stored one, so the SDK could never decide
 * whether another attempt was worth making.
 */
enum class CrashUploadOutcome {
    /** The backend stored the report, or already had it. Nothing left to do. */
    ACCEPTED,

    /** The backend refused this payload permanently (e.g. malformed request). Retrying cannot help. */
    REJECTED,

    /** Quota, server-side failure, or network error. The same report is worth uploading again later. */
    RETRYABLE,
    ;

    val accepted: Boolean
        get() = this == ACCEPTED
}
