package com.jesjobom.ararai.chat

import com.jesjobom.ararai.knowledge.WebSearchProvider
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelKnowledgeToolCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionPreferencesTest {
    @Test
    fun `advertises Wikipedia only when preference and model capability are both enabled`() {
        val supported =
            LocalModel(
                id = "supported",
                name = "Supported",
                filePath = "/tmp/model",
                knowledgeToolCapabilities =
                ModelKnowledgeToolCapabilities(setOf(WIKIPEDIA_SEARCH_TOOL_NAME)),
            )
        val unsupported = supported.copy(knowledgeToolCapabilities = ModelKnowledgeToolCapabilities())

        assertEquals(
            setOf(WIKIPEDIA_SEARCH_TOOL_NAME),
            eligibleKnowledgeToolNames(InstructionSettings(wikipediaEnabled = true), supported),
        )
        assertEquals(
            emptySet<String>(),
            eligibleKnowledgeToolNames(InstructionSettings(wikipediaEnabled = false), supported),
        )
        assertEquals(
            emptySet<String>(),
            eligibleKnowledgeToolNames(InstructionSettings(wikipediaEnabled = true), unsupported),
        )
        assertEquals(
            emptySet<String>(),
            eligibleKnowledgeToolNames(InstructionSettings(wikipediaEnabled = true), null),
        )
    }

    @Test
    fun `advertises experimental web search only for configured gate and capability`() {
        val supported =
            LocalModel(
                id = "supported",
                name = "Supported",
                filePath = "/tmp/model",
                knowledgeToolCapabilities =
                ModelKnowledgeToolCapabilities(setOf(WEB_SEARCH_TOOL_NAME)),
            )

        assertEquals(
            setOf(WEB_SEARCH_TOOL_NAME),
            eligibleKnowledgeToolNames(
                InstructionSettings(),
                supported,
                selectedWebProvider = WebSearchProvider.Tavily,
                experimentalWebSearchEnabled = true,
            ),
        )
        assertEquals(
            emptySet<String>(),
            eligibleKnowledgeToolNames(
                InstructionSettings(),
                supported,
                selectedWebProvider = WebSearchProvider.Tavily,
                experimentalWebSearchEnabled = false,
            ),
        )
        assertEquals(
            emptySet<String>(),
            eligibleKnowledgeToolNames(
                InstructionSettings(),
                supported,
                selectedWebProvider = null,
                experimentalWebSearchEnabled = true,
            ),
        )
    }

    @Test
    fun `uses independent checked in defaults`() {
        val preferences = InMemoryInstructionPreferences()

        assertEquals(InstructionDefaults.CHAT, preferences.settings.value.chatInstruction)
        assertEquals(InstructionDefaults.VOICE, preferences.settings.value.voiceInstruction)
        assertFalse(preferences.settings.value.wikipediaEnabled)
    }

    @Test
    fun `edits and restores only selected mode`() {
        val preferences = InMemoryInstructionPreferences()
        preferences.setInstruction(InteractionMode.Chat, "  Detailed answers.  ")
        preferences.setInstruction(InteractionMode.Voice, "Short answers.")

        preferences.restoreDefault(InteractionMode.Chat)

        assertEquals(InstructionDefaults.CHAT, preferences.settings.value.chatInstruction)
        assertEquals("Short answers.", preferences.settings.value.voiceInstruction)
    }

    @Test
    fun `effective instruction always retains app invariants`() {
        val settings = InstructionSettings(chatInstruction = "")

        val effective = effectiveSystemInstruction(settings, InteractionMode.Chat)

        assertEquals(InstructionDefaults.APP_INVARIANTS, effective)
        assertTrue(effective.contains("untrusted"))
    }

    @Test
    fun `turn settings select independent Chat and Voice instructions`() {
        val settings =
            InstructionSettings(
                chatInstruction = "Detailed written answer.",
                voiceInstruction = "Brief spoken answer.",
            )

        val temporalContext = TemporalContext("2026-07-29", "America/Toronto", "-04:00")
        val chat = conversationTurnSettings(settings, InteractionMode.Chat, temporalContext = temporalContext)
        val voice = conversationTurnSettings(settings, InteractionMode.Voice, temporalContext = temporalContext)

        assertTrue(chat.systemInstruction.contains("Detailed written answer."))
        assertTrue(voice.systemInstruction.contains("Brief spoken answer."))
        assertTrue(chat.systemInstruction.startsWith(InstructionDefaults.APP_INVARIANTS))
        assertTrue(voice.systemInstruction.startsWith(InstructionDefaults.APP_INVARIANTS))
        assertTrue(chat.systemInstruction.contains("synthesize the best available answer"))
        assertTrue(voice.systemInstruction.contains("all four digits"))
        assertTrue(chat.systemInstruction.contains("Current date: 2026-07-29"))
        assertTrue(voice.systemInstruction.contains("Timezone: America/Toronto (UTC-04:00)"))
    }

    @Test
    fun `turn settings normalize an extensible advertised skill set`() {
        val turn =
            conversationTurnSettings(
                settings = InstructionSettings(),
                mode = InteractionMode.Chat,
                advertisedToolNames = setOf(" wikipedia_search ", "", "calendar_lookup"),
            )

        assertEquals(setOf("calendar_lookup", "wikipedia_search"), turn.advertisedToolNames)
        assertTrue(turn.systemInstruction.contains("Use wikipedia_search"))
        assertTrue(turn.systemInstruction.contains("birth date"))
        assertTrue(turn.systemInstruction.contains("Do not use it for current news"))
        assertTrue(turn.systemInstruction.contains("use web_search for those when available"))
        assertTrue(turn.systemInstruction.contains("at most three calls"))
        assertTrue(turn.systemInstruction.contains("Search in English first"))
        assertTrue(turn.systemInstruction.contains("detect the language"))
    }

    @Test
    fun `does not add Wikipedia instruction when tool is not advertised`() {
        val turn =
            conversationTurnSettings(
                settings = InstructionSettings(wikipediaEnabled = true),
                mode = InteractionMode.Chat,
                advertisedToolNames = emptySet(),
            )

        assertFalse(turn.systemInstruction.contains("wikipedia_search"))
    }

    @Test
    fun `web search instruction identifies provider and final synthesis limit`() {
        val turn =
            conversationTurnSettings(
                settings = InstructionSettings(),
                mode = InteractionMode.Chat,
                advertisedToolNames = setOf(WEB_SEARCH_TOOL_NAME),
                webSearchProvider = WebSearchProvider.Exa,
            )

        assertTrue(turn.systemInstruction.contains("Use web_search through Exa"))
        assertTrue(turn.systemInstruction.contains("at most two calls"))
        assertTrue(turn.systemInstruction.contains("synthesize"))
    }

    @Test
    fun `bounds editable instructions`() {
        val preferences = InMemoryInstructionPreferences()
        preferences.setInstruction(InteractionMode.Chat, "x".repeat(InstructionDefaults.MAX_LENGTH + 50))

        assertEquals(InstructionDefaults.MAX_LENGTH, preferences.settings.value.chatInstruction.length)
    }
}
