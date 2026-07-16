package com.chloemlla.lumen.crash

/**
 * Non-overridable author attribution baked into the SDK.
 * Host apps cannot replace these values through [LumenCrashConfig].
 */
object CrashAuthorAttribution {
    const val AUTHOR_NAME: String = "Chloemlla"
    const val AUTHOR_URL: String = "https://github.com/Chloemlla/"
    const val AUTHOR_HANDLE: String = "chloemlla"

    /** SHA-256 of `AUTHOR_NAME|AUTHOR_URL` as lowercase hex. */
    const val FINGERPRINT_HEX: String =
        "94796096a87a0f85807aed69ed465dc51f07804eb90fa8964a11f54990896145"

    const val FOOTER_LABEL: String = "Crash SDK by Chloemlla · https://github.com/Chloemlla/"

    fun payload(): String = "$AUTHOR_NAME|$AUTHOR_URL"
}
