@file:Suppress("LongMethod", "LongParameterList", "MaxLineLength", "CyclomaticComplexMethod")

package com.jesjobom.ararai.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.BuildConfig
import com.jesjobom.ararai.R
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import com.jesjobom.ararai.validation.widgetToolCallingDiagnosticEnvironment
import com.jesjobom.ararai.widget.managed.WidgetAuthoringProgress
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStage
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageFailureCode
import com.jesjobom.ararai.widget.managed.WidgetConfirmationMode
import com.jesjobom.ararai.widget.managed.WidgetConfirmationResult
import com.jesjobom.ararai.widget.managed.WidgetToolCallingDiagnosticMode
import com.jesjobom.ararai.widget.managed.WidgetToolCallingDiagnosticReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private sealed interface WidgetAuthoringError {
    data object ModelUnavailable : WidgetAuthoringError
    data object ModelLoadFailed : WidgetAuthoringError
    data object MissingWidget : WidgetAuthoringError
    data class Unachievable(val reason: String) : WidgetAuthoringError
    data class NeedsClarification(val question: String) : WidgetAuthoringError
    data class StageFailed(
        val stage: WidgetAuthoringStage,
        val code: WidgetAuthoringStageFailureCode,
    ) : WidgetAuthoringError
    data object GenerationTimedOut : WidgetAuthoringError
}

private sealed interface ToolCallingDiagnosticState {
    data object Idle : ToolCallingDiagnosticState
    data class Running(val mode: WidgetToolCallingDiagnosticMode) : ToolCallingDiagnosticState
    data class Completed(val report: WidgetToolCallingDiagnosticReport) : ToolCallingDiagnosticState
    data class Failed(val mode: WidgetToolCallingDiagnosticMode) : ToolCallingDiagnosticState
}

