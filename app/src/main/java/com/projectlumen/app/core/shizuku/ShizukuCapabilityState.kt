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
    val deviceInteractive: Boolean = true,
    val batteryLevelPercent: Int = -1,
    val lowBatteryActive: Boolean = false,
    val powerSaveActive: Boolean = false,
    val dndActive: Boolean = false,
    val thermalStatus: Int = 0,
    val cameraPrivacyEnabled: Boolean = false,
    val systemShouldDeferSampling: Boolean = false,
    val nativeEyeProtectionApplied: Boolean = false,
    val nativeColorTemperatureKelvin: Int = 0,
    val nativeBrightnessPercent: Int = 0,
    val nativeExtraDimEnabled: Boolean = false,
    val nativeExtraDimPercent: Int = 0,
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

data class ShizukuSystemContext(
    val deviceInteractive: Boolean,
    val batteryLevelPercent: Int,
    val lowBatteryActive: Boolean,
    val powerSaveActive: Boolean,
    val dndActive: Boolean,
    val thermalStatus: Int,
    val cameraPrivacyEnabled: Boolean,
    val shouldDeferSampling: Boolean,
)
