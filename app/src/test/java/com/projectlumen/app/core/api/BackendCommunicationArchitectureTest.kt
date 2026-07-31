package com.projectlumen.app.core.api

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendCommunicationArchitectureTest {
    @Test
    fun everyApiEndpointDeclaresACapabilityBeforeSigningAndNetwork() {
        val apiSource = source("core/api/ProjectLumenApiClient.kt").readText()
        val endpointCalls = apiSource.substringBefore("private suspend fun <T> request(")
            .split("= request(")
            .drop(1)

        assertTrue("Expected Project Lumen API endpoints", endpointCalls.isNotEmpty())
        endpointCalls.forEachIndexed { index, call ->
            assertTrue(
                "API endpoint ${index + 1} must declare a backend capability",
                call.trimStart().startsWith("capability = BackendCapability."),
            )
        }

        val gateIndex = apiSource.indexOf("backendGate.requireExecutable(capability)")
        val signerIndex = apiSource.indexOf("ProjectLumenRequestSigner.headers")
        val networkIndex = apiSource.indexOf("httpClient.newCall(request).execute()")
        assertTrue(gateIndex >= 0)
        assertTrue(gateIndex < signerIndex)
        assertTrue(gateIndex < networkIndex)
    }

    @Test
    fun productionUsesOneSharedMainBackendClientAndGate() {
        val sourceRoot = findAppSourceRoot()
        val productionConstructions = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "ProjectLumenApiClient.kt" }
            .filter { it.readText().contains("ProjectLumenApiClient(") }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
            .toList()

        assertEquals(listOf("ProjectLumenApplication.kt"), productionConstructions)
        val application = source("ProjectLumenApplication.kt").readText()
        assertTrue(application.contains("ProjectLumenApiClient(backendGate = backendConnectivity)"))
        assertTrue(application.contains("healthProbe = { apiClient.health() }"))

        val updateChecker = source("core/update/UpdateChecker.kt").readText()
        assertTrue(updateChecker.contains("backendGate.decision(BackendCapability.RELEASE_DISCOVERY)"))
        val appUi = source("app/ProjectLumenApp.kt").readText()
        assertTrue(appUi.contains("apiClient = application.apiClient"))
        assertTrue(appUi.contains("backendGate = application.backendConnectivity"))
    }

    @Test
    fun recoveryPathsAndBackgroundCollectorsRemainExplicitlySeparated() {
        val translation = source("app/ProjectLumenTranslationScreen.kt").readText()
        assertTrue(translation.contains("ProjectLumenTranslationApiClient"))
        assertTrue(!translation.contains("BackendCapability"))

        val telemetry = source("core/telemetry/EyeCareTelemetryReporter.kt").readText()
        assertTrue(telemetry.contains("decision(BackendCapability.TELEMETRY)"))
        assertTrue(telemetry.contains("decision(BackendCapability.FACE_ANALYSIS)"))
        val proximity = source("core/proximity/ProximityDetectionService.kt").readText()
        assertTrue(proximity.contains("decision(BackendCapability.FACE_ANALYSIS)"))
        val deviceControl = source("core/devicecontrol/PrivilegedDeviceControlCoordinator.kt").readText()
        assertTrue(deviceControl.contains("decision(BackendCapability.DEVICE_CONTROL)"))
        assertTrue(deviceControl.contains("onBackendUnavailable"))
    }

    @Test
    fun ordinarySettingsHideBackendFeaturesWhileDeveloperRecoveryRemainsVisible() {
        val settings = source("app/ProjectLumenSettingsScreen.kt").readText()
        assertTrue(settings.contains("if (backendFeaturesVisible)"))
        assertTrue(settings.contains("RemoteCloudAccountCard("))
        assertTrue(settings.contains("cloudCapabilityVisible = backendFeaturesVisible"))
        assertTrue(settings.contains("backendFeaturesVisible = backendFeaturesVisible"))

        val privacy = source("app/ProjectLumenSettingsPrivacyCenter.kt").readText()
        assertTrue(privacy.contains("if (backendFeaturesVisible)"))
        assertTrue(privacy.contains("PermissionSetupTarget.DIAGNOSTICS"))
        val shizuku = source("app/ProjectLumenShizukuSettingsSection.kt").readText()
        assertTrue(shizuku.contains("if (backendFeaturesVisible)"))
        assertTrue(shizuku.contains("ShizukuDiagnosticUploadSettings"))

        val developer = source("app/ProjectLumenDeveloperDebugScreen.kt").readText()
        assertTrue(developer.contains("BackendConnectivityDeveloperControls("))
        val controls = source("app/ProjectLumenBackendConnectivityDeveloperControls.kt").readText()
        assertTrue(controls.contains("backend_connectivity_force_enable"))
        assertTrue(controls.contains("onRefresh"))
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
