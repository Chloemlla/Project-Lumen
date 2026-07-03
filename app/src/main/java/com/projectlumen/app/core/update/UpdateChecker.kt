package com.projectlumen.app.core.update

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import com.projectlumen.app.core.api.ProjectLumenApiConfig
import com.projectlumen.app.core.security.ProjectLumenRequestSigner
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant

class UpdateChecker(
    private val context: Context,
    private val backendBaseUrl: String = ProjectLumenApiConfig.baseUrl,
    private val githubReleaseApiUrl: String = PROJECT_LUMEN_RELEASE_API,
    private val channel: String = DEFAULT_CHANNEL,
) {
    fun checkForUpdate(currentBuild: BuildMetadata = BuildMetadata.current()): UpdateCandidate? {
        when (val backendResult = runCatching { fetchBackendReleaseManifest(currentBuild) }.getOrNull()) {
            is BackendReleaseResult.Update -> return backendResult.candidate
            BackendReleaseResult.NoUpdate -> return null
            null -> Unit
        }

        val latest = fetchLatestGitHubRelease() ?: return null
        val localVersion = parseVersionDescriptor("${currentBuild.versionName}-${currentBuild.shortHash}")
            ?: parseVersionDescriptor(currentBuild.versionName)
            ?: return null
        if (isExactVersionMatch(latest.tagName, localVersion)) return null

        val versionComparison = compareReleaseVersion(latest.tagName, localVersion)
        val publishTimeNewer = latest.publishedAtUtcMillis > currentBuild.buildTimeUtcMillis + PUBLISH_TIME_TOLERANCE_MILLIS

        val shouldUpdate = versionComparison > 0 || publishTimeNewer
        if (!shouldUpdate) return null

        return UpdateCandidate(
            currentBuild = currentBuild,
            release = latest,
            matchedAsset = selectBestAsset(latest.assets),
            matchReason = if (versionComparison > 0) UpdateMatchReason.SEMANTIC_VERSION else UpdateMatchReason.PUBLISHED_AT,
        )
    }

    private fun fetchBackendReleaseManifest(currentBuild: BuildMetadata): BackendReleaseResult {
        val requestUrl = buildBackendReleaseCheckUrl(currentBuild)
        val connection = openHttpConnection(requestUrl).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            ProjectLumenRequestSigner.headers("GET", requestUrl.toHttpUrl(), null)
                .forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Backend release manifest request failed with HTTP ${connection.responseCode}")
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            if (!json.optBoolean("updateAvailable")) return BackendReleaseResult.NoUpdate
            val versionCode = json.optLong("versionCode", 0L)
            if (versionCode <= currentBuild.versionCode.toLong()) return BackendReleaseResult.NoUpdate

            val release = parseBackendReleaseInfo(json)
            val matchedAsset = selectBestAsset(release.assets)
                ?: throw IOException("Backend release manifest did not include a verified APK asset.")
            return BackendReleaseResult.Update(
                UpdateCandidate(
                    currentBuild = currentBuild,
                    release = release,
                    matchedAsset = matchedAsset,
                    matchReason = UpdateMatchReason.VERSION_CODE,
                ),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchLatestGitHubRelease(): ReleaseInfo? {
        val connection = openHttpConnection(githubReleaseApiUrl).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub release request failed with HTTP ${connection.responseCode}")
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(payload)
            val releaseAssets = parseReleaseAssets(json)
            val checksums = parseSha256Checksums(json.optString("body")) + fetchSha256ChecksumAssets(releaseAssets)
            return ReleaseInfo(
                tagName = json.optString("tag_name").orEmpty(),
                releaseName = json.optString("name").ifBlank { json.optString("tag_name").orEmpty() },
                body = json.optString("body"),
                htmlUrl = json.optString("html_url").orEmpty(),
                publishedAtUtcMillis = parseInstant(json.optString("published_at"))
                    ?: throw IOException("Release published_at is missing or invalid"),
                assets = releaseAssets.map { asset ->
                    if (asset.name.endsWith(".apk", ignoreCase = true)) {
                        asset.copy(sha256 = checksums[normalizeChecksumName(asset.name)])
                    } else {
                        asset
                    }
                },
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseBackendReleaseInfo(json: JSONObject): ReleaseInfo {
        val versionName = json.optString("versionName").ifBlank { json.optLong("versionCode").toString() }
        val tagName = json.optString("tagName").ifBlank { "v$versionName" }
        val releaseUrl = json.optString("releaseUrl")
        val createdAt = json.optLong("createdAt").takeIf { it > 0L }
            ?: json.optLong("checkedAt").takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val releaseAssets = parseBackendReleaseAssets(json)
        val selectedSha256 = json.optString("fullApkSha256").ifBlank { json.optString("sha256") }
        val selectedAssetUrl = json.optString("fullApkUrl")
        val selectedAssetName = selectedAssetUrl.substringAfterLast('/').ifBlank {
            "Project-Lumen_android_${versionName}_${json.optString("abi", "universal")}.apk"
        }
        val assets = if (selectedAssetUrl.isNotBlank() && selectedSha256.isNotBlank()) {
            val selected = ReleaseAsset(
                name = selectedAssetName,
                downloadUrl = selectedAssetUrl,
                contentType = "application/vnd.android.package-archive",
                sha256 = selectedSha256.lowercase(),
                abi = json.optString("abi", "universal"),
                sizeBytes = json.optLong("fullApkSizeBytes").takeIf { it > 0L },
            )
            (listOf(selected) + releaseAssets)
                .distinctBy { it.downloadUrl.lowercase() }
        } else {
            releaseAssets
        }
        return ReleaseInfo(
            tagName = tagName,
            releaseName = json.optString("releaseName").ifBlank { tagName },
            body = buildBackendReleaseBody(json),
            htmlUrl = releaseUrl,
            publishedAtUtcMillis = createdAt,
            assets = assets,
            versionCode = json.optLong("versionCode"),
            rollout = json.optString("rollout"),
            forceUpdate = json.optBoolean("forceUpdate"),
            channel = json.optString("channel", DEFAULT_CHANNEL),
            patches = parseBackendReleasePatches(json.optJSONArray("patches")),
        )
    }

    private fun parseBackendReleaseAssets(json: JSONObject): List<ReleaseAsset> {
        return json.optJSONArray("assets")
            ?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val asset = array.optJSONObject(index) ?: continue
                        val url = asset.optString("url").ifBlank { asset.optString("downloadUrl") }
                        val sha256 = asset.optString("sha256")
                        if (url.isBlank() || sha256.isBlank()) continue
                        add(
                            ReleaseAsset(
                                name = asset.optString("name").ifBlank {
                                    url.substringAfterLast('/').ifBlank { "Project-Lumen_android.apk" }
                                },
                                downloadUrl = url,
                                contentType = asset.optString("contentType").takeIf { it.isNotBlank() },
                                sha256 = sha256.lowercase(),
                                abi = asset.optString("abi").takeIf { it.isNotBlank() },
                                sizeBytes = asset.optLong("sizeBytes").takeIf { it > 0L },
                            ),
                        )
                    }
                }
            }
            .orEmpty()
    }

    private fun parseBackendReleasePatches(array: JSONArray?): List<ReleasePatch> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val patch = array.optJSONObject(index) ?: continue
                val patchUrl = patch.optString("patchUrl")
                val patchSha256 = patch.optString("patchSha256")
                if (patchUrl.isBlank() || patchSha256.isBlank()) continue
                add(
                    ReleasePatch(
                        fromVersionCode = patch.optLong("fromVersionCode"),
                        fromSha256 = patch.optString("fromSha256").lowercase(),
                        toSha256 = patch.optString("toSha256").lowercase(),
                        patchUrl = patchUrl,
                        patchSha256 = patchSha256.lowercase(),
                        algorithm = patch.optString("algorithm").ifBlank { "bsdiff" },
                        sizeBytes = patch.optLong("sizeBytes").takeIf { it > 0L },
                    ),
                )
            }
        }
    }

    private fun buildBackendReleaseBody(json: JSONObject): String {
        val rollout = json.optString("rollout").takeIf { it.isNotBlank() } ?: "100%"
        val channel = json.optString("channel", DEFAULT_CHANNEL)
        val forceUpdate = json.optBoolean("forceUpdate")
        return "Channel: $channel\nRollout: $rollout\nForce update: $forceUpdate"
    }

    private fun buildBackendReleaseCheckUrl(currentBuild: BuildMetadata): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.takeIf { it.isNotBlank() } ?: "universal"
        val rolloutKey = deviceRolloutKey()
        return buildString {
            append(backendBaseUrl.trimEnd('/'))
            append("/v1/releases/check?currentVersionCode=")
            append(currentBuild.versionCode)
            append("&abi=")
            append(queryEncode(abi))
            append("&channel=")
            append(queryEncode(channel.ifBlank { DEFAULT_CHANNEL }))
            if (rolloutKey.isNotBlank()) {
                append("&rolloutKey=")
                append(queryEncode(rolloutKey))
            }
        }
    }

    private fun deviceRolloutKey(): String {
        return runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
    }

    private fun queryEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    private fun parseReleaseAssets(json: JSONObject): List<ReleaseAsset> {
        return json.optJSONArray("assets")
            ?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val asset = array.optJSONObject(index) ?: continue
                        val name = asset.optString("name").orEmpty()
                        val downloadUrl = asset.optString("browser_download_url").orEmpty()
                        if (name.isBlank() || downloadUrl.isBlank()) continue
                        add(
                            ReleaseAsset(
                                name = name,
                                downloadUrl = downloadUrl,
                                contentType = asset.optString("content_type").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            }
            .orEmpty()
    }

    private fun fetchSha256ChecksumAssets(assets: List<ReleaseAsset>): Map<String, String> {
        return assets
            .filter { asset ->
                !asset.name.endsWith(".apk", ignoreCase = true) &&
                    normalizeName(asset.name).let { it.contains("checksum") || it.contains("sha256") }
            }
            .fold(emptyMap()) { checksums, asset ->
                val assetChecksums = fetchTextAsset(asset.downloadUrl)
                    ?.let(::parseSha256Checksums)
                    .orEmpty()
                checksums + assetChecksums
            }
    }

    private fun fetchTextAsset(url: String): String? {
        val connection = openHttpConnection(url).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "text/plain, application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSha256Checksums(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        return buildMap {
            text.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                val match = SHA256_REGEX.find(line) ?: return@forEach
                val hash = match.value.lowercase()
                val beforeHash = line.substring(0, match.range.first)
                val afterHash = line.substring(match.range.last + 1)
                val assetName = (apkFileNames(afterHash) + apkFileNames(beforeHash)).firstOrNull()
                    ?: return@forEach
                put(normalizeChecksumName(assetName), hash)
            }
        }
    }

    private fun apkFileNames(value: String): List<String> {
        return APK_FILE_NAME_REGEX.findAll(value)
            .map { it.value.substringAfterLast('/') }
            .toList()
    }

    private fun normalizeChecksumName(value: String): String {
        return value.substringAfterLast('/')
            .lowercase()
            .trim()
    }

    private fun openHttpConnection(url: String): HttpURLConnection {
        val parsedUrl = URL(url)
        if (parsedUrl.protocol != "https") {
            throw IOException("Update endpoints must use HTTPS.")
        }
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
            ?: return parsedUrl.openConnection() as HttpURLConnection
        return network.openConnection(parsedUrl) as HttpURLConnection
    }

    private fun compareReleaseVersion(remoteTagName: String, localVersion: VersionDescriptor): Int {
        val remote = parseVersionDescriptor(remoteTagName) ?: return 0
        return remote.semanticVersion.compareTo(localVersion.semanticVersion)
    }

    private fun isExactVersionMatch(remoteTagName: String, localVersion: VersionDescriptor): Boolean {
        val remote = parseVersionDescriptor(remoteTagName) ?: return false
        return remote.semanticVersion == localVersion.semanticVersion &&
            remote.shortHash.isNotBlank() &&
            localVersion.shortHash.isNotBlank() &&
            remote.shortHash.equals(localVersion.shortHash, ignoreCase = true)
    }

    private fun parseVersionDescriptor(value: String): VersionDescriptor? {
        val cleaned = value.trim()
        if (cleaned.isBlank()) return null

        val versionPart = cleaned
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('(')
            .substringBefore('+')
            .substringBefore('-')

        val shortHash = extractShortHash(cleaned)
        val parts = versionPart.split('.')
            .mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null
        return VersionDescriptor(
            semanticVersion = SemanticVersion(
                major = parts.getOrNull(0) ?: 0,
                minor = parts.getOrNull(1) ?: 0,
                patch = parts.getOrNull(2) ?: 0,
            ),
            shortHash = shortHash,
        )
    }

    private fun extractShortHash(value: String): String {
        val bracketMatch = SHORT_HASH_IN_PARENS_REGEX.find(value)
        if (bracketMatch != null) return bracketMatch.groupValues[1]

        val suffixMatch = SHORT_HASH_SUFFIX_REGEX.find(value)
        if (suffixMatch != null) return suffixMatch.groupValues[1]

        return ""
    }

    private fun selectBestAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        val apkAssets = assets.filter {
            it.name.endsWith(".apk", ignoreCase = true) && !it.sha256.isNullOrBlank()
        }
        if (apkAssets.isEmpty()) return null

        val preferredAbis = Build.SUPPORTED_ABIS.map { normalizeAbiToken(it) }
        val scored = apkAssets.mapNotNull { asset ->
            val normalizedName = normalizeName(asset.name)
            val normalizedAssetAbi = asset.abi?.let(::normalizeAbiToken).orEmpty()
            val abiScore = preferredAbis.indexOfFirst { abi ->
                abi.isNotBlank() && (normalizedAssetAbi == abi || normalizedName.contains(abi))
            }
            val fallbackScore = when {
                normalizedAssetAbi == "universal" -> 10_000
                normalizedAssetAbi == "all" -> 10_001
                normalizedName.contains("universal") -> 10_000
                normalizedName.contains("all") -> 10_001
                else -> 20_000
            }
            if (abiScore >= 0) {
                asset to abiScore
            } else {
                asset to fallbackScore
            }
        }
        return scored.minWithOrNull(compareBy<Pair<ReleaseAsset, Int>> { it.second }.thenBy { it.first.name.length })?.first
    }

    private fun normalizeName(value: String): String {
        return value.lowercase()
            .replace('-', '_')
            .replace('.', '_')
            .replace(' ', '_')
    }

    private fun normalizeAbiToken(value: String): String {
        return value.lowercase()
            .replace('-', '_')
            .replace('.', '_')
    }

    private fun parseInstant(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            return compareValuesBy(this, other, SemanticVersion::major, SemanticVersion::minor, SemanticVersion::patch)
        }
    }

    private data class VersionDescriptor(
        val semanticVersion: SemanticVersion,
        val shortHash: String,
    )

    private sealed interface BackendReleaseResult {
        data object NoUpdate : BackendReleaseResult
        data class Update(val candidate: UpdateCandidate) : BackendReleaseResult
    }

    private companion object {
        private const val REQUEST_TIMEOUT_MILLIS = 6_000
        private const val PUBLISH_TIME_TOLERANCE_MILLIS = 90_000L
        private const val USER_AGENT = "Project-Lumen"
        private const val DEFAULT_CHANNEL = "stable"
        private const val PROJECT_LUMEN_RELEASE_API = "https://api.github.com/repos/Chloemlla/Project-Lumen/releases/latest"
        private val SHORT_HASH_IN_PARENS_REGEX = Regex("""\(([0-9a-fA-F]{7,40})\)$""")
        private val SHORT_HASH_SUFFIX_REGEX = Regex("""(?:-|_)([0-9a-fA-F]{7,40})$""")
        private val SHA256_REGEX = Regex("""\b[0-9a-fA-F]{64}\b""")
        private val APK_FILE_NAME_REGEX = Regex("""[A-Za-z0-9._+-]+\.apk""", RegexOption.IGNORE_CASE)
    }
}
