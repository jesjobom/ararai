package com.jesjobom.ararai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jesjobom.ararai.R
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.reporting.GeneratedContentReportDraft
import com.jesjobom.ararai.reporting.MAX_REPORT_COMMENT_LENGTH
import com.jesjobom.ararai.reporting.PendingReport
import com.jesjobom.ararai.reporting.ReportReason

@Composable
@Suppress("LongMethod")
internal fun GeneratedContentReportDialog(
    draft: GeneratedContentReportDraft,
    onSubmit: (ReportReason, String?, Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    ReportDialog(
        draft = draft,
        pendingReports = null,
        onSubmit = onSubmit,
        onDeletePendingReport = {},
        onDismiss = onDismiss,
    )
}

@Composable
internal fun ReportCenterDialog(
    draft: GeneratedContentReportDraft?,
    pendingReports: List<PendingReport>,
    onSubmit: (ReportReason, String?, Set<String>) -> Unit,
    onDeletePendingReport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ReportDialog(
        draft = draft,
        pendingReports = pendingReports,
        onSubmit = onSubmit,
        onDeletePendingReport = onDeletePendingReport,
        onDismiss = onDismiss,
    )
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun ReportDialog(
    draft: GeneratedContentReportDraft?,
    pendingReports: List<PendingReport>?,
    onSubmit: (ReportReason, String?, Set<String>) -> Unit,
    onDeletePendingReport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasTabs = pendingReports != null
    var selectedTab by remember(draft?.reportId, hasTabs) { mutableStateOf(if (draft == null) 1 else 0) }
    var reason by remember(draft?.reportId) { mutableStateOf<ReportReason?>(null) }
    var comment by remember(draft?.reportId) { mutableStateOf("") }
    var selectedIds by remember(draft?.reportId) { mutableStateOf(draft?.initiallySelectedContextIds.orEmpty()) }
    val showingDraft = !hasTabs || selectedTab == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (hasTabs) R.string.report_center_title else R.string.report_response_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasTabs) {
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            enabled = draft != null,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.report_tab_new)) },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(stringResource(R.string.report_tab_pending, pendingReports.orEmpty().size))
                            },
                        )
                    }
                }
                if (showingDraft && draft != null) {
                    Text(stringResource(R.string.report_response_disclosure))
                    ReportPreview(
                        label = stringResource(R.string.report_response_required),
                        text = draft.reportedResponse,
                        emphasized = true,
                    )
                    Text(stringResource(R.string.report_reason_required), style = MaterialTheme.typography.titleSmall)
                    ReportReason.entries.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = reason == option, onClick = { reason = option })
                            Text(option.localizedLabel(), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    if (draft.availableContext.isNotEmpty()) {
                        Text(
                            stringResource(R.string.report_context_optional),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        draft.availableContext.forEach { item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Checkbox(
                                    checked = item.messageId in selectedIds,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) {
                                            selectedIds + item.messageId
                                        } else {
                                            selectedIds - item.messageId
                                        }
                                    },
                                )
                                ReportPreview(
                                    if (item.role == ChatRole.User) {
                                        stringResource(R.string.chat_you)
                                    } else {
                                        stringResource(R.string.app_name)
                                    },
                                    item.text,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { comment = it.take(MAX_REPORT_COMMENT_LENGTH) },
                        label = { Text(stringResource(R.string.report_comment_optional)) },
                        supportingText = { Text("${comment.length}/$MAX_REPORT_COMMENT_LENGTH") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.report_us_hosting_disclosure),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    PendingReportsContent(
                        reports = pendingReports.orEmpty(),
                        onDelete = onDeletePendingReport,
                    )
                }
            }
        },
        confirmButton = {
            if (showingDraft && draft != null) {
                TextButton(
                    enabled = reason != null,
                    onClick = { reason?.let { onSubmit(it, comment.takeIf(String::isNotBlank), selectedIds) } },
                ) { Text(stringResource(R.string.report_submit)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = if (showingDraft && draft != null) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
        } else {
            null
        },
    )
}

@Composable
private fun ReportPreview(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (emphasized) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (emphasized) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReportReason.localizedLabel(): String = stringResource(
    when (this) {
        ReportReason.HateOrHarassment -> R.string.report_reason_hate
        ReportReason.SexualContent -> R.string.report_reason_sexual
        ReportReason.ViolenceOrSelfHarm -> R.string.report_reason_violence
        ReportReason.DangerousOrIllegal -> R.string.report_reason_dangerous
        ReportReason.Privacy -> R.string.report_reason_privacy
        ReportReason.FalseOrMisleading -> R.string.report_reason_false
        ReportReason.Other -> R.string.report_reason_other
    },
)
