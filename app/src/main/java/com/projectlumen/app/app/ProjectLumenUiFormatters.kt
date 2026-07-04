package com.projectlumen.app.app

import android.annotation.SuppressLint
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.projectlumen.app.BuildConfig
import com.projectlumen.app.R
import com.projectlumen.app.core.crash.CrashReport
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.enums.QuietMode
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.enums.TemplateBackgroundType
import com.projectlumen.app.core.i18n.LocaleController
import com.projectlumen.app.core.services.BackupImportSummary
import com.projectlumen.app.core.update.BuildMetadata
import com.projectlumen.app.core.update.ReleaseAsset
import com.projectlumen.app.core.update.ReleaseInfo
import com.projectlumen.app.core.update.UpdateInstaller
import com.projectlumen.app.core.update.UpdateCandidate
import com.projectlumen.app.core.update.UpdateChecker
import com.projectlumen.app.ui.theme.ProjectLumenTheme
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun statusLabel(runtime: RuntimeStateEntity): String {
    return when (runtime.activeEngine) {
        ActiveEngine.REMINDER.name -> when (runtime.reminderPhase) {
            ReminderPhase.WORKING.name -> stringResource(R.string.status_working)
            ReminderPhase.PRE_ALERT.name -> stringResource(R.string.status_pre_alert)
            ReminderPhase.AWAITING_ACTION.name -> stringResource(R.string.status_waiting)
            ReminderPhase.RESTING.name -> stringResource(R.string.status_resting)
            ReminderPhase.PAUSED.name -> stringResource(R.string.status_paused)
            else -> stringResource(R.string.status_ready)
        }
        ActiveEngine.POMODORO.name -> when (runtime.pomodoroPhase) {
            PomodoroPhase.FOCUS.name -> stringResource(R.string.status_focus)
            PomodoroPhase.SHORT_BREAK.name -> stringResource(R.string.status_short_break)
            PomodoroPhase.LONG_BREAK.name -> stringResource(R.string.status_long_break)
            else -> stringResource(R.string.status_ready)
        }
        else -> stringResource(R.string.status_ready)
    }
}

@Composable
internal fun compactTime(totalSeconds: Long): String {
    val safeSeconds = max(0L, totalSeconds)
    return stringResource(R.string.minutes_compact, (safeSeconds / 60L).toInt(), (safeSeconds % 60L).toInt())
}

@Composable
internal fun minutesLabel(minutes: Int): String = stringResource(R.string.minutes_short, minutes)

@Composable
internal fun timeOfDayLabel(totalMinutes: Int): String {
    val safeMinutes = totalMinutes.coerceIn(0, 1439)
    return stringResource(R.string.time_value, safeMinutes / 60, safeMinutes % 60)
}

internal fun snapTimeMinute(value: Int): Int {
    return (((value.coerceIn(0, 1435) + 2) / 5) * 5).coerceIn(0, 1435)
}

internal fun isAutoDarkActive(nowMillis: Long, startMinute: Int, endMinute: Int): Boolean {
    val currentMinute = Instant.ofEpochMilli(nowMillis)
        .atZone(ZoneId.systemDefault())
        .let { it.hour * 60 + it.minute }
    val start = snapTimeMinute(startMinute)
    val end = snapTimeMinute(endMinute)
    return when {
        start == end -> false
        start < end -> currentMinute in start until end
        else -> currentMinute >= start || currentMinute < end
    }
}

@Composable
internal fun templateDisplayName(template: TipTemplateEntity?): String {
    return when (template?.id) {
        1L -> stringResource(R.string.template_electric_city_nights)
        2L -> stringResource(R.string.template_white_with_blue)
        3L -> stringResource(R.string.template_turquoise)
        4L -> stringResource(R.string.template_de283b)
        5L -> stringResource(R.string.template_dance_network)
        6L -> stringResource(R.string.template_orange_flat_shadow)
        else -> template?.name ?: stringResource(R.string.template_electric_city_nights)
    }
}

@Composable
internal fun templateSubtitle(template: TipTemplateEntity?): String {
    return when (template?.id) {
        1L -> stringResource(R.string.template_electric_city_nights_subtitle)
        2L -> stringResource(R.string.template_white_with_blue_subtitle)
        3L -> stringResource(R.string.template_turquoise_subtitle)
        4L -> stringResource(R.string.template_de283b_subtitle)
        5L -> stringResource(R.string.template_dance_network_subtitle)
        6L -> stringResource(R.string.template_orange_flat_shadow_subtitle)
        else -> template?.subtitleText ?: stringResource(R.string.break_message)
    }
}

@Composable
internal fun templateBreakTitle(template: TipTemplateEntity?): String {
    val title = template?.titleText.orEmpty()
    return if (title.isBlank() || title == DEFAULT_TEMPLATE_TITLE) {
        stringResource(R.string.break_title)
    } else {
        title
    }
}

