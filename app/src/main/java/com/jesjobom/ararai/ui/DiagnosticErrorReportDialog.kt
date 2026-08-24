package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.reporting.DiagnosticErrorCategory
import com.jesjobom.ararai.reporting.DiagnosticErrorReportState

@Composable
internal fun DiagnosticErrorReportDialog(
    state: DiagnosticErrorReportState,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    val sending = state is DiagnosticErrorReportState.Sending
    AlertDialog(
        modifier = Modifier.testTag("diagnostic-error-report-dialog"),
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.diagnostic_error_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        when (state.payload.category) {
                            DiagnosticErrorCategory.ToolCallParsing -> R.string.diagnostic_error_tool_call
                            DiagnosticErrorCategory.UnexpectedGeneration -> R.string.diagnostic_error_generation
                        },
                    ),
                )
                Text(stringResource(R.string.diagnostic_error_privacy_description))
                when (state) {
                    is DiagnosticErrorReportState.Sending -> CircularProgressIndicator()
                    is DiagnosticErrorReportState.Sent -> Text(stringResource(R.string.diagnostic_error_sent))
                    is DiagnosticErrorReportState.Failed -> Text(stringResource(R.string.diagnostic_error_send_failed))
                    is DiagnosticErrorReportState.AwaitingConsent -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is DiagnosticErrorReportState.AwaitingConsent ->
                    TextButton(onClick = onSend) { Text(stringResource(R.string.diagnostic_error_send)) }
                is DiagnosticErrorReportState.Sending -> Unit
                is DiagnosticErrorReportState.Sent,
                is DiagnosticErrorReportState.Failed,
                -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (state is DiagnosticErrorReportState.AwaitingConsent) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.diagnostic_error_not_now)) }
            }
        },
    )
}
