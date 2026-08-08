package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library

private data class ExternalLicenseNotice(
    val name: String,
    val license: String,
    val sourceUrl: String,
)

private val externalLicenseNotices = listOf(
    ExternalLicenseNotice(
        name = "whisper.cpp",
        license = "MIT License",
        sourceUrl = "https://github.com/ggml-org/whisper.cpp",
    ),
    ExternalLicenseNotice(
        name = "Gemma 4 E2B and E4B LiteRT-LM bundles",
        license = "Apache License 2.0",
        sourceUrl = "https://huggingface.co/litert-community",
    ),
    ExternalLicenseNotice(
        name = "Whisper Base and Small Q5_1 models",
        license = "MIT License",
        sourceUrl = "https://huggingface.co/ggerganov/whisper.cpp",
    ),
)

@Composable
internal fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val libraries = remember(context) {
        context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { reader ->
            Libs.Builder().withJson(reader.readText()).build().libraries
        }
    }
    var selectedLibrary by remember { mutableStateOf<Library?>(null) }

    ArarAiScaffold(
        title = stringResource(R.string.open_source_licenses_title),
        onBack = onBack,
    ) { modifier ->
        LazyColumn(
            modifier = modifier,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.open_source_licenses_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                LicenseSectionTitle(stringResource(R.string.open_source_licenses_external_title))
            }
            items(externalLicenseNotices, key = ExternalLicenseNotice::name) { notice ->
                LicenseCard(
                    title = notice.name,
                    subtitle = notice.license,
                    onClick = { uriHandler.openUri(notice.sourceUrl) },
                )
            }
            item {
                LicenseSectionTitle(stringResource(R.string.open_source_licenses_gradle_title))
            }
            items(libraries, key = Library::uniqueId) { library ->
                LicenseCard(
                    title = library.name.ifBlank { library.uniqueId },
                    subtitle = library.licenses.joinToString { it.name },
                    onClick = { selectedLibrary = library },
                )
            }
        }
    }

    selectedLibrary?.let { library ->
        LibraryLicenseDialog(
            library = library,
            onDismiss = { selectedLibrary = null },
            onOpenWebsite = library.website?.let { website -> { uriHandler.openUri(website) } },
        )
    }
}

@Composable
private fun LicenseSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LicenseCard(title: String, subtitle: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryLicenseDialog(
    library: Library,
    onDismiss: () -> Unit,
    onOpenWebsite: (() -> Unit)?,
) {
    val licenseText = library.licenses.joinToString("\n\n") { license ->
        license.licenseContent?.takeIf(String::isNotBlank) ?: license.url ?: license.name
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(library.name.ifBlank { library.uniqueId }) },
        text = {
            Text(
                text = licenseText,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = onOpenWebsite?.let { openWebsite ->
            {
                TextButton(onClick = openWebsite) {
                    Text(stringResource(R.string.action_open_source))
                }
            }
        },
    )
}
