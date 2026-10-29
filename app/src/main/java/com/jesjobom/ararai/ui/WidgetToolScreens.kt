package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.WEB_SEARCH_CONTENT_TOOL_NAME
import com.jesjobom.ararai.tools.WEB_SEARCH_TOP_RESULTS_TOOL_NAME
import com.jesjobom.ararai.widget.managed.authoringWidgetContracts

@Composable
internal fun WidgetToolsScreen(
    contracts: List<ApplicationToolContract>,
    wikipediaEnabled: Boolean,
    onWikipediaEnabledChange: (Boolean) -> Unit,
    webSearchEnabled: Boolean = false,
    webProviderConfigured: Boolean = false,
    onWebSearchEnabledChange: (Boolean) -> Unit = {},
    onConfigureWebProviders: () -> Unit = {},
    onBack: () -> Unit,
) {
    ArarAiScaffold(title = stringResource(R.string.widgets_tools_title), onBack = onBack) { modifier ->
        Column(
            modifier.verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.wikipedia_name), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.widgets_wikipedia_tools_description))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                if (wikipediaEnabled) {
                                    R.string.widgets_tools_enabled
                                } else {
                                    R.string.widgets_tools_disabled
                                },
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = wikipediaEnabled,
                            onCheckedChange = onWikipediaEnabledChange,
                            modifier = Modifier.testTag("widget-wikipedia-enabled"),
                        )
                    }
                    contracts.currentWidgetToolContracts()
                        .filter { it.id.startsWith("wikipedia_") }
                        .forEach { contract ->
                            Text("${contract.displayName} · ${contract.id}@${contract.version}")
                        }
                }
            }
            contracts.currentWidgetToolContracts()
                .filter { it.id == WEB_SEARCH_TOP_RESULTS_TOOL_NAME || it.id == WEB_SEARCH_CONTENT_TOOL_NAME }
                .takeIf(List<*>::isNotEmpty)
                ?.let { webContracts ->
                    WidgetWebSearchToolCard(
                        webContracts,
                        webSearchEnabled,
                        webProviderConfigured,
                        onWebSearchEnabledChange,
                        onConfigureWebProviders,
                    )
                }
            Text(
                stringResource(R.string.widgets_tools_revision_note),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WidgetWebSearchToolCard(
    contracts: List<ApplicationToolContract>,
    enabled: Boolean,
    configured: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onConfigure: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.widgets_web_search_name), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.widgets_web_search_description))
            Text(
                stringResource(
                    if (configured) R.string.widgets_web_provider_ready else R.string.widgets_web_provider_missing,
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        if (enabled) R.string.widgets_tools_enabled else R.string.widgets_tools_disabled,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.testTag("widget-web-search-enabled"),
                )
            }
            OutlinedButton(onClick = onConfigure) {
                Text(stringResource(R.string.widgets_web_configure_providers))
            }
            contracts.forEach { contract ->
                Text("${contract.displayName} · ${contract.id}@${contract.version}")
            }
        }
    }
}

/** Show the contract offered to new widgets; older versions remain registered for saved revisions. */
internal fun List<ApplicationToolContract>.currentWidgetToolContracts(): List<ApplicationToolContract> = this
    .authoringWidgetContracts()
