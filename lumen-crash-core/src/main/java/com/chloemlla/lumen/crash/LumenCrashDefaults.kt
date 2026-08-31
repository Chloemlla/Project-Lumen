package com.chloemlla.lumen.crash

/**
 * Default wiring used by convenience install / file-share paths.
 *
 * Hosts can override these values through [LumenCrashConfig], but the short integration
 * path should not require hand-written authorities or metadata.
 */
object LumenCrashDefaults {
    const val FILE_PROVIDER_AUTHORITY_SUFFIX: String = ".lumen.crash.fileprovider"
    const val SHARE_DIRECTORY_NAME: String = "lumen-crash-share"

    /** Default HTTPS base URL for the Lumen crash-report backend. */
    const val DEFAULT_CRASH_BACKEND_BASE_URL: String = "https://tts.chloemlla.com"

    /** Default path of the anonymous crash-report upload endpoint. */
    const val DEFAULT_CRASH_BACKEND_ENDPOINT_PATH: String = "/api/crash-sdk/v1/crash-report"

    /** Version of the ingest payload shape, so the backend can branch on old installs. */
    const val INGEST_SCHEMA_VERSION: Int = 1

    /** SDK version reported alongside every uploaded report; mirrors `lumen-crash/sdk.version`. */
    const val SDK_VERSION: String = "0.1.0"

    fun fileProviderAuthority(packageName: String): String {
        return packageName + FILE_PROVIDER_AUTHORITY_SUFFIX
    }
}
