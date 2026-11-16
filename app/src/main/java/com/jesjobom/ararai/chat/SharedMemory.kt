package com.jesjobom.ararai.chat

import com.google.gson.JsonParser

data class MemoryEntry(val id: String, val text: String)

sealed interface MemoryWriteResult {
    data class Saved(val entry: MemoryEntry, val evicted: List<MemoryEntry>) : MemoryWriteResult
    data object NotFound : MemoryWriteResult
    data object TooLarge : MemoryWriteResult
}

fun InstructionSettings.memoryCapacity(): Int {
    val promptLength = maxOf(chatInstruction.length, voiceInstruction.length)
    return (InstructionDefaults.MAX_LENGTH - promptLength).coerceAtLeast(0)
}

fun memoryEvictionsForPrompt(settings: InstructionSettings, mode: InteractionMode, value: String): List<MemoryEntry> {
    val normalized = normalizeEditableInstruction(value)
    val capacity = when (mode) {
        InteractionMode.Chat ->
            InstructionDefaults.MAX_LENGTH - maxOf(normalized.length, settings.voiceInstruction.length)
        InteractionMode.Voice ->
            InstructionDefaults.MAX_LENGTH - maxOf(settings.chatInstruction.length, normalized.length)
    }
    val remaining = settings.memory.toMutableList()
    val evicted = mutableListOf<MemoryEntry>()
    while (remaining.sumOf { it.text.length } > capacity) evicted += remaining.removeAt(0)
    return evicted
}

internal fun List<MemoryEntry>.fitToBudget(capacity: Int): List<MemoryEntry> = toMutableList().apply {
    while (sumOf { it.text.length } > capacity) removeAt(0)
}

internal fun normalizeMemoryText(value: String): String? = value.trim().takeIf {
    it.isNotEmpty() && it.length <= InstructionDefaults.MAX_MEMORY_ENTRY_LENGTH &&
        it.none { char -> char.isISOControl() && char != '\n' }
}

internal fun loadMemory(raw: String?): List<MemoryEntry> = runCatching {
    JsonParser.parseString(raw ?: "[]").asJsonArray.mapNotNull { item ->
        val json = item.asJsonObject
        val id = json.get("id")?.asString ?: return@mapNotNull null
        val text = json.get("text")?.asString?.let(::normalizeMemoryText) ?: return@mapNotNull null
        MemoryEntry(id, text)
    }
}.getOrDefault(emptyList())
