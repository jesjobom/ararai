package com.jesjobom.ararai.reporting

import com.jesjobom.ararai.chat.ChatSessionStore

class GeneratedContentReportingController(
    private val sessionStore: ChatSessionStore,
    private val queue: PendingReportQueue,
    private val deliveryScheduler: ReportDeliveryScheduler = ReportDeliveryScheduler { },
    private val projector: GeneratedContentReportProjector = GeneratedContentReportProjector(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    fun draftFor(
        sessionId: String,
        messageId: String,
        metadata: ReportTechnicalMetadata,
    ): GeneratedContentReportDraft = projector.createDraft(
        messages = sessionStore.getMessages(sessionId),
        reportedMessageId = messageId,
        metadata = metadata,
    )

    fun latestDraft(
        sessionId: String,
        metadata: ReportTechnicalMetadata,
    ): GeneratedContentReportDraft? {
        val messages = sessionStore.getMessages(sessionId)
        val latest = messages.lastOrNull(projector::isReportable) ?: return null
        return projector.createDraft(messages, latest.id, metadata)
    }

    fun hasReportableResponse(sessionId: String): Boolean = sessionStore
        .getMessages(sessionId)
        .any(projector::isReportable)

    fun submit(
        draft: GeneratedContentReportDraft,
        reason: ReportReason,
        comment: String?,
        selectedContextIds: Set<String>,
    ): PendingReport {
        val now = nowEpochMillis()
        val payload = projector.buildPayload(draft, reason, comment, selectedContextIds, now)
        return queue.enqueue(payload, now).also { deliveryScheduler.schedule(it.payload.reportId) }
    }

    fun pendingReports(): List<PendingReport> = queue.list(nowEpochMillis())

    fun deletePending(reportId: String): Boolean = queue.delete(reportId)
}
