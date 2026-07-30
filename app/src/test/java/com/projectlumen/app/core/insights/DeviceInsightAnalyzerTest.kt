package com.projectlumen.app.core.insights

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInsightAnalyzerTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun summarizeUsage_mergesOverlappingAppsWithoutDoubleCountingExposure() {
        val start = millis(2026, 7, 29, 21, 0)
        val end = millis(2026, 7, 30, 1, 0)
        val summary = DeviceInsightAnalyzer.summarizeUsage(
            intervals = listOf(
                ForegroundInterval("app.alpha", millis(2026, 7, 29, 22, 0), millis(2026, 7, 29, 23, 0)),
                ForegroundInterval("app.beta", millis(2026, 7, 29, 22, 30), millis(2026, 7, 29, 23, 30)),
            ),
            descriptors = mapOf(
                "app.alpha" to AppUsageDescriptor("Alpha", AppUsageCategory.READING),
                "app.beta" to AppUsageDescriptor("Beta", AppUsageCategory.VIDEO),
            ),
            periodStartMillis = start,
            periodEndMillis = end,
            zoneId = utc,
            appSwitchCount = 1,
        )

        assertEquals(90L * MINUTE, summary.totalForegroundMillis)
        assertEquals(90L * MINUTE, summary.longestContinuousSessionMillis)
        assertEquals(90L * MINUTE, summary.lateNightForegroundMillis)
        assertEquals(listOf("Alpha", "Beta"), summary.topApps.map { it.label })
        assertEquals(UsageDataQuality.EVENT_TIMELINE, summary.quality)
    }

    @Test
    fun summarizeUsage_joinsShortAppSwitchGapsForContinuousSessionOnly() {
        val start = millis(2026, 7, 29, 9, 0)
        val end = millis(2026, 7, 29, 11, 0)
        val summary = DeviceInsightAnalyzer.summarizeUsage(
            intervals = listOf(
                ForegroundInterval("app.alpha", start, start + 30L * MINUTE),
                ForegroundInterval("app.beta", start + 31L * MINUTE, start + 60L * MINUTE),
            ),
            descriptors = emptyMap(),
            periodStartMillis = start,
            periodEndMillis = end,
            zoneId = utc,
            appSwitchCount = 1,
        )

        assertEquals(59L * MINUTE, summary.totalForegroundMillis)
        assertEquals(60L * MINUTE, summary.longestContinuousSessionMillis)
        assertEquals(0L, summary.lateNightForegroundMillis)
    }

    @Test
    fun recommendations_areDeterministicAndThresholdDriven() {
        val usage = DeviceUsageSummary(
            periodStartMillis = 0L,
            periodEndMillis = 24L * 60L * MINUTE,
            totalForegroundMillis = 5L * 60L * MINUTE,
            longestContinuousSessionMillis = 70L * MINUTE,
            lateNightForegroundMillis = 45L * MINUTE,
            appSwitchCount = 95,
            topApps = listOf(
                AppUsageSummary("Reader", AppUsageCategory.READING, 2L * 60L * MINUTE),
            ),
            quality = UsageDataQuality.EVENT_TIMELINE,
        )
        val power = DevicePowerSnapshot(
            levelPercent = 15,
            chargeState = BatteryChargeState.DISCHARGING,
            temperatureCelsius = 41f,
            powerSaveMode = true,
            appBackgroundRestricted = true,
        )

        val kinds = DeviceInsightAnalyzer.recommendations(usage, power).map { it.kind }

        assertEquals(
            listOf(
                DeviceInsightRecommendationKind.HIGH_DAILY_EXPOSURE,
                DeviceInsightRecommendationKind.LONG_CONTINUOUS_USE,
                DeviceInsightRecommendationKind.LATE_NIGHT_USE,
                DeviceInsightRecommendationKind.FREQUENT_APP_SWITCHING,
                DeviceInsightRecommendationKind.DOMINANT_VISUAL_APP,
                DeviceInsightRecommendationKind.DEVICE_WARM,
                DeviceInsightRecommendationKind.LOW_BATTERY,
                DeviceInsightRecommendationKind.POWER_RESTRICTION,
            ),
            kinds,
        )
    }

    @Test
    fun recommendations_returnStablePatternWhenNoRuleMatches() {
        val usage = DeviceUsageSummary(
            periodStartMillis = 0L,
            periodEndMillis = 24L * 60L * MINUTE,
            totalForegroundMillis = 30L * MINUTE,
            longestContinuousSessionMillis = 20L * MINUTE,
            lateNightForegroundMillis = 0L,
            appSwitchCount = 4,
            topApps = emptyList(),
            quality = UsageDataQuality.EVENT_TIMELINE,
        )

        val recommendations = DeviceInsightAnalyzer.recommendations(usage, DevicePowerSnapshot(levelPercent = 80))

        assertEquals(1, recommendations.size)
        assertEquals(DeviceInsightRecommendationKind.STABLE_PATTERN, recommendations.single().kind)
        assertTrue(recommendations.single().subject.isEmpty())
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, utc).toInstant().toEpochMilli()

    private companion object {
        private const val MINUTE = 60_000L
    }
}
