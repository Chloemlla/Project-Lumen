package com.projectlumen.app.core.api

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendCommunicationArchitectureTest {
    @Test
    fun everyApiEndpointDeclaresACapabilityBeforeSigningAndNetwork() {
        val apiSource = read("core/api/ProjectLumenApiClient.kt")
        assertTrue(
            "ProjectLumenApiClient must funnel every endpoint through a shared request() helper",
            apiSource.contains(REQUEST_HELPER_ANCHOR),
        )
        val endpointCalls = apiSource.substringBefore(REQUEST_HELPER_ANCHOR)
            .split("= request(")
            .drop(1)

        assertTrue("Expected Project Lumen API endpoints", endpointCalls.isNotEmpty())
        endpointCalls.forEachIndexed { index, call ->
            assertTrue(
                "API endpoint ${index + 1} must pass a BackendCapability to request()",
                call.take(ARGUMENT_WINDOW).contains("BackendCapability."),
            )
        }

        val helperBody = apiSource.substringAfter(REQUEST_HELPER_ANCHOR)
        val gateIndex = helperBody.indexOf("requireExecutable(")
        val signerIndex = helperBody.indexOf("ProjectLumenRequestSigner.")
        val networkIndex = helperBody.indexOf("httpClient.newCall(")
        assertTrue("request() must consult the backend gate", gateIndex >= 0)
        assertTrue("request() must sign outgoing requests", signerIndex >= 0)
        assertTrue("request() must dispatch through the shared OkHttp client", networkIndex >= 0)
        assertTrue("The gate must be consulted before signing", gateIndex < signerIndex)
        assertTrue("The gate must be consulted before any network call", gateIndex < networkIndex)
    }

    @Test
    fun productionUsesOneSharedMainBackendClientAndGate() {
        val sourceRoot = findAppSourceRoot()
        val productionConstructions = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "ProjectLumenApiClient.kt" }
            .filter { it.readText().contains("ProjectLumenApiClient(") }
            .map { it.relativeTo(sourceRoot).invariantSeparatorsPath }
            .sorted()
            .toList()

        assertEquals(
            "ProjectLumenApiClient must be constructed in exactly one place",
            listOf("ProjectLumenApplication.kt"),
            productionConstructions,
        )
        val application = read("ProjectLumenApplication.kt")
        assertTrue(application.contains("ProjectLumenApiClient("))
        assertTrue("The shared client must be handed the backend gate", application.contains("backendGate ="))
        assertTrue("The shared client must be handed a health probe", application.contains("healthProbe ="))

        assertTrue(
            "UpdateChecker must gate release discovery",
            read("core/update/UpdateChecker.kt").contains("BackendCapability.RELEASE_DISCOVERY"),
        )
        val appUi = read("app/ProjectLumenApp.kt")
        assertTrue("The UI must reuse the application-scoped client", appUi.contains("application.apiClient"))
        assertTrue("The UI must reuse the application-scoped gate", appUi.contains("application.backendConnectivity"))
    }

    @Test
    fun recoveryPathsAndBackgroundCollectorsRemainExplicitlySeparated() {
        val translation = read("app/ProjectLumenTranslationScreen.kt")
        assertTrue(translation.contains("ProjectLumenTranslationApiClient"))
        assertTrue(
            "Translation is a recovery path and must stay outside the capability gate",
            !translation.contains("BackendCapability"),
        )

        val telemetry = read("core/telemetry/EyeCareTelemetryReporter.kt")
        assertTrue(telemetry.contains("BackendCapability.TELEMETRY"))
        assertTrue(telemetry.contains("BackendCapability.FACE_ANALYSIS"))
        assertTrue(read("core/proximity/ProximityDetectionService.kt").contains("BackendCapability.FACE_ANALYSIS"))
        val deviceControl = read("core/devicecontrol/PrivilegedDeviceControlCoordinator.kt")
        assertTrue(deviceControl.contains("BackendCapability.DEVICE_CONTROL"))
        assertTrue(deviceControl.contains("onBackendUnavailable"))
    }

    @Test
    fun ordinarySettingsHideBackendFeaturesWhileDeveloperRecoveryRemainsVisible() {
        val settings = read("app/ProjectLumenSettingsScreen.kt")
        assertTrue(settings.contains("backendFeaturesVisible"))
        assertTrue(settings.contains("RemoteCloudAccountCard"))
        assertTrue(settings.contains("cloudCapabilityVisible"))

        val privacy = read("app/ProjectLumenSettingsPrivacyCenter.kt")
        assertTrue(privacy.contains("backendFeaturesVisible"))
        assertTrue(privacy.contains("PermissionSetupTarget.DIAGNOSTICS"))
        val shizuku = read("app/ProjectLumenShizukuSettingsSection.kt")
        assertTrue(shizuku.contains("backendFeaturesVisible"))
        assertTrue(shizuku.contains("ShizukuDiagnosticUploadSettings"))

        val developer = read("app/ProjectLumenDeveloperDebugScreen.kt")
        assertTrue(developer.contains("BackendConnectivityDeveloperControls"))
        val controls = read("app/ProjectLumenBackendConnectivityDeveloperControls.kt")
        assertTrue(controls.contains("backend_connectivity_force_enable"))
        assertTrue(controls.contains("onRefresh"))
    }

    private fun read(relativePath: String): String {
        val file = File(findAppSourceRoot(), relativePath)
        assertTrue("Missing production source: $relativePath", file.isFile)
        return file.readText()
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
        const val REQUEST_HELPER_ANCHOR = "fun <T> request("
        const val ARGUMENT_WINDOW = 600
    }
}
