@file:Suppress("TooManyFunctions")

package com.jesjobom.ararai.tools

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jesjobom.ararai.chat.CALCULATOR_TOOL_NAME
import com.jesjobom.ararai.chat.InstructionPreferences
import com.jesjobom.ararai.chat.WEB_SEARCH_TOOL_NAME
import com.jesjobom.ararai.chat.WIKIPEDIA_SEARCH_TOOL_NAME
import com.jesjobom.ararai.knowledge.KnowledgeSource
import com.jesjobom.ararai.knowledge.KnowledgeTool
import com.jesjobom.ararai.knowledge.ToolRequest
import com.jesjobom.ararai.knowledge.ToolResult
import com.jesjobom.ararai.knowledge.WebSearchPreferences
import com.jesjobom.ararai.knowledge.validWebSearchRequest
import com.jesjobom.ararai.math.EvalExLocalMathEngine
import com.jesjobom.ararai.math.LocalMathEngine
import com.jesjobom.ararai.math.MathEvaluationResult
import com.jesjobom.ararai.model.LocalModel
import java.util.Locale

fun defaultApplicationToolRegistry(
    instructionPreferences: InstructionPreferences,
    webSearchPreferences: WebSearchPreferences,
    wikipediaTool: KnowledgeTool,
    webSearchTool: () -> KnowledgeTool?,
    calculatorEngine: LocalMathEngine = EvalExLocalMathEngine(),
    experimentalWebSearchEnabled: Boolean,
    languageProvider: () -> String = { Locale.getDefault().language },
): ApplicationToolRegistry = ApplicationToolRegistry(
    listOf(
        wikipediaApplicationTool(
            tool = wikipediaTool,
            state = {
                ApplicationToolOperationalState(
                    enabled = instructionPreferences.settings.value.wikipediaEnabled,
                    ready = true,
                )
            },
        ),
        webSearchApplicationTool(
            tool = webSearchTool,
            state = {
                val settings = webSearchPreferences.settings.value
                val enabled = experimentalWebSearchEnabled && settings.orderedEnabledProviders.isNotEmpty()
                ApplicationToolOperationalState(
                    enabled = enabled,
                    ready = enabled && settings.orderedEnabledProviders.any { webSearchPreferences.token(it) != null },
                )
            },
            languageProvider = languageProvider,
        ),
        calculatorApplicationTool(
            engine = calculatorEngine,
            state = {
                ApplicationToolOperationalState(
                    enabled = instructionPreferences.settings.value.calculatorEnabled,
                    ready = true,
                )
            },
        ),
    ),
)

fun eligibleModelToolIds(
    registry: ApplicationToolRegistry,
    model: LocalModel?,
): Set<String> = registry
    .availableToolIds(ApplicationToolConsumer.Model)
    .intersect(model?.toolCapabilities?.toolNames.orEmpty())

internal fun wikipediaApplicationTool(
    tool: KnowledgeTool,
    state: () -> ApplicationToolOperationalState,
): RegisteredApplicationTool = applicationToolBinding(
    contract = ApplicationToolContract(
        id = WIKIPEDIA_SEARCH_TOOL_NAME,
        version = CURRENT_TOOL_CONTRACT_VERSION,
        displayName = tool.displayName,
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(ApplicationToolConsumer.Model),
        inputSchemaJson = WIKIPEDIA_INPUT_SCHEMA,
        outputSchemaJson = KNOWLEDGE_OUTPUT_SCHEMA,
    ),
    state = state,
    executor = tool,
    decodeArguments = { arguments ->
        arguments.takeIf { it.keySet() == setOf("query", "language") }
            ?.let {
                val language = it.strictString("language")?.takeIf(LANGUAGE_PATTERN::matches)
                ToolRequest(
                    it.strictString("query") ?: return@let null,
                    language ?: return@let null,
                )
            }
    },
    encodeResult = ::encodeKnowledgeResult,
)

internal fun webSearchApplicationTool(
    tool: () -> KnowledgeTool?,
    state: () -> ApplicationToolOperationalState,
    languageProvider: () -> String,
): RegisteredApplicationTool = applicationToolBinding(
    contract = ApplicationToolContract(
        id = WEB_SEARCH_TOOL_NAME,
        version = CURRENT_TOOL_CONTRACT_VERSION,
        displayName = WEB_SEARCH_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(ApplicationToolConsumer.Model),
        inputSchemaJson = WEB_SEARCH_INPUT_SCHEMA,
        outputSchemaJson = KNOWLEDGE_OUTPUT_SCHEMA,
    ),
    state = state,
    executor = ApplicationTool(
        displayName = WEB_SEARCH_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
    ) { request: ToolRequest ->
        tool()?.execute(request) ?: ToolResult.Failure(com.jesjobom.ararai.knowledge.ToolFailureReason.Unavailable)
    },
    decodeArguments = { arguments ->
        arguments.takeIf { it.keySet() == setOf("query") }
            ?.strictString("query")
            ?.let { query ->
                ToolRequest(
                    query = query,
                    language = normalizedLanguage(languageProvider()),
                    focus = query,
                ).takeIf(::validWebSearchRequest)
            }
    },
    encodeResult = ::encodeKnowledgeResult,
)

