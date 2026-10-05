package com.jesjobom.ararai.widget.managed

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object WidgetAuthoringWorkflowPolicy {
    const val MAX_INSTRUCTION_BYTES = 8 * 1024
    const val MAX_INFERENCE_CONFIG_BYTES = 4 * 1024
    const val MAX_STAGE_KEY_CHARS = 96
    const val MAX_ACTION_ID_CHARS = 128
    const val MAX_REPAIRS_PER_STAGE_REVISION = WidgetAuthoringPipelinePolicy.MAX_REPAIRS
}

internal sealed interface WidgetAuthoringStageKey {
    val wireValue: String

    data object Feasibility : WidgetAuthoringStageKey {
        override val wireValue = "feasibility"
    }

    data object Algorithm : WidgetAuthoringStageKey {
        override val wireValue = "algorithm"
    }

    data class CallFunction(val stepId: String) : WidgetAuthoringStageKey {
        init {
            require(STEP_ID_PATTERN.matches(stepId))
        }

        override val wireValue: String = "call:$stepId"
    }

    data object Plan : WidgetAuthoringStageKey {
        override val wireValue = "plan"
    }

    data object Render : WidgetAuthoringStageKey {
        override val wireValue = "render"
    }

    data object Assembly : WidgetAuthoringStageKey {
        override val wireValue = "assembly"
    }

    companion object {
        fun parse(value: String): WidgetAuthoringStageKey {
            require(value.length <= WidgetAuthoringWorkflowPolicy.MAX_STAGE_KEY_CHARS)
            return when {
                value == Feasibility.wireValue -> Feasibility
                value == Algorithm.wireValue -> Algorithm
                value == Plan.wireValue -> Plan
                value == Render.wireValue -> Render
                value == Assembly.wireValue -> Assembly
                value.startsWith(CALL_PREFIX) -> CallFunction(value.removePrefix(CALL_PREFIX))
                else -> throw IllegalArgumentException("Unknown widget authoring stage key")
            }
        }
    }
}

internal data class WidgetAuthoringStageGraph(
    val callStepIds: List<String>,
) {
    init {
        require(callStepIds.distinct().size == callStepIds.size)
        callStepIds.forEach { WidgetAuthoringStageKey.CallFunction(it) }
    }

    val orderedStages: List<WidgetAuthoringStageKey> = buildList {
        add(WidgetAuthoringStageKey.Feasibility)
        add(WidgetAuthoringStageKey.Algorithm)
        callStepIds.mapTo(this, WidgetAuthoringStageKey::CallFunction)
        add(WidgetAuthoringStageKey.Plan)
        add(WidgetAuthoringStageKey.Render)
        add(WidgetAuthoringStageKey.Assembly)
    }

    fun directDependencies(stage: WidgetAuthoringStageKey): Set<WidgetAuthoringStageKey> = when (stage) {
        WidgetAuthoringStageKey.Feasibility -> emptySet()
        WidgetAuthoringStageKey.Algorithm -> setOf(WidgetAuthoringStageKey.Feasibility)
        is WidgetAuthoringStageKey.CallFunction -> setOf(WidgetAuthoringStageKey.Algorithm)
        WidgetAuthoringStageKey.Plan ->
            callStepIds
                .mapTo(mutableSetOf(), WidgetAuthoringStageKey::CallFunction)
                .ifEmpty { mutableSetOf(WidgetAuthoringStageKey.Algorithm) }
        WidgetAuthoringStageKey.Render -> setOf(WidgetAuthoringStageKey.Plan)
        WidgetAuthoringStageKey.Assembly -> setOf(WidgetAuthoringStageKey.Render)
    }.also {
        require(stage in orderedStages) { "Stage is not part of the authoring graph" }
    }

    fun transitiveDescendants(stage: WidgetAuthoringStageKey): Set<WidgetAuthoringStageKey> {
        require(stage in orderedStages) { "Stage is not part of the authoring graph" }
        val descendants = mutableSetOf<WidgetAuthoringStageKey>()
        var changed: Boolean
        do {
            changed = false
            orderedStages.forEach { candidate ->
                if (candidate != stage && candidate !in descendants) {
                    val dependencies = directDependencies(candidate)
                    if (stage in dependencies || dependencies.any(descendants::contains)) {
                        changed = descendants.add(candidate) || changed
                    }
                }
            }
        } while (changed)
        return descendants
    }
}

