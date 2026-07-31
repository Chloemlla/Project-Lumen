package com.projectlumen.app.core.update

data class BuildUpdateNotes(
    val versionName: String,
    val versionCode: Int,
    val commitHash: String,
    val shortHash: String,
    val buildTimeUtcMillis: Long,
    val title: String,
    val bodyMarkdownOrPlain: String,
    val highlights: List<String>,
)
