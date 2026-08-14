package com.jesjobom.ararai.reporting

sealed interface ReportDeliveryResult {
    data class Accepted(val receiptId: String) : ReportDeliveryResult
    data object TransientFailure : ReportDeliveryResult
    data object PermanentFailure : ReportDeliveryResult
}

fun interface GeneratedContentReportTransport {
    suspend fun submit(payload: GeneratedContentReportPayload): ReportDeliveryResult
}

enum class ReportDeliveryOutcome { Sent, Retry, Failed, Missing }

class GeneratedContentReportDelivery(
    private val queue: PendingReportQueue,
    private val transport: GeneratedContentReportTransport,
    private val receiptStore: ReportDeliveryReceiptStore? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    @Suppress("ReturnCount")
    suspend fun deliver(reportId: String): ReportDeliveryOutcome {
        val now = nowEpochMillis()
        val report = queue.list(now).firstOrNull { it.payload.reportId == reportId }
            ?: return ReportDeliveryOutcome.Missing
        val attempted = queue.markAttempt(reportId, now) ?: return ReportDeliveryOutcome.Missing
        if (attempted.status == PendingReportStatus.PermanentFailure) {
            return ReportDeliveryOutcome.Failed
        }
        return when (transport.submit(report.payload)) {
            is ReportDeliveryResult.Accepted -> {
                queue.delete(reportId)
                receiptStore?.record(reportId, nowEpochMillis())
                ReportDeliveryOutcome.Sent
            }
            ReportDeliveryResult.TransientFailure -> ReportDeliveryOutcome.Retry
            ReportDeliveryResult.PermanentFailure -> {
                queue.markPermanentFailure(reportId)
                ReportDeliveryOutcome.Failed
            }
        }
    }
}
