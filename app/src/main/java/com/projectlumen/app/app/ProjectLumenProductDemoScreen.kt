package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R

private data class ProductDemoScene(
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
    val icon: ImageVector,
)

private data class ProductDemoMetric(
    @StringRes val labelRes: Int,
    val value: String,
)

private data class ProductDemoSignal(
    @StringRes val labelRes: Int,
    val value: String,
    val progress: Float,
    val icon: ImageVector,
)

private val productDemoScenes = listOf(
    ProductDemoScene(
        R.string.product_demo_scene_onboarding,
        R.string.product_demo_scene_onboarding_detail,
        Icons.Outlined.CheckCircle,
    ),
    ProductDemoScene(
        R.string.product_demo_scene_home,
        R.string.product_demo_scene_home_detail,
        Icons.Outlined.Spa,
    ),
    ProductDemoScene(
        R.string.product_demo_scene_break_loop,
        R.string.product_demo_scene_break_loop_detail,
        Icons.Outlined.NotificationsActive,
    ),
    ProductDemoScene(
        R.string.product_demo_scene_focus,
        R.string.product_demo_scene_focus_detail,
        Icons.Outlined.LocalCafe,
    ),
    ProductDemoScene(
        R.string.product_demo_scene_sensors,
        R.string.product_demo_scene_sensors_detail,
        Icons.Outlined.PhotoCamera,
    ),
    ProductDemoScene(
        R.string.product_demo_scene_advanced,
        R.string.product_demo_scene_advanced_detail,
        Icons.Outlined.Security,
    ),
)

private val productDemoSignals = listOf(
    ProductDemoSignal(R.string.product_demo_signal_distance, "165%", 0.72f, Icons.Outlined.PhotoCamera),
    ProductDemoSignal(R.string.product_demo_signal_blink, "1", 0.42f, Icons.Outlined.Spa),
    ProductDemoSignal(R.string.product_demo_signal_lux, "8 lux", 0.18f, Icons.Outlined.WbSunny),
)

@Composable
internal fun ProductDemoScreen() {
    LumenPage {
        PageIntro(
            icon = Icons.Outlined.PlayArrow,
            titleRes = R.string.nav_product_demo,
            message = stringResource(R.string.product_demo_subtitle),
        )
        ProductDemoPhoneCard()
        ProductDemoSignalCard()
        ProductDemoSceneTimeline()
        ProductDemoCapabilityWall()
    }
}

@Composable
private fun ProductDemoPhoneCard() {
    val animatedProgress by animateFloatAsState(
        targetValue = 0.68f,
        animationSpec = tween(durationMillis = 700),
        label = "productDemoRestProgress",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DemoStatusBar()
            Text(
                stringResource(R.string.product_demo_phone_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier.size(184.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(184.dp),
                    strokeWidth = 12.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("12:40", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.product_demo_resting_state),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            DemoMetricGrid()
            DemoBottomNav()
        }
    }
}

@Composable
private fun DemoStatusBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("09:42", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Project Lumen", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text("100%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DemoMetricGrid() {
    val demoMetrics = listOf(
        ProductDemoMetric(R.string.working_time, minutesLabel(96)),
        ProductDemoMetric(R.string.rest_time, minutesLabel(12)),
        ProductDemoMetric(R.string.completed_breaks, "6"),
        ProductDemoMetric(R.string.completed_tomatoes, "3"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        demoMetrics.chunked(2).forEach { rowMetrics ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowMetrics.forEach { metric ->
                    DemoMetricChip(metric, modifier = Modifier.weight(1f))
                }
                if (rowMetrics.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DemoMetricChip(metric: ProductDemoMetric, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LumenCardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(metric.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(metric.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DemoBottomNav() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DemoNavItem(Icons.Outlined.Spa, R.string.nav_break, selected = true)
        DemoNavItem(Icons.Outlined.LocalCafe, R.string.nav_pomodoro, selected = false)
        DemoNavItem(Icons.Outlined.BarChart, R.string.nav_stats, selected = false)
        DemoNavItem(Icons.Outlined.Style, R.string.nav_templates, selected = false)
    }
}

@Composable
private fun DemoNavItem(icon: ImageVector, @StringRes labelRes: Int, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ProductDemoSignalCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(Icons.Outlined.PhotoCamera, R.string.product_demo_signal_title)
            productDemoSignals.forEach { signal ->
                ProductDemoSignalRow(signal)
            }
        }
    }
}

@Composable
private fun ProductDemoSignalRow(signal: ProductDemoSignal) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(signal.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(
                stringResource(signal.labelRes),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(signal.value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { signal.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun ProductDemoSceneTimeline() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(Icons.Outlined.PlayArrow, R.string.product_demo_timeline_title)
            productDemoScenes.forEachIndexed { index, scene ->
                ProductDemoSceneRow(index, scene)
            }
        }
    }
}

@Composable
private fun ProductDemoSceneRow(index: Int, scene: ProductDemoScene) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(scene.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.product_demo_scene_step, index + 1, productDemoScenes.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(scene.titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(scene.detailRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProductDemoCapabilityWall() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeader(Icons.Outlined.Security, R.string.product_demo_capability_title)
            LumenFlowRow {
                ProductDemoCapabilityChip(Icons.Outlined.Style, R.string.nav_templates)
                ProductDemoCapabilityChip(Icons.Outlined.Security, R.string.section_shizuku_advanced)
                ProductDemoCapabilityChip(Icons.Outlined.CloudDone, R.string.remote_cloud_title)
                ProductDemoCapabilityChip(Icons.Outlined.FileDownload, R.string.statistics_export)
                ProductDemoCapabilityChip(Icons.Outlined.Code, R.string.product_demo_capability_open_api)
            }
        }
    }
}

@Composable
private fun ProductDemoCapabilityChip(icon: ImageVector, @StringRes labelRes: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
