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
            "lumen_security_sockets.cpp",
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

    @Test
    fun managedRejectionsPrecedeNativeIdentityEstablishment() {
        val repositoryRoot = findRepositoryRoot()
        val guard = File(
            repositoryRoot,
            "app/src/main/java/com/projectlumen/app/core/security/AppIntegrityGuard.kt",
        ).readText()
        val bridge = File(
            repositoryRoot,
            "app/src/main/java/com/projectlumen/app/core/security/NativeSecurityBridge.kt",
        ).readText()
        val enforce = guard.substringBefore("fun nativeProtectionSummary")

        assertTrue(enforce.indexOf("managedIntegrityFailureReasons") >= 0)
        assertTrue(enforce.indexOf("NativeSecurityBridge.invalidateVerifiedIdentity()") >= 0)
        assertTrue(
            "Managed checks must happen before native evaluation",
            enforce.indexOf("managedFailureReasons") <
                enforce.indexOf("NativeSecurityBridge.evaluateEnvironmentOrNull"),
        )
        assertTrue(enforce.contains("establishReleaseIdentity = true"))
        assertTrue(
            "Diagnostics must not establish release identity",
            guard.substringAfter("fun nativeProtectionSummary")
                .contains("establishReleaseIdentity = false"),
        )
        assertTrue(bridge.contains("managedReleaseIdentityVerified = false"))
        assertTrue(bridge.contains("RELEASE_IDENTITY_NOT_VERIFIED.bit"))
    }

    @Test
    fun unixSocketScanIsBoundToSelfFdInodes() {
        val repositoryRoot = findRepositoryRoot()
        val runtime = File(
            repositoryRoot,
            "app/src/main/cpp/lumen_security_runtime.cpp",
        ).readText()
        val sockets = File(
            repositoryRoot,
            "app/src/main/cpp/lumen_security_sockets.cpp",
        ).readText()

        assertFalse(runtime.contains("scan_text_file(\"/proc/net/unix\""))
        assertTrue(runtime.contains("socket_inode_from_fd_target"))
        assertTrue(sockets.contains("unix_socket_line_is_owned"))
        assertTrue(sockets.contains("/proc/net/unix"))
    }

    @Test
    fun releaseSigningSecretCannotUseWhitespaceOrDevelopmentFallbacks() {
        val repositoryRoot = findRepositoryRoot()
        val gradleBuild = File(repositoryRoot, "app/build.gradle.kts").readText()
        val nativeEntry = File(repositoryRoot, "app/src/main/cpp/lumen_security.cpp").readText()
        val nativeMode = File(repositoryRoot, "app/src/main/cpp/lumen_security_mode.h").readText()
        val cmake = File(repositoryRoot, "app/src/main/cpp/CMakeLists.txt").readText()
        val secretValidationAction = File(
            repositoryRoot,
            ".github/actions/validate-release-request-signing-secret/action.yml",
        ).readText()
        val buildWorkflow = File(repositoryRoot, ".github/workflows/build.yml").readText()
        val releaseWorkflow = File(repositoryRoot, ".github/workflows/release.yml").readText()

        assertTrue(gradleBuild.contains("projectLumenConfiguredRequestSigningSecret.orEmpty()"))
        assertTrue(gradleBuild.contains("projectLumenTaskRequiresReleaseNativeSecurity"))
        assertTrue(gradleBuild.contains("leaf in setOf(\"assemble\", \"build\", \"bundle\")"))
        assertTrue(gradleBuild.contains("normalized.startsWith(\":app:\")"))
        assertTrue(gradleBuild.contains("projectLumenNativeReleaseBuild"))
        assertTrue(gradleBuild.contains("projectLumenNativeDebugBuild = \"0\""))
        assertTrue(gradleBuild.contains("-DLUMEN_NATIVE_RELEASE_BUILD=\$projectLumenNativeReleaseBuild"))
        assertTrue(gradleBuild.contains("-DLUMEN_NATIVE_RELEASE_BUILD=\$projectLumenNativeDebugBuild"))
        assertTrue(gradleBuild.contains("must not contain leading or trailing whitespace"))
        assertTrue(gradleBuild.contains("Release builds cannot use the local request-signing"))
        assertFalse(gradleBuild.contains("projectLumenRequestSigningSecret.trim()"))
        assertTrue(nativeEntry.contains("#define LUMEN_REQUEST_SIGNING_SECRET_HEX \"\""))
        assertTrue(nativeMode.contains("#define LUMEN_NATIVE_RELEASE_BUILD 1"))
        assertTrue(nativeMode.contains("effective_debug_allowed"))
        assertTrue(nativeMode.contains("!kNativeReleaseBuild"))
        assertTrue(nativeEntry.contains("effective_debug_allowed"))
        assertTrue(cmake.contains("LUMEN_NATIVE_RELEASE_BUILD=\${LUMEN_NATIVE_RELEASE_BUILD}"))
        assertTrue(cmake.contains("set(LUMEN_NATIVE_RELEASE_BUILD 1)"))
        assertTrue(secretValidationAction.contains("TRIMMED_REQUEST_SIGNING_SECRET"))
        assertTrue(secretValidationAction.contains("project-lumen-local-request-signing-key"))
        assertTrue(buildWorkflow.contains("validate-release-request-signing-secret"))
        assertTrue(releaseWorkflow.contains("validate-release-request-signing-secret"))
        assertTrue(
            buildWorkflow.indexOf("validate-release-request-signing-secret") <
                buildWorkflow.indexOf("Set up Java 21"),
        )
    }

    @Test
    fun aggregateReleaseGateKeepsDebugOnlyTasksOut() {
        val repositoryRoot = findRepositoryRoot()
        val gradleBuild = File(repositoryRoot, "app/build.gradle.kts").readText()

        assertTrue(gradleBuild.contains("if (leaf in setOf(\"assemble\", \"build\", \"bundle\")) return true"))
        assertTrue(gradleBuild.contains("return leaf.contains(\"release\")"))
        assertTrue(gradleBuild.contains("normalized.startsWith(\":app:\")"))
        assertTrue(gradleBuild.contains("taskName.trim().lowercase()"))
        assertFalse(gradleBuild.contains("leaf.contains(\"debug\")"))
        assertTrue(gradleBuild.contains("debug {"))
        assertTrue(gradleBuild.contains("release {"))
    }

    @Test
    fun updateCheckerRejectsDevicesWithoutAReleaseAbi() {
        val repositoryRoot = findRepositoryRoot()
        val checker = File(
            repositoryRoot,
            "app/src/main/java/com/projectlumen/app/core/update/UpdateChecker.kt",
        ).readText()
        val selector = File(
            repositoryRoot,
            "app/src/main/java/com/projectlumen/app/core/update/ReleaseAssetSelector.kt",
        ).readText()

        assertTrue(checker.contains("if (!isSupportedReleaseDevice(Build.SUPPORTED_ABIS.toList())) return null"))
        assertTrue(checker.contains("firstSupportedReleaseAbi(Build.SUPPORTED_ABIS.toList())"))
        assertTrue(selector.contains("arm64_v8a"))
        assertTrue(selector.contains("x86_64"))
        assertTrue(selector.contains("if (preferredAbis.isEmpty()) return null"))
        assertTrue(selector.contains("hasExplicitUnsupportedAbiToken"))
        assertTrue(selector.contains("explicitUnsupportedAbiPattern"))
    }

    @Test
    fun releasePackagingStaysOnVerifiedSixteenKilobyteAbis() {
        val repositoryRoot = findRepositoryRoot()
        val gradleBuild = File(repositoryRoot, "app/build.gradle.kts").readText()
        val buildWorkflow = File(repositoryRoot, ".github/workflows/build.yml").readText()
        val releaseWorkflow = File(repositoryRoot, ".github/workflows/release.yml").readText()
        val selector = File(
            repositoryRoot,
            "app/src/main/java/com/projectlumen/app/core/update/ReleaseAssetSelector.kt",
        ).readText()

        assertTrue(gradleBuild.contains("abiFilters += listOf(\"arm64-v8a\", \"x86_64\")"))
        assertTrue(gradleBuild.contains("include(\"arm64-v8a\", \"x86_64\")"))
        assertTrue(buildWorkflow.contains("for variant in arm64-v8a x86_64"))
        assertTrue(releaseWorkflow.contains("for variant in arm64-v8a x86_64"))
        assertTrue(selector.contains("setOf(\"arm64_v8a\", \"x86_64\")"))
        assertFalse(gradleBuild.contains("include(\"armeabi-v7a\""))
        assertFalse(buildWorkflow.contains("for variant in armeabi-v7a"))
        assertFalse(releaseWorkflow.contains("for variant in armeabi-v7a"))
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
