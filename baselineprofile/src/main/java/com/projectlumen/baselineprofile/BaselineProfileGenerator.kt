package com.projectlumen.baselineprofile

import android.content.Intent
import android.os.Build
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
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
            maxIterations = 5,
            stableIterations = 2,
            outputFilePrefix = "project-lumen",
            includeInStartupProfile = true,
        ) {
            // Clean process/task state inside the profile block.
            // BaselineProfileRule.collect in benchmark-macro-junit4:1.4.1 has no setupBlock.
            pressHome()
            killProcess()
            device.waitForIdle(IDLE_TIMEOUT_MILLIS)

            pressHome()
            startProjectLumenAndWait()
            dismissBlockingUiIfPresent()
            exerciseHomeSurface()
        }
    }

    private fun MacrobenchmarkScope.startProjectLumenAndWait() {
        // Prefer an explicit MAIN/LAUNCHER intent so launch is deterministic on managed devices.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(TARGET_PACKAGE)
            ?.apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                )
            }
        if (launchIntent != null) {
            startActivityAndWait(launchIntent)
        } else {
            startActivityAndWait()
        }

        val becameVisible = device.wait(
            Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)),
            APP_VISIBLE_TIMEOUT_MILLIS,
        )
        check(becameVisible) {
            "Target package $TARGET_PACKAGE did not become visible after launch " +
                "(api=${Build.VERSION.SDK_INT})."
        }

        // Macrobenchmark validates that the process remains running; give startup a short settle.
        device.waitForIdle(IDLE_TIMEOUT_MILLIS)
        val processStillRunning = device.executeShellCommand("pidof $TARGET_PACKAGE")
            .trim()
            .isNotEmpty()
        check(processStillRunning) {
            "Target package $TARGET_PACKAGE is not running after launch settle. " +
                "This usually means Application/Activity crashed on the managed emulator " +
                "(missing x86_64 libs, integrity enforcement, or startup exception)."
        }
    }

    private fun MacrobenchmarkScope.dismissBlockingUiIfPresent() {
        // Crash-report continue button (EN/ZH) if a previous run left a report on disk.
        clickIfPresent(
            pattern = Pattern.compile("(?i)Continue|继续|清除并继续|Clear and continue|Clear & continue"),
            timeoutMillis = FIND_UI_TIMEOUT_MILLIS,
        )
        // Onboarding skip if shown.
        clickIfPresent(
            pattern = Pattern.compile("(?i)Skip|跳过"),
            timeoutMillis = FIND_UI_TIMEOUT_MILLIS,
        )
        device.waitForIdle(IDLE_TIMEOUT_MILLIS)
    }

    private fun MacrobenchmarkScope.clickIfPresent(
        pattern: Pattern,
        timeoutMillis: Long,
    ) {
        val target = device.wait(Until.findObject(By.text(pattern)), timeoutMillis)
        target?.click()
        device.waitForIdle(IDLE_TIMEOUT_MILLIS)
    }

    private fun MacrobenchmarkScope.exerciseHomeSurface() {
        val centerX = device.displayWidth / 2
        val bottomY = (device.displayHeight * HOME_SCROLL_BOTTOM_FRACTION).toInt()
        val topY = (device.displayHeight * HOME_SCROLL_TOP_FRACTION).toInt()

        repeat(HOME_SCROLL_PASSES) {
            device.swipe(centerX, bottomY, centerX, topY, HOME_SCROLL_STEPS)
            device.waitForIdle(IDLE_TIMEOUT_MILLIS)
            device.swipe(centerX, topY, centerX, bottomY, HOME_SCROLL_STEPS)
            device.waitForIdle(IDLE_TIMEOUT_MILLIS)
        }
    }

    private companion object {
        private const val TARGET_PACKAGE = "com.chloemlla.projectlumen"
        private const val APP_VISIBLE_TIMEOUT_MILLIS = 15_000L
        private const val FIND_UI_TIMEOUT_MILLIS = 2_000L
        private const val IDLE_TIMEOUT_MILLIS = 2_000L
        private const val HOME_SCROLL_BOTTOM_FRACTION = 0.78f
        private const val HOME_SCROLL_TOP_FRACTION = 0.30f
        private const val HOME_SCROLL_PASSES = 2
        private const val HOME_SCROLL_STEPS = 12
    }
}
