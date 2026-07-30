package com.projectlumen.app.core.insights

enum class DeviceUsageAvailability {
    LOADING,
    USAGE_ACCESS_REQUIRED,
    AVAILABLE,
    EMPTY,
    RESTRICTED,
}

enum class UsageDataQuality {
    EVENT_TIMELINE,
    AGGREGATED_FALLBACK,
    UNAVAILABLE,
}

enum class AppUsageCategory {
    PRODUCTIVITY,
    SOCIAL,
    VIDEO,
    GAME,
    READING,
    COMMUNICATION,
    NAVIGATION,
    AUDIO,
    OTHER,
}

data class AppUsageSummary(
    val label: String,
    val category: AppUsageCategory,
    val foregroundMillis: Long,
)

data class DeviceUsageSummary(
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val totalForegroundMillis: Long,
    val longestContinuousSessionMillis: Long,
    val lateNightForegroundMillis: Long,
    val appSwitchCount: Int,
    val topApps: List<AppUsageSummary>,
    val quality: UsageDataQuality,
)

enum class BatteryChargeState {
    CHARGING,
    FULL,
    DISCHARGING,
    NOT_CHARGING,
    UNKNOWN,
}

data class DevicePowerSnapshot(
    val levelPercent: Int? = null,
    val chargeState: BatteryChargeState = BatteryChargeState.UNKNOWN,
    val temperatureCelsius: Float? = null,
    val powerSaveMode: Boolean = false,
    val thermalStatus: Int? = null,
    val appBackgroundRestricted: Boolean = false,
    val batteryOptimizationExempt: Boolean = false,
    val chargeTimeRemainingMillis: Long? = null,
)

enum class InsightPriority {
    INFO,
    ATTENTION,
    IMPORTANT,
}

enum class DeviceInsightRecommendationKind {
    HIGH_DAILY_EXPOSURE,
    LONG_CONTINUOUS_USE,
    LATE_NIGHT_USE,
    FREQUENT_APP_SWITCHING,
    DOMINANT_VISUAL_APP,
    DEVICE_WARM,
    LOW_BATTERY,
    POWER_RESTRICTION,
    STABLE_PATTERN,
}

data class DeviceInsightRecommendation(
    val kind: DeviceInsightRecommendationKind,
    val priority: InsightPriority,
    val value: Long = 0L,
    val subject: String = "",
)

data class DeviceInsightsState(
    val availability: DeviceUsageAvailability = DeviceUsageAvailability.LOADING,
    val usage: DeviceUsageSummary? = null,
    val power: DevicePowerSnapshot = DevicePowerSnapshot(),
    val recommendations: List<DeviceInsightRecommendation> = emptyList(),
    val lastUpdatedAtMillis: Long = 0L,
    val isRefreshing: Boolean = false,
    val failureReason: String? = null,
)

internal data class ForegroundInterval(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long,
)

internal data class AppUsageDescriptor(
    val label: String,
    val category: AppUsageCategory,
)
