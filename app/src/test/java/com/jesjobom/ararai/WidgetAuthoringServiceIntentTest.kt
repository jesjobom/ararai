package com.jesjobom.ararai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.widget.managed.WIDGET_AUTHORING_PROTOCOL_VERSION
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAttempt
import com.jesjobom.ararai.widget.managed.WidgetAuthoringCheckpoint
import com.jesjobom.ararai.widget.managed.WidgetAuthoringNotificationAction
import com.jesjobom.ararai.widget.managed.WidgetAuthoringSession
import com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionSnapshot
import com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class WidgetAuthoringServiceIntentTest {
    @Test
    fun `workflow intent accepts exact revision and rejects stale redelivery`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val snapshot = snapshot(revision = 4)
        val intent = WidgetAuthoringService.workflowActionIntent(
            context,
            snapshot,
            WidgetAuthoringNotificationAction.Retry,
        )

        assertTrue(intent.matchesWidgetAuthoringSession(snapshot))
        assertFalse(intent.matchesWidgetAuthoringSession(snapshot(revision = 5)))
        assertFalse(intent.matchesWidgetAuthoringSession(null))
    }

    @Test
    fun `foreground refresh is explicit and survives a rapid terminal transition`() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        assertEquals(
            WidgetAuthoringService.ACTION_REFRESH,
            WidgetAuthoringService.refreshIntent(context).action,
        )
        assertEquals(
            WidgetAuthoringForegroundDisposition.Keep,
            widgetAuthoringForegroundDisposition(
                snapshot(revision = 4, status = WidgetAuthoringSessionStatus.AttemptRunning),
                null,
            ),
        )
        assertEquals(
            WidgetAuthoringForegroundDisposition.DetachAndStop,
            widgetAuthoringForegroundDisposition(
                snapshot(revision = 5, status = WidgetAuthoringSessionStatus.AwaitingReview),
                null,
            ),
        )
        assertEquals(
            WidgetAuthoringForegroundDisposition.RemoveAndStop,
            widgetAuthoringForegroundDisposition(null, null),
        )
    }

    private fun snapshot(
        revision: Int,
        status: WidgetAuthoringSessionStatus = WidgetAuthoringSessionStatus.AwaitingRetry,
    ) = WidgetAuthoringSessionSnapshot(
        session = WidgetAuthoringSession(
            id = "session",
            revision = revision,
            status = status,
            instruction = "Create widget",
            targetWidgetId = null,
            modelId = "model",
            modelArtifactDigest = DIGEST,
            inferenceConfigJson = "{}",
            protocolVersion = WIDGET_AUTHORING_PROTOCOL_VERSION,
            schemaVersion = 1,
            toolContractDigest = DIGEST,
            currentStage = WidgetAuthoringStageKey.Feasibility,
            activeAttemptId = null,
            createdAtMillis = 1,
            updatedAtMillis = 2,
        ),
        attempts = emptyList<WidgetAuthoringAttempt>(),
        checkpoints = emptyList<WidgetAuthoringCheckpoint>(),
    )

    private companion object {
        val DIGEST = "a".repeat(64)
    }
}
