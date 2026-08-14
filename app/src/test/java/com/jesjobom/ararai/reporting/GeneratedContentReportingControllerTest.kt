package com.jesjobom.ararai.reporting

import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.InMemoryChatSessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneratedContentReportingControllerTest {
    private val store = InMemoryChatSessionStore()
    private val queue = RecordingQueue()
    private var scheduledReportId: String? = null
    private val controller = GeneratedContentReportingController(
        sessionStore = store,
        queue = queue,
        deliveryScheduler = ReportDeliveryScheduler { scheduledReportId = it },
        projector = GeneratedContentReportProjector { "stable-id" },
        nowEpochMillis = { 500L },
    )
    private val metadata = ReportTechnicalMetadata("v1", "en", "model", "runtime")

    @Test
    fun `latest draft targets completed assistant response`() {
        val session = store.createSession("Test")
        store.appendMessage(session.id, ChatRole.User, "question")
        store.appendMessage(session.id, ChatRole.Assistant, "answer")

        val draft = controller.latestDraft(session.id, metadata)

        assertEquals("answer", draft?.reportedResponse)
        assertEquals("stable-id", draft?.reportId)
    }

    @Test
    fun `latest draft is absent without eligible response`() {
        val session = store.createSession("Test")
        store.appendMessage(session.id, ChatRole.User, "question")

        assertNull(controller.latestDraft(session.id, metadata))
        assertEquals(false, controller.hasReportableResponse(session.id))
    }

    @Test
    fun `persisted assistant response stays reportable independently of transient voice state`() {
        val session = store.createSession("Voice")
        store.appendMessage(session.id, ChatRole.User, "spoken question")
        store.appendMessage(session.id, ChatRole.Assistant, "spoken answer")

        assertEquals(true, controller.hasReportableResponse(session.id))
    }

    @Test
    fun `submit queues only reviewed selection`() {
        val session = store.createSession("Test")
        val user = store.appendMessage(session.id, ChatRole.User, "question")
        store.appendMessage(session.id, ChatRole.Assistant, "answer")
        val draft = requireNotNull(controller.latestDraft(session.id, metadata))

        controller.submit(draft, ReportReason.Privacy, "comment", setOf(user.id))

        val payload = requireNotNull(queue.pending).payload
        assertEquals(ReportReason.Privacy, payload.reason)
        assertEquals(listOf("question"), payload.context.map { it.text })
        assertEquals(500L, payload.reportedAtEpochMillis)
        assertEquals("stable-id", scheduledReportId)
    }

    private class RecordingQueue : PendingReportQueue {
        var pending: PendingReport? = null
        override fun enqueue(
            payload: GeneratedContentReportPayload,
            nowEpochMillis: Long,
        ): PendingReport = PendingReport(
            payload,
            PendingReportStatus.Pending,
            nowEpochMillis,
            0,
        ).also { pending = it }

        override fun list(nowEpochMillis: Long) = listOfNotNull(pending)
        override fun markAttempt(reportId: String, nowEpochMillis: Long) = pending
        override fun markPermanentFailure(reportId: String) = pending
        override fun delete(reportId: String) = if (pending?.payload?.reportId == reportId) {
            pending = null
            true
        } else {
            false
        }
        override fun deleteExpired(nowEpochMillis: Long) = 0
    }
}
