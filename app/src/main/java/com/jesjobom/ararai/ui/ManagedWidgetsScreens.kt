@file:Suppress("LongMethod", "LongParameterList", "MaxLineLength", "TooManyFunctions")

package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.widget.managed.ManagedWidgetExecutionStatus
import com.jesjobom.ararai.widget.managed.ManagedWidgetStatus
import com.jesjobom.ararai.widget.managed.WidgetRunDiagnostic
import com.jesjobom.ararai.widget.managed.WidgetRunOutcome
import com.jesjobom.ararai.widget.runtime.WidgetProgramParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramValidationResult
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ManagedWidgetsRoute(
    controller: ManagedWidgetsController,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenWidget: (String) -> Unit,
) {
    var items by remember { mutableStateOf<List<ManagedWidgetListItemUiState>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        runCatching { controller.loadList() }
            .onSuccess {
                items = it
                failed = false
            }
            .onFailure { failed = true }
    }
    ManagedWidgetsScreen(
        items = items,
        loadFailed = failed,
        onBack = onBack,
        onCreate = onCreate,
        onOpenWidget = onOpenWidget,
        onRetry = { reload += 1 },
    )
}

@Composable
internal fun ManagedWidgetsScreen(
    items: List<ManagedWidgetListItemUiState>?,
    loadFailed: Boolean,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpenWidget: (String) -> Unit,
    onRetry: () -> Unit,
) {
    ArarAiScaffold(title = stringResource(R.string.widgets_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.widgets_create))
            }
            when {
                loadFailed -> WidgetLoadFailure(onRetry)
                items == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                items.isEmpty() -> WidgetEmptyState(onCreate)
                else -> items.forEach { item -> WidgetListCard(item, onOpenWidget) }
            }
        }
    }
}

@Composable
private fun WidgetEmptyState(onCreate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Widgets, contentDescription = null)
            Text(stringResource(R.string.widgets_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.widgets_empty_description), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCreate) { Text(stringResource(R.string.widgets_create)) }
        }
    }
}

@Composable
private fun WidgetLoadFailure(onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Error, contentDescription = null)
            Text(stringResource(R.string.widgets_load_failed))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.models_retry)) }
        }
    }
}

