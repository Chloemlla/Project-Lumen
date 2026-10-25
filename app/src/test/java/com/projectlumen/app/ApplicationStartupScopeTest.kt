package com.projectlumen.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-wiring regressions following AppIntegrityGuardScopeTest, without an Android runtime. */
class ApplicationStartupScopeTest {
    @Test
    fun processDecisionIsLazyAndSafeDuringAttachBaseContext() {
        val decision = sourceBlock("private val isHostProcess: Boolean by lazy {")
        assertTrue(decision.contains("AppStartupProcessPolicy.shouldInitializeHost("))
        assertTrue(decision.contains("packageName = packageName"))
        assertTrue(decision.contains("processName = Application.getProcessName()"))
        assertTrue(decision.contains("uid = Process.myUid()"))
        assertTrue(decision.contains("applicationUid = applicationInfo.uid"))
        assertFalse(
            "Early startup must not depend on applicationContext",
            decision.contains("applicationContext"),
        )
        assertTrue(
            "The coroutine scope must not be created during auxiliary Application construction",
            applicationSource().contains("private val applicationScope by lazy {"),
        )
    }

    @Test
    fun attachBaseContextGuardsEarlySdkInstallationAfterPlatformAttach() {
        val body = sourceBlock("override fun attachBaseContext(base: Context) {")
        assertPlatformThenGuard(body, "attachBaseContext(base)")
        assertBefore(body, PROCESS_GUARD, "installLumenCrashSdk()")
        assertBefore(body, PROCESS_GUARD, "CrashBreadcrumbs.record(")
    }

    @Test
    fun onCreateGuardsAllHostStartupAfterPlatformCreate() {
        val body = sourceBlock("override fun onCreate() {")
        assertPlatformThenGuard(body, "onCreate()")
        listOf(
            "appContext = this",
            "CrashReportPasteUploader.shouldSkipManualProxy",
            "installLumenCrashSdk()",
            "initializeMmkvOrRecordCrash()",
            "ProjectLumenClientIdentity.installDeviceIdProvider",
            "startBackgroundStartupWork()",
            "notifications.ensureChannels()",
            "LumenToast.install(this)",
            "backendConnectivity.start()",
            "ProcessLifecycleOwner.get()",
            "deviceControl.start()",
            "observeBackendAvailability()",
            "scheduleStoredCrashReportUpload()",
        ).forEach { hostStartup -> assertBefore(body, PROCESS_GUARD, hostStartup) }
    }

    @Test
    fun trimMemoryCannotLaunchHostSamplingInAuxiliaryProcesses() {
        val body = sourceBlock("override fun onTrimMemory(level: Int) {")
        assertPlatformThenGuard(body, "onTrimMemory(level)")
        assertBefore(body, PROCESS_GUARD, "applicationScope.launch")
        assertBefore(body, PROCESS_GUARD, "MemoryHealthMonitor.recordTrim(")
    }

    @Test
    fun sdkInstallationCannotBypassTheProcessBoundary() {
        val body = sourceBlock("private fun installLumenCrashSdk() {")
        assertBefore(body, PROCESS_GUARD, "LumenCrash.isInstalled()")
        assertBefore(body, PROCESS_GUARD, "LumenCrash.install(this)")
    }

    @Test
    fun crashReportsGetterGuardsBothSdkRetryAndFallbackStore() {
        val body = sourceBlock(
            "val crashReports: CrashReportStore\n        get() {",
            closingAnchor = "\n        }",
        )
        // The first statement must stay outside runCatching so a rejected getter cannot fall back.
        val firstStatement = body.lineSequence()
            .map { it.trim() }
            .first { it.isNotEmpty() && !it.startsWith("//") }
        assertTrue(firstStatement.startsWith("check(isHostProcess)"))
        listOf(
            "LumenCrash.isInstalled()",
            "installLumenCrashSdk()",
            "LumenCrash.store()",
            "CrashReportStore(this)",
        ).forEach { access -> assertBefore(body, "check(isHostProcess)", access) }
    }

    private fun assertPlatformThenGuard(body: String, lifecycleCall: String) {
        val expectedPrefix = "super.$lifecycleCall\n        $PROCESS_GUARD"
        assertTrue(
            "Auxiliary lifecycle must return immediately after $lifecycleCall",
            body.trimStart().startsWith(expectedPrefix),
        )
    }

    private fun assertBefore(body: String, first: String, second: String) {
        val firstIndex = body.indexOf(first)
        val secondIndex = body.indexOf(second)
        assertTrue("Missing source anchor: $first", firstIndex >= 0)
        assertTrue("Missing source anchor: $second", secondIndex >= 0)
        assertTrue("$first must precede $second", firstIndex < secondIndex)
    }

    private fun sourceBlock(anchor: String, closingAnchor: String = "\n    }"): String {
        val source = applicationSource()
        assertTrue("Missing Application source anchor: $anchor", source.contains(anchor))
        return source.substringAfter(anchor).substringBefore(closingAnchor)
    }

    private fun applicationSource(): String {
        val workingDirectory = File(System.getProperty("user.dir")).absoluteFile
        val source = generateSequence(workingDirectory) { it.parentFile }
            .flatMap { directory ->
                sequenceOf(
                    File(directory, "app/src/main/java/com/projectlumen/app/ProjectLumenApplication.kt"),
                    File(directory, "src/main/java/com/projectlumen/app/ProjectLumenApplication.kt"),
                )
            }
            .firstOrNull { it.isFile }
            ?: error("Unable to locate Application source from $workingDirectory")
        return source.readText().replace("\r\n", "\n")
    }

    private companion object {
        const val PROCESS_GUARD = "if (!isHostProcess) return"
    }
}