@Composable
internal fun ManagedWidgetAuthoringRoute(
    controller: ManagedWidgetsController,
    model: LocalModel?,
    inference: InferenceConfig?,
    modelArtifactSha256: String?,
    widgetId: String?,
    onBack: () -> Unit,
    onConfirmed: (String) -> Unit,
    onOpenToolSettings: () -> Unit,
    onShareDiagnosticReport: (String) -> Unit,
    onShareRawDiagnosticReport: (String) -> Unit,
) {
    var instruction by remember(widgetId) { mutableStateOf("") }
    var draft by remember(widgetId) { mutableStateOf<ManagedWidgetDraftUiState?>(null) }
    var error by remember(widgetId) { mutableStateOf<WidgetAuthoringError?>(null) }
    var confirmationError by remember(widgetId) { mutableStateOf(false) }
    var running by remember(widgetId) { mutableStateOf(false) }
    var progress by remember(widgetId) { mutableStateOf<WidgetAuthoringProgress?>(null) }
    var diagnosticState: ToolCallingDiagnosticState by remember(widgetId) {
        mutableStateOf(ToolCallingDiagnosticState.Idle)
    }
    var job by remember(widgetId) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val view = LocalView.current
    val diagnosticRunning = diagnosticState is ToolCallingDiagnosticState.Running
    val busy = running || diagnosticRunning
    fun cancelAndBack() {
        job?.cancel()
        controller.declineDraft()
        onBack()
    }
    DisposableEffect(controller, widgetId) {
        onDispose {
            job?.cancel()
            controller.declineDraft()
        }
    }
    DisposableEffect(view, diagnosticRunning) {
        if (diagnosticRunning) view.keepScreenOn = true
        onDispose {
            if (diagnosticRunning) view.keepScreenOn = false
        }
    }
    fun generate() {
        if (busy || instruction.isBlank()) return
        error = null
        confirmationError = false
        draft = null
        progress = WidgetAuthoringProgress.AnalyzingFeasibility
        running = true
        job = scope.launch {
            try {
                when (
                    val result = controller.generateDraft(
                        model,
                        inference,
                        instruction,
                        widgetId,
                    ) { current -> progress = current }
                ) {
                    is ManagedWidgetDraftGenerationResult.Ready -> draft = result.value
                    ManagedWidgetDraftGenerationResult.ModelUnavailable -> error = WidgetAuthoringError.ModelUnavailable
                    ManagedWidgetDraftGenerationResult.ModelLoadFailed -> error = WidgetAuthoringError.ModelLoadFailed
                    ManagedWidgetDraftGenerationResult.MissingWidget -> error = WidgetAuthoringError.MissingWidget
                    is ManagedWidgetDraftGenerationResult.Unachievable -> {
                        error = WidgetAuthoringError.Unachievable(result.reason)
                    }
                    is ManagedWidgetDraftGenerationResult.NeedsClarification -> {
                        error = WidgetAuthoringError.NeedsClarification(result.question)
                    }
                    is ManagedWidgetDraftGenerationResult.StageFailed -> {
                        error = WidgetAuthoringError.StageFailed(result.stage, result.code)
                    }
                    ManagedWidgetDraftGenerationResult.GenerationTimedOut -> {
                        error = WidgetAuthoringError.GenerationTimedOut
                    }
                }
            } finally {
                running = false
            }
        }
    }
    fun runToolCallingDiagnostic(mode: WidgetToolCallingDiagnosticMode) {
        val inputs = model?.let { selectedModel ->
            inference?.let { selectedInference ->
                modelArtifactSha256?.let { artifactSha256 ->
                    Triple(selectedModel, selectedInference, artifactSha256)
                }
            }
        }
        if (busy || inputs == null) return
        val (selectedModel, selectedInference, artifactSha256) = inputs
        diagnosticState = ToolCallingDiagnosticState.Running(mode)
        job = scope.launch {
            diagnosticState = try {
                val environment = widgetToolCallingDiagnosticEnvironment(context, artifactSha256)
                ToolCallingDiagnosticState.Completed(
                    controller.runToolCallingDiagnostic(selectedModel, selectedInference, environment, mode),
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ToolCallingDiagnosticState.Failed(mode)
            }
        }
    }
    fun confirm(mode: WidgetConfirmationMode) {
        val selectedDraft = draft ?: return
        if (running) return
        running = true
        job = scope.launch {
            try {
                when (val result = controller.confirm(selectedDraft, mode)) {
                    is WidgetConfirmationResult.Confirmed -> onConfirmed(result.definition.id)
                    is WidgetConfirmationResult.Blocked -> confirmationError = true
                    WidgetConfirmationResult.Failed -> confirmationError = true
                }
            } finally {
                running = false
            }
        }
    }
    ManagedWidgetAuthoringScreen(
        widgetId = widgetId,
        authoringAvailable = model != null &&
            inference != null &&
            model.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1),
        diagnosticAvailable = model != null && inference != null,
        instruction = instruction,
        draft = draft,
        error = error,
        confirmationError = confirmationError,
        running = running,
        progress = progress,
        diagnosticState = diagnosticState,
        onInstructionChange = { instruction = it },
        onGenerate = ::generate,
        onRunToolCallingDiagnostic = ::runToolCallingDiagnostic,
        onCopyDiagnosticReport = { report ->
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                ClipData.newPlainText("ArarAI tool-calling diagnostic", report),
            )
            Toast.makeText(context, R.string.widget_tool_diagnostic_copied, Toast.LENGTH_SHORT).show()
        },
        onShareDiagnosticReport = onShareDiagnosticReport,
        onShareRawDiagnosticReport = onShareRawDiagnosticReport,
        onConfirmEnabled = { confirm(WidgetConfirmationMode.CreateAndEnable) },
        onSaveDisabled = { confirm(WidgetConfirmationMode.SaveDisabled) },
        onDeclineDraft = {
            controller.declineDraft()
            draft = null
        },
        onOpenToolSettings = onOpenToolSettings,
        onBack = ::cancelAndBack,
    )
}

