package com.jesjobom.ararai.reporting

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.jesjobom.ararai.chat.ChatRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = ReportDeliveryWorkerTest.TestApplication::class, sdk = [35])
class ReportDeliveryWorkerTest {
    private lateinit var application: TestApplication

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        application.reportDelivery = null
    }

    @Test
    fun `maps accepted delivery to WorkManager success`() = runTest {
        val queue = FakeQueue(payload())
        application.reportDelivery = delivery(queue, ReportDeliveryResult.Accepted("receipt"))

        val result = worker(REPORT_ID).doWork()

        assertResultType(ListenableWorker.Result.success(), result)
        assertEquals(null, queue.pending)
    }

    @Test
    fun `maps transient delivery to WorkManager retry without losing payload`() = runTest {
        val queue = FakeQueue(payload())
        application.reportDelivery = delivery(queue, ReportDeliveryResult.TransientFailure)

        val result = worker(REPORT_ID).doWork()

        assertResultType(ListenableWorker.Result.retry(), result)
        assertEquals(REPORT_ID, queue.pending?.payload?.reportId)
    }

    @Test
    fun `maps permanent delivery to WorkManager failure and visible failed state`() = runTest {
        val queue = FakeQueue(payload())
        application.reportDelivery = delivery(queue, ReportDeliveryResult.PermanentFailure)

        val result = worker(REPORT_ID).doWork()

        assertResultType(ListenableWorker.Result.failure(), result)
        assertEquals(PendingReportStatus.PermanentFailure, queue.pending?.status)
    }

    @Test
    fun `fails closed when required worker input or provider is missing`() = runTest {
        assertResultType(ListenableWorker.Result.failure(), worker(null).doWork())
        assertResultType(ListenableWorker.Result.failure(), worker(REPORT_ID).doWork())
    }

    private fun worker(reportId: String?): ReportDeliveryWorker {
        val input = Data.Builder().apply {
            reportId?.let { putString(ReportDeliveryWorker.KEY_REPORT_ID, it) }
        }.build()
        return TestListenableWorkerBuilder<ReportDeliveryWorker>(application)
            .setInputData(input)
            .build()
    }

    private fun delivery(queue: PendingReportQueue, result: ReportDeliveryResult) = GeneratedContentReportDelivery(
        queue = queue,
        transport = GeneratedContentReportTransport { result },
        nowEpochMillis = { NOW },
    )

    private fun assertResultType(expected: ListenableWorker.Result, actual: ListenableWorker.Result) {
        assertEquals(expected.javaClass, actual.javaClass)
    }

    private fun payload() = GeneratedContentReportPayload(
        schemaVersion = REPORT_SCHEMA_VERSION,
        reportId = REPORT_ID,
        reportedResponse = "response",
        reason = ReportReason.Other,
        comment = null,
        context = listOf(ReportContextItem("context", ChatRole.User, "text")),
        mediaPresence = ReportMediaPresence(false, false, false),
        metadata = ReportTechnicalMetadata("1", "en", "model", "runtime"),
        reportedAtEpochMillis = NOW,
    )

    class TestApplication :
        Application(),
        ReportDeliveryProvider {
        override var reportDelivery: GeneratedContentReportDelivery? = null
    }

    private class FakeQueue(payload: GeneratedContentReportPayload) : PendingReportQueue {
        override val revision = MutableStateFlow(0L)
        var pending: PendingReport? = PendingReport(payload, PendingReportStatus.Pending, NOW, 0)

        override fun enqueue(payload: GeneratedContentReportPayload, nowEpochMillis: Long) = error("unused")
        override fun list(nowEpochMillis: Long) = listOfNotNull(pending)
        override fun markAttempt(reportId: String, nowEpochMillis: Long): PendingReport? = pending?.copy(
            status = PendingReportStatus.Sending,
            attemptCount = pending!!.attemptCount + 1,
            lastAttemptAtEpochMillis = nowEpochMillis,
        )?.also { pending = it }

        override fun markPermanentFailure(reportId: String): PendingReport? = pending?.copy(
            status = PendingReportStatus.PermanentFailure,
        )?.also { pending = it }

        override fun delete(reportId: String): Boolean = (pending?.payload?.reportId == reportId).also {
            if (it) pending = null
        }

        override fun deleteExpired(nowEpochMillis: Long) = 0
    }

    private companion object {
        const val REPORT_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val NOW = 1_700_000_000_000L
    }
}
