package com.projectlumen.app.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

class ShizukuCapabilityManager(
    private val context: Context,
) {
    private val _state = MutableStateFlow(ShizukuCapabilityState())
    val state = _state.asStateFlow()
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            refreshState()
        }
    }

    init {
        runCatching { Shizuku.addRequestPermissionResultListener(permissionResultListener) }
        refreshState()
    }

    fun refreshState() {
        _state.value = queryState()
    }

    fun requestPermission() {
        val current = queryState()
        _state.value = current
        if (!current.binderAvailable || current.permissionGranted) return
        if (!current.permissionRequestable) return
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
            .onFailure { throwable -> _state.value = queryState(error = throwable.message.orEmpty()) }
    }

    suspend fun refreshForegroundContext(): ShizukuForegroundContext? = withContext(Dispatchers.IO) {
        val baseState = queryState()
        if (!baseState.ready) {
            _state.value = baseState
            return@withContext null
        }
        val output = withTimeoutOrNull(FOREGROUND_QUERY_TIMEOUT_MS) {
            runPrivilegedCommand(FOREGROUND_CONTEXT_COMMAND)
        }
        val context = output?.lineSequence()
            ?.mapNotNull(::parseActivityComponent)
            ?.firstOrNull()
        val foregroundContext = context?.let { (packageName, activityName) ->
            val category = classifyForegroundContext(packageName, activityName)
            ShizukuForegroundContext(
                packageName = packageName,
                activityName = activityName,
                category = category,
                shouldDeferSampling = category != CATEGORY_NORMAL,
            )
        }
        _state.value = baseState.copy(
            foregroundPackage = foregroundContext?.packageName.orEmpty(),
            foregroundActivity = foregroundContext?.activityName.orEmpty(),
            foregroundCategory = foregroundContext?.category.orEmpty(),
            foregroundShouldDeferSampling = foregroundContext?.shouldDeferSampling == true,
            lastCheckedAt = System.currentTimeMillis(),
            lastError = if (output == null) "Unable to query foreground context." else "",
        )
        foregroundContext
    }

    suspend fun shouldDeferSampling(settings: AppSettingsEntity): Boolean {
        if (!settings.shizukuAdvancedModeEnabled || !settings.shizukuContextAwareSamplingEnabled) {
            return false
        }
        return refreshForegroundContext()?.shouldDeferSampling == true
    }

    fun isReady(): Boolean {
        val current = queryState()
        _state.value = current
        return current.ready
    }

    private fun queryState(error: String = ""): ShizukuCapabilityState {
        val binderAvailable = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAvailable) {
            return ShizukuCapabilityState(
                binderAvailable = false,
                permissionRequestable = false,
                lastCheckedAt = System.currentTimeMillis(),
                lastError = error,
            )
        }
        val permissionGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val permissionRequestable = !permissionGranted && runCatching {
            !Shizuku.shouldShowRequestPermissionRationale()
        }.getOrDefault(false)
        return ShizukuCapabilityState(
            binderAvailable = true,
            permissionGranted = permissionGranted,
            permissionRequestable = permissionRequestable,
            serverVersion = runCatching { Shizuku.getVersion() }.getOrDefault(0),
            serverUid = runCatching { Shizuku.getUid() }.getOrDefault(0),
            foregroundPackage = _state.value.foregroundPackage,
            foregroundActivity = _state.value.foregroundActivity,
            foregroundCategory = _state.value.foregroundCategory,
            foregroundShouldDeferSampling = _state.value.foregroundShouldDeferSampling,
            lastCheckedAt = System.currentTimeMillis(),
            lastError = error,
        )
    }

    private fun runPrivilegedCommand(command: String): String {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(MAX_COMMAND_OUTPUT_BYTES) }
        val error = process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(MAX_COMMAND_OUTPUT_BYTES / 4) }
        runCatching { process.waitFor() }
        return output.ifBlank { error }
    }

    private fun parseActivityComponent(line: String): Pair<String, String>? {
        if (!foregroundLineHints.any { line.contains(it, ignoreCase = true) }) return null
        val match = componentRegex.find(line) ?: return null
        val packageName = match.groupValues.getOrNull(1).orEmpty()
        val rawActivity = match.groupValues.getOrNull(2).orEmpty()
        if (packageName.isBlank() || rawActivity.isBlank()) return null
        val activityName = if (rawActivity.startsWith(".")) "$packageName$rawActivity" else rawActivity
        return packageName to activityName
    }

    private fun classifyForegroundContext(packageName: String, activityName: String): String {
        val combined = "$packageName $activityName".lowercase()
        return when {
            sensitiveCallHints.any { combined.contains(it) } -> CATEGORY_COMMUNICATION
            sensitiveCameraHints.any { combined.contains(it) } -> CATEGORY_CAMERA
            sensitiveMediaHints.any { combined.contains(it) } -> CATEGORY_MEDIA
            sensitiveGameHints.any { combined.contains(it) } -> CATEGORY_GAME
            else -> CATEGORY_NORMAL
        }
    }

    private companion object {
        private const val PERMISSION_REQUEST_CODE = 42017
        private const val FOREGROUND_QUERY_TIMEOUT_MS = 2_500L
        private const val MAX_COMMAND_OUTPUT_BYTES = 96_000
        private const val FOREGROUND_CONTEXT_COMMAND =
            "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity|mCurrentFocus' | head -n 8"
        private const val CATEGORY_NORMAL = "normal"
        private const val CATEGORY_CAMERA = "camera"
        private const val CATEGORY_COMMUNICATION = "communication"
        private const val CATEGORY_MEDIA = "media"
        private const val CATEGORY_GAME = "game"

        private val foregroundLineHints = listOf(
            "topResumedActivity",
            "mResumedActivity",
            "ResumedActivity",
            "mCurrentFocus",
        )
        private val componentRegex = Regex("""\b([A-Za-z][A-Za-z0-9_.]*)/([A-Za-z0-9_.$]+|\.[A-Za-z0-9_.$]+)""")
        private val sensitiveCameraHints = listOf("camera", "camerax", "scanner", "barcode", "qr")
        private val sensitiveCallHints = listOf("call", "voip", "meeting", "conference", "telecom", "zoom", "meet")
        private val sensitiveMediaHints = listOf("player", "video", "fullscreen", "youtube", "netflix", "bilibili", "tiktok")
        private val sensitiveGameHints = listOf("game", "unity", "unreal", "tmgp", "mihoyo", "hoyoverse", "netease")
    }
}
