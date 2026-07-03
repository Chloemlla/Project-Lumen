package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R

@Composable
internal fun ProjectLumenOnboardingScreen(
    state: ProjectLumenOnboardingState,
    onComplete: (applyRecommendedSetup: Boolean) -> Unit,
) {
    val pages = rememberOnboardingPages()
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[pageIndex.coerceIn(0, pages.lastIndex)]
    val progress = (pageIndex + 1).toFloat() / pages.size.toFloat()
    val iconPulse by rememberInfiniteTransition(label = "onboardingIconPulse").animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "onboardingIconPulseValue",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.onboarding_step_count, pageIndex + 1, pages.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onComplete(false) }) {
                        Icon(Icons.Outlined.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.onboarding_skip))
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                )
            }

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val direction = if (targetState.index > initialState.index) 1 else -1
                    fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it * direction / 5 } togetherWith
                        fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it * direction / 6 }
                },
                label = "onboardingPage",
            ) { activePage ->
                OnboardingPageCard(
                    page = activePage,
                    deviceFingerprint = state.deviceFingerprint,
                    newInstallDetected = state.newInstallDetected,
                    iconScale = iconPulse,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pageIndex > 0) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { pageIndex -= 1 },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (pageIndex < pages.lastIndex) {
                            pageIndex += 1
                        } else {
                            onComplete(true)
                        }
                    },
                ) {
                    Icon(
                        if (pageIndex < pages.lastIndex) Icons.Outlined.CheckCircle else Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (pageIndex < pages.lastIndex) {
                                R.string.onboarding_next
                            } else {
                                R.string.onboarding_finish_apply
                            },
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageCard(
    page: OnboardingPageSpec,
    deviceFingerprint: String,
    newInstallDetected: Boolean,
    iconScale: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(page.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(page.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (page.showFingerprint) {
                FingerprintPanel(deviceFingerprint, newInstallDetected)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                page.bulletRes.forEach { bulletRes ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(bulletRes),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FingerprintPanel(deviceFingerprint: String, newInstallDetected: Boolean) {
    val display = remember(deviceFingerprint) {
        if (deviceFingerprint.length > 20) {
            "${deviceFingerprint.take(12)}...${deviceFingerprint.takeLast(12)}"
        } else {
            deviceFingerprint
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(
                if (newInstallDetected) {
                    R.string.onboarding_fingerprint_new
                } else {
                    R.string.onboarding_fingerprint_ready
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            display,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberOnboardingPages(): List<OnboardingPageSpec> = remember {
    listOf(
        OnboardingPageSpec(
            index = 0,
            icon = Icons.Outlined.Spa,
            titleRes = R.string.onboarding_page_welcome_title,
            messageRes = R.string.onboarding_page_welcome_message,
            bulletRes = listOf(
                R.string.onboarding_page_welcome_bullet_1,
                R.string.onboarding_page_welcome_bullet_2,
                R.string.onboarding_page_welcome_bullet_3,
            ),
            showFingerprint = true,
        ),
        OnboardingPageSpec(
            index = 1,
            icon = Icons.Outlined.Schedule,
            titleRes = R.string.onboarding_page_protection_title,
            messageRes = R.string.onboarding_page_protection_message,
            bulletRes = listOf(
                R.string.onboarding_page_protection_bullet_1,
                R.string.onboarding_page_protection_bullet_2,
                R.string.onboarding_page_protection_bullet_3,
            ),
        ),
        OnboardingPageSpec(
            index = 2,
            icon = Icons.Outlined.NotificationsActive,
            titleRes = R.string.onboarding_page_permissions_title,
            messageRes = R.string.onboarding_page_permissions_message,
            bulletRes = listOf(
                R.string.onboarding_page_permissions_bullet_1,
                R.string.onboarding_page_permissions_bullet_2,
                R.string.onboarding_page_permissions_bullet_3,
            ),
        ),
        OnboardingPageSpec(
            index = 3,
            icon = Icons.Outlined.BarChart,
            titleRes = R.string.onboarding_page_cloud_title,
            messageRes = R.string.onboarding_page_cloud_message,
            bulletRes = listOf(
                R.string.onboarding_page_cloud_bullet_1,
                R.string.onboarding_page_cloud_bullet_2,
                R.string.onboarding_page_cloud_bullet_3,
            ),
        ),
        OnboardingPageSpec(
            index = 4,
            icon = Icons.Outlined.Settings,
            titleRes = R.string.onboarding_page_best_practice_title,
            messageRes = R.string.onboarding_page_best_practice_message,
            bulletRes = listOf(
                R.string.onboarding_page_best_practice_bullet_1,
                R.string.onboarding_page_best_practice_bullet_2,
                R.string.onboarding_page_best_practice_bullet_3,
            ),
        ),
    )
}

private data class OnboardingPageSpec(
    val index: Int,
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val bulletRes: List<Int>,
    val showFingerprint: Boolean = false,
)
