package com.projectlumen.app.core.shizuku

data class ShizukuCapabilityState(
    val binderAvailable: Boolean = false,
    val permissionGranted: Boolean = false,
    val permissionRequestable: Boolean = false,
    val serverVersion: Int = 0,
    val serverUid: Int = 0,
    val foregroundPackage: String = "",
    val foregroundActivity: String = "",
    val foregroundCategory: String = "",
    val foregroundShouldDeferSampling: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val lastError: String = "",
) {
    val ready: Boolean
        get() = binderAvailable && permissionGranted
}

data class ShizukuForegroundContext(
    val packageName: String,
    val activityName: String,
    val category: String,
    val shouldDeferSampling: Boolean,
)
