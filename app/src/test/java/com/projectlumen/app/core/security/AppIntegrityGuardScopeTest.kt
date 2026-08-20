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
        val enforceBody = guard.substringAfter("fun enforce(context: Context) {")
            .substringBefore("\n    fun ")

        val processGuardIndex = enforceBody.indexOf("Application.getProcessName() != appContext.packageName")
        val nativeCheckIndex = enforceBody.indexOf("NativeSecurityBridge.isNativeEnvironmentAllowedOrNull")
        assertTrue("enforce() must compare the current process against the package name", processGuardIndex >= 0)
        assertTrue("enforce() must skip auxiliary processes", enforceBody.contains("return"))
        assertTrue(
            "The process check must run before any native environment probe",
            processGuardIndex in 0 until nativeCheckIndex,
        )
    }

    private fun source(relativePath: String): File = File(findAppSourceRoot(), relativePath)

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
}
