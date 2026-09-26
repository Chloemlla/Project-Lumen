package com.projectlumen.app.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.projectlumen.app.R
import com.projectlumen.app.core.api.ProjectLumenCaptchaConfig
import com.projectlumen.app.core.api.ProjectLumenCaptchaType
import com.projectlumen.app.core.api.ProjectLumenIpChallenge
import com.projectlumen.app.core.api.ProjectLumenIpVerification
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Synapse 首访闸门要求人机验证时的界面（对应网页端的 `FirstVisitVerification`）。
 *
 * 拦截器只在收到 403 之后把结论塞进 [ProjectLumenIpVerification.challenge]，自己不阻塞等用户；
 * 这里观察到挑战就渲染验证模块，验证通过后拿到的访问令牌在服务端给的 TTL（默认 40 分钟）内
 * 一直被复用。IP 被直接封禁时没有可做的验证，只给原因。
 */
@Composable
internal fun ProjectLumenIpVerificationGate() {
    val challenge by ProjectLumenIpVerification.challenge.collectAsState()
    challenge?.let { ActiveChallengeDialog(challenge = it) }
}

@Composable
private fun ActiveChallengeDialog(challenge: ProjectLumenIpChallenge) {
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<ProjectLumenCaptchaConfig?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failure by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var widgetKey by remember { mutableStateOf(0) }

    LaunchedEffect(challenge, widgetKey) {
        if (challenge.banned) return@LaunchedEffect
        loading = true
        failure = null
        config = ProjectLumenIpVerification.captchaConfig().also {
            if (it == null) failure = "config"
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) ProjectLumenIpVerification.challengeCancelled() },
        title = {
            Text(
                stringResource(
                    if (challenge.banned) R.string.ip_verification_banned_title
                    else R.string.ip_verification_title,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        if (challenge.banned) R.string.ip_verification_banned_message
                        else R.string.ip_verification_message,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                when {
                    challenge.banned -> Unit

                    loading -> Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }

                    failure != null -> Text(
                        text = stringResource(
                            if (failure == "config") R.string.ip_verification_load_failed
                            else R.string.ip_verification_failed,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    else -> config?.let { captcha ->
                        CaptchaWebView(
                            config = captcha,
                            pageBaseUrl = ProjectLumenIpVerification.pageBaseUrl(),
                            refreshKey = widgetKey,
                            onToken = { token ->
                                submitting = true
                                scope.launch {
                                    val accepted = ProjectLumenIpVerification.complete(
                                        captchaToken = token,
                                        captchaType = captchaTypeName(captcha),
                                    )
                                    submitting = false
                                    if (!accepted) {
                                        failure = "submit"
                                        widgetKey += 1
                                    }
                                }
                            },
                            onError = {
                                submitting = false
                                failure = "widget"
                            },
                        )
                    }
                }
                if (submitting) {
                    Text(
                        text = stringResource(R.string.ip_verification_submitting),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { ProjectLumenIpVerification.challengeCancelled() },
                enabled = !submitting,
            ) {
                Text(stringResource(R.string.ip_verification_close))
            }
        },
        dismissButton = if (challenge.banned) {
            null
        } else {
            {
                TextButton(onClick = {
                    failure = null
                    widgetKey += 1
                }) {
                    Text(stringResource(R.string.ip_verification_retry))
                }
            }
        },
    )
}

/** 服务端只认 `turnstile` / `hcaptcha` 两个字面量。 */
private fun captchaTypeName(config: ProjectLumenCaptchaConfig): String =
    if (config.type == ProjectLumenCaptchaType.HCAPTCHA) "hcaptcha" else "turnstile"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaWebView(
    config: ProjectLumenCaptchaConfig,
    pageBaseUrl: String,
    refreshKey: Int,
    onToken: (String) -> Unit,
    onError: () -> Unit,
) {
    val bridge = remember { CaptchaBridge() }
    bridge.onToken = onToken
    bridge.onError = onError
    val loadKey = listOf(config.type.name, config.siteKey, refreshKey, pageBaseUrl).joinToString("|")
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(top = 4.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                addJavascriptInterface(bridge, CAPTCHA_BRIDGE_NAME)
                tag = loadKey
            }
        },
        update = { webView ->
            if (webView.tag != loadKey) {
                webView.tag = loadKey
                webView.loadDataWithBaseURL(
                    pageBaseUrl.trimEnd('/').ifBlank { null },
                    buildCaptchaHtml(config),
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        onRelease = { webView ->
            webView.removeJavascriptInterface(CAPTCHA_BRIDGE_NAME)
            webView.destroy()
        },
    )
}

private class CaptchaBridge {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var onToken: (String) -> Unit = {}

    @Volatile
    var onError: () -> Unit = {}

    @JavascriptInterface
    fun onVerify(token: String) {
        val trimmed = token.trim()
        mainHandler.post {
            if (trimmed.isEmpty()) onError() else onToken(trimmed)
        }
    }

    @JavascriptInterface
    fun onExpire() {
        mainHandler.post { onError() }
    }

    @JavascriptInterface
    fun onFailure() {
        mainHandler.post { onError() }
    }
}

/**
 * 在 WebView 里渲染 Turnstile / hCaptcha。
 *
 * `GET /api/turnstile/public-config` 一次给出两套公开配置，本端按「先 Turnstile 后 hCaptcha」
 * 取一个 —— 与网页端 `useSecureCaptchaSelection` 的取舍一致。组件回调只交出 `captchaToken`，
 * 令牌签发与有效期都由服务端决定，这里不关心。
 */
private fun buildCaptchaHtml(config: ProjectLumenCaptchaConfig): String {
    val isTurnstile = config.type == ProjectLumenCaptchaType.TURNSTILE
    val script = if (isTurnstile) TURNSTILE_SCRIPT else HCAPTCHA_SCRIPT
    val global = if (isTurnstile) "turnstile" else "hcaptcha"
    // Turnstile 的 render 收的是 CSS 选择器，hCaptcha 收的是元素 id —— 写反了组件直接不渲染。
    val target = if (isTurnstile) "#captcha" else "captcha"
    val siteKey = JSONObject.quote(config.siteKey)
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <script src="$script" async defer></script>
          <style>
            html, body { margin: 0; padding: 0; background: transparent; overflow: hidden; }
            #captcha { min-height: 88px; display: flex; align-items: center; justify-content: center; }
            #failed { font: 12px sans-serif; color: #c62828; padding: 8px; text-align: center; }
          </style>
        </head>
        <body>
          <div id="captcha"></div>
          <div id="failed"></div>
          <script>
            (function () {
              function bridge(method, payload) {
                try { window.$CAPTCHA_BRIDGE_NAME[method](payload); } catch (error) {}
              }
              var rendered = false;
              function start() {
                if (rendered) return;
                if (!window.$global) { window.setTimeout(start, 100); return; }
                rendered = true;
                try {
                  window.$global.render('$target', {
                    sitekey: $siteKey,
                    theme: 'light',
                    size: 'normal',
                    callback: function (token) { bridge('onVerify', token || ''); },
                    'expired-callback': function () { bridge('onExpire', ''); },
                    'error-callback': function () { bridge('onFailure', ''); }
                  });
                } catch (error) {
                  document.getElementById('failed').textContent = 'unavailable';
                  bridge('onFailure', '');
                }
              }
              window.addEventListener('load', start);
              start();
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}

private const val CAPTCHA_BRIDGE_NAME = "ProjectLumenCaptcha"
private const val TURNSTILE_SCRIPT = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
private const val HCAPTCHA_SCRIPT = "https://js.hcaptcha.com/1/api.js?render=explicit"
