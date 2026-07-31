package com.projectlumen.app.core.security

internal enum class NativeSecurityReason(
    val bit: Int,
    val diagnosticCode: String,
) {
    PACKAGE_MISMATCH(1 shl 0, "package_mismatch"),
    PROCESS_NAME_MISMATCH(1 shl 1, "process_name_mismatch"),
    CERTIFICATE_MISSING(1 shl 2, "certificate_missing"),
    CERTIFICATE_MISMATCH(1 shl 3, "certificate_mismatch"),
    TRACER_DETECTED(1 shl 4, "tracer_detected"),
    SUSPICIOUS_ENVIRONMENT(1 shl 5, "suspicious_environment"),
    HOOK_ARTIFACT_DETECTED(1 shl 6, "hook_artifact_detected"),
    RELEASE_IDENTITY_NOT_VERIFIED(1 shl 7, "release_identity_not_verified"),
    SIGNING_SECRET_INVALID(1 shl 8, "signing_secret_invalid"),
    INTERNAL_FAILURE(1 shl 9, "internal_failure"),
}

internal data class NativeSecurityVerdict(
    val mask: Int,
) {
    val isAllowed: Boolean get() = mask == 0

    val reasons: List<NativeSecurityReason>
        get() = NativeSecurityReason.entries.filter { reason -> mask and reason.bit != 0 }

    val unknownMask: Int
        get() = mask and knownMask.inv()

    fun diagnosticCodes(): List<String> = buildList {
        addAll(reasons.map { reason -> reason.diagnosticCode })
        if (unknownMask != 0) add("unknown_0x${unknownMask.toUInt().toString(16)}")
    }

    fun diagnosticSummary(): String {
        return diagnosticCodes().joinToString(separator = "|").ifBlank { "none" }
    }

    private companion object {
        val knownMask = NativeSecurityReason.entries.fold(0) { mask, reason -> mask or reason.bit }
    }
}
