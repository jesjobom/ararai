@file:Suppress("CyclomaticComplexMethod")

package com.jesjobom.ararai.widget.managed

internal enum class WidgetAuthoringStageDisplayStatus {
    Pending,
    Queued,
    Deferred,
    Running,
    AwaitingReview,
    AwaitingRetry,
    Accepted,
    Stale,
    Interrupted,
    Cancelled,
    Completed,
}

internal data class WidgetAuthoringAttemptDetails(
    val attemptId: String,
    val stageRevision: Int,
    val kind: WidgetAuthoringAttemptKind,
    val attemptNumber: Int,
    val status: WidgetAuthoringAttemptStatus,
    val durationMillis: Long?,
    val capturedBytes: Int?,
    val failureCode: WidgetAuthoringStageFailureCode?,
    val capturedArtifact: String?,
)

internal data class WidgetAuthoringStageProjection(
    val stage: WidgetAuthoringStageKey,
    val status: WidgetAuthoringStageDisplayStatus,
    val acceptedCheckpoint: WidgetAuthoringCheckpoint?,
    val attempts: List<WidgetAuthoringAttemptDetails>,
)

internal data class WidgetAuthoringWorkflowProjection(
    val sessionId: String,
    val sessionRevision: Int,
    val status: WidgetAuthoringSessionStatus,
    val currentStage: WidgetAuthoringStageKey,
    val stages: List<WidgetAuthoringStageProjection>,
    val allowedActions: Set<WidgetAuthoringUserAction>,
)

internal object WidgetAuthoringWorkflowProjector {
    fun project(snapshot: WidgetAuthoringSessionSnapshot): WidgetAuthoringWorkflowProjection {
        val knownStages = buildList {
            add(WidgetAuthoringStageKey.Feasibility)
            add(WidgetAuthoringStageKey.Algorithm)
            addAll(
                (
                    snapshot.checkpoints.map(WidgetAuthoringCheckpoint::stage) +
                        snapshot.attempts.map(WidgetAuthoringAttempt::stage)
                    )
                    .filterIsInstance<WidgetAuthoringStageKey.CallFunction>()
                    .distinctBy(WidgetAuthoringStageKey.CallFunction::stepId),
            )
            (snapshot.session.currentStage as? WidgetAuthoringStageKey.CallFunction)?.let(::add)
            add(WidgetAuthoringStageKey.Plan)
            add(WidgetAuthoringStageKey.Render)
            add(WidgetAuthoringStageKey.Assembly)
        }
        return WidgetAuthoringWorkflowProjection(
            sessionId = snapshot.session.id,
            sessionRevision = snapshot.session.revision,
            status = snapshot.session.status,
            currentStage = snapshot.session.currentStage,
            stages = knownStages.distinct().map { stage ->
                val checkpoint = snapshot.checkpoints.singleOrNull { it.stage == stage }
                val attempts = snapshot.attempts.filter { it.stage == stage }
                WidgetAuthoringStageProjection(
                    stage = stage,
                    status = displayStatus(snapshot, stage, checkpoint, attempts.lastOrNull()),
                    acceptedCheckpoint = checkpoint,
                    attempts = attempts.map { it.toDetails() },
                )
            },
            allowedActions = WidgetAuthoringWorkflowStateMachine.allowedActions(snapshot),
        )
    }

    private fun displayStatus(
        snapshot: WidgetAuthoringSessionSnapshot,
        stage: WidgetAuthoringStageKey,
        checkpoint: WidgetAuthoringCheckpoint?,
        latestAttempt: WidgetAuthoringAttempt?,
    ): WidgetAuthoringStageDisplayStatus = when {
        snapshot.session.status == WidgetAuthoringSessionStatus.ReadyForPreview &&
            stage == WidgetAuthoringStageKey.Assembly -> WidgetAuthoringStageDisplayStatus.Completed
        stage == snapshot.session.currentStage -> when (snapshot.session.status) {
            WidgetAuthoringSessionStatus.AttemptQueued -> WidgetAuthoringStageDisplayStatus.Queued
            WidgetAuthoringSessionStatus.AttemptDeferred -> WidgetAuthoringStageDisplayStatus.Deferred
            WidgetAuthoringSessionStatus.AttemptRunning -> WidgetAuthoringStageDisplayStatus.Running
            WidgetAuthoringSessionStatus.AwaitingReview -> WidgetAuthoringStageDisplayStatus.AwaitingReview
            WidgetAuthoringSessionStatus.AwaitingRetry -> when (latestAttempt?.status) {
                WidgetAuthoringAttemptStatus.Interrupted -> WidgetAuthoringStageDisplayStatus.Interrupted
                WidgetAuthoringAttemptStatus.Cancelled -> WidgetAuthoringStageDisplayStatus.Cancelled
                else -> WidgetAuthoringStageDisplayStatus.AwaitingRetry
            }
            WidgetAuthoringSessionStatus.Completed -> WidgetAuthoringStageDisplayStatus.Completed
            WidgetAuthoringSessionStatus.AwaitingStage,
            WidgetAuthoringSessionStatus.ReadyForPreview,
            -> checkpoint.acceptedStatusOrPending()
        }
        checkpoint?.status == WidgetAuthoringCheckpointStatus.Stale -> WidgetAuthoringStageDisplayStatus.Stale
        else -> checkpoint.acceptedStatusOrPending()
    }

    @Suppress("MaxLineLength")
    private fun WidgetAuthoringCheckpoint?.acceptedStatusOrPending(): WidgetAuthoringStageDisplayStatus = when (this?.status) {
        WidgetAuthoringCheckpointStatus.Accepted -> WidgetAuthoringStageDisplayStatus.Accepted
        WidgetAuthoringCheckpointStatus.Stale -> WidgetAuthoringStageDisplayStatus.Stale
        null -> WidgetAuthoringStageDisplayStatus.Pending
    }

    private fun WidgetAuthoringAttempt.toDetails(): WidgetAuthoringAttemptDetails = WidgetAuthoringAttemptDetails(
        attemptId = id,
        stageRevision = stageRevision,
        kind = kind,
        attemptNumber = attemptNumber,
        status = status,
        durationMillis = if (startedAtMillis != null && completedAtMillis != null) {
            (completedAtMillis - startedAtMillis).coerceAtLeast(0)
        } else {
            null
        },
        capturedBytes = artifact?.toByteArray(Charsets.UTF_8)?.size,
        failureCode = failureCode,
        capturedArtifact = artifact,
    )
}
