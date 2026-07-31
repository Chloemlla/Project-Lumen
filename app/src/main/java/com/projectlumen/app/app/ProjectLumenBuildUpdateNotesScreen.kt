package com.projectlumen.app.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.update.BuildUpdateNotes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun ProjectLumenBuildUpdateNotesScreen(
    notes: BuildUpdateNotes,
    onContinue: () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    if (onDismiss != null) {
        BackHandler(onBack = onDismiss)
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
                    Text(stringResource(R.string.build_update_notes_continue))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(PaddingValues(start = 20.dp, top = 28.dp, end = 20.dp, bottom = 28.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.NewReleases,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.build_update_notes_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.build_update_notes_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BuildUpdateNotesIdentityCard(notes)
                BuildUpdateNotesContent(notes)
            }
        }
    }
}

@Composable
private fun BuildUpdateNotesIdentityCard(notes: BuildUpdateNotes) {
    val locale = LocalConfiguration.current.locales[0]
    val buildTime = remember(notes.buildTimeUtcMillis, locale.toLanguageTag()) {
        runCatching {
            Instant.ofEpochMilli(notes.buildTimeUtcMillis)
                .atZone(ZoneId.systemDefault())
                .format(
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                        .withLocale(locale),
                )
        }.getOrElse {
            runCatching { Instant.ofEpochMilli(notes.buildTimeUtcMillis).toString() }
                .getOrDefault(notes.buildTimeUtcMillis.toString())
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BuildUpdateNotesIdentityRow(
                label = stringResource(R.string.build_update_notes_version_label),
                value = stringResource(
                    R.string.build_update_notes_version_value,
                    notes.versionName,
                    notes.versionCode,
                ),
            )
            BuildUpdateNotesIdentityRow(
                label = stringResource(R.string.build_update_notes_commit_label),
                value = notes.shortHash,
                monospace = true,
            )
            BuildUpdateNotesIdentityRow(
                label = stringResource(R.string.build_update_notes_build_time_label),
                value = buildTime,
            )
        }
    }
}

@Composable
private fun BuildUpdateNotesIdentityRow(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BuildUpdateNotesContent(notes: BuildUpdateNotes) {
    val bodyText = if (notes.bodyMarkdownOrPlain.isBlank()) {
        stringResource(R.string.build_update_notes_no_details)
    } else {
        notes.bodyMarkdownOrPlain
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = notes.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (notes.highlights.isNotEmpty()) {
            Text(
                text = stringResource(R.string.build_update_notes_highlights_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            notes.highlights.forEach { highlight ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = highlight,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.build_update_notes_details_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = bodyText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