@Composable
private fun ManagedWidgetAuthoringScreen(
    widgetId: String?,
    authoringAvailable: Boolean,
    diagnosticAvailable: Boolean,
    instruction: String,
    draft: ManagedWidgetDraftUiState?,
    error: WidgetAuthoringError?,
    confirmationError: Boolean,
    running: Boolean,
    progress: WidgetAuthoringProgress?,
    diagnosticState: ToolCallingDiagnosticState,
    onInstructionChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onRunToolCallingDiagnostic: (WidgetToolCallingDiagnosticMode) -> Unit,
    onCopyDiagnosticReport: (String) -> Unit,
    onShareDiagnosticReport: (String) -> Unit,
    onShareRawDiagnosticReport: (String) -> Unit,
    onConfirmEnabled: () -> Unit,
    onSaveDisabled: () -> Unit,
    onDeclineDraft: () -> Unit,
    onOpenToolSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val diagnosticRunning = diagnosticState is ToolCallingDiagnosticState.Running
    val busy = running || diagnosticRunning
    ArarAiScaffold(
        title = stringResource(
            if (widgetId == null) R.string.widget_authoring_create_title else R.string.widget_authoring_edit_title,
        ),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!authoringAvailable) {
                WidgetAuthoringMessage(R.string.widget_authoring_model_unavailable, warning = true)
            }
            OutlinedTextField(
                value = instruction,
                onValueChange = onInstructionChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && authoringAvailable,
                label = { Text(stringResource(R.string.widget_authoring_prompt_label)) },
                supportingText = { Text(stringResource(R.string.widget_authoring_prompt_hint)) },
                minLines = 4,
                maxLines = 10,
            )
            Button(
                onClick = onGenerate,
                enabled = !busy && authoringAvailable && instruction.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Text(stringResource(R.string.widget_authoring_generate))
            }
            if (running) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Text(progress?.label() ?: stringResource(R.string.widget_authoring_generating))
                }
            }
            error?.let { WidgetAuthoringErrorMessage(it) }
            if (confirmationError) WidgetAuthoringMessage(R.string.widget_operation_failed, warning = true)
            ToolCallingDiagnosticCard(
                modelAvailable = diagnosticAvailable,
                state = diagnosticState,
                onRun = onRunToolCallingDiagnostic,
                onCopy = onCopyDiagnosticReport,
                onShare = onShareDiagnosticReport,
                onShareRaw = onShareRawDiagnosticReport,
            )
            draft?.let {
                WidgetDraftPreview(
                    value = it,
                    running = busy,
                    isEdit = widgetId != null,
                    onConfirmEnabled = onConfirmEnabled,
                    onSaveDisabled = onSaveDisabled,
                    onDecline = onDeclineDraft,
                    onOpenToolSettings = onOpenToolSettings,
                )
            }
        }
    }
}