internal enum class WidgetAuthoringSessionStatus {
    AwaitingStage,
    AttemptQueued,
    AttemptDeferred,
    AttemptRunning,
    AwaitingReview,
    AwaitingRetry,
    ReadyForPreview,
    Completed,
}

internal enum class WidgetAuthoringAttemptStatus {
    Queued,
    Deferred,
    Running,
    Succeeded,
    Failed,
    Cancelled,
    Interrupted,
}

internal enum class WidgetAuthoringAttemptKind { Initial, Repair, Reprocess }

internal enum class WidgetAuthoringCheckpointStatus { Accepted, Stale }

internal data class NewWidgetAuthoringSession(
    val instruction: String,
    val targetWidgetId: String?,
    val modelId: String,
    val modelArtifactDigest: String,
    val inferenceConfigJson: String,
    val protocolVersion: Int = WIDGET_AUTHORING_PROTOCOL_VERSION,
    val schemaVersion: Int = 1,
    val toolContractDigest: String,
) {
    init {
        require(instruction.isNotBlank())
        require(instruction.utf8Bytes() <= WidgetAuthoringWorkflowPolicy.MAX_INSTRUCTION_BYTES)
        require(targetWidgetId == null || WIDGET_WORKFLOW_ID_PATTERN.matches(targetWidgetId))
        require(WIDGET_WORKFLOW_ID_PATTERN.matches(modelId))
        require(WIDGET_WORKFLOW_DIGEST_PATTERN.matches(modelArtifactDigest))
        require(inferenceConfigJson.utf8Bytes() <= WidgetAuthoringWorkflowPolicy.MAX_INFERENCE_CONFIG_BYTES)
        require(protocolVersion > 0)
        require(schemaVersion > 0)
        require(WIDGET_WORKFLOW_DIGEST_PATTERN.matches(toolContractDigest))
    }
}