@Composable
internal fun templateBreakSubtitle(template: TipTemplateEntity?): String {
    val subtitle = template?.subtitleText.orEmpty()
    return if (subtitle.isBlank() || subtitle == DEFAULT_TEMPLATE_SUBTITLE) {
        stringResource(R.string.break_message)
    } else {
        subtitle
    }
}

internal fun templateCountdownStyle(template: TipTemplateEntity?): String {
    val style = runCatching {
        JSONObject(template?.layoutJson?.takeIf { it.isNotBlank() } ?: "{}")
            .optString("countdownStyle", COUNTDOWN_STYLE_CIRCLE)
    }.getOrDefault(COUNTDOWN_STYLE_CIRCLE)
    return when (style) {
        COUNTDOWN_STYLE_BAR,
        COUNTDOWN_STYLE_NUMBER,
        COUNTDOWN_STYLE_CIRCLE -> style
        else -> COUNTDOWN_STYLE_CIRCLE
    }
}

internal fun activeTemplate(uiState: ProjectLumenUiState): TipTemplateEntity? {
    return uiState.templates.firstOrNull { it.id == uiState.settings.activeTipTemplateId } ?: uiState.templates.firstOrNull()
}

internal fun planTier(settings: AppSettingsEntity): PlanTier {
    return PlanTier.entries.firstOrNull { it.name == settings.planTier } ?: PlanTier.FREE
}

internal fun remainingSeconds(endAt: Long, nowMillis: Long): Long {
    if (endAt <= 0L) return 0L
    return max(0L, (endAt - nowMillis) / 1000L)
}

internal fun progress(startAt: Long, endAt: Long, nowMillis: Long): Float {
    if (startAt <= 0L || endAt <= startAt) return 0f
    val elapsed = (nowMillis - startAt).coerceAtLeast(0L).toFloat()
    val duration = (endAt - startAt).toFloat()
    return (elapsed / duration).coerceIn(0f, 1f)
}

@Composable
internal fun templateBackgroundColor(template: TipTemplateEntity?): Color {
    return if (template?.backgroundType == TemplateBackgroundType.SYSTEM.name) {
        systemThemeColor(template.backgroundValue)
    } else {
        parseColor(template?.backgroundValue, MaterialTheme.colorScheme.primaryContainer)
    }
}

@Composable
internal fun templatePrimaryColor(template: TipTemplateEntity?): Color {
    return if (template?.backgroundType == TemplateBackgroundType.SYSTEM.name) {
        systemThemeColor(template.primaryColor)
    } else {
        parseColor(template?.primaryColor, MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun systemThemeColor(key: String?): Color {
    val colors = MaterialTheme.colorScheme
    return when (key) {
        "primary" -> colors.primary
        "secondary" -> colors.secondary
        "tertiary" -> colors.tertiary
        "primaryContainer" -> colors.primaryContainer
        "secondaryContainer" -> colors.secondaryContainer
        "tertiaryContainer" -> colors.tertiaryContainer
        "surfaceContainer" -> colors.surfaceContainer
        "surfaceVariant" -> colors.surfaceVariant
        else -> colors.primaryContainer
    }
}

internal fun parseColor(hex: String?, fallback: Color): Color {
    return hex
        ?.let { runCatching { Color(it.toColorInt()) }.getOrDefault(fallback) }
        ?: fallback
}

@Composable
internal fun rememberBuildVersionLabel(): String {
    val metadata = remember { BuildMetadata.current() }
    return "${metadata.versionName} (${metadata.shortHash})"
}

internal fun chooseFallbackAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
    val verifiedApks = assets.filter { asset ->
        asset.name.endsWith(".apk", ignoreCase = true) && !asset.sha256.isNullOrBlank()
    }
    return verifiedApks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
        ?: verifiedApks.firstOrNull { it.name.contains("all", ignoreCase = true) }
}

internal fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    startSettingsActivity(context, intent)
}

internal fun persistReadableUri(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun needsNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION) !=
        PackageManager.PERMISSION_GRANTED
}

internal fun needsCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
        PackageManager.PERMISSION_GRANTED
}

internal fun needsExactAlarmSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    return !alarmManager.canScheduleExactAlarms()
}

internal fun needsFullScreenIntentSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    return !notificationManager.canUseFullScreenIntent()
}

internal fun needsOverlayPermission(context: Context): Boolean {
    return !Settings.canDrawOverlays(context)
}

internal fun needsWriteSettingsPermission(context: Context): Boolean {
    return !Settings.System.canWrite(context)
}

internal fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        "package:${context.packageName}".toUri(),
    )
    startSettingsActivity(context, intent)
}

internal fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
        "package:${context.packageName}".toUri(),
    )
    startSettingsActivity(context, intent)
}

internal fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    )
    startSettingsActivity(context, intent)
}

internal fun openWriteSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        "package:${context.packageName}".toUri(),
    )
    startSettingsActivity(context, intent)
}

private fun startSettingsActivity(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
        .recoverCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri(),
                ),
            )
        }
        .onFailure {
            Toast.makeText(context, R.string.system_settings_open_failed, Toast.LENGTH_SHORT).show()
        }
}

