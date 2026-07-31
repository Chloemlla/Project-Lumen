package com.projectlumen.app.core.security

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSecurityArchitectureTest {
    @Test
    fun rawSigningSecretCannotCrossTheManagedBridge() {
        val repositoryRoot = findRepositoryRoot()
        val bridge = File(
            repositoryRoot,
            "app/src/main/java/com/projectlumen/app/core/security/NativeSecurityBridge.kt",
        ).readText()
        val nativeEntry = File(repositoryRoot, "app/src/main/cpp/lumen_security.cpp").readText()

        assertFalse(bridge.contains("requestSigningSecret"))
        assertFalse(nativeEntry.contains("NativeSecurityBridge_requestSigningSecret"))
        assertTrue(bridge.contains("signCanonicalPayload"))
        assertTrue(nativeEntry.contains("NativeSecurityBridge_signCanonicalPayload"))
    }

    @Test
    fun nativeSourcesStaySplitByResponsibility() {
        val repositoryRoot = findRepositoryRoot()
        val cmake = File(repositoryRoot, "app/src/main/cpp/CMakeLists.txt").readText()

        listOf(
            "lumen_security.cpp",
            "lumen_security_crypto.cpp",
            "lumen_security_identity.cpp",
            "lumen_security_runtime.cpp",
        ).forEach { source -> assertTrue("CMake is missing $source", cmake.contains(source)) }
    }

    @Test
    fun nativeAndKotlinReasonBitContractsStayAligned() {
        val repositoryRoot = findRepositoryRoot()
        val nativeReasons = File(
            repositoryRoot,
            "app/src/main/cpp/lumen_security_reasons.h",
        ).readText()
        val kotlinReasons = File(
            repositoryRoot,
            "app/src/main/java/com/projectlumen/app/core/security/NativeSecurityVerdict.kt",
        ).readText()

        reasonContracts.forEach { (nativeName, kotlinDeclaration) ->
            assertTrue("Native reason is missing: $nativeName", nativeReasons.contains(nativeName))
            assertTrue(
                "Kotlin reason is missing: $kotlinDeclaration",
                kotlinReasons.contains(kotlinDeclaration),
            )
        }
    }

    private fun findRepositoryRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir")).absoluteFile
        return generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "app/src/main/cpp").isDirectory }
            ?: error("Unable to locate repository root from $workingDirectory")
    }

    private companion object {
        val reasonContracts = listOf(
            "kPackageMismatch = 1U << 0U" to "PACKAGE_MISMATCH(1 shl 0",
            "kProcessNameMismatch = 1U << 1U" to "PROCESS_NAME_MISMATCH(1 shl 1",
            "kCertificateMissing = 1U << 2U" to "CERTIFICATE_MISSING(1 shl 2",
            "kCertificateMismatch = 1U << 3U" to "CERTIFICATE_MISMATCH(1 shl 3",
            "kTracerDetected = 1U << 4U" to "TRACER_DETECTED(1 shl 4",
            "kSuspiciousEnvironment = 1U << 5U" to "SUSPICIOUS_ENVIRONMENT(1 shl 5",
            "kHookArtifactDetected = 1U << 6U" to "HOOK_ARTIFACT_DETECTED(1 shl 6",
            "kReleaseIdentityNotVerified = 1U << 7U" to "RELEASE_IDENTITY_NOT_VERIFIED(1 shl 7",
            "kSigningSecretInvalid = 1U << 8U" to "SIGNING_SECRET_INVALID(1 shl 8",
            "kInternalFailure = 1U << 9U" to "INTERNAL_FAILURE(1 shl 9",
        )
    }
}