internal fun calculatorApplicationTool(
    engine: LocalMathEngine,
    state: () -> ApplicationToolOperationalState,
): RegisteredApplicationTool = applicationToolBinding(
    contract = ApplicationToolContract(
        id = CALCULATOR_TOOL_NAME,
        version = CURRENT_TOOL_CONTRACT_VERSION,
        displayName = CALCULATOR_DISPLAY_NAME,
        category = ApplicationToolCategory.LocalCompute,
        consumers = setOf(ApplicationToolConsumer.Model),
        inputSchemaJson = CALCULATOR_INPUT_SCHEMA,
        outputSchemaJson = CALCULATOR_OUTPUT_SCHEMA,
    ),
    state = state,
    executor = ApplicationTool(
        displayName = CALCULATOR_DISPLAY_NAME,
        category = ApplicationToolCategory.LocalCompute,
    ) { expression: String -> engine.evaluate(expression) },
    decodeArguments = { arguments ->
        arguments.takeIf { it.keySet() == setOf("expression") }
            ?.strictString("expression")
    },
    encodeResult = ::encodeMathResult,
)

internal fun singleWikipediaDispatcher(tool: KnowledgeTool): ApplicationToolDispatcher = ApplicationToolDispatcher(
    ApplicationToolRegistry(
        listOf(
            wikipediaApplicationTool(tool) {
                ApplicationToolOperationalState(enabled = true, ready = true)
            },
        ),
    ),
)

internal fun singleWebSearchDispatcher(
    tool: KnowledgeTool,
    languageProvider: () -> String,
): ApplicationToolDispatcher = ApplicationToolDispatcher(
    ApplicationToolRegistry(
        listOf(
            webSearchApplicationTool(
                tool = { tool },
                state = { ApplicationToolOperationalState(enabled = true, ready = true) },
                languageProvider = languageProvider,
            ),
        ),
    ),
)

internal fun singleCalculatorDispatcher(engine: LocalMathEngine): ApplicationToolDispatcher = ApplicationToolDispatcher(
    ApplicationToolRegistry(
        listOf(
            calculatorApplicationTool(engine) {
                ApplicationToolOperationalState(enabled = true, ready = true)
            },
        ),
    ),
)

internal fun modelApplicationToolDispatcher(
    wikipediaTool: KnowledgeTool,
    webSearchTool: () -> KnowledgeTool?,
    calculatorEngine: LocalMathEngine,
    languageProvider: () -> String = { Locale.getDefault().language },
): ApplicationToolDispatcher = ApplicationToolDispatcher(
    ApplicationToolRegistry(
        listOf(
            wikipediaApplicationTool(wikipediaTool) {
                ApplicationToolOperationalState(enabled = true, ready = true)
            },
            webSearchApplicationTool(
                tool = webSearchTool,
                state = {
                    ApplicationToolOperationalState(
                        enabled = true,
                        ready = webSearchTool() != null,
                    )
                },
                languageProvider = languageProvider,
            ),
            calculatorApplicationTool(calculatorEngine) {
                ApplicationToolOperationalState(enabled = true, ready = true)
            },
        ),
    ),
)

internal fun ApplicationToolDispatchResult.Executed.knowledgeResult(): ToolResult = domainResult as ToolResult

@Suppress("MaxLineLength")
internal fun ApplicationToolDispatchResult.Executed.mathResult(): MathEvaluationResult = domainResult as MathEvaluationResult

private fun JsonObject.strictString(name: String): String? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString

private fun normalizedLanguage(raw: String): String = raw
    .trim()
    .lowercase(Locale.ROOT)
    .takeIf(LANGUAGE_PATTERN::matches)
    ?: DEFAULT_LANGUAGE

private fun encodeKnowledgeResult(result: ToolResult): String = when (result) {
    is ToolResult.Success -> JsonObject().apply {
        addProperty("kind", "success")
        addProperty("untrustedContext", result.untrustedContext)
        add("sources", JsonArray().apply { result.sources.forEach { add(it.toJson()) } })
    }.toString()
    is ToolResult.Failure -> JsonObject().apply {
        addProperty("kind", "failure")
        addProperty("reason", result.reason.name)
    }.toString()
}

private fun KnowledgeSource.toJson(): JsonObject = JsonObject().apply {
    addProperty("provider", provider)
    addProperty("title", title)
    addProperty("canonicalUrl", canonicalUrl)
    addProperty("language", language)
    addProperty("retrievedAtMillis", retrievedAtMillis)
}

private fun encodeMathResult(result: MathEvaluationResult): String = when (result) {
    is MathEvaluationResult.Success -> JsonObject().apply {
        addProperty("kind", "success")
        addProperty("value", result.value)
        addProperty("precision", result.kind.name)
    }.toString()
    is MathEvaluationResult.Failure -> JsonObject().apply {
        addProperty("kind", "failure")
        addProperty("reason", result.reason.name)
    }.toString()
}

internal const val CURRENT_TOOL_CONTRACT_VERSION = 1
internal const val WEB_SEARCH_DISPLAY_NAME = "Web search"
internal const val CALCULATOR_DISPLAY_NAME = "Local calculator"

private const val DEFAULT_LANGUAGE = "en"
private val LANGUAGE_PATTERN = Regex("[a-z]{2,3}")
private const val WIKIPEDIA_INPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string"},"language":{"type":"string","pattern":"^[a-z]{2,3}$"}},"required":["query","language"]}"""
private const val WEB_SEARCH_INPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string"}},"required":["query"]}"""
private const val CALCULATOR_INPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"expression":{"type":"string","maxLength":512}},"required":["expression"]}"""
private const val KNOWLEDGE_OUTPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"kind":{"type":"string"},"untrustedContext":{"type":"string"},"sources":{"type":"array"},"reason":{"type":"string"}},"required":["kind"]}"""
private const val CALCULATOR_OUTPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"kind":{"type":"string"},"value":{"type":"string"},"precision":{"type":"string"},"reason":{"type":"string"}},"required":["kind"]}"""
