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
        val identifier = FakeLanguageIdentifier()
        val states = mutableListOf<ChatTextToSpeechState>()
        val controller = ChatTextToSpeechController(service, identifier, states::add)
        val message = ChatMessage(
            role = ChatRole.Assistant,
            id = "message-1",
            content = MessageContent.TextPrompt(
                text = "Visible response",
                reasoningText = "Private reasoning",
            ),
        )

        controller.prepare(message.id, message.text)
        assertFalse(controller.isPrepared(message.id))
        controller.toggle(message.id, message.text)
        assertTrue(service.spokenRequests.isEmpty())

        identifier.complete(languageTag = "en")
        assertTrue(controller.isPrepared(message.id))
        controller.toggle(message.id, message.text)

        assertEquals(listOf(SpeechRequest("Visible response", "en")), service.spokenRequests)
        assertEquals("message-1", controller.state.activeMessageId)
        service.complete()
        assertNull(controller.state.activeMessageId)
        assertFalse(service.spokenRequests.single().text.contains("Private reasoning"))
    }

    @Test
    fun replacementStopsPreviousSpeechAndIgnoresItsLateCallback() {
        val service = FakeTextToSpeechService()
        val identifier = FakeLanguageIdentifier()
        val controller = ChatTextToSpeechController(service, identifier) {}

        controller.prepare("first", "First response")
        identifier.complete("en")
        controller.prepare("second", "Second response")
        identifier.complete("fr")

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
        val identifier = FakeLanguageIdentifier()
        val controller = ChatTextToSpeechController(service, identifier) {}

        controller.prepare("message", "Response")
        identifier.complete(null)

        controller.toggle("message", "Response")
        service.fail("TTS unavailable")
        assertEquals("TTS unavailable", controller.state.error)

        controller.toggle("message", "Response")
        controller.stop()
        assertNull(controller.state.activeMessageId)

        controller.toggle("message", "Response")
        controller.close()
        assertTrue(service.closed)
        assertTrue(identifier.closed)
        assertNull(controller.state.activeMessageId)
    }

    @Test
    fun changedTextIgnoresStaleDetectionAndFallbackStillBecomesPrepared() {
        val service = FakeTextToSpeechService()
        val identifier = FakeLanguageIdentifier()
        val controller = ChatTextToSpeechController(service, identifier) {}

        controller.prepare("message", "Old response")
        val staleListener = identifier.requests.single().listener
        controller.prepare("message", "New response")
        staleListener.onIdentified("en")

        assertFalse(controller.isPrepared("message"))
        identifier.requests.last().listener.onIdentified(null)
        assertTrue(controller.isPrepared("message"))

        controller.toggle("message", "New response")
        assertEquals(SpeechRequest("New response", null), service.spokenRequests.single())
    }

    private class FakeTextToSpeechService : ChatTextToSpeechService {
        val spokenRequests = mutableListOf<SpeechRequest>()
        var listener: ChatTextToSpeechListener? = null
        var stopCount = 0
        var closed = false

        override fun speak(text: String, languageTag: String?, listener: ChatTextToSpeechListener) {
            spokenRequests += SpeechRequest(text, languageTag)
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

    private class FakeLanguageIdentifier : ChatLanguageIdentifier {
        val requests = mutableListOf<IdentificationRequest>()
        var closed = false

        override fun identify(text: String, listener: ChatLanguageIdentificationListener) {
            requests += IdentificationRequest(text, listener)
        }

        override fun close() {
            closed = true
        }

        fun complete(languageTag: String?) = requests.last().listener.onIdentified(languageTag)
    }

    private data class SpeechRequest(val text: String, val languageTag: String?)

    private data class IdentificationRequest(
        val text: String,
        val listener: ChatLanguageIdentificationListener,
    )
}
