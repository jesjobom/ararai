package com.jesjobom.ararai.chat

import com.jesjobom.ararai.knowledge.WebSearchProvider
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelToolCapabilities
import com.jesjobom.ararai.tools.WIKIPEDIA_ON_THIS_DAY_TOOL_NAME
import com.jesjobom.ararai.tools.WIKIPEDIA_PAGES_TOOL_NAME
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
                toolCapabilities =
                ModelToolCapabilities(setOf(WIKIPEDIA_PAGES_TOOL_NAME, WIKIPEDIA_ON_THIS_DAY_TOOL_NAME)),
            )
        val unsupported = supported.copy(toolCapabilities = ModelToolCapabilities())

        assertEquals(
            setOf(WIKIPEDIA_PAGES_TOOL_NAME, WIKIPEDIA_ON_THIS_DAY_TOOL_NAME),
            eligibleToolNames(InstructionSettings(wikipediaEnabled = true), supported),
        )
        assertEquals(
            emptySet<String>(),
            eligibleToolNames(InstructionSettings(wikipediaEnabled = false), supported),
        )
        assertEquals(
            emptySet<String>(),
            eligibleToolNames(InstructionSettings(wikipediaEnabled = true), unsupported),
        )
        assertEquals(
            emptySet<String>(),
            eligibleToolNames(InstructionSettings(wikipediaEnabled = true), null),
        )
    }

    @Test
    fun `advertises experimental web search only for configured gate and capability`() {
        val supported =
            LocalModel(
                id = "supported",
                name = "Supported",
                filePath = "/tmp/model",
                toolCapabilities =
                ModelToolCapabilities(setOf(WEB_SEARCH_TOOL_NAME)),
            )

        assertEquals(
            setOf(WEB_SEARCH_TOOL_NAME),
            eligibleToolNames(
                InstructionSettings(),
                supported,
                selectedWebProvider = WebSearchProvider.Tavily,
                experimentalWebSearchEnabled = true,
            ),
        )
        assertEquals(
            emptySet<String>(),
            eligibleToolNames(
                InstructionSettings(),
                supported,
                selectedWebProvider = WebSearchProvider.Tavily,
                experimentalWebSearchEnabled = false,
            ),
        )
        assertEquals(
            emptySet<String>(),
            eligibleToolNames(
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
        assertFalse(preferences.settings.value.calculatorEnabled)
    }

    @Test
    fun `advertises calculator only when preference and model capability are enabled`() {
        val supported = LocalModel(
            id = "supported",
            name = "Supported",
            filePath = "/tmp/model",
            toolCapabilities = ModelToolCapabilities(setOf(CALCULATOR_TOOL_NAME)),
        )

        assertEquals(
            setOf(CALCULATOR_TOOL_NAME),
            eligibleToolNames(InstructionSettings(calculatorEnabled = true), supported),
        )
        assertEquals(emptySet<String>(), eligibleToolNames(InstructionSettings(), supported))
        assertEquals(
            emptySet<String>(),
            eligibleToolNames(
                InstructionSettings(calculatorEnabled = true),
                supported.copy(toolCapabilities = ModelToolCapabilities()),
            ),
        )
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
    fun `normal Chat and Voice advertise only the supplied application tools`() {
        val normalTools = setOf(
            WIKIPEDIA_PAGES_TOOL_NAME,
            WIKIPEDIA_ON_THIS_DAY_TOOL_NAME,
            WEB_SEARCH_TOOL_NAME,
            CALCULATOR_TOOL_NAME,
        )

        val chat = conversationTurnSettings(
            settings = InstructionSettings(),
            mode = InteractionMode.Chat,
            advertisedToolNames = normalTools,
        )
        val voice = conversationTurnSettings(
            settings = InstructionSettings(),
            mode = InteractionMode.Voice,
            advertisedToolNames = normalTools,
        )

        assertEquals(normalTools, chat.advertisedToolNames)
        assertEquals(normalTools, voice.advertisedToolNames)
        assertFalse("propose_widget" in chat.advertisedToolNames)
        assertFalse("propose_widget" in voice.advertisedToolNames)
        assertTrue(WIKIPEDIA_PAGES_TOOL_NAME in chat.advertisedToolNames)
        assertTrue(WIKIPEDIA_ON_THIS_DAY_TOOL_NAME in voice.advertisedToolNames)
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
        assertTrue(turn.systemInstruction.contains("direct, stable encyclopedic lookup"))
        assertTrue(turn.systemInstruction.contains("Do not use it for current news"))
        assertTrue(turn.systemInstruction.contains("at most three calls"))
        assertTrue(turn.systemInstruction.contains("never expose tool protocol or JSON"))
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
        assertFalse(turn.systemInstruction.contains(WIKIPEDIA_PAGES_TOOL_NAME))
        assertFalse(turn.systemInstruction.contains(WIKIPEDIA_ON_THIS_DAY_TOOL_NAME))
    }

    @Test
    fun `distinguishes dated events from direct wikipedia page lookup`() {
        val turn = conversationTurnSettings(
            settings = InstructionSettings(wikipediaEnabled = true),
            mode = InteractionMode.Chat,
            advertisedToolNames = setOf(WIKIPEDIA_PAGES_TOOL_NAME, WIKIPEDIA_ON_THIS_DAY_TOOL_NAME),
            temporalContext = TemporalContext("2026-09-09", "America/Toronto", "-04:00"),
        )

        assertTrue(turn.systemInstruction.contains("actual events, not a date-index page"))
        assertTrue(turn.systemInstruction.contains("Use wikipedia_pages only for a direct, stable"))
        assertTrue(turn.systemInstruction.contains("Across both Wikipedia tools, use at most three calls"))
        assertTrue(turn.systemInstruction.contains("derive month and day from the current date"))
    }

    @Test
    fun `explicitly forbids calculator protocol when calculator is not advertised`() {
        val turn =
            conversationTurnSettings(
                settings = InstructionSettings(calculatorEnabled = false),
                mode = InteractionMode.Chat,
                advertisedToolNames = emptySet(),
            )

        assertTrue(turn.systemInstruction.contains("No calculator or math tool is available"))
        assertTrue(turn.systemInstruction.contains("without emitting tool-call markup"))
        assertFalse(turn.advertisedToolNames.contains(CALCULATOR_TOOL_NAME))
    }

    @Test
    fun `does not forbid calculator when calculator is advertised`() {
        val turn =
            conversationTurnSettings(
                settings = InstructionSettings(calculatorEnabled = true),
                mode = InteractionMode.Chat,
                advertisedToolNames = setOf(CALCULATOR_TOOL_NAME),
            )

        assertFalse(turn.systemInstruction.contains("No calculator or math tool is available"))
        assertTrue(turn.systemInstruction.contains("Use calculator"))
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
