package com.chloemlla.lumen.crash

/**
 * Non-overridable author attribution baked into the SDK.
 * Host apps cannot replace these values through [LumenCrashConfig].
 */
object CrashAuthorAttribution {
    const val AUTHOR_NAME: String = "ChloeMlla"
    const val AUTHOR_URL: String = "https://github.com/Chloemlla/"
    const val AUTHOR_HANDLE: String = "chloemlla"

    /** SHA-256 of `AUTHOR_NAME|AUTHOR_URL` as lowercase hex. */
    const val FINGERPRINT_HEX: String =
        "c6485425f032c152fa08a8695ca7a4da18f4f3db9c5f6b380e57cf7c12285d30"

    const val FOOTER_LABEL: String = "Crash SDK by ChloeMlla · https://github.com/Chloemlla/"

    fun payload(): String = "$AUTHOR_NAME|$AUTHOR_URL"
}
