package com.jesjobom.ararai.reporting

import com.jesjobom.ararai.chat.AssistantCompletionStatus
import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.chat.AudioTranscriptionStatus
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.ImageAttachment
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.chat.StoredChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedContentReportProjectorTest {
    private val projector = GeneratedContentReportProjector { "report-id" }
    private val metadata = ReportTechnicalMetadata("1.0", "pt-BR", "gemma", "litert-lm")

    @Test
    fun `only completed nonblank assistant text is reportable`() {
        assertTrue(projector.isReportable(message("answer", ChatRole.Assistant, text("result"))))
        assertFalse(projector.isReportable(message("user", ChatRole.User, text("result"))))
        assertFalse(projector.isReportable(message("blank", ChatRole.Assistant, text("  "))))
        assertFalse(
            projector.isReportable(
                message(
                    "streaming",
                    ChatRole.Assistant,
                    text("partial", AssistantCompletionStatus.Incomplete),
                ),
            ),
        )
    }

    @Test
    fun `draft contains exact response and bounded textual context without reasoning`() {
        val messages = (1..6).map { index ->
            val role = if (index % 2 == 0) ChatRole.Assistant else ChatRole.User
            message("m$index", role, text("text $index", reasoning = "secret $index"))
        } + message("target", ChatRole.Assistant, text("reported", reasoning = "hidden reasoning"))

        val draft = projector.createDraft(messages, "target", metadata)

        assertEquals("reported", draft.reportedResponse)
        assertEquals(listOf("m3", "m4", "m5", "m6"), draft.availableContext.map { it.messageId })
        assertEquals(setOf("m5"), draft.initiallySelectedContextIds)
        assertTrue(draft.availableContext.none { "secret" in it.text })
    }

    @Test
    fun `audio and image become presence flags while raw media is excluded`() {
        val audio = MessageContent.AudioPromptContent(
            audio = AudioPrompt("file:///private/audio.wav", "audio/wav", "secret.wav"),
            imageAttachments = listOf(ImageAttachment("file:///private/photo.jpg", "image/jpeg", "secret.jpg")),
            transcript = "spoken context",
            transcriptionStatus = AudioTranscriptionStatus.Completed,
        )
        val draft = projector.createDraft(
            listOf(message("user", ChatRole.User, audio), message("answer", ChatRole.Assistant, text("response"))),
            "answer",
            metadata,
        )

        assertEquals(ReportMediaPresence(image = true, audio = true, transcript = true), draft.mediaPresence)
        assertEquals("spoken context", draft.availableContext.single().text)
        assertFalse(draft.toString().contains("file:///"))
        assertFalse(draft.toString().contains("secret.wav"))
    }

    @Test
    fun `media flags only describe the reported turn`() {
        val oldImage = MessageContent.TextPrompt(
            text = "old prompt",
            imageAttachments = listOf(ImageAttachment("file:///old.jpg", "image/jpeg")),
        )
        val draft = projector.createDraft(
            listOf(
                message("old-user", ChatRole.User, oldImage),
                message("old-answer", ChatRole.Assistant, text("old response")),
                message("current-user", ChatRole.User, text("current prompt")),
                message("answer", ChatRole.Assistant, text("response")),
            ),
            "answer",
            metadata,
        )

        assertEquals(ReportMediaPresence(image = false, audio = false, transcript = false), draft.mediaPresence)
    }

    @Test
    fun `payload includes only explicitly selected context and keeps stable id`() {
        val messages = listOf(
            message("u1", ChatRole.User, text("first")),
            message("a1", ChatRole.Assistant, text("older")),
            message("u2", ChatRole.User, text("immediate")),
            message("answer", ChatRole.Assistant, text("response")),
        )
        val draft = projector.createDraft(messages, "answer", metadata)

        val payload = projector.buildPayload(draft, ReportReason.Other, " comment ", setOf("u1"), 123L)

        assertEquals("report-id", payload.reportId)
        assertEquals("comment", payload.comment)
        assertEquals(listOf("u1"), payload.context.map { it.messageId })
        assertEquals(123L, payload.reportedAtEpochMillis)
    }

    private fun message(id: String, role: ChatRole, content: MessageContent): StoredChatMessage = StoredChatMessage(
        id,
        "session",
        role,
        content,
        id.removePrefix("m").toLongOrNull() ?: 100L,
    )

    private fun text(
        value: String,
        status: AssistantCompletionStatus = AssistantCompletionStatus.Complete,
        reasoning: String = "",
    ) = MessageContent.TextPrompt(value, reasoningText = reasoning, completionStatus = status)
}
