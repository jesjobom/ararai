package com.jesjobom.ararai.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.jesjobom.ararai.R
import com.jesjobom.ararai.reporting.PendingReport
import com.jesjobom.ararai.reporting.PendingReportStatus
import com.jesjobom.ararai.reporting.ReportDeliveryReceipt
import kotlinx.coroutines.delay

@Composable
internal fun ReportCenterButton(
    pendingReports: List<PendingReport>,
    latestReceipt: ReportDeliveryReceipt?,
    onClick: () -> Unit,
) {
    val hasPermanentFailure = pendingReports.any { it.status == PendingReportStatus.PermanentFailure }
    var showSuccess by remember(latestReceipt) { mutableStateOf(false) }
    LaunchedEffect(latestReceipt) {
        val receipt = latestReceipt ?: return@LaunchedEffect
        val remaining = SUCCESS_INDICATOR_MILLIS - (System.currentTimeMillis() - receipt.sentAtEpochMillis)
        if (remaining > 0L) {
            showSuccess = true
            delay(remaining)
            showSuccess = false
        }
    }
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (pendingReports.isNotEmpty()) {
                    Badge(
                        containerColor = if (hasPermanentFailure) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    ) {
                        Text(if (hasPermanentFailure) "!" else pendingReports.size.toString())
                    }
                }
            },
        ) {
            Icon(
                imageVector = if (showSuccess && pendingReports.isEmpty()) {
                    Icons.Filled.Check
                } else {
                    Icons.Filled.Flag
                },
                tint = if (showSuccess && pendingReports.isEmpty()) {
                    SUCCESS_GREEN
                } else {
                    LocalContentColor.current
                },
                contentDescription = if (showSuccess && pendingReports.isEmpty()) {
                    stringResource(R.string.report_sent_content_description)
                } else if (pendingReports.isEmpty()) {
                    stringResource(R.string.report_center_content_description)
                } else {
                    stringResource(R.string.report_center_pending_content_description, pendingReports.size)
                },
            )
        }
    }
}

private const val SUCCESS_INDICATOR_MILLIS = 3_000L
private val SUCCESS_GREEN = Color(0xFF2E7D32)
