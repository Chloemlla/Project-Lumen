package com.projectlumen.app.core.work

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-wiring regressions for the WorkManager crash `0d56b7959042` ("WorkManager is not
 * initialized properly …"), where a process created before the user's first unlock skipped
 * `androidx.startup`'s initializer and the first `getInstance` threw into a coroutine.
 *
 * These assertions stay at the source level on purpose: the failure needs a device that boots the
 * app from a direct-boot broadcast, which no plain JVM test can reproduce.
 */
class WorkSchedulerContractTest {
    @Test
    fun applicationRepairsASkippedWorkManagerInitializer() {
        val source = applicationSource()
        assertTrue(
            "ProjectLumenApplication must implement Configuration.Provider so WorkManager can " +
                "initialize itself when its eager initializer never ran",
            source.contains("class ProjectLumenApplication : Application(), Configuration.Provider,"),
        )
        assertTrue(
            "The provider must expose the default WorkManager configuration",
            source.contains("override val workManagerConfiguration: Configuration by lazy"),
        )
        assertTrue(
            "The configuration must stay the default one the eager initializer would have used",
            source.contains("Configuration.Builder().build()"),
        )
    }

    @Test
    fun workManagerAccessStaysInsideTheSharedScheduler() {
        val sourceRoot = findAppSourceRoot()
        val violations = sourceRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .filter { file -> file.name != "WorkScheduler.kt" }
            .filter { file ->
                val source = file.readText()
                source.contains("WorkManager.getInstance(") || source.contains("WorkManager.initialize(")
            }
            .map { file -> file.relativeTo(sourceRoot).invariantSeparatorsPath }
            .toList()

        assertTrue(
            "Direct WorkManager access bypasses the shared scheduler and can crash a coroutine: " +
                violations,
            violations.isEmpty(),
        )
    }

    @Test
    fun schedulerRefusesLockedStorageBeforeTouchingWorkManager() {
        val body = schedulerSource().substringAfter("private fun workManager(context: Context)")
        val unlockGuard = body.indexOf("if (!isStorageUnlocked(appContext))")
        val workManagerAccess = body.indexOf("WorkManager.getInstance(appContext)")
        assertTrue("The scheduler must check storage availability", unlockGuard >= 0)
        assertTrue("The scheduler must reach WorkManager through getInstance", workManagerAccess >= 0)
        assertTrue(
            "Credential-encrypted storage must be readable before WorkManager's database is opened",
            unlockGuard < workManagerAccess,
        )
        assertTrue(
            "The unlocked check must be the platform's UserManager verdict",
            schedulerSource().contains("isUserUnlocked"),
        )
    }

    @Test
    fun everySchedulingCallSiteKeepsItsFailuresContained() {
        val sourceRoot = findAppSourceRoot()
        schedulingCallSites.forEach { relativePath ->
            val source = File(sourceRoot, relativePath).readText()
            assertTrue("$relativePath must enqueue through WorkScheduler", source.contains("WorkScheduler.enqueue("))
            assertTrue("$relativePath must cancel through WorkScheduler", source.contains("WorkScheduler.cancel("))
            assertFalse("$relativePath must not touch WorkManager directly", source.contains("WorkManager."))
        }
    }

    private fun applicationSource(): String {
        return File(findAppSourceRoot(), "ProjectLumenApplication.kt").readText().replace("\r\n", "\n")
    }

    private fun schedulerSource(): String {
        return File(findAppSourceRoot(), "core/work/WorkScheduler.kt").readText().replace("\r\n", "\n")
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
        val schedulingCallSites = listOf(
            "core/proximity/ProximityDetectionWorker.kt",
            "core/services/TimerReconciliationWorker.kt",
            "core/services/ShizukuResilienceWorker.kt",
        )
    }
}
