package com.projectlumen.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupProcessPolicyTest {
    @Test
    fun defaultProcessInitializesHost() {
        assertTrue(AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, PACKAGE_NAME, APP_UID, APP_UID))
        assertTrue(AppStartupProcessPolicy.shouldInitializeHost("org.example.lumen", "org.example.lumen", APP_UID, APP_UID))
    }

    @Test
    fun nativeProbeProcessesDoNotInitializeHost() {
        listOf("virtualization_probe", "zygisk_fd_detector").forEach { suffix ->
            assertFalse(
                AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, "$PACKAGE_NAME:$suffix", APP_UID, APP_UID),
            )
        }
    }

    @Test
    fun isolatedProcessesDoNotInitializeHost() {
        listOf(
            "$PACKAGE_NAME:virtualization_probe:isolated",
            "$PACKAGE_NAME:zygisk_fd_detector:isolated",
            "android:isolated",
        ).forEach { processName ->
            assertFalse(AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, processName, APP_UID, APP_UID))
        }
    }

    @Test
    fun customAndForeignProcessesDoNotInitializeHost() {
        listOf("$PACKAGE_NAME:worker", "custom.worker.process", "other.package", "${PACKAGE_NAME}extra")
            .forEach { processName ->
                assertFalse(AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, processName, APP_UID, APP_UID))
            }
    }

    @Test
    fun missingProcessInformationFailsClosed() {
        listOf(null, "", " ", "\t\n").forEach { processName ->
            assertFalse(AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, processName, APP_UID, APP_UID))
        }
    }

    @Test
    fun missingPackageInformationFailsClosedEvenWhenBothNamesMatch() {
        listOf(null, "", " ", "\t\n").forEach { packageName ->
            assertFalse(AppStartupProcessPolicy.shouldInitializeHost(packageName, PACKAGE_NAME, APP_UID, APP_UID))
            assertFalse(AppStartupProcessPolicy.shouldInitializeHost(packageName, packageName, APP_UID, APP_UID))
        }
    }

    @Test
    fun processNameMustMatchExactlyWithoutNormalization() {
        listOf(" $PACKAGE_NAME", "$PACKAGE_NAME ", "COM.CHLOEMLLA.PROJECTLUMEN").forEach { processName ->
            assertFalse(AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, processName, APP_UID, APP_UID))
        }
    }

    @Test
    fun isolatedUidCannotInitializeHostEvenWithTheDefaultProcessName() {
        listOf(99001, 199001).forEach { isolatedUid ->
            assertFalse(
                AppStartupProcessPolicy.shouldInitializeHost(
                    packageName = PACKAGE_NAME,
                    processName = PACKAGE_NAME,
                    uid = isolatedUid,
                    applicationUid = APP_UID,
                ),
            )
        }
    }

    @Test
    fun multiUserMainProcessMustMatchItsOwnApplicationUid() {
        assertTrue(
            AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, PACKAGE_NAME, 110001, 110001),
        )
        assertFalse(
            AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, PACKAGE_NAME, 110001, APP_UID),
        )
    }

    @Test
    fun missingOrNegativeUidInformationFailsClosed() {
        listOf(null, -1, Int.MIN_VALUE).forEach { invalidUid ->
            assertFalse(
                AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, PACKAGE_NAME, invalidUid, APP_UID),
            )
            assertFalse(
                AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, PACKAGE_NAME, APP_UID, invalidUid),
            )
            assertFalse(
                AppStartupProcessPolicy.shouldInitializeHost(PACKAGE_NAME, PACKAGE_NAME, invalidUid, invalidUid),
            )
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.chloemlla.projectlumen"
        const val APP_UID = 10001
    }
}
