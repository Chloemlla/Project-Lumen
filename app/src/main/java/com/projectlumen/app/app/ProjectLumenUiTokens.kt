package com.projectlumen.app.app

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

internal data class LumenUiTokens(
    val topBar: LumenTopBarTokens = LumenTopBarTokens(),
    val page: LumenPageTokens = LumenPageTokens(),
) {
    companion object {
        fun load(context: Context): LumenUiTokens {
            return runCatching {
                context.assets.open("lumen-ui-tokens.json").bufferedReader(Charsets.UTF_8).use { reader ->
                    fromJson(JSONObject(reader.readText()))
                }
            }.getOrElse {
                LumenUiTokens()
            }
        }

        private fun fromJson(json: JSONObject): LumenUiTokens {
            return LumenUiTokens(
                topBar = LumenTopBarTokens.fromJson(json.optJSONObject("topBar")),
                page = LumenPageTokens.fromJson(json.optJSONObject("page")),
            )
        }
    }
}

internal data class LumenTopBarTokens(
    val containerStartPaddingDp: Float = 4f,
    val containerEndPaddingDp: Float = 12f,
    val contentTopGapDp: Float = 0f,
    val contentHeightDp: Float = 64f,
    val contentBottomGapDp: Float = 0f,
    val collapseThresholdDp: Float = 96f,
    val primaryTitleStartDp: Float = 16f,
    val secondaryLeadingWidthDp: Float = 48f,
    val titleFontSizeSp: Float = 22f,
    val titleFontWeight: Int = 600,
    val titleMaxLines: Int = 2,
    val primaryColor: Color? = null,
    val onPrimaryColor: Color? = null,
) {
    companion object {
        fun fromJson(json: JSONObject?): LumenTopBarTokens {
            val fallback = LumenTopBarTokens()
            if (json == null) return fallback
            return fallback.copy(
                containerStartPaddingDp = json.optFloat("containerStartPaddingDp", fallback.containerStartPaddingDp),
                containerEndPaddingDp = json.optFloat("containerEndPaddingDp", fallback.containerEndPaddingDp),
                contentTopGapDp = json.optFloat("contentTopGapDp", fallback.contentTopGapDp),
                contentHeightDp = json.optFloat("contentHeightDp", fallback.contentHeightDp),
                contentBottomGapDp = json.optFloat("contentBottomGapDp", fallback.contentBottomGapDp),
                collapseThresholdDp = json.optFloat("collapseThresholdDp", fallback.collapseThresholdDp),
                primaryTitleStartDp = json.optFloat("primaryTitleStartDp", fallback.primaryTitleStartDp),
                secondaryLeadingWidthDp = json.optFloat("secondaryLeadingWidthDp", fallback.secondaryLeadingWidthDp),
                titleFontSizeSp = json.optFloat("titleFontSizeSp", fallback.titleFontSizeSp),
                titleFontWeight = json.optInt("titleFontWeight", fallback.titleFontWeight).coerceIn(100, 900),
                titleMaxLines = json.optInt("titleMaxLines", fallback.titleMaxLines).coerceIn(1, 4),
                primaryColor = json.optColor("primaryColor"),
                onPrimaryColor = json.optColor("onPrimaryColor"),
            )
        }
    }
}

internal data class LumenPageTokens(
    val maxContentWidthDp: Float = 720f,
    val contentPaddingStartDp: Float = 12f,
    val contentPaddingTopDp: Float = 8f,
    val contentPaddingEndDp: Float = 12f,
    val contentPaddingBottomDp: Float = 24f,
    val sectionGapDp: Float = 10f,
) {
    companion object {
        fun fromJson(json: JSONObject?): LumenPageTokens {
            val fallback = LumenPageTokens()
            if (json == null) return fallback
            return fallback.copy(
                maxContentWidthDp = json.optFloat("maxContentWidthDp", fallback.maxContentWidthDp),
                contentPaddingStartDp = json.optFloat("contentPaddingStartDp", fallback.contentPaddingStartDp),
                contentPaddingTopDp = json.optFloat("contentPaddingTopDp", fallback.contentPaddingTopDp),
                contentPaddingEndDp = json.optFloat("contentPaddingEndDp", fallback.contentPaddingEndDp),
                contentPaddingBottomDp = json.optFloat("contentPaddingBottomDp", fallback.contentPaddingBottomDp),
                sectionGapDp = json.optFloat("sectionGapDp", fallback.sectionGapDp),
            )
        }
    }
}

@Composable
internal fun rememberLumenUiTokens(context: Context): LumenUiTokens {
    return remember(context) { LumenUiTokens.load(context) }
}

private fun JSONObject.optFloat(name: String, fallback: Float): Float {
    return if (has(name)) optDouble(name, fallback.toDouble()).toFloat() else fallback
}

private fun JSONObject.optColor(name: String): Color? {
    val value = optString(name, "").takeIf { it.isNotBlank() && it != "null" } ?: return null
    return runCatching { Color(AndroidColor.parseColor(value)) }.getOrNull()
}
