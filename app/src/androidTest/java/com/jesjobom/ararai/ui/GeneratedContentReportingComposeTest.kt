package com.jesjobom.ararai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.jesjobom.ararai.R
import com.jesjobom.ararai.reporting.GeneratedContentReportDraft
import com.jesjobom.ararai.reporting.GeneratedContentReportPayload
import com.jesjobom.ararai.reporting.PendingReport
import com.jesjobom.ararai.reporting.PendingReportStatus
import com.jesjobom.ararai.reporting.ReportDeliveryReceipt
import com.jesjobom.ararai.reporting.ReportMediaPresence
import com.jesjobom.ararai.reporting.ReportReason
import com.jesjobom.ararai.reporting.ReportTechnicalMetadata
import org.junit.Rule
import org.junit.Test

class GeneratedContentReportingComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reportCenterButtonExposesNeutralPendingFailureAndSuccessStates() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        var pendingReports by mutableStateOf(emptyList<PendingReport>())
        var receipt by mutableStateOf<ReportDeliveryReceipt?>(null)
        composeRule.setContent {
            MaterialTheme { ReportCenterButton(pendingReports, receipt, {}) }
        }
        composeRule.onNodeWithContentDescription(
            resources.getString(R.string.report_center_content_description),
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            pendingReports = listOf(pending())
        }
        composeRule.onNodeWithContentDescription(
            resources.getString(R.string.report_center_pending_content_description, 1),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("1").assertIsDisplayed()

        composeRule.runOnIdle {
            pendingReports = listOf(pending(PendingReportStatus.PermanentFailure))
        }
        composeRule.onNodeWithText("!").assertIsDisplayed()

        composeRule.runOnIdle {
            pendingReports = emptyList()
            receipt = ReportDeliveryReceipt(REPORT_ID, System.currentTimeMillis())
        }
        composeRule.onNodeWithContentDescription(
            resources.getString(R.string.report_sent_content_description),
        ).assertIsDisplayed()
    }

    @Test
    fun reportCenterKeepsNewAndPendingFlowsAccessible() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        composeRule.setContent {
            MaterialTheme {
                ReportCenterDialog(
                    draft = draft(),
                    pendingReports = listOf(pending()),
                    onSubmit = { _, _, _ -> },
                    onDeletePendingReport = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText(resources.getString(R.string.report_tab_new)).assertIsDisplayed()
        composeRule.onNodeWithText(resources.getString(R.string.report_tab_pending, 1)).assertIsDisplayed()
        composeRule.onNodeWithText("reported response").assertIsDisplayed()
        composeRule.onNodeWithText(resources.getString(R.string.report_submit)).assertIsNotEnabled()
        composeRule.onNodeWithText(resources.getString(R.string.report_us_hosting_disclosure)).assertIsDisplayed()
    }

    private fun draft() = GeneratedContentReportDraft(
        reportId = REPORT_ID,
        reportedMessageId = "message-id",
        reportedResponse = "reported response",
        availableContext = emptyList(),
        initiallySelectedContextIds = emptySet(),
        mediaPresence = ReportMediaPresence(false, false, false),
        metadata = metadata(),
    )

    private fun pending(status: PendingReportStatus = PendingReportStatus.Pending) = PendingReport(
        payload = GeneratedContentReportPayload(
            schemaVersion = 1,
            reportId = REPORT_ID,
            reportedResponse = "reported response",
            reason = ReportReason.Other,
            comment = null,
            context = emptyList(),
            mediaPresence = ReportMediaPresence(false, false, false),
            metadata = metadata(),
            reportedAtEpochMillis = 1_700_000_000_000L,
        ),
        status = status,
        createdAtEpochMillis = 1_700_000_000_000L,
        attemptCount = 0,
    )

    private fun metadata() = ReportTechnicalMetadata("1", "en", "model", "runtime")

    private companion object {
        const val REPORT_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
