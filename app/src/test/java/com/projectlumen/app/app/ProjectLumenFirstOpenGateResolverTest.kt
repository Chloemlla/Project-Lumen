package com.projectlumen.app.app

import com.projectlumen.app.core.update.BuildMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectLumenFirstOpenGateResolverTest {
    private val currentBuild = BuildMetadata(
        versionName = "1.2.3",
        versionCode = 42,
        buildTimeUtcMillis = 1_722_222_222_000L,
        commitHash = "abcdef0123456789abcdef0123456789abcdef01",
        shortHash = "abcdef01",
    )

    @Test
    fun freshInstallStartsWithOpenSourceNotice() {
        assertEquals(
            ProjectLumenFirstOpenGate.OPEN_SOURCE_NOTICE,
            resolve(ossCompletedAt = 0L, onboardingCompletedAt = 0L),
        )
    }

    @Test
    fun completingOpenSourceNoticeAdvancesToOnboarding() {
        assertEquals(
            ProjectLumenFirstOpenGate.ONBOARDING,
            resolve(ossCompletedAt = 10L, onboardingCompletedAt = 0L),
        )
    }

    @Test
    fun completingOnboardingAdvancesToBuildNotes() {
        assertEquals(
            ProjectLumenFirstOpenGate.BUILD_UPDATE_NOTES,
            resolve(ossCompletedAt = 10L, onboardingCompletedAt = 20L),
        )
    }

    @Test
    fun matchingCommitAndBuildTimeSuppressBuildNotes() {
        assertEquals(
            ProjectLumenFirstOpenGate.NONE,
            resolve(
                ossCompletedAt = 10L,
                onboardingCompletedAt = 20L,
                acknowledgedCommitHash = currentBuild.commitHash,
                acknowledgedBuildTimeUtcMillis = currentBuild.buildTimeUtcMillis,
            ),
        )
    }

    @Test
    fun changedCommitOrBuildTimeRearmsBuildNotes() {
        assertEquals(
            ProjectLumenFirstOpenGate.BUILD_UPDATE_NOTES,
            resolve(
                ossCompletedAt = 10L,
                onboardingCompletedAt = 20L,
                acknowledgedCommitHash = "ffffffffffffffffffffffffffffffffffffffff",
                acknowledgedBuildTimeUtcMillis = currentBuild.buildTimeUtcMillis,
            ),
        )
        assertEquals(
            ProjectLumenFirstOpenGate.BUILD_UPDATE_NOTES,
            resolve(
                ossCompletedAt = 10L,
                onboardingCompletedAt = 20L,
                acknowledgedCommitHash = currentBuild.commitHash,
                acknowledgedBuildTimeUtcMillis = currentBuild.buildTimeUtcMillis - 1L,
            ),
        )
    }

    @Test
    fun completedOnboardingGrandfathersLegacyOpenSourceNotice() {
        assertEquals(
            ProjectLumenFirstOpenGate.BUILD_UPDATE_NOTES,
            resolve(
                ossCompletedAt = 0L,
                onboardingCompletedAt = 20L,
                grandfatherOssNotice = true,
            ),
        )
    }

    private fun resolve(
        ossCompletedAt: Long,
        onboardingCompletedAt: Long,
        acknowledgedCommitHash: String = "",
        acknowledgedBuildTimeUtcMillis: Long = 0L,
        grandfatherOssNotice: Boolean = false,
        onboardingEligible: Boolean = true,
    ): ProjectLumenFirstOpenGate {
        return ProjectLumenFirstOpenGateResolver.resolve(
            ProjectLumenFirstOpenGateInput(
                ossNoticeCompletedAt = ossCompletedAt,
                onboardingCompletedAt = onboardingCompletedAt,
                lastAcknowledgedCommitHash = acknowledgedCommitHash,
                lastAcknowledgedBuildTimeUtcMillis = acknowledgedBuildTimeUtcMillis,
                currentBuild = currentBuild,
                grandfatherOssNotice = grandfatherOssNotice,
                onboardingEligible = onboardingEligible,
            ),
        )
    }
}
