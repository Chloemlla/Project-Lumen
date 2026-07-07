package com.projectlumen.app.core.haptics

import org.junit.Assert.assertTrue
import org.junit.Test

class HapticHeEffectTest {
    @Test
    fun transientEffectClampsParametersIntoHeRanges() {
        val json = HapticHeEffect.transient(
            HapticTransientEffect(
                relativeTimeMillis = -1,
                intensity = 120,
                frequency = -20,
            ),
        )

        assertTrue(json.contains("\"Type\": \"transient\""))
        assertTrue(json.contains("\"RelativeTime\": 0"))
        assertTrue(json.contains("\"Intensity\": 100"))
        assertTrue(json.contains("\"Frequency\": 0"))
    }

    @Test
    fun continuousEffectAddsRequiredZeroEndpoints() {
        val json = HapticHeEffect.continuous(
            HapticContinuousEffect(
                durationMillis = 90,
                curve = listOf(
                    HapticCurvePoint(timeMillis = 45, intensityScale = 0.8f, frequencyOffset = 12),
                ),
            ),
        )

        assertTrue(json.contains("\"Type\": \"continuous\""))
        assertTrue(json.contains("\"Duration\": 90"))
        assertTrue(json.contains("\"Time\": 0"))
        assertTrue(json.contains("\"Time\": 90"))
        assertTrue(json.contains("\"Intensity\": 0.8"))
    }
}