internal data class WidgetAuthoringSession(
    val id: String,
    val revision: Int,
    val status: WidgetAuthoringSessionStatus,
    val instruction: String,
    val targetWidgetId: String?,
    val modelId: String,
    val modelArtifactDigest: String,
    val inferenceConfigJson: String,
    val protocolVersion: Int,
    val schemaVersion: Int,
    val toolContractDigest: String,
    val currentStage: WidgetAuthoringStageKey,
    val activeAttemptId: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

internal data class WidgetAuthoringAttempt(
    val id: String,
    val sessionId: String,
    val actionId: String,
    val stage: WidgetAuthoringStageKey,
    val stageRevision: Int,
    val attemptNumber: Int,
    val kind: WidgetAuthoringAttemptKind,
    val status: WidgetAuthoringAttemptStatus,
    val artifact: String?,
    val artifactDigest: String?,
    val upstreamDigest: String,
    val failureCode: WidgetAuthoringStageFailureCode?,
    val startedAtMillis: Long?,
    val completedAtMillis: Long?,
) {
    val repairCount: Int
        get() = (attemptNumber - 1).coerceAtLeast(0)
}

internal data class WidgetAuthoringCheckpoint(
    val sessionId: String,
    val stage: WidgetAuthoringStageKey,
    val stageRevision: Int,
    val acceptedAttemptId: String,
    val artifact: String,
    val artifactDigest: String,
    val upstreamDigest: String,
    val status: WidgetAuthoringCheckpointStatus,
    val acceptedAtMillis: Long,
)

internal data class WidgetAuthoringSessionSnapshot(
    val session: WidgetAuthoringSession,
    val attempts: List<WidgetAuthoringAttempt>,
    val checkpoints: List<WidgetAuthoringCheckpoint>,
)

internal data class StartWidgetAuthoringAttempt(
    val sessionId: String,
    val expectedSessionRevision: Int,
    val actionId: String,
    val stage: WidgetAuthoringStageKey,
    val kind: WidgetAuthoringAttemptKind,
    val upstreamDigest: String,
) {
    init {
        require(expectedSessionRevision > 0)
        require(actionId.isNotBlank() && actionId.length <= WidgetAuthoringWorkflowPolicy.MAX_ACTION_ID_CHARS)
        require(WIDGET_WORKFLOW_DIGEST_PATTERN.matches(upstreamDigest))
    }
}

internal sealed interface StartWidgetAuthoringAttemptResult {
    data class Started(
        val attempt: WidgetAuthoringAttempt,
        val sessionRevision: Int,
    ) : StartWidgetAuthoringAttemptResult

    data class Duplicate(
        val attempt: WidgetAuthoringAttempt,
        val sessionRevision: Int,
    ) : StartWidgetAuthoringAttemptResult

    data class Stale(val currentSessionRevision: Int) : StartWidgetAuthoringAttemptResult
}

internal data class CompleteWidgetAuthoringAttempt(
    val sessionId: String,
    val attemptId: String,
    val status: WidgetAuthoringAttemptStatus,
    val artifact: String?,
    val failureCode: WidgetAuthoringStageFailureCode?,
) {
    init {
        require(status in TERMINAL_ATTEMPT_STATUSES)
        require(artifact == null || artifact.utf8Bytes() <= WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES)
        require((status == WidgetAuthoringAttemptStatus.Succeeded) == (artifact != null && failureCode == null))
        require(status != WidgetAuthoringAttemptStatus.Failed || failureCode != null)
    }
}

internal data class AcceptWidgetAuthoringCandidate(
    val sessionId: String,
    val expectedSessionRevision: Int,
    val actionId: String,
    val attemptId: String,
    val nextStage: WidgetAuthoringStageKey?,
)

internal enum class WidgetAuthoringUserAction {
    Start,
    Continue,
    Retry,
    Reprocess,
    CancelAttempt,
    Discard,
    Confirm,
}

internal object WidgetAuthoringWorkflowStateMachine {
    fun requireAllowed(
        snapshot: WidgetAuthoringSessionSnapshot,
        action: WidgetAuthoringUserAction,
    ) {
        require(action in allowedActions(snapshot)) {
            "Action $action is not allowed while ${snapshot.session.status}"
        }
    }

    fun allowedActions(snapshot: WidgetAuthoringSessionSnapshot): Set<WidgetAuthoringUserAction> = when (
        snapshot.session.status
    ) {
        WidgetAuthoringSessionStatus.AttemptQueued,
        WidgetAuthoringSessionStatus.AttemptDeferred,
        WidgetAuthoringSessionStatus.AttemptRunning,
        -> setOf(WidgetAuthoringUserAction.CancelAttempt)
        WidgetAuthoringSessionStatus.AwaitingStage -> buildSet {
            add(WidgetAuthoringUserAction.Start)
            add(WidgetAuthoringUserAction.Discard)
            if (snapshot.hasAcceptedCheckpoint()) add(WidgetAuthoringUserAction.Reprocess)
        }
        WidgetAuthoringSessionStatus.AwaitingReview -> setOf(
            WidgetAuthoringUserAction.Continue,
            WidgetAuthoringUserAction.Discard,
        )
        WidgetAuthoringSessionStatus.AwaitingRetry -> buildSet {
            add(WidgetAuthoringUserAction.Discard)
            if (snapshot.hasRepairBudget()) add(WidgetAuthoringUserAction.Retry)
            if (snapshot.hasAcceptedCheckpoint()) add(WidgetAuthoringUserAction.Reprocess)
            if (snapshot.hasCompleteAcceptedGraph()) add(WidgetAuthoringUserAction.Confirm)
        }
        WidgetAuthoringSessionStatus.ReadyForPreview -> setOf(
            WidgetAuthoringUserAction.Reprocess,
            WidgetAuthoringUserAction.Discard,
            WidgetAuthoringUserAction.Confirm,
        )
        WidgetAuthoringSessionStatus.Completed -> emptySet()
    }

    private fun WidgetAuthoringSessionSnapshot.hasAcceptedCheckpoint(): Boolean = checkpoints.any {
        it.status == WidgetAuthoringCheckpointStatus.Accepted
    }

    private fun WidgetAuthoringSessionSnapshot.hasRepairBudget(): Boolean {
        val currentStageAttempts = attempts.filter { it.stage == session.currentStage }
        val currentRevision = currentStageAttempts.maxOfOrNull(WidgetAuthoringAttempt::stageRevision)
        val latest = currentRevision?.let { revision ->
            currentStageAttempts
                .filter { it.stageRevision == revision }
                .maxByOrNull(WidgetAuthoringAttempt::attemptNumber)
        }
        return latest != null &&
            latest.status != WidgetAuthoringAttemptStatus.Succeeded &&
            latest.attemptNumber <= WidgetAuthoringWorkflowPolicy.MAX_REPAIRS_PER_STAGE_REVISION
    }

    internal fun WidgetAuthoringSessionSnapshot.hasCompleteAcceptedGraph(): Boolean = checkpoints.any {
        it.stage == WidgetAuthoringStageKey.Render && it.status == WidgetAuthoringCheckpointStatus.Accepted
    } && checkpoints.none { it.status == WidgetAuthoringCheckpointStatus.Stale }
}

internal fun requireValidAuthoringStageTransition(
    completedStage: WidgetAuthoringStageKey,
    nextStage: WidgetAuthoringStageKey?,
) {
    val valid = when (completedStage) {
        WidgetAuthoringStageKey.Feasibility -> nextStage == WidgetAuthoringStageKey.Algorithm
        WidgetAuthoringStageKey.Algorithm ->
            nextStage == WidgetAuthoringStageKey.Plan || nextStage is WidgetAuthoringStageKey.CallFunction
        is WidgetAuthoringStageKey.CallFunction ->
            nextStage == WidgetAuthoringStageKey.Plan || nextStage is WidgetAuthoringStageKey.CallFunction
        WidgetAuthoringStageKey.Plan -> nextStage == WidgetAuthoringStageKey.Render
        WidgetAuthoringStageKey.Render -> nextStage == null
        WidgetAuthoringStageKey.Assembly -> false
    }
    require(valid) { "Invalid widget authoring stage transition" }
}

internal sealed interface WidgetAuthoringMutationResult {
    data class Applied(val snapshot: WidgetAuthoringSessionSnapshot) : WidgetAuthoringMutationResult
    data class Duplicate(val snapshot: WidgetAuthoringSessionSnapshot) : WidgetAuthoringMutationResult
    data class Stale(val snapshot: WidgetAuthoringSessionSnapshot) : WidgetAuthoringMutationResult
}

internal sealed interface FinalizeWidgetAuthoringResult {
    data class Finalized(val definition: ManagedWidgetDefinition) : FinalizeWidgetAuthoringResult
    data class Stale(val snapshot: WidgetAuthoringSessionSnapshot) : FinalizeWidgetAuthoringResult
    data object Missing : FinalizeWidgetAuthoringResult
}

internal val ACTIVE_ATTEMPT_STATUSES = setOf(
    WidgetAuthoringAttemptStatus.Queued,
    WidgetAuthoringAttemptStatus.Deferred,
    WidgetAuthoringAttemptStatus.Running,
)

internal val TERMINAL_ATTEMPT_STATUSES = WidgetAuthoringAttemptStatus.entries.toSet() - ACTIVE_ATTEMPT_STATUSES

internal fun authoringDigest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun String.utf8Bytes(): Int = toByteArray(StandardCharsets.UTF_8).size

private const val CALL_PREFIX = "call:"
private val STEP_ID_PATTERN = Regex("[a-z][a-z0-9_]{0,31}")
private val WIDGET_WORKFLOW_ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
private val WIDGET_WORKFLOW_DIGEST_PATTERN = Regex("[a-f0-9]{64}")