@Composable
private fun WidgetListCard(
    item: ManagedWidgetListItemUiState,
    onOpenWidget: (String) -> Unit,
) {
    Card(onClick = { onOpenWidget(item.definition.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.definition.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(if (item.definition.enabled) stringResource(R.string.widgets_enabled) else stringResource(R.string.widgets_disabled))
            }
            Text(
                if (item.definition.status == ManagedWidgetStatus.NeedsAttention) {
                    stringResource(R.string.widgets_needs_attention)
                } else {
                    stringResource(R.string.widgets_ready)
                },
                color = if (item.definition.status == ManagedWidgetStatus.NeedsAttention) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (item.stale) Text(stringResource(R.string.widgets_stale), color = MaterialTheme.colorScheme.error)
            Text(
                item.definition.lastSuccessAtMillis?.let {
                    stringResource(R.string.widgets_last_result, formatWidgetTimestamp(it))
                } ?: stringResource(R.string.widgets_never_run),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun ManagedWidgetDetailRoute(
    controller: ManagedWidgetsController,
    widgetId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onDuplicated: (String) -> Unit,
) {
    var state by remember(widgetId) { mutableStateOf<ManagedWidgetDetailUiState?>(null) }
    var failed by remember(widgetId) { mutableStateOf(false) }
    var busy by remember(widgetId) { mutableStateOf(false) }
    var reload by remember(widgetId) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(widgetId, reload) {
        runCatching { controller.loadDetail(widgetId) }
            .onSuccess {
                state = it
                failed = it == null
            }
            .onFailure { failed = true }
    }
    fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (_: RuntimeException) {
                failed = true
            } finally {
                busy = false
            }
        }
    }
    ManagedWidgetDetailScreen(
        state = state,
        loadFailed = failed,
        busy = busy,
        onBack = onBack,
        onEdit = onEdit,
        onRefresh = {
            operation {
                val result = controller.refresh(widgetId)
                if (result == ManagedWidgetExecutionStatus.Missing) onDeleted() else reload += 1
            }
        },
        onEnabledChange = { enabled ->
            operation {
                controller.setEnabled(widgetId, enabled)
                reload += 1
            }
        },
        onDuplicate = {
            operation {
                val duplicate = controller.duplicate(widgetId)
                if (duplicate == null) failed = true else onDuplicated(duplicate.id)
            }
        },
        onDelete = {
            operation {
                controller.delete(widgetId)
                onDeleted()
            }
        },
        onRetry = { reload += 1 },
    )
}

@Composable
internal fun ManagedWidgetDetailScreen(
    state: ManagedWidgetDetailUiState?,
    loadFailed: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.widget_delete_title)) },
            text = { Text(stringResource(R.string.widget_delete_description)) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.widget_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    ArarAiScaffold(title = state?.definition?.displayName ?: stringResource(R.string.widget_detail_title), onBack = onBack) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                loadFailed -> WidgetLoadFailure(onRetry)
                state == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                else -> {
                    WidgetPresentationSection(state) { uriHandler.openUri(it) }
                    WidgetStatusSection(state)
                    WidgetManagementActions(
                        state = state,
                        busy = busy,
                        onRefresh = onRefresh,
                        onEnabledChange = onEnabledChange,
                        onEdit = onEdit,
                        onDuplicate = onDuplicate,
                        onDelete = { confirmDelete = true },
                    )
                    WidgetPermissionSection(state)
                    WidgetRunHistory(state.runs)
                    Text(stringResource(R.string.widget_source), style = MaterialTheme.typography.titleMedium)
                    Text(state.revision.source, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun WidgetPresentationSection(
    state: ManagedWidgetDetailUiState,
    onOpenArticle: (String) -> Unit,
) {
    when {
        state.presentationInvalid -> Text(
            stringResource(R.string.widget_invalid_presentation),
            color = MaterialTheme.colorScheme.error,
        )
        state.presentation != null -> ManagedWidgetPresentation(
            presentation = state.presentation,
            onOpenWikipediaArticle = onOpenArticle,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> Text(stringResource(R.string.widget_no_presentation))
    }
    if (state.stale) Text(stringResource(R.string.widgets_stale), color = MaterialTheme.colorScheme.error)
    state.presentationCompletedAtMillis?.let {
        Text(
            stringResource(R.string.widgets_last_result, formatWidgetTimestamp(it)),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun WidgetStatusSection(state: ManagedWidgetDetailUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (state.definition.enabled) stringResource(R.string.widgets_enabled) else stringResource(R.string.widgets_disabled),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                state.definition.periodicIntervalHours?.let {
                    stringResource(R.string.widget_schedule_periodic, it)
                } ?: stringResource(R.string.widget_schedule_manual),
            )
            Text(stringResource(R.string.widget_schedule_inexact), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.widget_active_revision, state.definition.activeRevision))
        }
    }
}

@Composable
private fun WidgetManagementActions(
    state: ManagedWidgetDetailUiState,
    busy: Boolean,
    onRefresh: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (state.definition.enabled) stringResource(R.string.widget_disable) else stringResource(R.string.widget_enable),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = state.definition.enabled,
            enabled = !busy,
            onCheckedChange = onEnabledChange,
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onRefresh, enabled = !busy && state.definition.enabled) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(stringResource(R.string.widget_refresh))
        }
        OutlinedButton(onClick = onEdit, enabled = !busy) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Text(stringResource(R.string.widget_edit))
        }
        OutlinedButton(onClick = onDuplicate, enabled = !busy) { Text(stringResource(R.string.widget_duplicate)) }
        TextButton(onClick = onDelete, enabled = !busy) {
            Icon(Icons.Filled.Delete, contentDescription = null)
            Text(stringResource(R.string.widget_delete))
        }
    }
    if (busy) CircularProgressIndicator()
}

@Composable
private fun WidgetPermissionSection(state: ManagedWidgetDetailUiState) {
    Text(stringResource(R.string.widget_permissions), style = MaterialTheme.typography.titleMedium)
    val parsed = remember(state.revision) {
        WidgetProgramParser.parse(state.revision.manifestJson, state.revision.source)
    }
    if (parsed is WidgetProgramValidationResult.Valid) {
        val capabilities = parsed.program.manifest.capabilities
        Text(stringResource(R.string.widget_tools, capabilities.tools.joinToString { "${it.id}@${it.version}" }.ifEmpty { "—" }))
        Text(stringResource(R.string.widget_runtime_values, capabilities.runtimeValues.joinToString { it.wireName }.ifEmpty { "—" }))
        Text(stringResource(R.string.widget_presentation_scope, capabilities.presentation.joinToString { it.wireName }.ifEmpty { "—" }))
    } else {
        Text(stringResource(R.string.widgets_needs_attention), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun WidgetRunHistory(runs: List<com.jesjobom.ararai.widget.managed.WidgetRunRecord>) {
    Text(stringResource(R.string.widget_run_history), style = MaterialTheme.typography.titleMedium)
    if (runs.isEmpty()) {
        Text(stringResource(R.string.widget_no_runs))
    } else {
        runs.forEachIndexed { index, run ->
            Text(
                stringResource(
                    R.string.widget_run_entry,
                    formatWidgetTimestamp(run.startedAtMillis),
                    run.revision,
                    run.diagnostic?.localizedName() ?: run.outcome.localizedName(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (index != runs.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun WidgetRunOutcome.localizedName(): String = name.replace(Regex("([a-z])([A-Z])"), "$1 $2")

@Composable
private fun WidgetRunDiagnostic.localizedName(): String = name.replace(Regex("([a-z])([A-Z])"), "$1 $2")

private fun formatWidgetTimestamp(timestampMillis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestampMillis))
