package com.projectlumen.app.core.update

import org.json.JSONObject

object BuildUpdateNotesParser {
    fun parseOrFallback(
        json: String?,
        currentBuild: BuildMetadata,
    ): BuildUpdateNotes {
        if (json.isNullOrBlank()) return fallback(currentBuild)

        return runCatching {
            val root = JSONObject(json)
            val assetCommitHash = root.requiredString("commitHash")
            val assetBuildTimeUtcMillis = root.requiredLong("buildTimeUtcMillis")
            require(assetCommitHash == currentBuild.commitHash) { "Packaged notes commit does not match this build" }
            require(assetBuildTimeUtcMillis == currentBuild.buildTimeUtcMillis) {
                "Packaged notes build time does not match this build"
            }

            val highlights = root.optJSONArray("highlights")
                ?.let { array ->
                    buildList {
                        repeat(array.length()) { index ->
                            array.optString(index)
                                .trim()
                                .takeIf(String::isNotEmpty)
                                ?.let(::add)
                        }
                    }
                }
                .orEmpty()

            BuildUpdateNotes(
                versionName = currentBuild.versionName,
                versionCode = currentBuild.versionCode,
                commitHash = currentBuild.commitHash,
                shortHash = currentBuild.shortHash,
                buildTimeUtcMillis = currentBuild.buildTimeUtcMillis,
                title = root.optString("title").trim().ifBlank { fallbackTitle(currentBuild) },
                bodyMarkdownOrPlain = root.optString("body").trim(),
                highlights = highlights,
            )
        }.getOrElse { fallback(currentBuild) }
    }

    fun fallback(currentBuild: BuildMetadata): BuildUpdateNotes {
        return BuildUpdateNotes(
            versionName = currentBuild.versionName,
            versionCode = currentBuild.versionCode,
            commitHash = currentBuild.commitHash,
            shortHash = currentBuild.shortHash,
            buildTimeUtcMillis = currentBuild.buildTimeUtcMillis,
            title = fallbackTitle(currentBuild),
            bodyMarkdownOrPlain = "",
            highlights = emptyList(),
        )
    }

    private fun fallbackTitle(currentBuild: BuildMetadata): String {
        return "Project Lumen ${currentBuild.versionName}"
    }

    private fun JSONObject.requiredString(name: String): String {
        require(has(name) && !isNull(name)) { "Missing $name" }
        return getString(name).trim().also { require(it.isNotEmpty()) { "Empty $name" } }
    }

    private fun JSONObject.requiredLong(name: String): Long {
        require(has(name) && !isNull(name)) { "Missing $name" }
        return getLong(name)
    }
}
