@file:Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod", "ReturnCount")

package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.BuildConfig
import com.jesjobom.ararai.R
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import com.jesjobom.ararai.widget.managed.AcceptWidgetAuthoringStageResult
import com.jesjobom.ararai.widget.managed.BeginWidgetAuthoringWorkflowResult
import com.jesjobom.ararai.widget.managed.RunWidgetAuthoringStageResult
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAssemblyResult
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAttemptDetails
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAttemptKind
import com.jesjobom.ararai.widget.managed.WidgetAuthoringCheckpointStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageDisplayStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageKey
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageProjection
import com.jesjobom.ararai.widget.managed.WidgetAuthoringUserAction
import com.jesjobom.ararai.widget.managed.WidgetAuthoringWorkflowCoordinator
import com.jesjobom.ararai.widget.managed.WidgetAuthoringWorkflowProjector
import com.jesjobom.ararai.widget.managed.WidgetConfirmationMode
import com.jesjobom.ararai.widget.managed.WidgetConfirmationResult
import com.jesjobom.ararai.widget.managed.forAuthoringCharacterization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun ResumableManagedWidgetAuthoringRoute(
    coordinator: WidgetAuthoringWorkflowCoordinator,
    presentationController: ManagedWidgetsController,
    model: LocalModel?,
    inference: InferenceConfig?,
    modelArtifactSha256: String?,
    widgetId: String?,
    onBack: () -> Unit,
    onConfirmed: (String) -> Unit,
    onOpenToolSettings: () -> Unit,
) {
    val snapshot by coordinator.activeSession.collectAsState()
    var instruction by remember(widgetId) { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var preview by remember { mutableStateOf<ManagedWidgetDraftUiState?>(null) }
    var discardConfirmation by remember { mutableStateOf(false) }
    var reprocessConfirmation by remember { mutableStateOf<WidgetAuthoringStageKey?>(null) }
    val scope = rememberCoroutineScope()
    val projection = snapshot?.let(WidgetAuthoringWorkflowProjector::project)
    val attemptActive = snapshot?.session?.status in setOf(
        WidgetAuthoringSessionStatus.AttemptQueued,
        WidgetAuthoringSessionStatus.AttemptDeferred,
        WidgetAuthoringSessionStatus.AttemptRunning,
    )
    val productionConfigurationAvailable = model != null &&
        inference != null &&
        modelArtifactSha256 != null &&
        model.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1)
    val diagnosticConfigurationAvailable = BuildConfig.DEBUG &&
        model != null &&
        inference != null &&
        modelArtifactSha256 != null &&
        !model.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1)
    val workflowModel = model?.let { selected ->
        if (
            selected.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1) ||
            !BuildConfig.DEBUG ||
            snapshot == null
        ) {
            selected
        } else {
            selected.forAuthoringCharacterization()
        }
    }
    val configurationAvailable = workflowModel != null && inference != null && modelArtifactSha256 != null

    fun runAction(block: suspend () -> Unit) {
        if (working) return
        working = true
        error = null
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                error = R.string.widget_authoring_workflow_unexpected
            } finally {
                working = false
            }
        }
    }

    fun submitInitial() {
        val selectedModel = workflowModel ?: return
        val selectedInference = inference ?: return
        val digest = modelArtifactSha256 ?: return
        runAction {
            when (
                val started = coordinator.startCurrent(
                    selectedModel,
                    selectedInference,
                    digest,
                    UUID.randomUUID().toString(),
                )
            ) {
                is RunWidgetAuthoringStageResult.Submitted -> Unit
                RunWidgetAuthoringStageResult.ConfigurationChanged -> {
                    error = R.string.widget_authoring_workflow_configuration_changed
                }
                RunWidgetAuthoringStageResult.MissingTarget -> error = R.string.widget_authoring_workflow_missing_target
            }
        }
    }

    fun begin(characterizeForDiagnostic: Boolean = false) {
        val selectedModel = model?.let { selected ->
            if (characterizeForDiagnostic) selected.forAuthoringCharacterization() else selected
        } ?: return
        val selectedInference = inference ?: return
        val digest = modelArtifactSha256 ?: return
        runAction {
            when (
                coordinator.begin(
                    selectedModel,
                    selectedInference,
                    digest,
                    instruction,
                    widgetId,
                )
            ) {
                is BeginWidgetAuthoringWorkflowResult.Created -> {
                    when (
                        coordinator.startCurrent(
                            selectedModel,
                            selectedInference,
                            digest,
                            UUID.randomUUID().toString(),
                        )
                    ) {
                        is RunWidgetAuthoringStageResult.Submitted -> Unit
                        RunWidgetAuthoringStageResult.ConfigurationChanged -> {
                            error = R.string.widget_authoring_workflow_configuration_changed
                        }
                        RunWidgetAuthoringStageResult.MissingTarget -> {
                            error = R.string.widget_authoring_workflow_missing_target
                        }
                    }
                }
                is BeginWidgetAuthoringWorkflowResult.Existing -> Unit
                BeginWidgetAuthoringWorkflowResult.MissingTarget -> {
                    error = R.string.widget_authoring_workflow_missing_target
                }
            }
        }
    }

    fun retry() {
        val selectedModel = workflowModel ?: return
        val selectedInference = inference ?: return
        val digest = modelArtifactSha256 ?: return
        runAction {
            when (
                coordinator.retryCurrent(
                    selectedModel,
                    selectedInference,
                    digest,
                    UUID.randomUUID().toString(),
                )
            ) {
                is RunWidgetAuthoringStageResult.Submitted -> Unit
                RunWidgetAuthoringStageResult.ConfigurationChanged -> {
                    error = R.string.widget_authoring_workflow_configuration_changed
                }
                RunWidgetAuthoringStageResult.MissingTarget -> error = R.string.widget_authoring_workflow_missing_target
            }
        }
    }

    fun reprocess(stage: WidgetAuthoringStageKey) {
        val selectedModel = workflowModel ?: return
        val selectedInference = inference ?: return
        val digest = modelArtifactSha256 ?: return
        runAction {
            when (
                coordinator.reprocess(
                    stage,
                    selectedModel,
                    selectedInference,
                    digest,
                    UUID.randomUUID().toString(),
                )
            ) {
                is RunWidgetAuthoringStageResult.Submitted -> Unit
                RunWidgetAuthoringStageResult.ConfigurationChanged -> {
                    error = R.string.widget_authoring_workflow_configuration_changed
                }
                RunWidgetAuthoringStageResult.MissingTarget -> error = R.string.widget_authoring_workflow_missing_target
            }
        }
    }

    fun continueWorkflow() = runAction {
        when (val result = coordinator.acceptCurrent(UUID.randomUUID().toString())) {
            is AcceptWidgetAuthoringStageResult.Advanced -> Unit
            is AcceptWidgetAuthoringStageResult.PreviewReady -> {
                preview = presentationController.presentDraft(widgetId, result.draft)
            }
            is AcceptWidgetAuthoringStageResult.AssemblyInvalid -> {
                error = R.string.widget_authoring_workflow_assembly_invalid
            }
            is AcceptWidgetAuthoringStageResult.FeasibilityStopped -> {
                error = R.string.widget_authoring_workflow_feasibility_stopped
            }
        }
    }

    fun confirm(mode: WidgetConfirmationMode) = runAction {
        when (val result = coordinator.confirm(mode)) {
            is WidgetConfirmationResult.Confirmed -> onConfirmed(result.definition.id)
            is WidgetConfirmationResult.Blocked -> error = R.string.widget_authoring_workflow_blocked_tools
            WidgetConfirmationResult.Failed -> error = R.string.widget_operation_failed
        }
    }

    LaunchedEffect(snapshot?.session?.revision, snapshot?.session?.status) {
        if (projection?.allowedActions?.contains(WidgetAuthoringUserAction.Confirm) == true) {
            preview = when (val result = coordinator.preview()) {
                is WidgetAuthoringAssemblyResult.Ready -> presentationController.presentDraft(widgetId, result.draft)
                is WidgetAuthoringAssemblyResult.Invalid -> {
                    error = R.string.widget_authoring_workflow_assembly_invalid
                    null
                }
            }
        } else {
            preview = null
        }
    }

    ArarAiScaffold(
        title = stringResource(
            if (widgetId == null) R.string.widget_authoring_create_title else R.string.widget_authoring_edit_title,
        ),
        onBack = onBack,
    ) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (snapshot == null) {
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working,
                    label = { Text(stringResource(R.string.widget_authoring_prompt_label)) },
                    supportingText = { Text(stringResource(R.string.widget_authoring_prompt_hint)) },
                    minLines = 4,
                    maxLines = 10,
                )
                Button(
                    onClick = { begin() },
                    enabled = productionConfigurationAvailable && instruction.isNotBlank() && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.widget_authoring_workflow_start))
                }
                if (diagnosticConfigurationAvailable) {
                    Text(
                        stringResource(R.string.widget_authoring_workflow_diagnostic_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { begin(characterizeForDiagnostic = true) },
                        enabled = instruction.isNotBlank() && !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.widget_authoring_workflow_diagnostic_start))
                    }
                }
            } else if (projection != null) {
                Text(
                    stringResource(R.string.widget_authoring_workflow_saved),
                    style = MaterialTheme.typography.bodyMedium,
                )
                projection.stages.forEach { stage ->
                    AuthoringStageCard(
                        stage = stage,
                        reprocessEnabled = WidgetAuthoringUserAction.Reprocess in projection.allowedActions &&
                            stage.acceptedCheckpoint?.status == WidgetAuthoringCheckpointStatus.Accepted &&
                            !working,
                        onReprocess = { reprocessConfirmation = stage.stage },
                    )
                }
                when {
                    attemptActive -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.widget_authoring_workflow_attempt_running))
                        OutlinedButton(onClick = { coordinator.cancelActiveAttempt() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                    WidgetAuthoringUserAction.Start in projection.allowedActions -> {
                        Button(onClick = ::submitInitial, enabled = configurationAvailable && !working) {
                            Text(stringResource(R.string.widget_authoring_workflow_run_stage))
                        }
                    }
                    WidgetAuthoringUserAction.Continue in projection.allowedActions -> {
                        Button(onClick = ::continueWorkflow, enabled = !working) {
                            Text(stringResource(R.string.widget_authoring_workflow_continue))
                        }
                    }
                    WidgetAuthoringUserAction.Retry in projection.allowedActions -> {
                        Button(onClick = ::retry, enabled = configurationAvailable && !working) {
                            Text(stringResource(R.string.widget_authoring_workflow_retry))
                        }
                    }
                }
                if (!attemptActive && WidgetAuthoringUserAction.Discard in projection.allowedActions) {
                    TextButton(onClick = { discardConfirmation = true }, enabled = !working) {
                        Text(stringResource(R.string.widget_authoring_workflow_discard))
                    }
                }
            }
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            preview?.let { draft ->
                WidgetDraftPreview(
                    value = draft,
                    running = working,
                    isEdit = widgetId != null,
                    onConfirmEnabled = { confirm(WidgetConfirmationMode.CreateAndEnable) },
                    onSaveDisabled = { confirm(WidgetConfirmationMode.SaveDisabled) },
                    onDecline = { discardConfirmation = true },
                    onOpenToolSettings = onOpenToolSettings,
                )
            }
        }
    }

    if (discardConfirmation) {
        AlertDialog(
            onDismissRequest = { discardConfirmation = false },
            title = { Text(stringResource(R.string.widget_authoring_workflow_discard_title)) },
            text = { Text(stringResource(R.string.widget_authoring_workflow_discard_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        discardConfirmation = false
                        runAction { coordinator.discard() }
                    },
                ) {
                    Text(stringResource(R.string.widget_authoring_workflow_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { discardConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    reprocessConfirmation?.let { stage ->
        AlertDialog(
            onDismissRequest = { reprocessConfirmation = null },
            title = { Text(stringResource(R.string.widget_authoring_workflow_reprocess_title)) },
            text = { Text(stringResource(R.string.widget_authoring_workflow_reprocess_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        reprocessConfirmation = null
                        reprocess(stage)
                    },
                ) {
                    Text(stringResource(R.string.widget_authoring_workflow_reprocess))
                }
            },
            dismissButton = {
                TextButton(onClick = { reprocessConfirmation = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
internal fun AuthoringStageCard(
    stage: WidgetAuthoringStageProjection,
    reprocessEnabled: Boolean,
    onReprocess: () -> Unit,
) {
    val stageLabel = stage.stage.label()
    val statusLabel = stage.status.label()
    val accessibilityLabel = stringResource(
        R.string.widget_authoring_workflow_stage_accessibility,
        stageLabel,
        statusLabel,
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityLabel },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(statusLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            stage.attempts.forEach { AttemptDetails(it) }
            if (reprocessEnabled) {
                OutlinedButton(onClick = onReprocess) {
                    Text(stringResource(R.string.widget_authoring_workflow_reprocess))
                }
            }
        }
    }
}

@Composable
private fun AttemptDetails(attempt: WidgetAuthoringAttemptDetails) {
    Text(
        stringResource(
            R.string.widget_authoring_workflow_attempt_details,
            attempt.stageRevision,
            attempt.attemptNumber,
            attempt.kind.label(),
            attempt.durationMillis?.toString() ?: "—",
            attempt.capturedBytes?.toString() ?: "—",
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    attempt.failureCode?.let {
        Text(
            stringResource(R.string.widget_authoring_workflow_validation_result, it.name),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (attempt.capturedArtifact == null) {
        Text(stringResource(R.string.widget_authoring_workflow_no_artifact), style = MaterialTheme.typography.bodySmall)
    } else {
        Text(stringResource(R.string.widget_authoring_workflow_captured_content), fontWeight = FontWeight.SemiBold)
        SelectionContainer {
            Text(
                attempt.capturedArtifact,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun WidgetAuthoringStageKey.label(): String = when (this) {
    WidgetAuthoringStageKey.Feasibility -> stringResource(R.string.widget_authoring_workflow_stage_feasibility)
    WidgetAuthoringStageKey.Algorithm -> stringResource(R.string.widget_authoring_workflow_stage_algorithm)
    is WidgetAuthoringStageKey.CallFunction -> stringResource(R.string.widget_authoring_workflow_stage_call, stepId)
    WidgetAuthoringStageKey.Plan -> stringResource(R.string.widget_authoring_workflow_stage_plan)
    WidgetAuthoringStageKey.Render -> stringResource(R.string.widget_authoring_workflow_stage_render)
    WidgetAuthoringStageKey.Assembly -> stringResource(R.string.widget_authoring_workflow_stage_assembly)
}

@Composable
private fun WidgetAuthoringStageDisplayStatus.label(): String = stringResource(
    when (this) {
        WidgetAuthoringStageDisplayStatus.Pending -> R.string.widget_authoring_workflow_status_pending
        WidgetAuthoringStageDisplayStatus.Queued -> R.string.widget_authoring_workflow_status_queued
        WidgetAuthoringStageDisplayStatus.Deferred -> R.string.widget_authoring_workflow_status_deferred
        WidgetAuthoringStageDisplayStatus.Running -> R.string.widget_authoring_workflow_status_running
        WidgetAuthoringStageDisplayStatus.AwaitingReview -> R.string.widget_authoring_workflow_status_review
        WidgetAuthoringStageDisplayStatus.AwaitingRetry -> R.string.widget_authoring_workflow_status_retry
        WidgetAuthoringStageDisplayStatus.Accepted -> R.string.widget_authoring_workflow_status_accepted
        WidgetAuthoringStageDisplayStatus.Stale -> R.string.widget_authoring_workflow_status_stale
        WidgetAuthoringStageDisplayStatus.Interrupted -> R.string.widget_authoring_workflow_status_interrupted
        WidgetAuthoringStageDisplayStatus.Cancelled -> R.string.widget_authoring_workflow_status_cancelled
        WidgetAuthoringStageDisplayStatus.Completed -> R.string.widget_authoring_workflow_status_completed
    },
)

@Composable
private fun WidgetAuthoringAttemptKind.label(): String = stringResource(
    when (this) {
        WidgetAuthoringAttemptKind.Initial -> R.string.widget_authoring_workflow_attempt_initial
        WidgetAuthoringAttemptKind.Repair -> R.string.widget_authoring_workflow_attempt_repair
        WidgetAuthoringAttemptKind.Reprocess -> R.string.widget_authoring_workflow_attempt_reprocess
    },
)
