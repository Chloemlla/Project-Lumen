package com.projectlumen.app.core.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The native hooking-artifact scan reads `/proc/self/cmdline`, which on Android holds the
 * process name. CRooot's probe process is named `:zygisk_fd_detector`, so running the gate
 * there makes the app accuse itself of being injected.
 */
class AppIntegrityGuardScopeTest {
    @Test
    fun coldStartEnforcementIsScopedToTheMainProcess() {
        val guard = source("core/security/AppIntegrityGuard.kt").readText()
        assertTrue("AppIntegrityGuard must expose enforce()", guard.contains(ENFORCE_ANCHOR))
        val enforceBody = guard.substringAfter(ENFORCE_ANCHOR)
            .substringBefore("\n    fun ")

        val processGuardIndex = enforceBody.indexOf("getProcessName()")
        val nativeCheckIndex = enforceBody.indexOf("NativeSecurityBridge.isNativeEnvironmentAllowed")
        assertTrue("enforce() must inspect the current process name", processGuardIndex >= 0)
        assertTrue(
            "enforce() must compare the process name against the package name",
            enforceBody.contains("packageName"),
        )
        assertTrue("enforce() must bail out of auxiliary processes", enforceBody.contains("return"))
        if (nativeCheckIndex >= 0) {
            assertTrue(
                "The process check must run before any native environment probe",
                processGuardIndex < nativeCheckIndex,
            )
        }
    }

    private fun source(relativePath: String): File {
        val file = File(findAppSourceRoot(), relativePath)
        assertTrue("Missing production source: $relativePath", file.isFile)
        return file
    }

    private fun findAppSourceRoot(): File {
        val workingDirectory = File(System.getProperty("user.dir")).absoluteFile
        return generateSequence(workingDirectory) { it.parentFile }
            .flatMap { directory ->
                sequenceOf(
                    File(directory, "app/src/main/java/com/projectlumen/app"),
                    File(directory, "src/main/java/com/projectlumen/app"),
                )
            }
            .firstOrNull { it.isDirectory }
            ?: error("Unable to locate Project Lumen Android source root from $workingDirectory")
    }

    private companion object {
        const val ENFORCE_ANCHOR = "fun enforce("
    }
}
