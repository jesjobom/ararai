package com.jesjobom.ararai.voice

import com.jesjobom.ararai.ui.ChatLanguageIdentificationListener
import com.jesjobom.ararai.ui.ChatLanguageIdentifier
import com.jesjobom.ararai.ui.ChatTextToSpeechListener
import com.jesjobom.ararai.ui.ChatTextToSpeechResult
import com.jesjobom.ararai.ui.ChatTextToSpeechService
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSpeechQueueTest {
    @Test
    fun `plays queued segments sequentially`() {
        val speech = FakeSpeechService()
        var completed = 0
        val queue =
            SequentialVoiceSpeechQueue(
                speech = speech,
                languageIdentifier = ImmediateIdentifier,
                onSpeechStarted = {},
                onSpeechRange = {},
                onQueueComplete = { completed++ },
                onError = {},
            )
        queue.enqueue(segment("first", 0))
        queue.enqueue(segment("second", 6))
        queue.markGenerationComplete()

        assertEquals(listOf("first"), speech.started)
        speech.complete()
        assertEquals(listOf("first", "second"), speech.started)
        speech.complete()
        assertEquals(1, completed)
    }

    @Test
    fun `stop invalidates active callback and queued segments`() {
        val speech = FakeSpeechService()
        var completed = 0
        val queue =
            SequentialVoiceSpeechQueue(
                speech = speech,
                languageIdentifier = ImmediateIdentifier,
                onSpeechStarted = {},
                onSpeechRange = {},
                onQueueComplete = { completed++ },
                onError = {},
            )
        queue.enqueue(segment("first", 0))
        queue.enqueue(segment("stale", 6))
        queue.markGenerationComplete()
        queue.stop()
        speech.complete()

        assertEquals(listOf("first"), speech.started)
        assertEquals(0, completed)
    }

    @Test
    fun `uses first segment language for the whole response`() {
        val speech = FakeSpeechService()
        val identifier = SequenceIdentifier("pt", "en")
        val queue =
            SequentialVoiceSpeechQueue(
                speech = speech,
                languageIdentifier = identifier,
                onSpeechStarted = {},
                onSpeechRange = {},
                onQueueComplete = {},
                onError = {},
            )

        queue.enqueue(segment("Uma resposta em português.", 0))
        queue.enqueue(segment("Kotlin Spring PostgreSQL.", 28))
        queue.markGenerationComplete()
        speech.complete()
        speech.complete()

        assertEquals(listOf("Uma resposta em português."), identifier.identifiedTexts)
        assertEquals(listOf("pt", "pt"), speech.languages)
    }

    @Test
    fun `identifies language again for the next response`() {
        val speech = FakeSpeechService()
        val identifier = SequenceIdentifier("pt", "en")
        var completed = 0
        val queue =
            SequentialVoiceSpeechQueue(
                speech = speech,
                languageIdentifier = identifier,
                onSpeechStarted = {},
                onSpeechRange = {},
                onQueueComplete = { completed++ },
                onError = {},
            )

        queue.enqueue(segment("Primeiro turno", 0))
        queue.markGenerationComplete()
        speech.complete()
        queue.enqueue(segment("Second turn", 14))
        speech.complete()
        assertEquals(1, completed)
        queue.markGenerationComplete()

        assertEquals(listOf("Primeiro turno", "Second turn"), identifier.identifiedTexts)
        assertEquals(listOf("pt", "en"), speech.languages)
        assertEquals(2, completed)
    }

    @Test
    fun `maps native speech ranges back to the generated response`() {
        val speech = FakeSpeechService()
        val ranges = mutableListOf<IntRange>()
        val queue =
            SequentialVoiceSpeechQueue(
                speech = speech,
                languageIdentifier = ImmediateIdentifier,
                onSpeechStarted = ranges::add,
                onSpeechRange = ranges::add,
                onQueueComplete = {},
                onError = {},
            )

        queue.enqueue(VoiceSpeechSegment("**hello** world", "hello world", 10))
        speech.range(0, 5)
        speech.range(6, 11)

        assertEquals(listOf(10..24, 12..16, 20..24), ranges)
    }

    @Test
    fun `applies selected speech rate to every segment`() {
        val speech = FakeSpeechService()
        val queue =
            SequentialVoiceSpeechQueue(
                speech = speech,
                languageIdentifier = ImmediateIdentifier,
                speechRate = { 2.0f },
                onSpeechStarted = {},
                onSpeechRange = {},
                onQueueComplete = {},
                onError = {},
            )

        queue.enqueue(segment("first", 0))
        queue.enqueue(segment("second", 6))
        speech.complete()

        assertEquals(listOf(2.0f, 2.0f), speech.rates)
    }
}

private fun segment(text: String, sourceStart: Int) = VoiceSpeechSegment(text, text, sourceStart)

private object ImmediateIdentifier : ChatLanguageIdentifier {
    override fun identify(text: String, listener: ChatLanguageIdentificationListener) = listener.onIdentified("en")
    override fun close() = Unit
}

private class SequenceIdentifier(vararg languages: String?) : ChatLanguageIdentifier {
    private val pendingLanguages = ArrayDeque(languages.toList())
    val identifiedTexts = mutableListOf<String>()

    override fun identify(text: String, listener: ChatLanguageIdentificationListener) {
        identifiedTexts += text
        listener.onIdentified(pendingLanguages.removeFirst())
    }

    override fun close() = Unit
}

private class FakeSpeechService : ChatTextToSpeechService {
    val started = mutableListOf<String>()
    val languages = mutableListOf<String?>()
    val rates = mutableListOf<Float>()
    private var listener: ChatTextToSpeechListener? = null

    override fun speak(
        text: String,
        languageTag: String?,
        speechRate: Float,
        listener: ChatTextToSpeechListener,
    ) {
        started += text
        languages += languageTag
        rates += speechRate
        this.listener = listener
    }

    fun complete() {
        val current = listener
        listener = null
        current?.onResult(ChatTextToSpeechResult.Completed)
    }

    fun range(start: Int, endExclusive: Int) {
        listener?.onRangeStart(start, endExclusive)
    }

    override fun stop() = Unit
    override fun close() = Unit
}
