package com.jesjobom.ararai.tools

import com.jesjobom.ararai.chat.CALCULATOR_TOOL_NAME
import com.jesjobom.ararai.chat.InMemoryInstructionPreferences
import com.jesjobom.ararai.chat.InstructionSettings
import com.jesjobom.ararai.chat.WEB_SEARCH_TOOL_NAME
import com.jesjobom.ararai.chat.WIKIPEDIA_SEARCH_TOOL_NAME
import com.jesjobom.ararai.knowledge.InMemoryWebSearchPreferences
import com.jesjobom.ararai.knowledge.KnowledgeTool
import com.jesjobom.ararai.knowledge.ToolResult
import com.jesjobom.ararai.knowledge.WebSearchProvider
import com.jesjobom.ararai.math.LocalMathEngine
import com.jesjobom.ararai.math.MathEvaluationResult
import com.jesjobom.ararai.math.MathResultKind
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelToolCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DefaultApplicationToolsTest {
    @Test
    fun `registers current tools as version one model only contracts`() {
        val registry = registry()

        assertEquals(
            setOf(WIKIPEDIA_SEARCH_TOOL_NAME, WEB_SEARCH_TOOL_NAME, CALCULATOR_TOOL_NAME),
            registry.descriptors().mapTo(mutableSetOf()) { it.id },
        )
        registry.descriptors().forEach { contract ->
            assertEquals(1, contract.version)
            assertEquals(setOf(ApplicationToolConsumer.Model), contract.consumers)
        }
        assertEquals(emptySet<String>(), registry.availableToolIds(ApplicationToolConsumer.Widget))
    }

    @Test
    fun `resolves enabled configured tools independently from model capability`() {
        val instructions = InMemoryInstructionPreferences(
            InstructionSettings(wikipediaEnabled = true, calculatorEnabled = true),
        )
        val webPreferences = InMemoryWebSearchPreferences(
            initialTokens = mapOf(WebSearchProvider.Exa to "secret-token"),
            enabledProviders = setOf(WebSearchProvider.Exa),
        )
        val registry = registry(instructions, webPreferences)
        val allCapable = LocalModel(
            id = "all-capable",
            name = "All capable",
            filePath = "/tmp/model",
            toolCapabilities = ModelToolCapabilities(
                setOf(WIKIPEDIA_SEARCH_TOOL_NAME, WEB_SEARCH_TOOL_NAME, CALCULATOR_TOOL_NAME),
            ),
        )
        val calculatorOnly = allCapable.copy(
            toolCapabilities = ModelToolCapabilities(setOf(CALCULATOR_TOOL_NAME)),
        )

        assertEquals(allCapable.toolCapabilities.toolNames, eligibleModelToolIds(registry, allCapable))
        assertEquals(setOf(CALCULATOR_TOOL_NAME), eligibleModelToolIds(registry, calculatorOnly))

        instructions.setCalculatorEnabled(false)
        assertFalse(CALCULATOR_TOOL_NAME in eligibleModelToolIds(registry, allCapable))
        assertEquals(emptySet<String>(), registry.availableToolIds(ApplicationToolConsumer.Widget))
    }

    @Test
    fun `keeps web search unavailable without verified enabled credentials`() {
        val noCredential = registry(
            webPreferences = InMemoryWebSearchPreferences(),
        )
        val disabledFeature = registry(
            webPreferences = InMemoryWebSearchPreferences(
                initialTokens = mapOf(WebSearchProvider.Exa to "secret-token"),
                enabledProviders = setOf(WebSearchProvider.Exa),
            ),
            experimentalWebSearchEnabled = false,
        )

        assertFalse(WEB_SEARCH_TOOL_NAME in noCredential.availableToolIds(ApplicationToolConsumer.Model))
        assertFalse(WEB_SEARCH_TOOL_NAME in disabledFeature.availableToolIds(ApplicationToolConsumer.Model))
        assertFalse(disabledFeature.descriptors().joinToString().contains("secret-token"))
    }

    private fun registry(
        instructions: InMemoryInstructionPreferences = InMemoryInstructionPreferences(),
        webPreferences: InMemoryWebSearchPreferences = InMemoryWebSearchPreferences(),
        experimentalWebSearchEnabled: Boolean = true,
    ): ApplicationToolRegistry = defaultApplicationToolRegistry(
        instructionPreferences = instructions,
        webSearchPreferences = webPreferences,
        wikipediaTool = KnowledgeTool { ToolResult.Failure(com.jesjobom.ararai.knowledge.ToolFailureReason.NoResults) },
        webSearchTool = {
            KnowledgeTool { ToolResult.Failure(com.jesjobom.ararai.knowledge.ToolFailureReason.NoResults) }
        },
        calculatorEngine = LocalMathEngine {
            MathEvaluationResult.Success("4", MathResultKind.Exact)
        },
        experimentalWebSearchEnabled = experimentalWebSearchEnabled,
    )
}
