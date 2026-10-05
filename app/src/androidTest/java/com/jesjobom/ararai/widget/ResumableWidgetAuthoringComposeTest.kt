package com.jesjobom.ararai.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.jesjobom.ararai.R
import com.jesjobom.ararai.ui.AuthoringStageCard
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAttemptDetails
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAttemptKind
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAttemptStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageDisplayStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageKey
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageProjection
import org.junit.Rule
import org.junit.Test

class ResumableWidgetAuthoringComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stageCardExposesLocalizedStageAndStatusAsOneAccessibilityLabel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accessibilityLabel = context.getString(
            R.string.widget_authoring_workflow_stage_accessibility,
            context.getString(R.string.widget_authoring_workflow_stage_feasibility),
            context.getString(R.string.widget_authoring_workflow_status_review),
        )
        val reprocessLabel = context.getString(R.string.widget_authoring_workflow_reprocess)
        val capturedContentLabel = context.getString(R.string.widget_authoring_workflow_captured_content)
        composeRule.setContent {
            MaterialTheme {
                AuthoringStageCard(
                    stage = stage(),
                    reprocessEnabled = true,
                    onReprocess = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(accessibilityLabel)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(accessibilityLabel)
        composeRule.onNodeWithText(reprocessLabel).assertHasClickAction()
        composeRule.onNodeWithText(capturedContentLabel).assertIsDisplayed()
    }

    private fun stage() = WidgetAuthoringStageProjection(
        stage = WidgetAuthoringStageKey.Feasibility,
        status = WidgetAuthoringStageDisplayStatus.AwaitingReview,
        acceptedCheckpoint = null,
        attempts = listOf(
            WidgetAuthoringAttemptDetails(
                attemptId = "attempt",
                stageRevision = 1,
                kind = WidgetAuthoringAttemptKind.Initial,
                attemptNumber = 1,
                status = WidgetAuthoringAttemptStatus.Succeeded,
                durationMillis = 25,
                capturedBytes = 2,
                failureCode = null,
                capturedArtifact = "{}",
            ),
        ),
    )
}
