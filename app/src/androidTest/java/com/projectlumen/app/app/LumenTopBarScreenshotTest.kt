package com.projectlumen.app.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.ui.theme.ProjectLumenTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LumenTopBarScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun capturesTopBarAlignment() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokens = LumenUiTokens.load(context)
        val deltaDp = tokens.topBar.secondaryLeadingWidthDp - tokens.topBar.primaryTitleStartDp
        assertTrue(
            "Secondary leading width should leave primary title slightly left of the back-button title, delta=$deltaDp",
            deltaDp in 1f..24f,
        )

        composeRule.setContent {
            ProjectLumenTheme(
                themeMode = AppThemeMode.LIGHT,
                useDynamicColors = false,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                ) {
                    LumenTopBar(title = "Home", onNavigateBack = null)
                    LumenTopBar(title = "Templates", onNavigateBack = {})
                }
            }
        }

        composeRule.waitForIdle()
        saveScreenshot(
            fileName = "lumen-top-bar-alignment.png",
            bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap(),
        )
    }

    private fun saveScreenshot(fileName: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val file = File(directory, fileName)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
