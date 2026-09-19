package com.projectlumen.app.core.quarkkeeper

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens Quark, preferring a deep link straight to the check-in page and degrading in two steps.
 *
 * The deep link is attempted first because the whole point of the module is to shorten the path that
 * makes people skip the check-in. It cannot be relied on though: the activity handling it is not part
 * of Quark's public contract, so a version bump may drop it, and a user may have a build that never
 * had it. The launch-intent fallback then opens the app's main screen, and the web fallback covers a
 * device where Quark is not installed at all.
 *
 * Every step is resolved before it is fired, and every failure is a returned `false` rather than a
 * silent no-op — the caller shows the "Quark not installed" dialog on `false`.
 */
object QuarkKeeperLauncher {

    /**
     * The app that owns the check-in task, best first. 夸克扫描王 is the one that actually carries the
     * daily sign-in board; the browser builds are kept as fallbacks because they ship the same Quark
     * account and web entry point, so a user on one of them still gets a usable shortcut instead of
     * the "not installed" dialog.
     */
    private val QUARK_PACKAGES = listOf(
        "com.quark.scanking",
        "com.quark.browser",
        "com.quark.cloudpan",
    )

    /**
     * Candidate deep links, most specific first. None of these is a documented public contract, so
     * each one is only used after a successful resolve.
     */
    private val DEEP_LINKS = listOf(
        "quark://web?url=https%3A%2F%2Fpan.quark.cn%2F",
        "quark://",
    )

    private const val WEB_CHECK_IN_URL = "https://pan.quark.cn/"

    fun installedPackage(context: Context): String? {
        return QUARK_PACKAGES.firstOrNull { packageName -> isInstalled(context, packageName) }
    }

    fun isQuarkInstalled(context: Context): Boolean = installedPackage(context) != null

    /**
     * Tries the deep link, then Quark's launch intent. Returns false when neither can be resolved, so
     * the caller can offer the install and web paths instead of leaving the user on a dead button.
     */
    fun launchCheckIn(context: Context): Boolean {
        if (launchDeepLink(context)) return true
        return launchApp(context)
    }

    private fun launchDeepLink(context: Context): Boolean {
        val packageName = installedPackage(context) ?: return false
        return DEEP_LINKS.any { link ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                // Pinning the package keeps a same-named handler in another app from intercepting the
                // launch; without it any app registering the scheme answers the intent chooser.
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // resolveActivity, not just startActivity: an unresolvable intent throws, and the next
            // candidate link should be tried rather than the whole launch abandoned.
            if (intent.resolveActivity(context.packageManager) == null) return@any false
            runCatching { context.startActivity(intent) }.isSuccess
        }
    }

    private fun launchApp(context: Context): Boolean {
        val packageName = installedPackage(context) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * Store listing for the device's app market, falling back to the Play web page when no market app
     * answers the `market://` intent — a device without Google Play still has to be able to install.
     */
    fun openStoreListing(context: Context) {
        val packageName = QUARK_PACKAGES.first()
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (market.resolveActivity(context.packageManager) != null) {
            if (runCatching { context.startActivity(market) }.isSuccess) return
        }
        openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
    }

    /** Web check-in page, for a user who would rather not install the app. */
    fun openWebCheckIn(context: Context) {
        openUrl(context, WEB_CHECK_IN_URL)
    }

    private fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No browser either. There is no further fallback that could complete a check-in, so the
            // caller's button simply does nothing rather than crashing on an unresolvable intent.
        }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
        }.isSuccess
    }
}
