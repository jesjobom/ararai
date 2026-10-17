package com.jesjobom.ararai.widget.managed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAuthoringWorkflowProjectionTest {
    @Test
    fun `warning on review candidate does not block continuing`() {
        val algorithm = attempt(
            stage = WidgetAuthoringStageKey.Algorithm,
            status = WidgetAuthoringAttemptStatus.Succeeded,
            artifact = """{"steps":[{"kind":"tool_call","toolId":"wikipedia_pages","selectionMode":"first"}]}""",
        )
        val projection = WidgetAuthoringWorkflowProjector.project(
            snapshot(
                status = WidgetAuthoringSessionStatus.AwaitingReview,
                currentStage = WidgetAuthoringStageKey.Algorithm,
                attempts = listOf(algorithm),
                checkpoints = emptyList(),
                instruction = "Mostre uma página aleatória da Wikipedia",
            ),
        )

        assertEquals(
            listOf(WidgetAuthoringWarning.RandomSelectionMayUseFirst),
            projection.stages.single { it.stage == WidgetAuthoringStageKey.Algorithm }.warnings,
        )
        assertEquals(
            setOf(WidgetAuthoringUserAction.Continue, WidgetAuthoringUserAction.Discard),
            projection.allowedActions,
        )
    }

    @Test
    fun `time warning follows accepted call but never marks it invalid`() {
        val stage = WidgetAuthoringStageKey.CallFunction("search")
        val projection = WidgetAuthoringWorkflowProjector.project(
            snapshot(
                status = WidgetAuthoringSessionStatus.AwaitingStage,
                currentStage = WidgetAuthoringStageKey.Plan,
                attempts = emptyList(),
                checkpoints = listOf(
                    checkpoint(stage, WidgetAuthoringCheckpointStatus.Accepted).copy(
                        artifact = """{"source":"function call(runtime) { return runtime.language; }"}""",
                    ),
                ),
                instruction = "Mostre Europa de dia e América de noite",
            ),
        )

        val call = projection.stages.single { it.stage == stage }
        assertEquals(WidgetAuthoringStageDisplayStatus.Accepted, call.status)
        assertEquals(listOf(WidgetAuthoringWarning.TimeConditionNotEvident), call.warnings)
    }

    @Test
    fun `warning detector ignores unrelated or malformed content`() {
        val stage = WidgetAuthoringStageKey.Algorithm
        assertEquals(
            emptyList<WidgetAuthoringWarning>(),
            WidgetAuthoringWarningDetector.detect(
                "Show the first Wikipedia page",
                stage,
                """{"steps":[{"toolId":"wikipedia_pages","selectionMode":"first"}]}""",
            ),
        )
        assertEquals(emptyList<WidgetAuthoringWarning>(), WidgetAuthoringWarningDetector.detect("random", stage, "{}"))
        assertEquals(emptyList<WidgetAuthoringWarning>(), WidgetAuthoringWarningDetector.detect("random", stage, "{"))
    }

    @Test
    fun `page selection mismatch warnings cover random first and unsupported last`() {
        val stage = WidgetAuthoringStageKey.Algorithm
        val first = """{"steps":[{"toolId":"wikipedia_pages","selectionMode":"first"}]}"""
        val random = """{"steps":[{"toolId":"wikipedia_pages","selectionMode":"seeded_random"}]}"""
        val cases = listOf(
            Triple("Mostre uma página randômica", first, WidgetAuthoringWarning.RandomSelectionMayUseFirst),
            Triple("Mostre a primeira página", random, WidgetAuthoringWarning.FirstSelectionMayUseRandom),
            Triple("Mostre a última página", first, WidgetAuthoringWarning.LastSelectionUnavailable),
            Triple("Show the last page", random, WidgetAuthoringWarning.LastSelectionUnavailable),
        )
        cases.forEach { (instruction, artifact, expected) ->
            assertEquals(listOf(expected), WidgetAuthoringWarningDetector.detect(instruction, stage, artifact))
        }
        assertEquals(
            emptyList<WidgetAuthoringWarning>(),
            WidgetAuthoringWarningDetector.detect("randômica", stage, random),
        )
        assertEquals(
            emptyList<WidgetAuthoringWarning>(),
            WidgetAuthoringWarningDetector.detect("primeira", stage, first),
        )
        assertEquals(
            emptyList<WidgetAuthoringWarning>(),
            WidgetAuthoringWarningDetector.detect("Primeira ou última página", stage, first),
        )
    }

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
        instruction: String = "Build a widget",
    ) = WidgetAuthoringSessionSnapshot(
        session = WidgetAuthoringSession(
            id = "session",
            revision = 4,
            status = status,
            instruction = instruction,
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
