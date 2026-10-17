package com.jesjobom.ararai.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
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
import com.jesjobom.ararai.widget.managed.WidgetAuthoringWarning
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

    @Test
    fun stageCardDisplaysWarningWithoutChangingReviewStatus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val warning = context.getString(R.string.widget_authoring_warning_random_selection)
        val status = context.getString(R.string.widget_authoring_workflow_status_review)
        composeRule.setContent {
            MaterialTheme {
                AuthoringStageCard(
                    stage = stage().copy(warnings = listOf(WidgetAuthoringWarning.RandomSelectionMayUseFirst)),
                    reprocessEnabled = true,
                    onReprocess = {},
                )
            }
        }

        composeRule.onNodeWithText(warning).assertIsDisplayed()
        composeRule.onNodeWithText(status).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.widget_authoring_workflow_reprocess))
            .assertHasClickAction()
    }

    @Test
    fun stageCardsExposeEveryWorkflowStageAndControlledStatusToAccessibility() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val visibleStage = mutableStateOf(stage())
        composeRule.setContent {
            MaterialTheme {
                AuthoringStageCard(
                    stage = visibleStage.value,
                    reprocessEnabled = false,
                    onReprocess = {},
                )
            }
        }
        val cases = listOf(
            Triple(
                WidgetAuthoringStageKey.Feasibility,
                R.string.widget_authoring_workflow_stage_feasibility,
                WidgetAuthoringStageDisplayStatus.AwaitingReview to R.string.widget_authoring_workflow_status_review,
            ),
            Triple(
                WidgetAuthoringStageKey.Algorithm,
                R.string.widget_authoring_workflow_stage_algorithm,
                WidgetAuthoringStageDisplayStatus.AwaitingRetry to R.string.widget_authoring_workflow_status_retry,
            ),
            Triple(
                WidgetAuthoringStageKey.CallFunction("events"),
                R.string.widget_authoring_workflow_stage_call,
                WidgetAuthoringStageDisplayStatus.Queued to R.string.widget_authoring_workflow_status_queued,
            ),
            Triple(
                WidgetAuthoringStageKey.Plan,
                R.string.widget_authoring_workflow_stage_plan,
                WidgetAuthoringStageDisplayStatus.Accepted to R.string.widget_authoring_workflow_status_accepted,
            ),
            Triple(
                WidgetAuthoringStageKey.Render,
                R.string.widget_authoring_workflow_stage_render,
                WidgetAuthoringStageDisplayStatus.Running to R.string.widget_authoring_workflow_status_running,
            ),
            Triple(
                WidgetAuthoringStageKey.Assembly,
                R.string.widget_authoring_workflow_stage_assembly,
                WidgetAuthoringStageDisplayStatus.Completed to R.string.widget_authoring_workflow_status_completed,
            ),
        )
        cases.forEach { (key, stageLabelId, status) ->
            composeRule.runOnIdle { visibleStage.value = stage().copy(stage = key, status = status.first) }
            val stageLabel = if (key is WidgetAuthoringStageKey.CallFunction) {
                context.getString(stageLabelId, key.stepId)
            } else {
                context.getString(stageLabelId)
            }
            val label = context.getString(
                R.string.widget_authoring_workflow_stage_accessibility,
                stageLabel,
                context.getString(status.second),
            )
            composeRule.onNodeWithContentDescription(label).assertIsDisplayed()
        }
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
