package com.jesjobom.ararai.voice

import com.jesjobom.ararai.ui.ChatLanguageIdentifier
import com.jesjobom.ararai.ui.ChatTextToSpeechResult
import com.jesjobom.ararai.ui.ChatTextToSpeechService
import com.jesjobom.ararai.ui.UserMessageKey
import java.io.Closeable
import java.util.ArrayDeque

internal interface VoiceSpeechQueue : Closeable {
    fun enqueue(segment: VoiceSpeechSegment)
    fun markGenerationComplete()
    fun stop()
}

@Suppress("LongParameterList")
internal class SequentialVoiceSpeechQueue(
    private val speech: ChatTextToSpeechService,
    private val languageIdentifier: ChatLanguageIdentifier,
    private val speechRate: () -> Float = { 1.0f },
    private val onSpeechStarted: (IntRange) -> Unit,
    private val onSpeechRange: (IntRange) -> Unit,
    private val onQueueComplete: () -> Unit,
    private val onError: (UserMessageKey) -> Unit,
) : VoiceSpeechQueue {
    private val pending = ArrayDeque<VoiceSpeechSegment>()
    private var busy = false
    private var generationComplete = false
    private var generation = 0L
    private var responseLanguageResolved = false
    private var responseLanguageTag: String? = null

    override fun enqueue(segment: VoiceSpeechSegment) {
        if (segment.speechText.isBlank()) return
        pending += segment
        drain()
    }

    override fun markGenerationComplete() {
        generationComplete = true
        if (!busy && pending.isEmpty()) completeQueue()
    }

    private fun drain() {
        if (busy || pending.isEmpty()) return
        busy = true
        val segment = pending.removeFirst()
        val current = generation
        if (responseLanguageResolved) {
            speak(segment, responseLanguageTag, current)
            return
        }
        languageIdentifier.identify(segment.speechText) { language ->
            if (current != generation) return@identify
            responseLanguageResolved = true
            responseLanguageTag = language
            speak(segment, language, current)
        }
    }

    private fun speak(segment: VoiceSpeechSegment, languageTag: String?, current: Long) {
        onSpeechStarted(segment.sourceRange)
        var lastSourceEnd = 0
        speech.speak(
            segment.speechText,
            languageTag,
            speechRate(),
            object : com.jesjobom.ararai.ui.ChatTextToSpeechListener {
                override fun onRangeStart(start: Int, endExclusive: Int) {
                    if (current != generation) return
                    segment.mapSpeechRange(start, endExclusive, lastSourceEnd)?.let { mapped ->
                        lastSourceEnd = mapped.last - segment.sourceStart + 1
                        onSpeechRange(mapped)
                    }
                }

                override fun onResult(result: ChatTextToSpeechResult) {
                    if (current != generation) return
                    busy = false
                    when (result) {
                        ChatTextToSpeechResult.Completed -> {
                            if (pending.isNotEmpty()) {
                                drain()
                            } else if (generationComplete) {
                                completeQueue()
                            }
                        }
                        is ChatTextToSpeechResult.Failed -> onError(result.messageKey)
                    }
                }
            },
        )
    }

    private fun completeQueue() {
        generationComplete = false
        responseLanguageResolved = false
        responseLanguageTag = null
        onQueueComplete()
    }

    override fun stop() {
        generation++
        pending.clear()
        busy = false
        generationComplete = false
        responseLanguageResolved = false
        responseLanguageTag = null
        speech.stop()
    }

    override fun close() {
        stop()
        languageIdentifier.close()
        speech.close()
    }
}

@Suppress("ReturnCount")
private fun VoiceSpeechSegment.mapSpeechRange(
    start: Int,
    endExclusive: Int,
    sourceSearchStart: Int,
): IntRange? {
    if (start !in speechText.indices || endExclusive <= start) return null
    val spoken = speechText.substring(start, endExclusive.coerceAtMost(speechText.length)).trim()
    if (spoken.isBlank()) return null
    val localStart = sourceText.indexOf(spoken, startIndex = sourceSearchStart, ignoreCase = true)
        .takeIf { it >= 0 }
        ?: sourceText.indexOf(spoken, ignoreCase = true).takeIf { it >= 0 }
        ?: return null
    return (sourceStart + localStart) until (sourceStart + localStart + spoken.length)
}
