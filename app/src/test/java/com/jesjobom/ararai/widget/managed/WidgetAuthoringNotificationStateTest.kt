package com.jesjobom.ararai.widget.managed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAuthoringNotificationStateTest {
    @Test
    fun `active attempt is ongoing and exposes only cancel`() {
        val state = WidgetAuthoringNotificationProjector.project(snapshot(WidgetAuthoringSessionStatus.AttemptRunning))

        assertTrue(state.ongoing)
        assertEquals(setOf(WidgetAuthoringNotificationAction.Cancel), state.actions)
    }

    @Test
    fun `successful attempt stops foreground and exposes review plus continue`() {
        val state = WidgetAuthoringNotificationProjector.project(snapshot(WidgetAuthoringSessionStatus.AwaitingReview))

        assertFalse(state.ongoing)
        assertEquals(
            setOf(
                WidgetAuthoringNotificationAction.Review,
                WidgetAuthoringNotificationAction.Continue,
            ),
            state.actions,
        )
    }

    @Test
    fun `failed attempt stops foreground and exposes review plus retry`() {
        val state = WidgetAuthoringNotificationProjector.project(snapshot(WidgetAuthoringSessionStatus.AwaitingRetry))

        assertFalse(state.ongoing)
        assertEquals(
            setOf(
                WidgetAuthoringNotificationAction.Review,
                WidgetAuthoringNotificationAction.Retry,
            ),
            state.actions,
        )
    }

    @Test
    fun `every user boundary stops foreground execution`() {
        listOf(
            WidgetAuthoringSessionStatus.AwaitingStage,
            WidgetAuthoringSessionStatus.AwaitingReview,
            WidgetAuthoringSessionStatus.AwaitingRetry,
            WidgetAuthoringSessionStatus.ReadyForPreview,
            WidgetAuthoringSessionStatus.Completed,
        ).forEach { status ->
            assertFalse(status.name, WidgetAuthoringNotificationProjector.project(snapshot(status)).ongoing)
        }
    }

    private fun snapshot(status: WidgetAuthoringSessionStatus) = WidgetAuthoringSessionSnapshot(
        session = WidgetAuthoringSession(
            id = "session",
            revision = 2,
            status = status,
            instruction = "Build a widget",
            targetWidgetId = null,
            modelId = "model",
            modelArtifactDigest = DIGEST,
            inferenceConfigJson = "{}",
            protocolVersion = WIDGET_AUTHORING_PROTOCOL_VERSION,
            schemaVersion = 1,
            toolContractDigest = DIGEST,
            currentStage = WidgetAuthoringStageKey.Feasibility,
            activeAttemptId = if (
                status in setOf(
                    WidgetAuthoringSessionStatus.AttemptQueued,
                    WidgetAuthoringSessionStatus.AttemptDeferred,
                    WidgetAuthoringSessionStatus.AttemptRunning,
                )
            ) {
                "attempt"
            } else {
                null
            },
            createdAtMillis = 1,
            updatedAtMillis = 2,
        ),
        attempts = emptyList(),
        checkpoints = emptyList(),
    )

    private companion object {
        val DIGEST = "a".repeat(64)
    }
}
