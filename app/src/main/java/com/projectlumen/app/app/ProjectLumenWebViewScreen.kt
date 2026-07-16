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
import com.chloemlla.lumen.crash.CrashReport
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
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebViewScreen(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val initialPageUri = remember(url) { url.toUri() }
    val initialPageAllowed = remember(url) { isProjectLumenRepoUrl(initialPageUri) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember(url) { mutableStateOf("") }
    var currentUrl by rememberSaveable(url) { mutableStateOf(url) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    var showCompatibilityDialog by remember { mutableStateOf(false) }
    val currentPageUrl = webView?.url ?: currentUrl

    LaunchedEffect(initialPageAllowed, initialPageUri) {
        if (!initialPageAllowed) {
            openUri(context, initialPageUri)
            onDismiss()
        }
    }
    if (!initialPageAllowed) return

    BackHandler(onBack = onDismiss)
    DisposableEffect(webView) {
        val disposableWebView = webView
        onDispose {
            disposableWebView?.destroy()
        }
    }

    if (showCompatibilityDialog) {
        AlertDialog(
            onDismissRequest = { showCompatibilityDialog = false },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text(stringResource(R.string.webview_compatibility_title)) },
            text = {
                Text(stringResource(R.string.webview_compatibility_message, chromeVersion))
            },
            confirmButton = {
                OutlinedButton(onClick = { showCompatibilityDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                title = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = pageTitle.ifBlank { webView?.title.orEmpty() },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (chromeVersion in 1 until MINI_CHROME_VERSION) {
                        IconButton(onClick = { showCompatibilityDialog = true }) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = stringResource(R.string.webview_compatibility_action))
                        }
                    }
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.webview_more_options))
                    }
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            if (!isLoading) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.webview_refresh)) },
                                    onClick = {
                                        expanded = false
                                        webView?.reload()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.webview_copy_link)) },
                                onClick = {
                                    expanded = false
                                    copyWebPageUrl(context, currentPageUrl)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.webview_open_external)) },
                                onClick = {
                                    expanded = false
                                    openUri(context, currentPageUrl.toUri())
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        key(url) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                factory = {
                    WebView(it).apply {
                        webView = this
                        if (BuildConfig.DEBUG) {
                            addJavascriptInterface(ProjectLumenWebViewJsApi(it.applicationContext), "projectLumen")
                        }
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = BuildConfig.DEBUG
                        settings.domStorageEnabled = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            settings.setAlgorithmicDarkeningAllowed(false)
                        }
                        webViewClient = ProjectLumenWebViewClient(
                            onPageStarted = { startedUrl ->
                                isLoading = true
                                if (!startedUrl.isNullOrBlank()) currentUrl = startedUrl
                            },
                            onPageFinished = { finishedUrl, title ->
                                isLoading = false
                                if (!finishedUrl.isNullOrBlank()) currentUrl = finishedUrl
                                pageTitle = title.orEmpty()
                            },
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title.orEmpty()
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                isLoading = newProgress < 100
                                view?.url?.takeIf { it.isNotBlank() }?.let { currentUrl = it }
                                view?.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                            }
                        }
                        loadUrl(url)
                    }
                },
            )
        }
    }
}

internal class ProjectLumenWebViewClient(
    private val onPageStarted: (String?) -> Unit,
    private val onPageFinished: (String?, String?) -> Unit,
) : WebViewClient() {
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished(url, view.title)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (request.isForMainFrame && !isProjectLumenRepoUrl(uri)) {
            openUri(view.context, uri)
            return true
        }
        return false
    }
}

internal fun isProjectLumenRepoUrl(uri: Uri): Boolean {
    if (uri.scheme != "https") return false
    val url = uri.toString()
    return url == PROJECT_LUMEN_REPO_URL ||
        url.startsWith("$PROJECT_LUMEN_REPO_URL/") ||
        url.startsWith("$PROJECT_LUMEN_REPO_URL?") ||
        url.startsWith("$PROJECT_LUMEN_REPO_URL#")
}

internal class ProjectLumenWebViewJsApi(private val context: Context) {
    @JavascriptInterface
    fun getAppId() = context.packageName

    @JavascriptInterface
    fun getAppName() = context.getString(R.string.app_name)

    @JavascriptInterface
    fun getVersionCode() = appVersionCode(context)

    @JavascriptInterface
    fun getVersionName() = appVersionName(context)

    @JavascriptInterface
    fun getChannel() = "project-lumen"

    @JavascriptInterface
    fun getDebuggable() = BuildConfig.DEBUG
}

internal fun copyWebPageUrl(context: Context, url: String) {
    context.getSystemService<ClipboardManager>()?.setPrimaryClip(
        ClipData.newPlainText(context.packageName, url),
    )
    Toast.makeText(context, context.getString(R.string.webview_link_copied), Toast.LENGTH_SHORT).show()
}

internal fun openUri(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, context.getString(R.string.webview_open_failed), Toast.LENGTH_SHORT).show() }
}

@Suppress("DEPRECATION")
internal fun appVersionCode(context: Context): Int {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } else {
        packageInfo.versionCode
    }
}

@Suppress("DEPRECATION")
internal fun appVersionName(context: Context): String {
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}


