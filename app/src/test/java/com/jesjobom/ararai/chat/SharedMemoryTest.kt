package com.jesjobom.ararai.chat

import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolDispatchResult
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolInvocation
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.tools.memoryWriteApplicationTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMemoryTest {
    @Test
    fun `memory enters both modes and survives session-independent prompt construction`() {
        val preferences = InMemoryInstructionPreferences()
        val saved = preferences.addMemory("Prefers Portuguese")
        assertTrue(saved is MemoryWriteResult.Saved)
        val chat = conversationTurnSettings(preferences.settings.value, InteractionMode.Chat)
        val voice = conversationTurnSettings(preferences.settings.value, InteractionMode.Voice)
        assertTrue(chat.systemInstruction.contains("Prefers Portuguese"))
        assertTrue(voice.systemInstruction.contains("Prefers Portuguese"))
    }

    @Test
    fun `oldest whole entries rotate and prompts remain intact`() {
        val preferences = InMemoryInstructionPreferences()
        preferences.setInstruction(InteractionMode.Chat, "p".repeat(1_400))
        val oldest = preferences.addMemory("a".repeat(300)) as MemoryWriteResult.Saved
        preferences.addMemory("b".repeat(300))
        val latest = preferences.addMemory("c".repeat(300)) as MemoryWriteResult.Saved
        assertEquals(listOf(oldest.entry), latest.evicted)
        assertEquals(listOf("b".repeat(300), "c".repeat(300)), preferences.settings.value.memory.map { it.text })
        assertEquals("p".repeat(1_400), preferences.settings.value.chatInstruction)
        assertEquals(MemoryWriteResult.TooLarge, preferences.addMemory("x".repeat(501)))
    }

    @Test
    fun `prompt edit previews eviction and never rotates prompt`() {
        val preferences = InMemoryInstructionPreferences()
        val first = (preferences.addMemory("first") as MemoryWriteResult.Saved).entry
        preferences.addMemory("second")
        val nextPrompt = "p".repeat(1_990)
        assertEquals(
            listOf(first),
            memoryEvictionsForPrompt(preferences.settings.value, InteractionMode.Chat, nextPrompt),
        )
        preferences.setInstruction(InteractionMode.Chat, nextPrompt)
        assertEquals(nextPrompt, preferences.settings.value.chatInstruction)
        assertEquals(listOf("second"), preferences.settings.value.memory.map { it.text })
        assertEquals(10, preferences.settings.value.memoryCapacity())
    }

    @Test
    fun `edit and delete keep stable entry identity`() {
        val preferences = InMemoryInstructionPreferences()
        val original = (preferences.addMemory("old") as MemoryWriteResult.Saved).entry
        val edited = preferences.editMemory(original.id, "new") as MemoryWriteResult.Saved
        assertEquals(original.id, edited.entry.id)
        assertEquals("new", preferences.settings.value.memory.single().text)
        preferences.deleteMemory(original.id)
        assertTrue(preferences.settings.value.memory.isEmpty())
    }

    @Test
    fun `registry allows verified model write but never widget write`() = runBlocking {
        val preferences = InMemoryInstructionPreferences()
        val registry = ApplicationToolRegistry(listOf(memoryWriteApplicationTool(preferences)))
        assertFalse(MEMORY_WRITE_TOOL_NAME in registry.availableToolIds(ApplicationToolConsumer.Widget))
        val dispatcher = ApplicationToolDispatcher(registry)
        val invocation = ApplicationToolInvocation(
            id = MEMORY_WRITE_TOOL_NAME,
            version = 1,
            consumer = ApplicationToolConsumer.Model,
            argumentsJson = """{"text":"Remembered fact"}""",
            verifiedModelToolIds = setOf(MEMORY_WRITE_TOOL_NAME),
        )
        val result = dispatcher.execute(invocation)
        assertTrue(result is ApplicationToolDispatchResult.Executed)
        assertEquals("Remembered fact", preferences.settings.value.memory.single().text)
        preferences.setMemoryWriteEnabled(false)
        assertFalse(MEMORY_WRITE_TOOL_NAME in registry.availableToolIds(ApplicationToolConsumer.Model))
        val disabledResult = dispatcher.execute(invocation)
        assertEquals(
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.Disabled),
            disabledResult,
        )
        assertTrue(
            conversationTurnSettings(preferences.settings.value, InteractionMode.Chat)
                .systemInstruction.contains("Remembered fact"),
        )
        val widgetResult = dispatcher.execute(invocation.copy(consumer = ApplicationToolConsumer.Widget))
        assertTrue(widgetResult is ApplicationToolDispatchResult.Rejected)
        assertEquals(1, preferences.settings.value.memory.size)
    }
}
