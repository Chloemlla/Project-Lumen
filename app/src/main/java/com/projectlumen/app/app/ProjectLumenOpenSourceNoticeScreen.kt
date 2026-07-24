package com.projectlumen.app.app

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.ui.svg.DynamicColorImageVectors
import com.projectlumen.app.ui.svg.drawablevectors.coder

private data class OssCredit(
    val name: String,
    val author: String,
    @StringRes val descriptionRes: Int,
    val license: String,
    val url: String? = null,
)

@Composable
internal fun ProjectLumenOpenSourceNoticeScreen(
    onContinue: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val credits = rememberProjectLumenCredits()
    val projectLicense = stringResource(R.string.oss_notice_project_license_name)

    // First-run: leave system back to finish the activity.
    // Reopen from About: dismiss notice and return to the host screen.
    if (onDismiss != null) {
        BackHandler(onBack = onDismiss)
    }

    fun openUrl(url: String) {
        if (onOpenUrl != null) {
            onOpenUrl(url)
        } else {
            runCatching { uriHandler.openUri(url) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onContinue,
                ) {
                    Text(stringResource(R.string.oss_notice_continue))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.oss_notice_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Image(
                    imageVector = DynamicColorImageVectors.coder(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 4.dp)
                        .aspectRatio(16f / 9f),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.oss_notice_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OssNoticeSectionCard(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.oss_notice_repo_title),
                    body = stringResource(R.string.oss_notice_repo_body),
                    actionLabel = stringResource(R.string.oss_notice_open_repo),
                    onAction = { openUrl(PROJECT_LUMEN_REPO_URL) },
                )
            }
            item {
                OssNoticeSectionCard(
                    icon = Icons.Outlined.WarningAmber,
                    title = stringResource(R.string.oss_notice_free_title),
                    body = stringResource(R.string.oss_notice_free_body),
                )
            }
            item {
                OssNoticeSectionCard(
                    icon = Icons.Outlined.Gavel,
                    title = stringResource(R.string.oss_notice_project_license_title),
                    body = stringResource(R.string.oss_notice_project_license_body, projectLicense),
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VolunteerActivism,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.oss_notice_credits_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = stringResource(R.string.oss_notice_credits_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(credits) { credit ->
                CreditCard(
                    credit = credit,
                    onClick = credit.url?.let { url -> { openUrl(url) } },
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun OssNoticeSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onAction)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = actionLabel,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditCard(
    credit: OssCredit,
    onClick: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = credit.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = credit.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(credit.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = credit.license,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun rememberProjectLumenCredits(): List<OssCredit> = remember {
    listOf(
        OssCredit(
            name = "Lumen Crash",
            author = "Chloemlla",
            descriptionRes = R.string.credit_lumen_crash_desc,
            license = "Apache License, Version 2.0",
            url = "https://github.com/Chloemlla/Project-Lumen",
        ),
        OssCredit(
            name = "Jetpack Compose / Material 3",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.credit_compose_desc,
            license = "Apache License, Version 2.0",
            url = "https://developer.android.com/jetpack/compose",
        ),
        OssCredit(
            name = "AndroidX Core / AppCompat / Lifecycle / Navigation / WorkManager",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.credit_androidx_desc,
            license = "Apache License, Version 2.0",
            url = "https://developer.android.com/jetpack",
        ),
        OssCredit(
            name = "Room",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.credit_room_desc,
            license = "Apache License, Version 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/room",
        ),
        OssCredit(
            name = "DataStore Preferences",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.credit_datastore_desc,
            license = "Apache License, Version 2.0",
            url = "https://developer.android.com/topic/libraries/architecture/datastore",
        ),
        OssCredit(
            name = "Security Crypto",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.credit_security_crypto_desc,
            license = "Apache License, Version 2.0",
            url = "https://developer.android.com/topic/security/data",
        ),
        OssCredit(
            name = "ProfileInstaller",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.credit_profileinstaller_desc,
            license = "Apache License, Version 2.0",
            url = "https://developer.android.com/topic/performance/baselineprofiles",
        ),
        OssCredit(
            name = "Kotlin Coroutines",
            author = "JetBrains",
            descriptionRes = R.string.credit_coroutines_desc,
            license = "Apache License, Version 2.0",
            url = "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        OssCredit(
            name = "MMKV",
            author = "Tencent",
            descriptionRes = R.string.credit_mmkv_desc,
            license = "BSD 3-Clause License",
            url = "https://github.com/Tencent/MMKV",
        ),
        OssCredit(
            name = "OkHttp",
            author = "Square",
            descriptionRes = R.string.credit_okhttp_desc,
            license = "Apache License, Version 2.0",
            url = "https://square.github.io/okhttp/",
        ),
        OssCredit(
            name = "Shizuku",
            author = "Rikka apps",
            descriptionRes = R.string.credit_shizuku_desc,
            license = "Apache License, Version 2.0",
            url = "https://github.com/RikkaApps/Shizuku",
        ),
        OssCredit(
            name = "ML Kit Face Detection / Face Mesh Detection",
            author = "Google",
            descriptionRes = R.string.credit_mlkit_desc,
            license = "Apache License, Version 2.0 / Google Terms of Service",
            url = "https://developers.google.com/ml-kit",
        ),
        OssCredit(
            name = "ConstraintLayout Compose",
            author = "Android Open Source Project / Google",
            descriptionRes = R.string.credit_constraintlayout_desc,
            license = "Apache License, Version 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/constraintlayout",
        ),
        OssCredit(
            name = "Material Icons Extended",
            author = "Google",
            descriptionRes = R.string.credit_material_icons_desc,
            license = "Apache License, Version 2.0",
            url = "https://fonts.google.com/icons",
        ),
        OssCredit(
            name = "unDraw",
            author = "Katerina Limpitsouni",
            descriptionRes = R.string.credit_undraw_desc,
            license = "unDraw License",
            url = "https://undraw.co/",
        ),
    )
}
