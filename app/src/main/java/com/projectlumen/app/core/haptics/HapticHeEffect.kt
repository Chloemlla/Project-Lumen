package com.projectlumen.app.core.haptics

import java.util.Locale

data class HapticPlaybackOptions(
    val loop: Int = 1,
    val intervalMillis: Int? = null,
    val amplitude: Int? = null,
    val frequency: Int? = null,
)

data class HapticTransientEffect(
    val relativeTimeMillis: Int = 0,
    val intensity: Int = 80,
    val frequency: Int = 50,
)

data class HapticContinuousEffect(
    val relativeTimeMillis: Int = 0,
    val durationMillis: Int = 120,
    val intensity: Int = 100,
    val frequency: Int = 50,
    val curve: List<HapticCurvePoint> = defaultReminderCurve,
)

data class HapticCurvePoint(
    val timeMillis: Int,
    val intensityScale: Float,
    val frequencyOffset: Int,
)

object HapticHeEffect {
    fun transient(effect: HapticTransientEffect = HapticTransientEffect()): String {
        return """
            {
              "Metadata": {
                "Version": 1,
                "Created": "2026-07-07",
                "Description": "Project Lumen transient haptic"
              },
              "Pattern": [
                {
                  "Event": {
                    "Type": "transient",
                    "RelativeTime": ${effect.relativeTimeMillis.coerceAtLeast(0)},
                    "Parameters": {
                      "Intensity": ${effect.intensity.coerceIn(0, 100)},
                      "Frequency": ${effect.frequency.coerceIn(0, 100)}
                    }
                  }
                }
              ]
            }
        """.trimIndent()
    }

    fun continuous(effect: HapticContinuousEffect = HapticContinuousEffect()): String {
        val durationMillis = effect.durationMillis.coerceAtLeast(1)
        val curve = normalizedCurve(effect.curve, durationMillis)
            .joinToString(separator = ",\n") { point ->
                """
                        {
                          "Time": ${point.timeMillis.coerceIn(0, durationMillis)},
                          "Intensity": ${point.intensityScale.coerceIn(0f, 1f).jsonFloat()},
                          "Frequency": ${point.frequencyOffset.coerceIn(-100, 100)}
                        }""".trimIndent()
            }
        return """
            {
              "Metadata": {
                "Version": 1,
                "Created": "2026-07-07",
                "Description": "Project Lumen continuous haptic"
              },
              "Pattern": [
                {
                  "Event": {
                    "Type": "continuous",
                    "RelativeTime": ${effect.relativeTimeMillis.coerceAtLeast(0)},
                    "Duration": $durationMillis,
                    "Parameters": {
                      "Intensity": ${effect.intensity.coerceIn(0, 100)},
                      "Frequency": ${effect.frequency.coerceIn(0, 100)},
                      "Curve": [
            $curve
                      ]
                    }
                  }
                }
              ]
            }
        """.trimIndent()
    }

    fun reminderNudge(): String {
        return continuous(
            HapticContinuousEffect(
                durationMillis = 120,
                intensity = 100,
                frequency = 55,
                curve = defaultReminderCurve,
            ),
        )
    }

    private fun normalizedCurve(
        curve: List<HapticCurvePoint>,
        durationMillis: Int,
    ): List<HapticCurvePoint> {
        val sorted = curve
            .ifEmpty { defaultReminderCurve }
            .sortedBy(HapticCurvePoint::timeMillis)
            .toMutableList()
        if (sorted.firstOrNull()?.timeMillis != 0 || sorted.firstOrNull()?.intensityScale != 0f) {
            sorted.add(0, HapticCurvePoint(timeMillis = 0, intensityScale = 0f, frequencyOffset = 0))
        }
        if (sorted.lastOrNull()?.timeMillis != durationMillis || sorted.lastOrNull()?.intensityScale != 0f) {
            sorted.add(HapticCurvePoint(timeMillis = durationMillis, intensityScale = 0f, frequencyOffset = 0))
        }
        return sorted
    }

    private fun Float.jsonFloat(): String {
        return String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')
    }
}

private val defaultReminderCurve = listOf(
    HapticCurvePoint(timeMillis = 0, intensityScale = 0f, frequencyOffset = 0),
    HapticCurvePoint(timeMillis = 35, intensityScale = 0.72f, frequencyOffset = 10),
    HapticCurvePoint(timeMillis = 80, intensityScale = 0.46f, frequencyOffset = -12),
    HapticCurvePoint(timeMillis = 120, intensityScale = 0f, frequencyOffset = 0),
)
