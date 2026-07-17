package com.jesjobom.ararai.ui

import com.jesjobom.ararai.chat.ChatMessage
import com.jesjobom.ararai.chat.ChatRole
import com.jesjobom.ararai.chat.MessageContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTextToSpeechTest {
    @Test
    fun eligibilityRequiresCompletedNonBlankAssistantText() {
        val completed = ChatMessage(ChatRole.Assistant, "Response", id = "assistant")

        assertTrue(completed.isEligibleForTextToSpeech(isStreaming = false))
        assertFalse(completed.isEligibleForTextToSpeech(isStreaming = true))
        assertFalse(ChatMessage(ChatRole.Assistant, "  ").isEligibleForTextToSpeech(false))
        assertFalse(ChatMessage(ChatRole.User, "Prompt").isEligibleForTextToSpeech(false))
    }

    @Test
    fun speaksOnlyResponseTextAndCompletesActiveMessage() {
        val service = FakeTextToSpeechService()
        val states = mutableListOf<ChatTextToSpeechState>()
        val controller = ChatTextToSpeechController(service, states::add)
        val message = ChatMessage(
            role = ChatRole.Assistant,
            id = "message-1",
            content = MessageContent.TextPrompt(
                text = "Visible response",
                reasoningText = "Private reasoning",
            ),
        )

        controller.toggle(message.id, message.text)

        assertEquals(listOf("Visible response"), service.spokenTexts)
        assertEquals("message-1", controller.state.activeMessageId)
        service.complete()
        assertNull(controller.state.activeMessageId)
        assertFalse(service.spokenTexts.single().contains("Private reasoning"))
    }

    @Test
    fun replacementStopsPreviousSpeechAndIgnoresItsLateCallback() {
        val service = FakeTextToSpeechService()
        val controller = ChatTextToSpeechController(service) {}

        controller.toggle("first", "First response")
        val staleListener = service.listener
        controller.toggle("second", "Second response")
        staleListener?.onResult(ChatTextToSpeechResult.Completed)

        assertEquals("second", controller.state.activeMessageId)
        assertEquals(2, service.stopCount)
        service.complete()
        assertNull(controller.state.activeMessageId)
    }

    @Test
    fun stopErrorAndCloseClearPlaybackSafely() {
        val service = FakeTextToSpeechService()
        val controller = ChatTextToSpeechController(service) {}

        controller.toggle("message", "Response")
        service.fail("TTS unavailable")
        assertEquals("TTS unavailable", controller.state.error)

        controller.toggle("message", "Response")
        controller.stop()
        assertNull(controller.state.activeMessageId)

        controller.toggle("message", "Response")
        controller.close()
        assertTrue(service.closed)
        assertNull(controller.state.activeMessageId)
    }

    private class FakeTextToSpeechService : ChatTextToSpeechService {
        val spokenTexts = mutableListOf<String>()
        var listener: ChatTextToSpeechListener? = null
        var stopCount = 0
        var closed = false

        override fun speak(text: String, listener: ChatTextToSpeechListener) {
            spokenTexts += text
            this.listener = listener
        }

        override fun stop() {
            stopCount++
        }

        override fun close() {
            closed = true
        }

        fun complete() = listener?.onResult(ChatTextToSpeechResult.Completed) ?: Unit

        fun fail(message: String) =
            listener?.onResult(ChatTextToSpeechResult.Failed(message)) ?: Unit
    }
}
