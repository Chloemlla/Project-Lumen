package com.projectlumen.app.app

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.services.AuraAudioService

@Composable
internal fun SettingsSoundSection(settings: AppSettingsEntity, viewModel: ProjectLumenViewModel) {
    val context = LocalContext.current
    SettingsSection(R.string.section_sound, Icons.AutoMirrored.Outlined.VolumeUp, initiallyExpanded = false) {
        var auraInstalled by remember { mutableStateOf(AuraAudioService.isAuraInstalled(context)) }
        val auraLifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(auraLifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    auraInstalled = AuraAudioService.isAuraInstalled(context)
                }
            }
            auraLifecycleOwner.lifecycle.addObserver(observer)
            onDispose { auraLifecycleOwner.lifecycle.removeObserver(observer) }
        }
        AnimatedVisibility(
            visible = !auraInstalled,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
        ) {
            NotificationRequirementCard(
                titleRes = R.string.aura_not_installed,
                messageRes = R.string.aura_not_installed_message,
                actionLabelRes = R.string.aura_install_action,
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                onClick = { openUri(context, AuraAudioService.AURA_RELEASES_URL.toUri()) },
            )
        }
        SwitchRow(R.string.enable_sound, Icons.AutoMirrored.Outlined.VolumeUp, settings.soundEnabled) {
            viewModel.updateSettings { current -> current.copy(soundEnabled = it) }
        }
        SwitchRow(R.string.pre_alert_sound, Icons.Outlined.Schedule, settings.preAlertSoundEnabled) {
            viewModel.updateSettings { current -> current.copy(preAlertSoundEnabled = it) }
        }
        SwitchRow(R.string.rest_start_sound, Icons.Outlined.Spa, settings.restStartSoundEnabled) {
            viewModel.updateSettings { current -> current.copy(restStartSoundEnabled = it) }
        }
        SwitchRow(R.string.pomodoro_work_start_sound, Icons.Outlined.PlayArrow, settings.pomodoroWorkStartSoundEnabled) {
            viewModel.updateSettings { current -> current.copy(pomodoroWorkStartSoundEnabled = it) }
        }
        SwitchRow(R.string.pomodoro_work_end_sound, Icons.Outlined.Stop, settings.pomodoroWorkEndSoundEnabled) {
            viewModel.updateSettings { current -> current.copy(pomodoroWorkEndSoundEnabled = it) }
        }
        NumberSlider(R.string.pre_alert_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.preAlertVolumePercent, 0f..100f, 19, stringResource(R.string.percent_value, settings.preAlertVolumePercent)) {
            viewModel.updateSettings { current -> current.copy(preAlertVolumePercent = it) }
        }
        NumberSlider(R.string.rest_start_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.restStartVolumePercent, 0f..100f, 19, stringResource(R.string.percent_value, settings.restStartVolumePercent)) {
            viewModel.updateSettings { current -> current.copy(restStartVolumePercent = it) }
        }
        NumberSlider(R.string.rest_end_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.restEndVolumePercent, 0f..100f, 19, stringResource(R.string.percent_value, settings.restEndVolumePercent)) {
            viewModel.updateSettings { current -> current.copy(restEndVolumePercent = it) }
        }
        NumberSlider(R.string.pomodoro_start_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.pomodoroWorkStartVolumePercent, 0f..100f, 19, stringResource(R.string.percent_value, settings.pomodoroWorkStartVolumePercent)) {
            viewModel.updateSettings { current -> current.copy(pomodoroWorkStartVolumePercent = it) }
        }
        NumberSlider(R.string.pomodoro_end_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.pomodoroWorkEndVolumePercent, 0f..100f, 19, stringResource(R.string.percent_value, settings.pomodoroWorkEndVolumePercent)) {
            viewModel.updateSettings { current -> current.copy(pomodoroWorkEndVolumePercent = it) }
        }
    }
}

@Composable
internal fun SettingsAppearanceSection(
    settings: AppSettingsEntity,
    activeTemplate: TipTemplateEntity?,
    templates: List<TipTemplateEntity>,
    viewModel: ProjectLumenViewModel,
) {
    SettingsSection(R.string.section_appearance, Icons.Outlined.Style) {
        val proEnabled = planTier(settings) >= PlanTier.PRO
        SwitchRow(R.string.use_wallpaper_colors, Icons.Outlined.Style, settings.useDynamicColors) {
            viewModel.updateSettings { current -> current.copy(useDynamicColors = it) }
        }
        Text(
            stringResource(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    R.string.wallpaper_colors_message
                } else {
                    R.string.wallpaper_colors_unavailable
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(
            visible = !settings.useDynamicColors,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap)) {
                TemplatePreviewCard(activeTemplate)
                val selectableTemplates = remember(templates, proEnabled) {
                    templates.filter { !it.isPremium || proEnabled }
                }
                LumenFlowRow {
                    selectableTemplates.forEach { template ->
                        val selected = settings.activeTipTemplateId == template.id
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectTemplate(template.id) },
                            label = { Text(templateDisplayName(template)) },
                            leadingIcon = {
                                AnimatedVisibility(
                                    visible = selected,
                                    enter = scaleIn(tween(120)) + fadeIn(tween(120)),
                                    exit = scaleOut(tween(90)) + fadeOut(tween(90)),
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
