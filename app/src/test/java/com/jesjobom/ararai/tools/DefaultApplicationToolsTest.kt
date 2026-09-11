package com.jesjobom.ararai.tools

import com.google.gson.JsonParser
import com.jesjobom.ararai.chat.CALCULATOR_TOOL_NAME
import com.jesjobom.ararai.chat.InMemoryInstructionPreferences
import com.jesjobom.ararai.chat.InstructionSettings
import com.jesjobom.ararai.chat.WEB_SEARCH_TOOL_NAME
import com.jesjobom.ararai.chat.WIKIPEDIA_SEARCH_TOOL_NAME
import com.jesjobom.ararai.knowledge.InMemoryWebSearchPreferences
import com.jesjobom.ararai.knowledge.KnowledgeTool
import com.jesjobom.ararai.knowledge.ToolResult
import com.jesjobom.ararai.knowledge.WebSearchProvider
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayResult
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayTool
import com.jesjobom.ararai.knowledge.WikipediaPagesResult
import com.jesjobom.ararai.knowledge.WikipediaPagesTool
import com.jesjobom.ararai.math.LocalMathEngine
import com.jesjobom.ararai.math.MathEvaluationResult
import com.jesjobom.ararai.math.MathResultKind
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelToolCapabilities
import com.jesjobom.ararai.model.PROPOSE_WIDGET_TOOL_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DefaultApplicationToolsTest {
    @Test
    fun `registers version one tools with explicit consumer eligibility`() {
        val registry = registry()

        assertEquals(
            setOf(
                WIKIPEDIA_SEARCH_TOOL_NAME,
                WIKIPEDIA_PAGES_TOOL_NAME,
                WIKIPEDIA_ON_THIS_DAY_TOOL_NAME,
                WEB_SEARCH_TOOL_NAME,
                CALCULATOR_TOOL_NAME,
            ),
            registry.descriptors().mapTo(mutableSetOf()) { it.id },
        )
        registry.descriptors().forEach { contract ->
            assertEquals(1, contract.version)
            assertEquals(
                if (contract.id in setOf(WIKIPEDIA_PAGES_TOOL_NAME, WIKIPEDIA_ON_THIS_DAY_TOOL_NAME)) {
                    setOf(ApplicationToolConsumer.Model, ApplicationToolConsumer.Widget)
                } else {
                    setOf(ApplicationToolConsumer.Model)
                },
                contract.consumers,
            )
        }
        assertEquals(emptySet<String>(), registry.availableToolIds(ApplicationToolConsumer.Widget))
    }

    @Test
    fun `preserves the strict Wikipedia model contract before widget tools are added`() {
        val contract = registry().descriptors().single { it.id == WIKIPEDIA_SEARCH_TOOL_NAME }
        val input = JsonParser.parseString(contract.inputSchemaJson).asJsonObject
        val output = JsonParser.parseString(contract.outputSchemaJson).asJsonObject

        assertEquals(WIKIPEDIA_SEARCH_TOOL_NAME, contract.id)
        assertEquals(CURRENT_TOOL_CONTRACT_VERSION, contract.version)
        assertEquals(setOf(ApplicationToolConsumer.Model), contract.consumers)
        assertFalse(input["additionalProperties"].asBoolean)
        assertEquals(
            setOf("query", "language"),
            input["required"].asJsonArray.mapTo(mutableSetOf()) { it.asString },
        )
        assertEquals(
            setOf("query", "language"),
            input["properties"].asJsonObject.keySet(),
        )
        assertEquals(
            "^[a-z]{2,3}$",
            input["properties"].asJsonObject["language"].asJsonObject["pattern"].asString,
        )
        assertFalse(output["additionalProperties"].asBoolean)
        assertEquals(setOf("kind"), output["required"].asJsonArray.mapTo(mutableSetOf()) { it.asString })
        assertEquals(
            setOf("kind", "untrustedContext", "sources", "reason"),
            output["properties"].asJsonObject.keySet(),
        )
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
                setOf(
                    WIKIPEDIA_PAGES_TOOL_NAME,
                    WIKIPEDIA_ON_THIS_DAY_TOOL_NAME,
                    WEB_SEARCH_TOOL_NAME,
                    CALCULATOR_TOOL_NAME,
                ),
            ),
        )
        val calculatorOnly = allCapable.copy(
            toolCapabilities = ModelToolCapabilities(setOf(CALCULATOR_TOOL_NAME)),
        )
        val authoringOnly = allCapable.copy(
            toolCapabilities = ModelToolCapabilities(authoringToolNames = setOf(PROPOSE_WIDGET_TOOL_NAME)),
        )

        assertEquals(allCapable.toolCapabilities.toolNames, eligibleModelToolIds(registry, allCapable))
        assertEquals(setOf(CALCULATOR_TOOL_NAME), eligibleModelToolIds(registry, calculatorOnly))
        assertEquals(emptySet<String>(), eligibleModelToolIds(registry, authoringOnly))

        instructions.setCalculatorEnabled(false)
        assertFalse(CALCULATOR_TOOL_NAME in eligibleModelToolIds(registry, allCapable))
        assertEquals(
            setOf(WIKIPEDIA_PAGES_TOOL_NAME, WIKIPEDIA_ON_THIS_DAY_TOOL_NAME),
            registry.availableToolIds(ApplicationToolConsumer.Widget),
        )
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
        wikipediaPagesTool = WikipediaPagesTool {
            WikipediaPagesResult.Failure(com.jesjobom.ararai.knowledge.ToolFailureReason.NoResults)
        },
        wikipediaOnThisDayTool = WikipediaOnThisDayTool {
            WikipediaOnThisDayResult.Failure(com.jesjobom.ararai.knowledge.ToolFailureReason.NoResults)
        },
        webSearchTool = {
            KnowledgeTool { ToolResult.Failure(com.jesjobom.ararai.knowledge.ToolFailureReason.NoResults) }
        },
        calculatorEngine = LocalMathEngine {
            MathEvaluationResult.Success("4", MathResultKind.Exact)
        },
        experimentalWebSearchEnabled = experimentalWebSearchEnabled,
    )
}
