package com.projectlumen.app.core.update

import android.content.Context
import android.net.ConnectivityManager
import com.projectlumen.app.core.api.ProjectLumenApiConfig
import com.projectlumen.app.core.network.ClashPartnerCompat
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

internal object UpdateEndpointPolicy {
    const val USER_AGENT = "Project-Lumen"

    /**
     * Redirects are followed here instead of by [HttpURLConnection] so every hop is
     * re-checked: a 30x cannot move update traffic to plain HTTP or an unlisted host.
     */
    fun open(
        context: Context,
        url: String,
        configure: HttpURLConnection.() -> Unit,
    ): HttpURLConnection {
        var target = requireAllowedUrl(URL(url))
        var redirects = 0
        while (true) {
            val connection = openDirect(context, target).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                configure()
            }
            val statusCode = try {
                connection.responseCode
            } catch (error: Throwable) {
                connection.disconnect()
                throw error
            }
            if (statusCode !in REDIRECT_STATUS_CODES) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location.isNullOrBlank()) {
                throw IOException("Update endpoint returned HTTP $statusCode without a redirect target.")
            }
            redirects += 1
            if (redirects > MAX_REDIRECTS) {
                throw IOException("Update endpoint redirected more than $MAX_REDIRECTS times.")
            }
            target = requireAllowedUrl(URL(target, location))
        }
    }

    private fun requireAllowedUrl(url: URL): URL {
        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw IOException("Update endpoints must use HTTPS.")
        }
        val host = url.host.orEmpty().lowercase().trimEnd('.')
        val allowed = allowedDomains.any { domain -> host == domain || host.endsWith(".$domain") }
        if (!allowed) {
            throw IOException("Update host $host is not allow-listed.")
        }
        return url
    }

    private fun openDirect(context: Context, url: URL): HttpURLConnection {
        // Clash VPN path: process is bound to VPN; never stack system/app proxy.
        // openConnection(Proxy.NO_PROXY) still uses the process-bound Network.
        if (ClashPartnerCompat.shouldSkipManualProxy()) {
            return url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
        }
        val network = context.getSystemService(ConnectivityManager::class.java)?.activeNetwork
            ?: return url.openConnection() as HttpURLConnection
        return network.openConnection(url) as HttpURLConnection
    }

    private val allowedDomains: Set<String> by lazy {
        buildSet {
            addAll(RELEASE_MIRROR_DOMAINS)
            val apiHost = runCatching { URL(ProjectLumenApiConfig.baseUrl).host }
                .getOrNull()
                ?.lowercase()
                ?.trimEnd('.')
                .orEmpty()
            if (apiHost.isNotBlank()) {
                add(apiHost)
                val labels = apiHost.split('.')
                if (labels.size >= 3) {
                    add(labels.takeLast(2).joinToString("."))
                }
            }
        }
    }

    private const val MAX_REDIRECTS = 5
    private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    private val RELEASE_MIRROR_DOMAINS = setOf("github.com", "githubusercontent.com")
}
