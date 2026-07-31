package com.projectlumen.app.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildUpdateNotesParserTest {
    private val currentBuild = BuildMetadata(
        versionName = "1.2.3",
        versionCode = 42,
        buildTimeUtcMillis = 1_722_222_222_000L,
        commitHash = "abcdef0123456789abcdef0123456789abcdef01",
        shortHash = "abcdef01",
    )

    @Test
    fun validAssetUsesPackagedContentAndCurrentIdentity() {
        val notes = BuildUpdateNotesParser.parseOrFallback(
            json = assetJson(
                body = "Detailed change body",
                highlights = "[\"First highlight\", \"Second highlight\"]",
            ),
            currentBuild = currentBuild,
        )

        assertEquals("Improve first-open flow", notes.title)
        assertEquals("Detailed change body", notes.bodyMarkdownOrPlain)
        assertEquals(listOf("First highlight", "Second highlight"), notes.highlights)
        assertEquals(currentBuild.commitHash, notes.commitHash)
        assertEquals(currentBuild.buildTimeUtcMillis, notes.buildTimeUtcMillis)
    }

    @Test
    fun emptyBodyRemainsAValidPackagedNote() {
        val notes = BuildUpdateNotesParser.parseOrFallback(
            json = assetJson(body = "", highlights = "[]"),
            currentBuild = currentBuild,
        )

        assertEquals("Improve first-open flow", notes.title)
        assertTrue(notes.bodyMarkdownOrPlain.isEmpty())
        assertTrue(notes.highlights.isEmpty())
    }

    @Test
    fun mismatchedCommitFallsBackToCurrentBuildIdentity() {
        val notes = BuildUpdateNotesParser.parseOrFallback(
            json = assetJson(commitHash = "ffffffffffffffffffffffffffffffffffffffff"),
            currentBuild = currentBuild,
        )

        assertEquals("Project Lumen ${currentBuild.versionName}", notes.title)
        assertEquals(currentBuild.commitHash, notes.commitHash)
        assertTrue(notes.bodyMarkdownOrPlain.isEmpty())
        assertTrue(notes.highlights.isEmpty())
    }

    @Test
    fun mismatchedBuildTimeFallsBackToCurrentBuildIdentity() {
        val notes = BuildUpdateNotesParser.parseOrFallback(
            json = assetJson(buildTimeUtcMillis = currentBuild.buildTimeUtcMillis - 1L),
            currentBuild = currentBuild,
        )

        assertEquals("Project Lumen ${currentBuild.versionName}", notes.title)
        assertEquals(currentBuild.buildTimeUtcMillis, notes.buildTimeUtcMillis)
        assertTrue(notes.bodyMarkdownOrPlain.isEmpty())
    }

    @Test
    fun corruptJsonFallsBackWithoutThrowing() {
        val notes = BuildUpdateNotesParser.parseOrFallback(
            json = "{not-json",
            currentBuild = currentBuild,
        )

        assertEquals("Project Lumen ${currentBuild.versionName}", notes.title)
        assertEquals(currentBuild.shortHash, notes.shortHash)
        assertTrue(notes.bodyMarkdownOrPlain.isEmpty())
    }

    private fun assetJson(
        commitHash: String = currentBuild.commitHash,
        buildTimeUtcMillis: Long = currentBuild.buildTimeUtcMillis,
        body: String = "Detailed change body",
        highlights: String = "[]",
    ): String {
        return """
            {
              "versionName": "9.9.9",
              "versionCode": 999,
              "commitHash": "$commitHash",
              "shortHash": "ffffffff",
              "buildTimeUtcMillis": $buildTimeUtcMillis,
              "title": "Improve first-open flow",
              "body": "$body",
              "highlights": $highlights
            }
        """.trimIndent()
    }
}
