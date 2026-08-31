package com.projectlumen.app.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.enums.TemplateBackgroundType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun TemplatesScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val activeTemplate = activeTemplate(uiState)
    val context = LocalContext.current
    val proEnabled = planTier(uiState.settings) >= PlanTier.PRO
    var imageTargetTemplateId by rememberSaveable { mutableStateOf<Long?>(null) }
    val templateImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetTemplate = uiState.templates.firstOrNull { it.id == imageTargetTemplateId }
        imageTargetTemplateId = null
        if (uri != null) {
            if (targetTemplate == null) {
                Toast.makeText(context, R.string.template_image_target_lost, Toast.LENGTH_SHORT).show()
            } else {
                persistReadableUri(context, uri)
                viewModel.updateTemplateImage(targetTemplate, uri.toString())
            }
        }
    }
    LumenPage {
        SectionHeader(Icons.Outlined.Style, R.string.template_preview)
        TemplatePreviewCard(activeTemplate)
        uiState.templates.forEach { template ->
            val isActiveTemplate = template.id == uiState.settings.activeTipTemplateId
            val locked = template.isPremium && !proEnabled
            val templateEmphasis = when {
                isActiveTemplate -> LumenCardEmphasis.Primary
                locked -> LumenCardEmphasis.Quiet
                else -> LumenCardEmphasis.Standard
            }
            val borderColor by animateColorAsState(
                targetValue = if (isActiveTemplate) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(180),
                label = "templateBorderColor",
            )
            val useTemplateLabel = stringResource(R.string.use_template)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !locked && !isActiveTemplate,
                        onClickLabel = useTemplateLabel,
                        role = Role.Button,
                    ) { viewModel.selectTemplate(template.id) }
                    .semantics { selected = isActiveTemplate }
                    .border(2.dp, borderColor, LumenCardShape)
                    .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
                shape = LumenCardShape,
                colors = lumenCardColors(templateEmphasis),
                elevation = lumenCardElevation(),
                border = if (isActiveTemplate) null else lumenCardBorder(templateEmphasis),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TemplateColorSwatch(template)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (template.isPremium) {
                                    "${templateDisplayName(template)} · ${stringResource(R.string.premium_template)}"
                                } else {
                                    templateDisplayName(template)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(templateSubtitle(template), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isActiveTemplate) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    when {
                        locked -> StatusPill(Icons.Outlined.Lock, R.string.pro_required)
                        isActiveTemplate -> StatusPill(Icons.Outlined.CheckCircle, R.string.active_template)
                        else -> FilterChip(
                            selected = false,
                            onClick = { viewModel.selectTemplate(template.id) },
                            label = { Text(stringResource(R.string.use_template)) },
                        )
                    }
                    if (!locked) {
                        LumenFlowRow {
                            OutlinedButton(
                                onClick = {
                                    imageTargetTemplateId = template.id
                                    templateImageLauncher.launch(arrayOf("image/*"))
                                },
                            ) {
                                ButtonLabel(Icons.Outlined.FileDownload, R.string.choose_template_image)
                            }
                            if (template.imagePath.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { viewModel.updateTemplateImage(template, "") },
                                ) {
                                    Text(stringResource(R.string.clear_custom_file))
                                }
                            }
                        }
                        if (isActiveTemplate) {
                            TemplateEditor(template, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TemplateEditor(template: TipTemplateEntity, viewModel: ProjectLumenViewModel) {
    // Keyed on the id only: keying on updatedAt would let each keystroke's own database write
    // reset the field back to the persisted value and swallow characters.
    var titleText by remember(template.id) { mutableStateOf(template.titleText) }
    var subtitleText by remember(template.id) { mutableStateOf(template.subtitleText) }
    val latestTemplate by rememberUpdatedState(template)
    LaunchedEffect(template.id, titleText, subtitleText) {
        if (titleText == latestTemplate.titleText && subtitleText == latestTemplate.subtitleText) {
            return@LaunchedEffect
        }
        delay(TEMPLATE_TEXT_COMMIT_DEBOUNCE_MILLIS)
        viewModel.updateTemplateContent(
            latestTemplate,
            titleText,
            subtitleText,
            latestTemplate.showSkipButton,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.template_editor), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = titleText,
            onValueChange = { titleText = it },
            label = { Text(stringResource(R.string.template_title_text)) },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = subtitleText,
            onValueChange = { subtitleText = it },
            label = { Text(stringResource(R.string.template_subtitle_text)) },
        )
        SwitchRow(R.string.template_show_skip_button, Icons.Outlined.SkipNext, template.showSkipButton) {
            viewModel.updateTemplateContent(template, titleText, subtitleText, it)
        }
        Text(stringResource(R.string.template_countdown_style), style = MaterialTheme.typography.titleSmall)
        LumenFlowRow {
            CountdownStyleChip(R.string.template_countdown_circle, COUNTDOWN_STYLE_CIRCLE, template, viewModel)
            CountdownStyleChip(R.string.template_countdown_bar, COUNTDOWN_STYLE_BAR, template, viewModel)
            CountdownStyleChip(R.string.template_countdown_number, COUNTDOWN_STYLE_NUMBER, template, viewModel)
        }
    }
}

private const val TEMPLATE_TEXT_COMMIT_DEBOUNCE_MILLIS = 400L

@Composable
internal fun CountdownStyleChip(
    @StringRes labelRes: Int,
    style: String,
    template: TipTemplateEntity,
    viewModel: ProjectLumenViewModel,
) {
    FilterChip(
        selected = templateCountdownStyle(template) == style,
        onClick = { viewModel.updateTemplateCountdownStyle(template, style) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
internal fun SystemBackgroundPicker(template: TipTemplateEntity, viewModel: ProjectLumenViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Style, R.string.system_background_color)
            LumenFlowRow {
                SystemBackgroundColor.entries.forEach { option ->
                    FilterChip(
                        selected = template.backgroundType == TemplateBackgroundType.SYSTEM.name &&
                            template.backgroundValue == option.key,
                        onClick = {
                            viewModel.updateTemplateSystemBackground(
                                template = template,
                                backgroundValue = option.key,
                                primaryColor = option.primaryKey,
                            )
                        },
                        label = { Text(stringResource(option.labelRes)) },
                        leadingIcon = { ColorSwatch(systemThemeColor(option.key), size = 18.dp) },
                    )
                }
            }
        }
    }
}


