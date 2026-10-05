package com.jesjobom.ararai.widget.managed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAuthoringWorkflowTest {
    @Test
    fun `stage keys round trip and reject unknown values`() {
        val stages = WidgetAuthoringStageGraph(listOf("weather", "calendar")).orderedStages

        assertEquals(stages, stages.map { WidgetAuthoringStageKey.parse(it.wireValue) })
        assertThrows(IllegalArgumentException::class.java) {
            WidgetAuthoringStageKey.parse("call:Not-Valid")
        }
        assertThrows(IllegalArgumentException::class.java) {
            WidgetAuthoringStageKey.parse("unknown")
        }
    }

    @Test
    fun `workflow rejects oversized persisted prompt and artifact payloads`() {
        assertThrows(IllegalArgumentException::class.java) {
            NewWidgetAuthoringSession(
                instruction = "x".repeat(WidgetAuthoringWorkflowPolicy.MAX_INSTRUCTION_BYTES + 1),
                targetWidgetId = null,
                modelId = "model",
                modelArtifactDigest = DIGEST,
                inferenceConfigJson = "{}",
                toolContractDigest = DIGEST,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompleteWidgetAuthoringAttempt(
                sessionId = "session",
                attemptId = "attempt",
                status = WidgetAuthoringAttemptStatus.Succeeded,
                artifact = "x".repeat(WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES + 1),
                failureCode = null,
            )
        }
    }

    @Test
    fun `algorithm replacement invalidates every downstream stage`() {
        val graph = WidgetAuthoringStageGraph(listOf("weather", "calendar"))

        assertEquals(
            setOf(
                WidgetAuthoringStageKey.CallFunction("weather"),
                WidgetAuthoringStageKey.CallFunction("calendar"),
                WidgetAuthoringStageKey.Plan,
                WidgetAuthoringStageKey.Render,
                WidgetAuthoringStageKey.Assembly,
            ),
            graph.transitiveDescendants(WidgetAuthoringStageKey.Algorithm),
        )
    }

    @Test
    fun `call replacement invalidates only dependent aggregate stages`() {
        val graph = WidgetAuthoringStageGraph(listOf("weather", "calendar"))

        assertEquals(
            setOf(
                WidgetAuthoringStageKey.Plan,
                WidgetAuthoringStageKey.Render,
                WidgetAuthoringStageKey.Assembly,
            ),
            graph.transitiveDescendants(WidgetAuthoringStageKey.CallFunction("weather")),
        )
        assertTrue(
            WidgetAuthoringStageKey.CallFunction("calendar") !in
                graph.transitiveDescendants(WidgetAuthoringStageKey.CallFunction("weather")),
        )
    }

    @Test
    fun `zero-call graph makes plan depend directly on algorithm`() {
        val graph = WidgetAuthoringStageGraph(emptyList())

        assertEquals(
            setOf(WidgetAuthoringStageKey.Algorithm),
            graph.directDependencies(WidgetAuthoringStageKey.Plan),
        )
    }

    @Test
    fun `action matrix accepts only safe user actions for every state`() {
        val base = WidgetAuthoringSessionSnapshot(
            session = session(WidgetAuthoringSessionStatus.AwaitingStage),
            attempts = emptyList(),
            checkpoints = emptyList(),
        )

        val cases = mapOf(
            WidgetAuthoringSessionStatus.AwaitingStage to setOf(
                WidgetAuthoringUserAction.Start,
                WidgetAuthoringUserAction.Discard,
            ),
            WidgetAuthoringSessionStatus.AttemptQueued to setOf(WidgetAuthoringUserAction.CancelAttempt),
            WidgetAuthoringSessionStatus.AttemptDeferred to setOf(WidgetAuthoringUserAction.CancelAttempt),
            WidgetAuthoringSessionStatus.AttemptRunning to setOf(WidgetAuthoringUserAction.CancelAttempt),
            WidgetAuthoringSessionStatus.AwaitingReview to setOf(
                WidgetAuthoringUserAction.Continue,
                WidgetAuthoringUserAction.Discard,
            ),
            WidgetAuthoringSessionStatus.AwaitingRetry to setOf(
                WidgetAuthoringUserAction.Retry,
                WidgetAuthoringUserAction.Discard,
            ),
            WidgetAuthoringSessionStatus.ReadyForPreview to setOf(
                WidgetAuthoringUserAction.Reprocess,
                WidgetAuthoringUserAction.Discard,
                WidgetAuthoringUserAction.Confirm,
            ),
            WidgetAuthoringSessionStatus.Completed to emptySet(),
        )

        cases.forEach { (status, expected) ->
            val snapshot = base.copy(
                session = session(status),
                attempts = if (status == WidgetAuthoringSessionStatus.AwaitingRetry) {
                    listOf(failedAttempt())
                } else {
                    emptyList()
                },
                checkpoints = if (status == WidgetAuthoringSessionStatus.ReadyForPreview) {
                    listOf(checkpoint())
                } else {
                    emptyList()
                },
            )
            assertEquals(expected, WidgetAuthoringWorkflowStateMachine.allowedActions(snapshot))
            WidgetAuthoringUserAction.entries.forEach { action ->
                if (action in expected) {
                    WidgetAuthoringWorkflowStateMachine.requireAllowed(snapshot, action)
                } else {
                    assertThrows(IllegalArgumentException::class.java) {
                        WidgetAuthoringWorkflowStateMachine.requireAllowed(snapshot, action)
                    }
                }
            }
        }
    }

    @Test
    fun `failed replacement keeps complete accepted graph confirmable`() {
        val snapshot = WidgetAuthoringSessionSnapshot(
            session = session(WidgetAuthoringSessionStatus.AwaitingRetry).copy(
                currentStage = WidgetAuthoringStageKey.Algorithm,
            ),
            attempts = listOf(
                failedAttempt().copy(
                    stage = WidgetAuthoringStageKey.Algorithm,
                    kind = WidgetAuthoringAttemptKind.Reprocess,
                ),
            ),
            checkpoints = listOf(
                checkpoint(WidgetAuthoringStageKey.Feasibility),
                checkpoint(WidgetAuthoringStageKey.Algorithm),
                checkpoint(WidgetAuthoringStageKey.Plan),
                checkpoint(WidgetAuthoringStageKey.Render),
            ),
        )

        assertEquals(
            setOf(
                WidgetAuthoringUserAction.Retry,
                WidgetAuthoringUserAction.Reprocess,
                WidgetAuthoringUserAction.Discard,
                WidgetAuthoringUserAction.Confirm,
            ),
            WidgetAuthoringWorkflowStateMachine.allowedActions(snapshot),
        )
    }

    @Test
    fun `stage transitions cannot skip directly to preview`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidAuthoringStageTransition(WidgetAuthoringStageKey.Feasibility, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            requireValidAuthoringStageTransition(
                WidgetAuthoringStageKey.Algorithm,
                WidgetAuthoringStageKey.Render,
            )
        }

        requireValidAuthoringStageTransition(
            WidgetAuthoringStageKey.Feasibility,
            WidgetAuthoringStageKey.Algorithm,
        )
        requireValidAuthoringStageTransition(WidgetAuthoringStageKey.Render, null)
    }

    private fun session(status: WidgetAuthoringSessionStatus) = WidgetAuthoringSession(
        id = "session",
        revision = 1,
        status = status,
        instruction = "Create a widget",
        targetWidgetId = null,
        modelId = "model",
        modelArtifactDigest = DIGEST,
        inferenceConfigJson = "{}",
        protocolVersion = 1,
        schemaVersion = 1,
        toolContractDigest = DIGEST,
        currentStage = WidgetAuthoringStageKey.Feasibility,
        activeAttemptId = null,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun checkpoint(
        stage: WidgetAuthoringStageKey = WidgetAuthoringStageKey.Feasibility,
    ) = WidgetAuthoringCheckpoint(
        sessionId = "session",
        stage = stage,
        stageRevision = 1,
        acceptedAttemptId = "attempt",
        artifact = "{}",
        artifactDigest = DIGEST,
        upstreamDigest = DIGEST,
        status = WidgetAuthoringCheckpointStatus.Accepted,
        acceptedAtMillis = 1,
    )

    private fun failedAttempt() = WidgetAuthoringAttempt(
        id = "attempt",
        sessionId = "session",
        actionId = "action",
        stage = WidgetAuthoringStageKey.Feasibility,
        stageRevision = 1,
        attemptNumber = 1,
        kind = WidgetAuthoringAttemptKind.Initial,
        status = WidgetAuthoringAttemptStatus.Failed,
        artifact = "{}",
        artifactDigest = DIGEST,
        upstreamDigest = DIGEST,
        failureCode = WidgetAuthoringStageFailureCode.InvalidFeasibilityShape,
        startedAtMillis = 1,
        completedAtMillis = 2,
    )

    private companion object {
        val DIGEST = "a".repeat(64)
    }
}
