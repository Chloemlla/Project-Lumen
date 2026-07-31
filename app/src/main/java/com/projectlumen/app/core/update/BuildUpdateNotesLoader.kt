package com.projectlumen.app.core.update

import android.content.Context

class BuildUpdateNotesLoader(context: Context) {
    private val assets = context.applicationContext.assets
    private val cacheLock = Any()

    @Volatile
    private var cachedBuild: BuildMetadata? = null

    @Volatile
    private var cachedNotes: BuildUpdateNotes? = null

    fun load(currentBuild: BuildMetadata = BuildMetadata.current()): BuildUpdateNotes {
        cachedNotes?.takeIf { cachedBuild == currentBuild }?.let { return it }

        return synchronized(cacheLock) {
            cachedNotes?.takeIf { cachedBuild == currentBuild } ?: run {
                val json = runCatching {
                    assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull()
                BuildUpdateNotesParser.parseOrFallback(json, currentBuild).also { notes ->
                    cachedBuild = currentBuild
                    cachedNotes = notes
                }
            }
        }
    }

    private companion object {
        private const val ASSET_NAME = "build-update-notes.json"
    }
}
