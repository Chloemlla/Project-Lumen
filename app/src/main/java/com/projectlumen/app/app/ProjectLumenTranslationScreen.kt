package com.projectlumen.app.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.api.ProjectLumenTranslationApiClient
import com.projectlumen.app.core.api.TranslationConfig
import com.projectlumen.app.core.api.TranslationResult
import kotlinx.coroutines.launch

private data class TranslationLanguageOption(
    val code: String,
    val labelRes: Int,
)

private val sourceLanguageOptions = listOf(
    TranslationLanguageOption("auto", R.string.translation_lang_auto),
    TranslationLanguageOption("ZH", R.string.translation_lang_zh),
    TranslationLanguageOption("EN", R.string.translation_lang_en),
    TranslationLanguageOption("JA", R.string.translation_lang_ja),
    TranslationLanguageOption("KO", R.string.translation_lang_ko),
)

private val targetLanguageOptions = listOf(
    TranslationLanguageOption("ZH", R.string.translation_lang_zh),
    TranslationLanguageOption("EN", R.string.translation_lang_en),
    TranslationLanguageOption("JA", R.string.translation_lang_ja),
    TranslationLanguageOption("KO", R.string.translation_lang_ko),
)

@Composable
internal fun TranslationScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val api = remember(context) { ProjectLumenTranslationApiClient(context.applicationContext) }
    var sourceText by rememberSaveable { mutableStateOf("") }
    var sourceLang by rememberSaveable { mutableStateOf("auto") }
    var targetLang by rememberSaveable { mutableStateOf("ZH") }
    var config by remember { mutableStateOf<TranslationConfig?>(null) }
    var result by remember { mutableStateOf<TranslationResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadingConfig by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    val blankTextMessage = stringResource(R.string.translation_error_blank_text)
    val textTooLongMessage = stringResource(R.string.translation_error_text_too_long)
    val unavailableMessage = stringResource(R.string.translation_service_unavailable)
    val genericError = stringResource(R.string.translation_failed)
    val trimmedText = sourceText.trim()
    val serviceEnabled = config?.enabled != false

    fun refreshConfig() {
        scope.launch {
            loadingConfig = true
            errorMessage = null
            runCatching { api.fetchConfig() }
                .onSuccess { config = it }
                .onFailure { errorMessage = it.message ?: genericError }
            loadingConfig = false
        }
    }

    fun translate() {
        when {
            trimmedText.isBlank() -> {
                errorMessage = blankTextMessage
                return
            }
            trimmedText.length > 5000 -> {
                errorMessage = textTooLongMessage
                return
            }
            !serviceEnabled -> {
                errorMessage = unavailableMessage
                return
            }
        }
        scope.launch {
            translating = true
            errorMessage = null
            result = null
            runCatching {
                api.translate(
                    text = trimmedText,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                )
            }.onSuccess {
                result = it
            }.onFailure {
                errorMessage = it.message ?: genericError
            }
            translating = false
        }
    }

    LaunchedEffect(api) {
        loadingConfig = true
        runCatching { api.fetchConfig() }
            .onSuccess { config = it }
            .onFailure { errorMessage = it.message ?: genericError }
        loadingConfig = false
    }

    LumenPage {
        PageIntro(
            icon = Icons.Outlined.Translate,
            titleRes = R.string.nav_translation,
            message = stringResource(R.string.translation_subtitle),
        )
        ActionCard {
            SectionHeader(Icons.Outlined.Translate, R.string.translation_input)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = sourceText,
                onValueChange = {
                    sourceText = it.take(5000)
                    if (errorMessage == blankTextMessage || errorMessage == textTooLongMessage) {
                        errorMessage = null
                    }
                },
                label = { Text(stringResource(R.string.translation_text_label)) },
                supportingText = { Text(stringResource(R.string.translation_character_count, sourceText.length, 5000)) },
                minLines = 5,
                maxLines = 9,
            )
            Text(stringResource(R.string.translation_source_language), style = MaterialTheme.typography.titleSmall)
            LanguageOptionsRow(sourceLanguageOptions, sourceLang) { sourceLang = it }
            Text(stringResource(R.string.translation_target_language), style = MaterialTheme.typography.titleSmall)
            LanguageOptionsRow(targetLanguageOptions, targetLang) { targetLang = it }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !translating && trimmedText.isNotBlank() && serviceEnabled,
                onClick = ::translate,
            ) {
                if (translating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(stringResource(R.string.translation_translating))
                } else {
                    ButtonLabel(Icons.Outlined.Translate, R.string.translation_translate)
                }
            }
            AnimatedVisibility(
                visible = loadingConfig || !serviceEnabled,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                StatusLine(
                    icon = if (serviceEnabled) Icons.Outlined.Sync else Icons.Outlined.ErrorOutline,
                    text = when {
                        loadingConfig -> stringResource(R.string.translation_checking_service)
                        else -> unavailableMessage
                    },
                )
            }
        }
        errorMessage?.let { message ->
            StatusLine(Icons.Outlined.ErrorOutline, message)
        }
        result?.let { translation ->
            ActionCard {
                SectionHeader(Icons.Outlined.Translate, R.string.translation_result)
                Text(
                    translation.translatedText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.translation_language_pair,
                            translation.sourceLang,
                            translation.targetLang,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(translation.translatedText)) },
                    ) {
                        ButtonLabel(Icons.Outlined.ContentCopy, R.string.translation_copy_result)
                    }
                }
                AnimatedVisibility(visible = translation.alternatives.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.translation_alternatives),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        translation.alternatives.forEach { alternative ->
                            StatusLine(Icons.Outlined.Info, alternative)
                        }
                    }
                }
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !loadingConfig,
            onClick = ::refreshConfig,
        ) {
            ButtonLabel(Icons.Outlined.Sync, R.string.translation_refresh_service)
        }
    }
}

@Composable
private fun LanguageOptionsRow(
    options: List<TranslationLanguageOption>,
    selectedCode: String,
    onSelected: (String) -> Unit,
) {
    LumenFlowRow {
        options.forEach { option ->
            FilterChip(
                selected = selectedCode == option.code,
                onClick = { onSelected(option.code) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}
