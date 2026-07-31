package com.projectlumen.app.app

import com.projectlumen.app.core.security.DeviceInstallProfile
import com.projectlumen.app.core.security.SecureCredentialStore
import com.projectlumen.app.core.update.BuildMetadata
import com.projectlumen.app.core.update.BuildUpdateNotesLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ProjectLumenFirstOpenGateEntry(
    private val secureCredentials: SecureCredentialStore,
    private val buildUpdateNotesLoader: BuildUpdateNotesLoader,
    private val currentBuild: BuildMetadata,
    private val initialInstallProfile: DeviceInstallProfile,
    private val deviceFingerprint: String,
) {
    private var installProfile = initialInstallProfile
    private var onboardingEligible = initialInstallProfile.ossNoticeCompletedAt > 0L &&
        initialInstallProfile.onboardingCompletedAt <= 0L
    private var newInstallDetected = false

    private val _ossNoticeState = MutableStateFlow(ProjectLumenOssNoticeState())
    private val _onboardingState = MutableStateFlow(
        ProjectLumenOnboardingState(deviceFingerprint = deviceFingerprint),
    )
    private val _buildUpdateNotesState = MutableStateFlow(ProjectLumenBuildUpdateNotesState())

    val ossNoticeState = _ossNoticeState.asStateFlow()
    val onboardingState = _onboardingState.asStateFlow()
    val buildUpdateNotesState = _buildUpdateNotesState.asStateFlow()

    init {
        applyAutomaticGate()
    }

    fun refresh(hadExistingLocalUse: Boolean) {
        installProfile = runCatching { secureCredentials.installProfile() }
            .getOrDefault(installProfile)

        val existingUserBeforeOssFeature = installProfile.ossNoticeCompletedAt <= 0L &&
            installProfile.onboardingCompletedAt > 0L
        if (existingUserBeforeOssFeature) {
            val completedAt = System.currentTimeMillis().coerceAtLeast(1L)
            runCatching { secureCredentials.markOssNoticeCompleted(completedAt) }
                .onSuccess {
                    installProfile = installProfile.copy(ossNoticeCompletedAt = completedAt)
                }
        }

        onboardingEligible = installProfile.onboardingCompletedAt <= 0L &&
            (
                installProfile.ossNoticeCompletedAt > 0L ||
                    (!initialInstallProfile.hadDeviceCredentialBeforeLaunch && !hadExistingLocalUse)
                )
        newInstallDetected = detectNewInstall()
        applyAutomaticGate()
    }

    fun completeOssNotice() {
        if (_ossNoticeState.value.reopenMode) {
            dismissOssNotice()
            return
        }

        val completedAt = System.currentTimeMillis().coerceAtLeast(1L)
        secureCredentials.markOssNoticeCompleted(completedAt)
        installProfile = installProfile.copy(ossNoticeCompletedAt = completedAt)
        onboardingEligible = installProfile.onboardingCompletedAt <= 0L
        applyAutomaticGate()
    }

    fun dismissOssNotice() {
        if (!_ossNoticeState.value.reopenMode) return
        _ossNoticeState.value = ProjectLumenOssNoticeState()
    }

    fun reopenOssNotice() {
        _onboardingState.value = _onboardingState.value.copy(visible = false)
        _buildUpdateNotesState.value = ProjectLumenBuildUpdateNotesState()
        _ossNoticeState.value = ProjectLumenOssNoticeState(visible = true, reopenMode = true)
    }

    fun completeOnboarding() {
        val completedAt = System.currentTimeMillis().coerceAtLeast(1L)
        secureCredentials.markOnboardingCompleted(completedAt)
        installProfile = installProfile.copy(onboardingCompletedAt = completedAt)
        onboardingEligible = false
        applyAutomaticGate()
    }

    fun completeBuildUpdateNotes() {
        if (_buildUpdateNotesState.value.reopenMode) {
            dismissBuildUpdateNotes()
            return
        }

        secureCredentials.markBuildUpdateNotesAcknowledged(
            commitHash = currentBuild.commitHash,
            buildTimeUtcMillis = currentBuild.buildTimeUtcMillis,
        )
        installProfile = installProfile.copy(
            lastAcknowledgedCommitHash = currentBuild.commitHash,
            lastAcknowledgedBuildTimeUtcMillis = currentBuild.buildTimeUtcMillis,
        )
        applyAutomaticGate()
    }

    fun dismissBuildUpdateNotes() {
        if (!_buildUpdateNotesState.value.reopenMode) return
        _buildUpdateNotesState.value = ProjectLumenBuildUpdateNotesState()
    }

    fun reopenBuildUpdateNotes() {
        _ossNoticeState.value = ProjectLumenOssNoticeState()
        _onboardingState.value = _onboardingState.value.copy(visible = false)
        _buildUpdateNotesState.value = ProjectLumenBuildUpdateNotesState(
            visible = true,
            reopenMode = true,
            notes = buildUpdateNotesLoader.load(currentBuild),
        )
    }

    private fun applyAutomaticGate() {
        if (_ossNoticeState.value.reopenMode || _buildUpdateNotesState.value.reopenMode) return

        val gate = ProjectLumenFirstOpenGateResolver.resolve(
            ProjectLumenFirstOpenGateInput(
                ossNoticeCompletedAt = installProfile.ossNoticeCompletedAt,
                onboardingCompletedAt = installProfile.onboardingCompletedAt,
                lastAcknowledgedCommitHash = installProfile.lastAcknowledgedCommitHash,
                lastAcknowledgedBuildTimeUtcMillis = installProfile.lastAcknowledgedBuildTimeUtcMillis,
                currentBuild = currentBuild,
                grandfatherOssNotice = installProfile.onboardingCompletedAt > 0L,
                onboardingEligible = onboardingEligible,
            ),
        )
        _ossNoticeState.value = ProjectLumenOssNoticeState(
            visible = gate == ProjectLumenFirstOpenGate.OPEN_SOURCE_NOTICE,
        )
        _onboardingState.value = ProjectLumenOnboardingState(
            visible = gate == ProjectLumenFirstOpenGate.ONBOARDING,
            deviceFingerprint = deviceFingerprint,
            newInstallDetected = newInstallDetected,
        )
        _buildUpdateNotesState.value = ProjectLumenBuildUpdateNotesState(
            visible = gate == ProjectLumenFirstOpenGate.BUILD_UPDATE_NOTES,
            notes = if (gate == ProjectLumenFirstOpenGate.BUILD_UPDATE_NOTES) {
                buildUpdateNotesLoader.load(currentBuild)
            } else {
                null
            },
        )
    }

    private fun detectNewInstall(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val firstInstallAt = initialInstallProfile.packageFirstInstallAt
        val freshPackageInstall = firstInstallAt <= 0L ||
            nowMillis - firstInstallAt <= FRESH_INSTALL_WINDOW_MILLIS
        return !initialInstallProfile.hadDeviceCredentialBeforeLaunch &&
            (
                freshPackageInstall ||
                    initialInstallProfile.firstSeenAt >= nowMillis - FIRST_SEEN_GRACE_MILLIS
                )
    }

    private companion object {
        private const val FRESH_INSTALL_WINDOW_MILLIS = 3L * 24L * 60L * 60L * 1_000L
        private const val FIRST_SEEN_GRACE_MILLIS = 5L * 60L * 1_000L
    }
}
