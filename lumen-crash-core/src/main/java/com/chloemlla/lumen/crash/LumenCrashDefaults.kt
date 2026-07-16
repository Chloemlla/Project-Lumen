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

    fun fileProviderAuthority(packageName: String): String {
        return packageName + FILE_PROVIDER_AUTHORITY_SUFFIX
    }
}
