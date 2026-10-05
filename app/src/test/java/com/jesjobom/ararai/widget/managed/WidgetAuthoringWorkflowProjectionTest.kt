package com.jesjobom.ararai.widget.managed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAuthoringWorkflowProjectionTest {
    @Test
    fun `projects resumed interrupted attempt and keeps accepted upstream stage`() {
        val feasibility = checkpoint(WidgetAuthoringStageKey.Feasibility, WidgetAuthoringCheckpointStatus.Accepted)
        val interrupted = attempt(
            stage = WidgetAuthoringStageKey.Algorithm,
            status = WidgetAuthoringAttemptStatus.Interrupted,
            failureCode = WidgetAuthoringStageFailureCode.RuntimeUnavailable,
        )
        val projection = WidgetAuthoringWorkflowProjector.project(
            snapshot(
                status = WidgetAuthoringSessionStatus.AwaitingRetry,
                currentStage = WidgetAuthoringStageKey.Algorithm,
                attempts = listOf(interrupted),
                checkpoints = listOf(feasibility),
            ),
        )

        assertEquals(
            WidgetAuthoringStageDisplayStatus.Accepted,
            projection.stages.single { it.stage == WidgetAuthoringStageKey.Feasibility }.status,
        )
        val algorithm = projection.stages.single { it.stage == WidgetAuthoringStageKey.Algorithm }
        assertEquals(WidgetAuthoringStageDisplayStatus.Interrupted, algorithm.status)
        assertEquals(WidgetAuthoringStageFailureCode.RuntimeUnavailable, algorithm.attempts.single().failureCode)
        assertNull(algorithm.attempts.single().capturedArtifact)
    }

    @Test
    fun `projects accepted replacement descendants as stale and captured source as inert data`() {
        val hostileSource = "{\"source\":\"<script>alert('x')</script>\"}"
        val renderAttempt = attempt(
            stage = WidgetAuthoringStageKey.Render,
            status = WidgetAuthoringAttemptStatus.Succeeded,
            artifact = hostileSource,
        )
        val projection = WidgetAuthoringWorkflowProjector.project(
            snapshot(
                status = WidgetAuthoringSessionStatus.AwaitingReview,
                currentStage = WidgetAuthoringStageKey.Render,
                attempts = listOf(renderAttempt),
                checkpoints = listOf(
                    checkpoint(WidgetAuthoringStageKey.Feasibility, WidgetAuthoringCheckpointStatus.Accepted),
                    checkpoint(WidgetAuthoringStageKey.Algorithm, WidgetAuthoringCheckpointStatus.Accepted),
                    checkpoint(WidgetAuthoringStageKey.Plan, WidgetAuthoringCheckpointStatus.Stale),
                ),
            ),
        )

        assertEquals(
            WidgetAuthoringStageDisplayStatus.Stale,
            projection.stages.single { it.stage == WidgetAuthoringStageKey.Plan }.status,
        )
        val render = projection.stages.single { it.stage == WidgetAuthoringStageKey.Render }
        assertEquals(WidgetAuthoringStageDisplayStatus.AwaitingReview, render.status)
        assertEquals(hostileSource, render.attempts.single().capturedArtifact)
        assertEquals(hostileSource.toByteArray().size, render.attempts.single().capturedBytes)
    }

    @Test
    fun `projects replacement candidate status ahead of its stale checkpoint`() {
        val replacement = attempt(
            stage = WidgetAuthoringStageKey.Algorithm,
            status = WidgetAuthoringAttemptStatus.Succeeded,
            artifact = "{\"protocolVersion\":1}",
        )
        val projection = WidgetAuthoringWorkflowProjector.project(
            snapshot(
                status = WidgetAuthoringSessionStatus.AwaitingReview,
                currentStage = WidgetAuthoringStageKey.Algorithm,
                attempts = listOf(replacement),
                checkpoints = listOf(
                    checkpoint(WidgetAuthoringStageKey.Feasibility, WidgetAuthoringCheckpointStatus.Accepted),
                    checkpoint(WidgetAuthoringStageKey.Algorithm, WidgetAuthoringCheckpointStatus.Stale),
                ),
            ),
        )

        assertEquals(
            WidgetAuthoringStageDisplayStatus.AwaitingReview,
            projection.stages.single { it.stage == WidgetAuthoringStageKey.Algorithm }.status,
        )
        assertEquals(
            setOf(WidgetAuthoringUserAction.Continue, WidgetAuthoringUserAction.Discard),
            projection.allowedActions,
        )
    }

    @Test
    fun `projects local assembly completion only when session is ready for preview`() {
        val projection = WidgetAuthoringWorkflowProjector.project(
            snapshot(
                status = WidgetAuthoringSessionStatus.ReadyForPreview,
                currentStage = WidgetAuthoringStageKey.Assembly,
                attempts = emptyList(),
                checkpoints = listOf(
                    checkpoint(WidgetAuthoringStageKey.Render, WidgetAuthoringCheckpointStatus.Accepted),
                ),
            ),
        )

        assertEquals(
            WidgetAuthoringStageDisplayStatus.Completed,
            projection.stages.single { it.stage == WidgetAuthoringStageKey.Assembly }.status,
        )
        assertEquals(
            setOf(
                WidgetAuthoringUserAction.Reprocess,
                WidgetAuthoringUserAction.Discard,
                WidgetAuthoringUserAction.Confirm,
            ),
            projection.allowedActions,
        )
    }

    private fun snapshot(
        status: WidgetAuthoringSessionStatus,
        currentStage: WidgetAuthoringStageKey,
        attempts: List<WidgetAuthoringAttempt>,
        checkpoints: List<WidgetAuthoringCheckpoint>,
    ) = WidgetAuthoringSessionSnapshot(
        session = WidgetAuthoringSession(
            id = "session",
            revision = 4,
            status = status,
            instruction = "Build a widget",
            targetWidgetId = null,
            modelId = "model",
            modelArtifactDigest = DIGEST,
            inferenceConfigJson = "{}",
            protocolVersion = WIDGET_AUTHORING_PROTOCOL_VERSION,
            schemaVersion = 1,
            toolContractDigest = DIGEST,
            currentStage = currentStage,
            activeAttemptId = null,
            createdAtMillis = 1,
            updatedAtMillis = 2,
        ),
        attempts = attempts,
        checkpoints = checkpoints,
    )

    private fun attempt(
        stage: WidgetAuthoringStageKey,
        status: WidgetAuthoringAttemptStatus,
        failureCode: WidgetAuthoringStageFailureCode? = null,
        artifact: String? = null,
    ) = WidgetAuthoringAttempt(
        id = "attempt-${stage.wireValue}",
        sessionId = "session",
        actionId = "action-${stage.wireValue}",
        stage = stage,
        stageRevision = 1,
        attemptNumber = 1,
        kind = WidgetAuthoringAttemptKind.Initial,
        status = status,
        artifact = artifact,
        artifactDigest = artifact?.let(::authoringDigest),
        upstreamDigest = DIGEST,
        failureCode = failureCode,
        startedAtMillis = 10,
        completedAtMillis = 25,
    )

    private fun checkpoint(
        stage: WidgetAuthoringStageKey,
        status: WidgetAuthoringCheckpointStatus,
    ) = WidgetAuthoringCheckpoint(
        sessionId = "session",
        stage = stage,
        stageRevision = 1,
        acceptedAttemptId = "accepted-${stage.wireValue}",
        artifact = "{}",
        artifactDigest = DIGEST,
        upstreamDigest = DIGEST,
        status = status,
        acceptedAtMillis = 20,
    )

    private companion object {
        val DIGEST = "a".repeat(64)
    }
}
