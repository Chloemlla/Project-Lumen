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
import androidx.compose.foundation.clickable
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
internal fun AboutScreen(viewModel: ProjectLumenViewModel) {
    val versionLabel = rememberBuildVersionLabel()
    val context = LocalContext.current
    var versionTapCount by rememberSaveable { mutableIntStateOf(0) }

    LumenPage {
        AboutHeroCard(
            versionLabel = versionLabel,
            onVersionClick = {
                versionTapCount += 1
                when {
                    versionTapCount in 3..6 -> {
                        val remainingSteps = 7 - versionTapCount
                        Toast.makeText(
                            context,
                            context.resources.getQuantityString(
                                R.plurals.developer_unlock_steps,
                                remainingSteps,
                                remainingSteps,
                            ),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    versionTapCount >= 7 -> {
                        viewModel.updateSettings { current -> current.copy(developerModeEnabled = true) }
                        Toast.makeText(context, context.getString(R.string.developer_unlocked), Toast.LENGTH_SHORT).show()
                        versionTapCount = 0
                    }
                }
            },
        )
        AboutLinksCard(viewModel)
    }
}

@Composable
internal fun AboutHeroCard(versionLabel: String, onVersionClick: () -> Unit) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
            shape = LumenCardShape,
            colors = lumenCardColors(),
            elevation = lumenCardElevation(),
        ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(Icons.Outlined.Info, R.string.app_name)
            Text(
                versionLabel,
                modifier = Modifier.clickable(onClick = onVersionClick),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AboutLinksCard(viewModel: ProjectLumenViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = LumenCardShape, colors = lumenCardColors(), elevation = lumenCardElevation()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Code, R.string.about_links)
            ConfirmExternalLinkButton(Icons.Outlined.Code, R.string.about_source_code, PROJECT_LUMEN_REPO_URL, viewModel)
            ConfirmExternalLinkButton(Icons.Outlined.Code, R.string.about_latest_release, PROJECT_LUMEN_RELEASES_URL, viewModel)
        }
    }
}

@Composable
internal fun ConfirmExternalLinkButton(icon: ImageVector, @StringRes labelRes: Int, url: String, viewModel: ProjectLumenViewModel) {
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = { pendingUrl = url }) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(labelRes))
    }
    pendingUrl?.let { targetUrl ->
        AlertDialog(
            onDismissRequest = { pendingUrl = null },
            title = { Text(stringResource(R.string.about_external_link_prompt_title)) },
            text = { Text(stringResource(R.string.about_external_link_prompt_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    pendingUrl = null
                    viewModel.navigateWebPage(targetUrl)
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingUrl = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun ProximityCalibrationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
        title = { Text(stringResource(R.string.proximity_calibration_title)) },
        text = { Text(stringResource(R.string.proximity_calibration_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.proximity_record_baseline))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.generic_cancel))
            }
        },
    )
}

