package com.jesjobom.ararai.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceResponseSegmenterTest {
    @Test fun `emits ordered text after minimum words`() {
        val segmenter = VoiceResponseSegmenter(4)
        assertEquals(emptyList<VoiceSpeechSegment>(), segmenter.append("one two"))
        assertEquals(listOf("one two three four."), segmenter.append("one two three four. next").map { it.speechText })
        assertEquals(listOf("next"), segmenter.complete("one two three four. next").map { it.speechText })
    }

    @Test fun `flushes residual below minimum`() {
        val segmenter = VoiceResponseSegmenter(12)
        assertEquals(listOf("short answer"), segmenter.complete("short answer").map { it.speechText })
    }

    @Test fun `does not split an unfinished sentence after minimum words`() {
        val segmenter = VoiceResponseSegmenter(4)

        assertEquals(emptyList<VoiceSpeechSegment>(), segmenter.append("one two three four five six"))
        assertEquals(
            listOf("one two three four five six."),
            segmenter.append("one two three four five six.").map { it.speechText },
        )
    }

    @Test fun `uses line breaks as natural speech boundaries`() {
        val segmenter = VoiceResponseSegmenter(3)

        assertEquals(
            listOf("first line has words"),
            segmenter.append("first line has words\nsecond line").map { it.speechText },
        )
    }

    @Test fun `groups short sentences until minimum words are available`() {
        val segmenter = VoiceResponseSegmenter(5)

        assertEquals(
            listOf("Hi. This sentence reaches five words."),
            segmenter.append("Hi. This sentence reaches five words. More").map { it.speechText },
        )
    }

    @Test fun `emits every eligible natural segment already streamed`() {
        val segmenter = VoiceResponseSegmenter(2)

        assertEquals(
            listOf("First sentence.", "Second sentence!"),
            segmenter.append("First sentence. Second sentence!").map { it.speechText },
        )
    }

    @Test fun `falls back to a word boundary before the safe TTS limit`() {
        val segmenter = VoiceResponseSegmenter(2)
        val unfinished = "word ".repeat(120)

        val segment = segmenter.append(unfinished).single()

        assertEquals(500, segment.sourceText.length)
        assertEquals(true, segment.sourceText.endsWith(" "))
    }

    @Test fun `prefers clause punctuation for an exceptionally long sentence`() {
        val segmenter = VoiceResponseSegmenter(2)
        val prefix = "word ".repeat(60) + ","
        val unfinished = prefix + " tail".repeat(60)

        val segment = segmenter.append(unfinished).first()

        assertEquals(prefix, segment.sourceText)
    }

    @Test fun `keeps repeated punctuation together at natural boundaries`() {
        val segmenter = VoiceResponseSegmenter(1)

        assertEquals(
            listOf("Wait...", "Really!!!", "What?!"),
            segmenter.append("Wait... Really!!! What?!").map { it.speechText },
        )
    }

    @Test fun `keeps repeated punctuation before a closing quote`() {
        val segmenter = VoiceResponseSegmenter(1)

        assertEquals(
            listOf("She said \"Stop!!!\"", "Then silence..."),
            segmenter.append("She said \"Stop!!!\" Then silence...").map { it.speechText },
        )
    }

    @Test fun `normalizes markup`() {
        val segmenter = VoiceResponseSegmenter(2)
        val segment = segmenter.complete("**hello** _world_.").single()
        assertEquals("hello world.", segment.speechText)
        assertEquals("**hello** _world_.", segment.sourceText)
        assertEquals(0, segment.sourceStart)
    }
}
