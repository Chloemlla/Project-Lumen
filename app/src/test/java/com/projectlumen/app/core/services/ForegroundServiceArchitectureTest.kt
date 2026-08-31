package com.projectlumen.app.core.services

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceArchitectureTest {
    @Test
    fun allForegroundServicesUseSharedPromotionBoundary() {
        val sourceRoot = findAppSourceRoot()
        foregroundServiceSources.forEach { relativePath ->
            val sourceFile = File(sourceRoot, relativePath)
            assertTrue("Missing foreground service source: $relativePath", sourceFile.isFile)
            assertTrue(
                "$relativePath must promote through ForegroundServiceController",
                sourceFile.readText().contains("ForegroundServiceController.promote"),
            )
        }
    }

    @Test
    fun androidXForegroundServiceCallsStayInsideSharedController() {
        val sourceRoot = findAppSourceRoot()
        val violations = sourceRoot.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension == "kt" &&
                    file.name != "ForegroundServiceController.kt"
            }
            .filter { file ->
                val source = file.readText()
                source.contains("ContextCompat.startForegroundService(") ||
                    source.contains("ServiceCompat.startForeground(")
            }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
            .toList()

        assertTrue(
            "Direct AndroidX foreground-service calls bypass the shared controller: $violations",
            violations.isEmpty(),
        )
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
        val foregroundServiceSources = listOf(
            "core/services/TimerForegroundService.kt",
            "core/proximity/ProximityDetectionService.kt",
            "core/light/LightMonitorService.kt",
            "core/overlay/EyeProtectionOverlayService.kt",
            "core/debug/DeveloperDebugOverlayService.kt",
        )
    }
}
