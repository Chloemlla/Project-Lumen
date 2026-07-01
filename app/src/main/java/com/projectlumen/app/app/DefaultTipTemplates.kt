package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.TipTemplateEntity
import org.json.JSONObject

internal object DefaultTipTemplates {
    val builtinIds = listOf(1L, 2L, 3L, 4L, 5L, 6L)

    fun create(nowMillis: Long): List<TipTemplateEntity> {
        return listOf(
            TipTemplateEntity(
                id = 1L,
                name = "Electric City Nights",
                backgroundValue = "#1E1E1E",
                primaryColor = "#0085FF",
                layoutJson = paletteJson(
                    primary100 = "#0085ff",
                    primary200 = "#69b4ff",
                    primary300 = "#e0ffff",
                    accent100 = "#006fff",
                    accent200 = "#e1ffff",
                    text100 = "#FFFFFF",
                    text200 = "#9e9e9e",
                    bg100 = "#1E1E1E",
                    bg200 = "#2d2d2d",
                    bg300 = "#454545",
                ),
                sortOrder = 0,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 2L,
                name = "White with blue",
                backgroundValue = "#FFFFFF",
                primaryColor = "#0077C2",
                layoutJson = paletteJson(
                    primary100 = "#0077C2",
                    primary200 = "#59a5f5",
                    primary300 = "#c8ffff",
                    accent100 = "#00BFFF",
                    accent200 = "#00619a",
                    text100 = "#333333",
                    text200 = "#5c5c5c",
                    bg100 = "#FFFFFF",
                    bg200 = "#f5f5f5",
                    bg300 = "#cccccc",
                ),
                sortOrder = 1,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 3L,
                name = "Turquoise",
                backgroundValue = "#E0F2F1",
                primaryColor = "#26A69A",
                layoutJson = paletteJson(
                    primary100 = "#26A69A",
                    primary200 = "#408d86",
                    primary300 = "#cdfaf6",
                    accent100 = "#80CBC4",
                    accent200 = "#43A49B",
                    text100 = "#263339",
                    text200 = "#728f9e",
                    bg100 = "#E0F2F1",
                    bg200 = "#D0EBEA",
                    bg300 = "#FFFFFF",
                ),
                sortOrder = 2,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 4L,
                name = "DE283B",
                backgroundValue = "#ffffff",
                primaryColor = "#de283b",
                layoutJson = paletteJson(
                    primary100 = "#de283b",
                    primary200 = "#ff6366",
                    primary300 = "#ffccc4",
                    accent100 = "#25b1bf",
                    accent200 = "#005461",
                    text100 = "#1a1a1a",
                    text200 = "#404040",
                    bg100 = "#ffffff",
                    bg200 = "#f5f5f5",
                    bg300 = "#cccccc",
                ),
                sortOrder = 3,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 5L,
                name = "Dance network",
                backgroundValue = "#F5F5F5",
                primaryColor = "#FF4081",
                layoutJson = paletteJson(
                    primary100 = "#FF4081",
                    primary200 = "#ff79b0",
                    primary300 = "#ffe4ff",
                    accent100 = "#00E5FF",
                    accent200 = "#00829b",
                    text100 = "#333333",
                    text200 = "#5c5c5c",
                    bg100 = "#F5F5F5",
                    bg200 = "#ebebeb",
                    bg300 = "#c2c2c2",
                ),
                sortOrder = 4,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 6L,
                name = "Orange Flat Shadow",
                backgroundValue = "#ffffff",
                primaryColor = "#FF6600",
                layoutJson = paletteJson(
                    primary100 = "#FF6600",
                    primary200 = "#ff983f",
                    primary300 = "#ffffa1",
                    accent100 = "#F5F5F5",
                    accent200 = "#929292",
                    text100 = "#1d1f21",
                    text200 = "#444648",
                    bg100 = "#ffffff",
                    bg200 = "#f5f5f5",
                    bg300 = "#cccccc",
                ),
                sortOrder = 5,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
        )
    }

    private fun paletteJson(
        primary100: String,
        primary200: String,
        primary300: String,
        accent100: String,
        accent200: String,
        text100: String,
        text200: String,
        bg100: String,
        bg200: String,
        bg300: String,
    ): String = JSONObject()
        .put("countdownStyle", COUNTDOWN_STYLE_CIRCLE)
        .put(
            "palette",
            JSONObject()
                .put("primary-100", primary100)
                .put("primary-200", primary200)
                .put("primary-300", primary300)
                .put("accent-100", accent100)
                .put("accent-200", accent200)
                .put("text-100", text100)
                .put("text-200", text200)
                .put("bg-100", bg100)
                .put("bg-200", bg200)
                .put("bg-300", bg300),
        )
        .toString()
}
