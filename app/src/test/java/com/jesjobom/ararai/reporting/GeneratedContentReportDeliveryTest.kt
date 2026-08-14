package com.jesjobom.ararai.reporting

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedContentReportDeliveryTest {
    @Test
    fun `accepted delivery records receipt`() = runDeliveryTest(ReportDeliveryResult.Accepted("receipt")) {
        assertEquals(ReportDeliveryOutcome.Sent, outcome)
        assertTrue(queue.list(200L).isEmpty())
        assertEquals(ReportDeliveryReceipt("id", 200L), receiptStore.latestReceipt.value)
    }

    @Test
    fun `transient failure retains same payload for retry`() = runDeliveryTest(ReportDeliveryResult.TransientFailure) {
        assertEquals(ReportDeliveryOutcome.Retry, outcome)
        assertEquals("id", queue.list(200L).single().payload.reportId)
        assertEquals(1, queue.list(200L).single().attemptCount)
    }

    @Test
    fun `permanent failure is retained for user visibility without retry`() {
        runDeliveryTest(ReportDeliveryResult.PermanentFailure) {
            assertEquals(ReportDeliveryOutcome.Failed, outcome)
            assertEquals(PendingReportStatus.PermanentFailure, queue.list(200L).single().status)
        }
    }

    private fun runDeliveryTest(
        result: ReportDeliveryResult,
        assertions: suspend DeliveryScope.() -> Unit,
    ) = kotlinx.coroutines.test.runTest {
        val queue = MemoryQueue()
        val receiptStore = MemoryReceiptStore()
        queue.enqueue(payload(), 100L)
        var submittedId: String? = null
        val delivery = GeneratedContentReportDelivery(
            queue = queue,
            transport = GeneratedContentReportTransport {
                submittedId = it.reportId
                result
            },
            receiptStore = receiptStore,
            nowEpochMillis = { 200L },
        )
        val outcome = delivery.deliver("id")
        assertEquals("id", submittedId)
        DeliveryScope(queue, receiptStore, outcome).assertions()
    }

    private data class DeliveryScope(
        val queue: MemoryQueue,
        val receiptStore: MemoryReceiptStore,
        val outcome: ReportDeliveryOutcome,
    )

    private class MemoryReceiptStore : ReportDeliveryReceiptStore {
        override val latestReceipt = MutableStateFlow<ReportDeliveryReceipt?>(null)
        override fun record(reportId: String, sentAtEpochMillis: Long) {
            latestReceipt.value = ReportDeliveryReceipt(reportId, sentAtEpochMillis)
        }
    }

    private fun payload() = GeneratedContentReportPayload(
        REPORT_SCHEMA_VERSION,
        "id",
        "response",
        ReportReason.Other,
        null,
        emptyList(),
        ReportMediaPresence(false, false, false),
        ReportTechnicalMetadata("1", "en", "model", "runtime"),
        50L,
    )

    private class MemoryQueue : PendingReportQueue {
        private var value: PendingReport? = null
        override fun enqueue(
            payload: GeneratedContentReportPayload,
            nowEpochMillis: Long,
        ) = PendingReport(
            payload,
            PendingReportStatus.Pending,
            nowEpochMillis,
            0,
        ).also { value = it }
        override fun list(nowEpochMillis: Long) = listOfNotNull(value)
        override fun markAttempt(reportId: String, nowEpochMillis: Long): PendingReport? = value?.copy(
            status = PendingReportStatus.Sending,
            attemptCount = value!!.attemptCount + 1,
            lastAttemptAtEpochMillis = nowEpochMillis,
        )?.also { value = it }
        override fun markPermanentFailure(reportId: String) = value?.copy(
            status = PendingReportStatus.PermanentFailure,
        )?.also { value = it }
        override fun delete(reportId: String): Boolean = (value?.payload?.reportId == reportId).also {
            if (it) value = null
        }
        override fun deleteExpired(nowEpochMillis: Long) = 0
    }
}
