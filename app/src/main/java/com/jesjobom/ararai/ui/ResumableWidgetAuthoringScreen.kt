@file:Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod", "ReturnCount")

package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import com.jesjobom.ararai.widget.managed.AcceptWidgetAuthoringStageResult
import com.jesjobom.ararai.widget.managed.BeginWidgetAuthoringWorkflowResult
import com.jesjobom.ararai.widget.managed.RunWidgetAuthoringStageResult
import com.jesjobom.ararai.widget.managed.SubmitWidgetAuthoringAttemptResult
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAssemblyResult
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAttemptDetails
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAttemptKind
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAttemptStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringCheckpointStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageDisplayStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageKey
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageProjection
import com.jesjobom.ararai.widget.managed.WidgetAuthoringUserAction
import com.jesjobom.ararai.widget.managed.WidgetAuthoringWarning
import com.jesjobom.ararai.widget.managed.WidgetAuthoringWorkflowCoordinator
import com.jesjobom.ararai.widget.managed.WidgetAuthoringWorkflowProjector
import com.jesjobom.ararai.widget.managed.WidgetConfirmationMode
import com.jesjobom.ararai.widget.managed.WidgetConfirmationResult
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
    initialInstruction: String? = null,
    onBack: () -> Unit,
    onConfirmed: (String) -> Unit,
    onOpenToolSettings: () -> Unit,
) {
    val snapshot by coordinator.activeSession.collectAsState()
    var instruction by remember(widgetId) { mutableStateOf(initialInstruction.orEmpty()) }
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
    val configurationAvailable = productionConfigurationAvailable

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
        val selectedModel = model?.takeIf {
            it.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1)
        } ?: return
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

    fun begin() {
        val selectedModel = model?.takeIf {
            it.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1)
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
        val selectedModel = model?.takeIf {
            it.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1)
        } ?: return
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
        val selectedModel = model?.takeIf {
            it.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1)
        } ?: return
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
        val (accepted, nextAttempt) = if (configurationAvailable) {
            val result = coordinator.advanceAndStartNext(
                model = requireNotNull(model),
                inference = requireNotNull(inference),
                modelArtifactDigest = requireNotNull(modelArtifactSha256),
                acceptActionId = UUID.randomUUID().toString(),
                startActionId = UUID.randomUUID().toString(),
            )
            result.accepted to result.nextAttempt
        } else {
            coordinator.acceptCurrent(UUID.randomUUID().toString()) to null
        }
        when (val result = accepted) {
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
        when (nextAttempt) {
            null -> Unit
            RunWidgetAuthoringStageResult.ConfigurationChanged -> {
                error = R.string.widget_authoring_workflow_configuration_changed
            }
            RunWidgetAuthoringStageResult.MissingTarget -> error = R.string.widget_authoring_workflow_missing_target
            is RunWidgetAuthoringStageResult.Submitted -> if (
                nextAttempt.result is SubmitWidgetAuthoringAttemptResult.Busy ||
                nextAttempt.result is SubmitWidgetAuthoringAttemptResult.Stale
            ) {
                error = R.string.widget_authoring_workflow_unexpected
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
                if (model != null && !productionConfigurationAvailable) {
                    Text(
                        stringResource(R.string.widget_authoring_model_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (projection != null) {
                Text(
                    stringResource(R.string.widget_authoring_workflow_saved),
                    style = MaterialTheme.typography.bodyMedium,
                )
                snapshot?.session?.instruction?.let { prompt ->
                    Text(stringResource(R.string.widget_initial_prompt), style = MaterialTheme.typography.titleMedium)
                    SelectionContainer { Text(prompt) }
                    Text(
                        stringResource(R.string.widget_authoring_saved_prompt_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (WidgetAuthoringUserAction.Discard in projection.allowedActions) {
                        OutlinedButton(
                            onClick = { discardConfirmation = true },
                            enabled = !working && !attemptActive,
                        ) {
                            Text(stringResource(R.string.widget_authoring_start_new_request))
                        }
                    }
                }
                projection.stages.forEach { stage ->
                    val current = stage.stage == projection.currentStage
                    AuthoringStageCard(
                        stage = stage,
                        reprocessEnabled = WidgetAuthoringUserAction.Reprocess in projection.allowedActions &&
                            (
                                stage.acceptedCheckpoint?.status == WidgetAuthoringCheckpointStatus.Accepted ||
                                    (current && projection.status == WidgetAuthoringSessionStatus.AwaitingReview)
                                ) &&
                            !working && configurationAvailable,
                        onReprocess = { reprocessConfirmation = stage.stage },
                        showAdvance = current && (
                            WidgetAuthoringUserAction.Start in projection.allowedActions ||
                                WidgetAuthoringUserAction.Continue in projection.allowedActions
                            ),
                        advanceEnabled = !working &&
                            (WidgetAuthoringUserAction.Continue in projection.allowedActions || configurationAvailable),
                        onAdvance = {
                            if (WidgetAuthoringUserAction.Continue in projection.allowedActions) {
                                continueWorkflow()
                            } else {
                                submitInitial()
                            }
                        },
                        showRetry = current && WidgetAuthoringUserAction.Retry in projection.allowedActions,
                        retryEnabled = configurationAvailable && !working,
                        onRetry = ::retry,
                        showCancel = current && attemptActive,
                        onCancel = { coordinator.cancelActiveAttempt() },
                    )
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
    showAdvance: Boolean = false,
    advanceEnabled: Boolean = false,
    onAdvance: () -> Unit = {},
    showRetry: Boolean = false,
    retryEnabled: Boolean = false,
    onRetry: () -> Unit = {},
    showCancel: Boolean = false,
    onCancel: () -> Unit = {},
) {
    var expanded by rememberSaveable(stage.stage.wireValue) { mutableStateOf(false) }
    val stageLabel = stage.stage.label()
    val statusLabel = stage.status.label()
    val accessibilityLabel = stringResource(
        R.string.widget_authoring_workflow_stage_accessibility,
        stageLabel,
        statusLabel,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = accessibilityLabel },
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stageLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(statusLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(
                        R.string.widget_authoring_workflow_stage_summary,
                        stage.attempts.size,
                        stage.attempts.lastOrNull()?.status?.label() ?: "—",
                        stage.attempts.lastOrNull()?.durationMillis?.let { "$it ms" } ?: "—",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stage.attempts.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(
                            stringResource(
                                if (expanded) {
                                    R.string.widget_authoring_workflow_hide_attempts
                                } else {
                                    R.string.widget_authoring_workflow_show_attempts
                                },
                            ),
                        )
                    }
                }
                if (expanded) stage.attempts.forEach { AttemptDetails(it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showAdvance) {
                Button(onClick = onAdvance, enabled = advanceEnabled) {
                    Text(stringResource(R.string.widget_authoring_workflow_advance))
                }
            }
            if (showRetry) {
                Button(onClick = onRetry, enabled = retryEnabled) {
                    Text(stringResource(R.string.widget_authoring_workflow_retry))
                }
            }
            if (reprocessEnabled) {
                OutlinedButton(onClick = onReprocess) {
                    Text(stringResource(R.string.widget_authoring_workflow_reprocess))
                }
            }
            if (showCancel) {
                CircularProgressIndicator()
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
        stage.warnings.forEach { warning ->
            Text(
                stringResource(
                    when (warning) {
                        WidgetAuthoringWarning.RandomSelectionMayUseFirst ->
                            R.string.widget_authoring_warning_random_selection
                        WidgetAuthoringWarning.FirstSelectionMayUseRandom ->
                            R.string.widget_authoring_warning_first_selection
                        WidgetAuthoringWarning.LastSelectionUnavailable ->
                            R.string.widget_authoring_warning_last_selection
                        WidgetAuthoringWarning.TimeConditionNotEvident ->
                            R.string.widget_authoring_warning_time_condition
                    },
                ),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AttemptDetails(attempt: WidgetAuthoringAttemptDetails) {
    Text(attempt.status.label(), style = MaterialTheme.typography.bodySmall)
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
private fun WidgetAuthoringAttemptStatus.label(): String = stringResource(
    when (this) {
        WidgetAuthoringAttemptStatus.Queued -> R.string.widget_authoring_workflow_status_queued
        WidgetAuthoringAttemptStatus.Deferred -> R.string.widget_authoring_workflow_status_deferred
        WidgetAuthoringAttemptStatus.Running -> R.string.widget_authoring_workflow_status_running
        WidgetAuthoringAttemptStatus.Succeeded -> R.string.widget_authoring_workflow_attempt_succeeded
        WidgetAuthoringAttemptStatus.Failed -> R.string.widget_authoring_workflow_attempt_failed
        WidgetAuthoringAttemptStatus.Cancelled -> R.string.widget_authoring_workflow_status_cancelled
        WidgetAuthoringAttemptStatus.Interrupted -> R.string.widget_authoring_workflow_status_interrupted
    },
)

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