@Composable
internal fun BackupImportDialog(
    summary: BackupImportSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_import_message))
                MetricRow(R.string.backup_schema_version, summary.schemaVersion.toString())
                MetricRow(R.string.backup_templates_count, summary.templateCount.toString())
                MetricRow(R.string.backup_eye_days_count, summary.eyeStatDays.toString())
                MetricRow(R.string.backup_pomodoro_days_count, summary.pomodoroStatDays.toString())
                MetricRow(R.string.backup_entitlements_count, summary.entitlementCount.toString())
                MetricRow(R.string.backup_plans_count, summary.reminderPlanCount.toString())
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.backup_import_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun UpdateDialog(
    viewModel: ProjectLumenViewModel,
    state: UpdateDialogState,
    downloadingUpdate: Boolean,
    downloadProgressBytes: Long,
    downloadProgressTotalBytes: Long?,
    onDismiss: () -> Unit,
    onDownloadUpdate: (UpdateCandidate, ReleaseAsset) -> Unit,
    onInstallDownloadedApk: (UpdateCandidate, File) -> Unit,
    onError: (String) -> Unit,
    updateInstaller: UpdateInstaller,
) {
    val context = LocalContext.current
    var pendingReleaseUrl by remember { mutableStateOf<String?>(null) }
    val showReleaseInfo: @Composable (ReleaseInfo, BuildMetadata, UpdateCandidate?) -> Unit = { release, current, candidate ->
        val publishTime = Instant.ofEpochMilli(release.publishedAtUtcMillis).atZone(ZoneOffset.UTC).format(updateDialogTimeFormatter)
        val buildTime = Instant.ofEpochMilli(current.buildTimeUtcMillis).atZone(ZoneOffset.UTC).format(updateDialogTimeFormatter)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.about_update_found, release.tagName))
            Text(stringResource(R.string.about_update_current_version, current.versionName, current.shortHash))
            Text(stringResource(R.string.about_update_build_time, buildTime))
            Text(stringResource(R.string.about_update_publish_time, publishTime))
            if (candidate?.isTimeFallback == true) {
                Text(stringResource(R.string.about_update_time_fallback), color = MaterialTheme.colorScheme.primary)
            }
            if (release.body.isNotBlank()) {
                Text(release.body)
            }
            Text(stringResource(R.string.about_update_release_notes, release.releaseName))
        }
    }

    when (val currentState = state) {
        UpdateDialogState.Hidden -> Unit
        UpdateDialogState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.about_update_checking_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.about_update_checking))
                }
            },
            confirmButton = {},
        )
        UpdateDialogState.NoUpdate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.about_update_up_to_date_title)) },
            text = { Text(stringResource(R.string.about_update_up_to_date_message)) },
            confirmButton = {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
            },
        )
        is UpdateDialogState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.about_update_failed_title)) },
            text = { Text(currentState.message) },
            confirmButton = {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
            },
        )
        is UpdateDialogState.UpdateAvailable -> {
            val candidate = currentState.candidate
            val release = candidate.release
            val targetAsset = candidate.matchedAsset ?: chooseFallbackAsset(release.assets)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(release.tagName) },
                text = { showReleaseInfo(release, candidate.currentBuild, candidate) },
                confirmButton = {
                    OutlinedButton(
                        enabled = !downloadingUpdate,
                        onClick = {
                            if (targetAsset != null) {
                                onDownloadUpdate(candidate, targetAsset)
                            } else {
                                pendingReleaseUrl = release.htmlUrl.ifBlank { PROJECT_LUMEN_RELEASES_BASE_URL }
                            }
                        },
                    ) {
                        ButtonLabel(Icons.Outlined.FileDownload, if (targetAsset != null) R.string.about_download_update else R.string.about_open_release)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        is UpdateDialogState.Downloading -> {
            val candidate = currentState.candidate
            val release = candidate.release
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.about_update_downloading_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        showReleaseInfo(release, candidate.currentBuild, candidate)
                        val progress = downloadProgressTotalBytes?.takeIf { it > 0 }?.let {
                            downloadProgressBytes.toFloat() / it.toFloat()
                        }
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = downloadProgressTotalBytes?.takeIf { it > 0 }?.let {
                                val percent = ((downloadProgressBytes * 100) / it).coerceIn(0, 100)
                                stringResource(R.string.about_update_downloading_progress, percent)
                            } ?: stringResource(R.string.about_update_downloading),
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {},
            )
        }
        is UpdateDialogState.InstallAuthorization -> {
            val candidate = currentState.candidate
            val release = candidate.release
            val permissionGranted = updateInstaller.canInstallPackages()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.about_install_permission_prompt_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.about_install_permission_prompt_message))
                        showReleaseInfo(release, candidate.currentBuild, candidate)
                    }
                },
                confirmButton = {
                    OutlinedButton(onClick = {
                        if (permissionGranted) {
                            onInstallDownloadedApk(candidate, currentState.file)
                        } else {
                            runCatching { context.startActivity(updateInstaller.createInstallPermissionIntent()) }
                                .onFailure { onError(it.message ?: "Unable to open install settings.") }
                        }
                    }) {
                        ButtonLabel(
                            if (permissionGranted) Icons.Outlined.FileDownload else Icons.AutoMirrored.Outlined.OpenInNew,
                            if (permissionGranted) R.string.about_install_now else R.string.about_update_grant_permission,
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
    }

    pendingReleaseUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingReleaseUrl = null },
            title = { Text(stringResource(R.string.about_external_link_prompt_title)) },
            text = { Text(stringResource(R.string.about_external_link_prompt_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    pendingReleaseUrl = null
                    viewModel.navigateWebPage(url)
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingReleaseUrl = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
@Composable
internal fun StateCard(runtime: RuntimeStateEntity, nowMillis: Long) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
            shape = LumenCardShape,
            colors = lumenCardColors(),
            elevation = lumenCardElevation(),
        ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.NotificationsActive, R.string.current_state)
            AnimatedContent(
                targetState = statusLabel(runtime),
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
                },
                label = "runtimeStatus",
            ) { status ->
                Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            MetricRow(
                R.string.next_reminder,
                if (runtime.nextReminderAt > 0) compactTime(remainingSeconds(runtime.nextReminderAt, nowMillis)) else stringResource(R.string.not_set),
            )
        }
    }
}


