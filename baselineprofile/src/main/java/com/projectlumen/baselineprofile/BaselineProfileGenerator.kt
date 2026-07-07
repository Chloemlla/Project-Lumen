package com.projectlumen.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateProjectLumenBaselineProfile() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 8,
            stableIterations = 2,
            outputFilePrefix = "project-lumen",
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle()
            dismissOnboardingIfPresent()
            exerciseHomeSurface()
        }
    }

    private fun MacrobenchmarkScope.dismissOnboardingIfPresent() {
        val skipButton = device.wait(
            Until.findObject(By.text(Pattern.compile("(?i)Skip|跳过"))),
            FIND_ONBOARDING_TIMEOUT_MILLIS,
        )
        skipButton?.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.exerciseHomeSurface() {
        val centerX = device.displayWidth / 2
        val bottomY = (device.displayHeight * HOME_SCROLL_BOTTOM_FRACTION).toInt()
        val topY = (device.displayHeight * HOME_SCROLL_TOP_FRACTION).toInt()

        repeat(HOME_SCROLL_PASSES) {
            device.swipe(centerX, bottomY, centerX, topY, HOME_SCROLL_STEPS)
            device.waitForIdle()
            device.swipe(centerX, topY, centerX, bottomY, HOME_SCROLL_STEPS)
            device.waitForIdle()
        }
    }

    private companion object {
        private const val TARGET_PACKAGE = "com.chloemlla.projectlumen"
        private const val FIND_ONBOARDING_TIMEOUT_MILLIS = 1_500L
        private const val HOME_SCROLL_BOTTOM_FRACTION = 0.78f
        private const val HOME_SCROLL_TOP_FRACTION = 0.30f
        private const val HOME_SCROLL_PASSES = 2
        private const val HOME_SCROLL_STEPS = 12
    }
}
