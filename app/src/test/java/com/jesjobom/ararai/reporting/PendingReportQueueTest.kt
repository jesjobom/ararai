package com.jesjobom.ararai.reporting

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingReportQueueTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var queue: SqlitePendingReportQueue

    @Before
    fun setUp() {
        context.deleteDatabase("ararai_pending_reports.db")
        queue = SqlitePendingReportQueue(context)
    }

    @After
    fun tearDown() {
        queue.close()
        context.deleteDatabase("ararai_pending_reports.db")
    }

    @Test
    fun `persists exact approved payload across queue recreation`() {
        val expected = payload("report-1")
        queue.enqueue(expected, 100L)
        queue.close()
        queue = SqlitePendingReportQueue(context)

        assertEquals(expected, queue.list(100L).single().payload)
    }

    @Test
    fun `duplicate id remains idempotent`() {
        queue.enqueue(payload("same", response = "original"), 100L)
        queue.enqueue(payload("same", response = "different"), 200L)

        val pending = queue.list(200L).single()
        assertEquals("original", pending.payload.reportedResponse)
        assertEquals(100L, pending.createdAtEpochMillis)
    }

    @Test
    fun `caps queue and rejects oversized payload`() {
        repeat(MAX_PENDING_REPORTS) { queue.enqueue(payload("report-$it"), it.toLong()) }
        assertThrows(PendingReportQueueException::class.java) {
            queue.enqueue(payload("overflow"), 100L)
        }
        assertThrows(PendingReportQueueException::class.java) {
            queue.enqueue(payload("large", "x".repeat(MAX_PENDING_REPORT_BYTES)), 100L)
        }
    }

    @Test
    fun `expires old payload and supports explicit deletion`() {
        queue.enqueue(payload("expired"), 0L)
        queue.enqueue(payload("current"), MAX_PENDING_REPORT_AGE_MILLIS)

        val reports = queue.list(MAX_PENDING_REPORT_AGE_MILLIS + 1L)

        assertEquals(listOf("current"), reports.map { it.payload.reportId })
        assertTrue(queue.delete("current"))
        assertFalse(queue.delete("missing"))
        assertTrue(queue.list(MAX_PENDING_REPORT_AGE_MILLIS + 1L).isEmpty())
    }

    @Test
    fun `attempt cap converts item to permanent failure`() {
        queue.enqueue(payload("attempts"), 100L)

        repeat(MAX_REPORT_ATTEMPTS) { queue.markAttempt("attempts", 200L + it) }

        val failed = queue.list(300L).single()
        assertEquals(MAX_REPORT_ATTEMPTS, failed.attemptCount)
        assertEquals(PendingReportStatus.PermanentFailure, failed.status)
        queue.markAttempt("attempts", 999L)
        assertEquals(MAX_REPORT_ATTEMPTS, queue.list(999L).single().attemptCount)
    }

    private fun payload(id: String, response: String = "response") = GeneratedContentReportPayload(
        schemaVersion = REPORT_SCHEMA_VERSION,
        reportId = id,
        reportedResponse = response,
        reason = ReportReason.Other,
        comment = "comment",
        context = emptyList(),
        mediaPresence = ReportMediaPresence(false, false, false),
        metadata = ReportTechnicalMetadata("1", "en-CA", "model", "runtime"),
        reportedAtEpochMillis = 50L,
    )
}
