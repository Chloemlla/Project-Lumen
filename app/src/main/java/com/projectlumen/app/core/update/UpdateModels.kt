package com.projectlumen.app.core.update

import com.projectlumen.app.BuildConfig

data class BuildMetadata(
    val versionName: String,
    val versionCode: Int,
    val buildTimeUtcMillis: Long,
    val commitHash: String,
    val shortHash: String,
) {
    companion object {
        fun current(): BuildMetadata = BuildMetadata(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildTimeUtcMillis = BuildConfig.BUILD_TIME_UTC_MILLIS,
            commitHash = BuildConfig.COMMIT_HASH,
            shortHash = BuildConfig.SHORT_HASH,
        )
    }
}

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val contentType: String? = null,
    val sha256: String? = null,
    val abi: String? = null,
    val sizeBytes: Long? = null,
)

data class ReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val publishedAtUtcMillis: Long,
    val assets: List<ReleaseAsset>,
    val versionCode: Long = 0L,
    val rollout: String = "",
    val forceUpdate: Boolean = false,
    val channel: String = "stable",
    val patches: List<ReleasePatch> = emptyList(),
)

data class ReleasePatch(
    val fromVersionCode: Long,
    val fromSha256: String,
    val toSha256: String,
    val patchUrl: String,
    val patchSha256: String,
    val algorithm: String,
    val sizeBytes: Long? = null,
)

enum class UpdateMatchReason {
    VERSION_CODE,
    SEMANTIC_VERSION,
    PUBLISHED_AT,
}

data class UpdateCandidate(
    val currentBuild: BuildMetadata,
    val release: ReleaseInfo,
    val matchedAsset: ReleaseAsset?,
    val matchReason: UpdateMatchReason,
) {
    val isTimeFallback: Boolean get() = matchReason == UpdateMatchReason.PUBLISHED_AT
}