@Composable
private fun ToolCallingDiagnosticCard(
    modelAvailable: Boolean,
    state: ToolCallingDiagnosticState,
    onRun: (WidgetToolCallingDiagnosticMode) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onShareRaw: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.widget_tool_diagnostic_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.widget_tool_diagnostic_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.widget_tool_diagnostic_privacy),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            when (state) {
                ToolCallingDiagnosticState.Idle -> {
                    DiagnosticRunButton(
                        label = R.string.widget_tool_diagnostic_probe_full,
                        mode = WidgetToolCallingDiagnosticMode.FeasibilityFullNatural,
                        enabled = modelAvailable,
                        onRun = onRun,
                    )
                    DiagnosticRunButton(
                        label = R.string.widget_tool_diagnostic_probe_compact,
                        mode = WidgetToolCallingDiagnosticMode.FeasibilityCompactNatural,
                        enabled = modelAvailable,
                        onRun = onRun,
                    )
                    DiagnosticRunButton(
                        label = R.string.widget_tool_diagnostic_probe_explicit,
                        mode = WidgetToolCallingDiagnosticMode.FeasibilityCompactExplicit,
                        enabled = modelAvailable,
                        onRun = onRun,
                    )
                    DiagnosticRunButton(
                        label = R.string.widget_tool_diagnostic_probe_algorithm,
                        mode = WidgetToolCallingDiagnosticMode.AlgorithmNatural,
                        enabled = modelAvailable,
                        onRun = onRun,
                    )
                    DiagnosticRunButton(
                        label = R.string.widget_tool_diagnostic_pipeline_cold,
                        mode = WidgetToolCallingDiagnosticMode.CompletePipelineCompactNatural,
                        enabled = modelAvailable,
                        onRun = onRun,
                    )
                    DiagnosticRunButton(
                        label = R.string.widget_tool_diagnostic_run,
                        mode = WidgetToolCallingDiagnosticMode.FullMatrix,
                        enabled = modelAvailable,
                        onRun = onRun,
                    )
                }
                is ToolCallingDiagnosticState.Running -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(
                        stringResource(
                            when (state.mode) {
                                WidgetToolCallingDiagnosticMode.FullMatrix ->
                                    R.string.widget_tool_diagnostic_running
                                WidgetToolCallingDiagnosticMode.CompletePipelineCompactNatural ->
                                    R.string.widget_tool_diagnostic_pipeline_running
                                WidgetToolCallingDiagnosticMode.AlgorithmNatural ->
                                    R.string.widget_tool_diagnostic_algorithm_running
                                else -> R.string.widget_tool_diagnostic_probe_running
                            },
                        ),
                    )
                }
                is ToolCallingDiagnosticState.Failed -> {
                    WidgetAuthoringMessage(R.string.widget_tool_diagnostic_internal_failure, warning = true)
                    OutlinedButton(onClick = { onRun(state.mode) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.widget_tool_diagnostic_run_again))
                    }
                }
                is ToolCallingDiagnosticState.Completed -> ToolCallingDiagnosticResult(
                    report = state.report,
                    onRunAgain = { onRun(state.report.mode) },
                    onCopy = onCopy,
                    onShare = onShare,
                    onShareRaw = onShareRaw,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRunButton(
    label: Int,
    mode: WidgetToolCallingDiagnosticMode,
    enabled: Boolean,
    onRun: (WidgetToolCallingDiagnosticMode) -> Unit,
) {
    OutlinedButton(
        onClick = { onRun(mode) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(label))
    }
}

@Composable
private fun ToolCallingDiagnosticResult(
    report: WidgetToolCallingDiagnosticReport,
    onRunAgain: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onShareRaw: (String) -> Unit,
) {
    val encoded = remember(report) { report.toCanonicalJson() }
    val rawEncoded = remember(report) { report.toRawDiagnosticJson() }
    var showRawExportWarning by remember(report) { mutableStateOf(false) }
    Text(
        text = stringResource(
            if (report.overallPassed) {
                R.string.widget_tool_diagnostic_passed
            } else {
                R.string.widget_tool_diagnostic_failed
            },
        ),
        color = if (report.overallPassed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
    )
    Text(
        stringResource(
            R.string.widget_tool_diagnostic_model_load,
            report.modelLoadOutcome,
            report.modelLoadDurationMillis,
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    report.cases.forEach { result ->
        Text(
            stringResource(
                R.string.widget_tool_diagnostic_case,
                if (result.passed) "PASS" else "FAIL",
                result.id,
                result.outcome,
                result.durationMillis,
            ),
            color = if (result.passed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = { onCopy(encoded) }) {
            Text(stringResource(R.string.widget_tool_diagnostic_copy))
        }
        Button(onClick = { onShare(encoded) }) {
            Text(stringResource(R.string.widget_tool_diagnostic_share))
        }
        if (BuildConfig.DEBUG && rawEncoded != null) {
            Button(onClick = { showRawExportWarning = true }) {
                Text(stringResource(R.string.widget_tool_diagnostic_export_raw))
            }
        }
        OutlinedButton(onClick = onRunAgain) {
            Text(stringResource(R.string.widget_tool_diagnostic_run_again))
        }
    }
    if (showRawExportWarning && rawEncoded != null) {
        AlertDialog(
            onDismissRequest = { showRawExportWarning = false },
            title = { Text(stringResource(R.string.widget_tool_diagnostic_export_raw_title)) },
            text = { Text(stringResource(R.string.widget_tool_diagnostic_export_raw_warning)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRawExportWarning = false
                        onShareRaw(rawEncoded)
                    },
                ) {
                    Text(stringResource(R.string.widget_tool_diagnostic_export_raw_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRawExportWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun WidgetDraftPreview(
    value: ManagedWidgetDraftUiState,
    running: Boolean,
    isEdit: Boolean,
    onConfirmEnabled: () -> Unit,
    onSaveDisabled: () -> Unit,
    onDecline: () -> Unit,
    onOpenToolSettings: () -> Unit,
) {
    val summary = value.draft.summary
    Text(stringResource(R.string.widget_draft_title), style = MaterialTheme.typography.headlineSmall)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(summary.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.widget_draft_behavior), style = MaterialTheme.typography.titleMedium)
            Text(
                summary.periodicIntervalHours?.let {
                    stringResource(R.string.widget_draft_schedule_periodic, it)
                } ?: stringResource(R.string.widget_draft_schedule_manual),
            )
            Text(stringResource(R.string.widget_draft_network, summary.tools.joinToString { "${it.id}@${it.version}" }.ifEmpty { "—" }))
            Text(
                stringResource(
                    R.string.widget_draft_retention,
                    summary.observationLimit,
                    summary.observationRetentionDays,
                ),
            )
            Text(
                stringResource(
                    R.string.widget_draft_actions,
                    summary.actions.joinToString().ifEmpty { stringResource(R.string.widget_draft_no_actions) },
                ),
            )
            Text(
                stringResource(
                    R.string.widget_runtime_values,
                    summary.runtimeValues.joinToString { it.wireName }.ifEmpty { "—" },
                ),
            )
            Text(
                stringResource(
                    R.string.widget_presentation_scope,
                    summary.presentation.joinToString { it.wireName }.ifEmpty { "—" },
                ),
            )
            value.diff?.let { diff ->
                if (diff.expandsAuthority) {
                    WidgetAuthoringMessage(R.string.widget_draft_authority_expansion, true)
                    WidgetDraftAuthorityExpansion(diff)
                }
                if (diff.source.changed) {
                    Text(
                        stringResource(
                            R.string.widget_draft_source_diff,
                            diff.source.firstChangedLine ?: 1,
                            diff.source.removedLineCount,
                            diff.source.addedLineCount,
                        ),
                    )
                }
            }
            if (value.draft.blockedTools.isNotEmpty()) {
                WidgetAuthoringMessage(
                    text = stringResource(
                        R.string.widget_draft_blocked_tools,
                        value.draft.blockedTools.joinToString { "${it.id}@${it.version}" },
                    ),
                    warning = true,
                )
                OutlinedButton(onClick = onOpenToolSettings, enabled = !running) {
                    Text(stringResource(R.string.widget_draft_open_tool_settings))
                }
            }
        }
    }
    Text(stringResource(R.string.widget_source), style = MaterialTheme.typography.titleMedium)
    Text(value.draft.program.source, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onConfirmEnabled, enabled = !running && value.draft.blockedTools.isEmpty()) {
            Text(
                stringResource(
                    if (isEdit) R.string.widget_draft_confirm_edit_enable else R.string.widget_draft_confirm_enable,
                ),
            )
        }
        OutlinedButton(onClick = onSaveDisabled, enabled = !running) {
            Text(stringResource(R.string.widget_draft_save_disabled))
        }
        TextButton(onClick = onDecline, enabled = !running) {
            Text(stringResource(R.string.widget_draft_decline))
        }
    }
}

@Composable
private fun WidgetDraftAuthorityExpansion(diff: com.jesjobom.ararai.widget.managed.WidgetDraftDiff) {
    if (diff.addedTools.isNotEmpty()) {
        Text(stringResource(R.string.widget_draft_added_tools, diff.addedTools.joinToString { "${it.id}@${it.version}" }))
    }
    if (diff.addedRuntimeValues.isNotEmpty()) {
        Text(stringResource(R.string.widget_draft_added_runtime, diff.addedRuntimeValues.joinToString { it.wireName }))
    }
    if (diff.addedPresentation.isNotEmpty()) {
        Text(stringResource(R.string.widget_draft_added_presentation, diff.addedPresentation.joinToString { it.wireName }))
    }
    if (diff.addedActions.isNotEmpty()) {
        Text(stringResource(R.string.widget_draft_added_actions, diff.addedActions.joinToString()))
    }
    if (diff.moreFrequentSchedule) Text(stringResource(R.string.widget_draft_higher_frequency))
}

@Composable
private fun WidgetAuthoringMessage(
    messageResource: Int,
    warning: Boolean,
) {
    WidgetAuthoringMessage(stringResource(messageResource), warning)
}

@Composable
private fun WidgetAuthoringMessage(
    text: String,
    warning: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (warning) Icon(Icons.Filled.Warning, contentDescription = null)
            Text(text, color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun WidgetAuthoringErrorMessage(error: WidgetAuthoringError) {
    val message = when (error) {
        WidgetAuthoringError.ModelUnavailable -> stringResource(R.string.widget_authoring_model_unavailable)
        WidgetAuthoringError.ModelLoadFailed -> stringResource(R.string.widget_authoring_model_load_failed)
        WidgetAuthoringError.MissingWidget -> stringResource(R.string.widgets_load_failed)
        is WidgetAuthoringError.Unachievable -> stringResource(
            R.string.widget_authoring_unachievable,
            error.reason,
        )
        is WidgetAuthoringError.NeedsClarification -> stringResource(
            R.string.widget_authoring_needs_clarification,
            error.question,
        )
        is WidgetAuthoringError.StageFailed -> stringResource(
            R.string.widget_authoring_stage_failed,
            error.stage.name,
            error.code.name,
        )
        WidgetAuthoringError.GenerationTimedOut -> stringResource(R.string.widget_authoring_timeout)
    }
    WidgetAuthoringMessage(message, warning = true)
}

@Composable
private fun WidgetAuthoringProgress.label(): String = when (this) {
    WidgetAuthoringProgress.AnalyzingFeasibility -> stringResource(R.string.widget_authoring_stage_feasibility)
    WidgetAuthoringProgress.DesigningAlgorithm -> stringResource(R.string.widget_authoring_stage_algorithm)
    is WidgetAuthoringProgress.GeneratingCall -> stringResource(R.string.widget_authoring_stage_call, index, count)
    WidgetAuthoringProgress.GeneratingPlan -> stringResource(R.string.widget_authoring_stage_plan)
    WidgetAuthoringProgress.GeneratingPresentation -> stringResource(R.string.widget_authoring_stage_render)
    WidgetAuthoringProgress.ValidatingAssembly -> stringResource(R.string.widget_authoring_stage_validation)
    is WidgetAuthoringProgress.Repairing -> stringResource(
        R.string.widget_authoring_stage_repair,
        stage.name,
        repair,
        maximum,
    )
    WidgetAuthoringProgress.DraftReady -> stringResource(R.string.widget_authoring_stage_ready)
}
