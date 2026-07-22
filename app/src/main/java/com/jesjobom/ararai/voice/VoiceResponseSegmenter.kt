@file:Suppress("ReturnCount")

package com.jesjobom.ararai.voice

internal data class VoiceSpeechSegment(
    val sourceText: String,
    val speechText: String,
    val sourceStart: Int,
) {
    val sourceRange: IntRange get() = sourceStart until (sourceStart + sourceText.length)
}

internal class VoiceResponseSegmenter(private val minimumWords: Int) {
    private var consumed = 0

    fun append(cumulativeText: String): List<VoiceSpeechSegment> {
        if (cumulativeText.length <= consumed) return emptyList()
        val segments = mutableListOf<VoiceSpeechSegment>()
        while (consumed < cumulativeText.length) {
            val pending = cumulativeText.substring(consumed)
            val boundary = pending.streamingBoundary() ?: break
            val sourceStart = consumed
            val sourceText = pending.substring(0, boundary)
            consumed += boundary
            sourceText.toSpeechSegment(sourceStart)?.let(segments::add)
        }
        return segments
    }

    fun complete(cumulativeText: String): List<VoiceSpeechSegment> {
        val result = append(cumulativeText).toMutableList()
        if (consumed < cumulativeText.length) {
            cumulativeText.substring(consumed).toSpeechSegment(consumed)?.let(result::add)
            consumed = cumulativeText.length
        }
        return result
    }

    private fun String.streamingBoundary(): Int? {
        naturalBoundary()?.let { return it }
        if (length < MAX_SOURCE_CHARS) return null
        return hardLimitBoundary()
    }

    private fun String.naturalBoundary(): Int? {
        forEachIndexed { index, character ->
            if (character.isSentenceBoundaryAt(this, index)) {
                val boundary = boundaryEnd(index)
                if (boundary <= MAX_SOURCE_CHARS && substring(0, boundary).wordCount() >= minimumWords) {
                    return boundary
                }
            }
        }
        return null
    }

    private fun String.hardLimitBoundary(): Int {
        val safePrefix = substring(0, MAX_SOURCE_CHARS)
        val clause = safePrefix.indexOfLast { it == ';' || it == ':' || it == ',' }
        if (clause >= MAX_SOURCE_CHARS / 2) return safePrefix.boundaryEnd(clause)
        return safePrefix.indexOfLast(Char::isWhitespace).takeIf { it > 0 }?.plus(1) ?: MAX_SOURCE_CHARS
    }

    private fun String.boundaryEnd(boundaryIndex: Int): Int {
        var end = boundaryIndex + 1
        while (end < length && this[end] in CLOSING_PUNCTUATION) end++
        return end
    }

    private companion object {
        const val MAX_SOURCE_CHARS = 500
        val CLOSING_PUNCTUATION = setOf('"', '\'', ')', ']', '}', '”', '’')
    }
}

private fun Char.isSentenceBoundaryAt(
    text: String,
    index: Int,
): Boolean {
    if (this == '\n') return true
    if (this != '.' && this != '!' && this != '?') return false
    val next = text.getOrNull(index + 1) ?: return true
    return next.isWhitespace() || next in setOf('"', '\'', ')', ']', '}', '”', '’')
}

@Suppress("MaxLineLength")
private fun String.toSpeechSegment(sourceStart: Int): VoiceSpeechSegment? = normalizeForSpeech().takeIf(String::isNotBlank)?.let { speechText ->
    VoiceSpeechSegment(sourceText = this, speechText = speechText, sourceStart = sourceStart)
}

internal fun String.normalizeForSpeech(): String = replace(Regex("```[\\s\\S]*?```"), " code block ")
    .replace(Regex("[*_#>`~]+"), " ")
    .replace(Regex("\\s+"), " ")
    .replace(Regex("\\s+([.!?,;:])"), "$1")
    .trim()

private fun String.wordCount(): Int = trim().split(Regex("\\s+")).count(String::isNotBlank)
