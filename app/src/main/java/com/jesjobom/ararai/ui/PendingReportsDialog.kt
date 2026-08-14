package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.reporting.PendingReport
import com.jesjobom.ararai.reporting.PendingReportStatus

@Composable
internal fun PendingReportsDialog(
    reports: List<PendingReport>,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_queue_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (reports.isEmpty()) {
                    Text(stringResource(R.string.report_queue_empty))
                }
                reports.forEach { report ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            report.status.localizedLabel(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            report.payload.reportedResponse,
                            maxLines = 3,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { onDelete(report.payload.reportId) }) {
                            Text(stringResource(R.string.report_delete_unsent))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

@Composable
internal fun PendingReportsContent(
    reports: List<PendingReport>,
    onDelete: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (reports.isEmpty()) Text(stringResource(R.string.report_queue_empty))
        reports.forEach { report ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(report.status.localizedLabel(), style = MaterialTheme.typography.labelLarge)
                Text(report.payload.reportedResponse, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onDelete(report.payload.reportId) }) {
                    Text(stringResource(R.string.report_delete_unsent))
                }
            }
        }
    }
}

@Composable
private fun PendingReportStatus.localizedLabel(): String = stringResource(
    when (this) {
        PendingReportStatus.Pending -> R.string.report_status_pending
        PendingReportStatus.Sending -> R.string.report_status_sending
        PendingReportStatus.PermanentFailure -> R.string.report_status_failed
    },
)
