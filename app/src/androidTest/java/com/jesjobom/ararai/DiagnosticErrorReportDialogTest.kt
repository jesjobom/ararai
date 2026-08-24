package com.jesjobom.ararai

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jesjobom.ararai.reporting.DiagnosticErrorCategory
import com.jesjobom.ararai.reporting.DiagnosticErrorReportPayload
import com.jesjobom.ararai.reporting.DiagnosticErrorReportState
import com.jesjobom.ararai.ui.DiagnosticErrorReportDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DiagnosticErrorReportDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun awaitingConsentExplainsPrivacyAndOffersExplicitActions() {
        var sends = 0
        var dismissals = 0
        composeRule.setContent {
            MaterialTheme {
                DiagnosticErrorReportDialog(
                    state = DiagnosticErrorReportState.AwaitingConsent(payload()),
                    onDismiss = { dismissals += 1 },
                    onSend = { sends += 1 },
                )
            }
        }

        composeRule.onNodeWithTag("diagnostic-error-report-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Send report").performClick()
        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(1, sends)
        assertEquals(1, dismissals)
    }

    private fun payload() = DiagnosticErrorReportPayload(
        reportId = "123e4567-e89b-42d3-a456-426614174000",
        category = DiagnosticErrorCategory.ToolCallParsing,
        stage = "chat_generation",
        exceptionType = "IllegalArgumentException",
        exceptionSummary = "The local runtime could not parse a model tool call.",
        stackSummary = emptyList(),
        appVersion = "test",
        androidApiLevel = 36,
        localeTag = "en",
        modelId = "test-model",
        runtime = "litert_lm",
        contextTokens = 6144,
        reasoningEnabled = true,
        enabledToolNames = listOf("web_search"),
        reportedAtEpochMillis = 1L,
    )
}
