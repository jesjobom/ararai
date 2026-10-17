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
import com.jesjobom.ararai.knowledge.WikipediaHistoricalEvent
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayRequest
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayResult
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayTool
import com.jesjobom.ararai.knowledge.WikipediaPage
import com.jesjobom.ararai.knowledge.WikipediaPageSummary
import com.jesjobom.ararai.knowledge.WikipediaPageSummaryRequest
import com.jesjobom.ararai.knowledge.WikipediaPageSummaryResult
import com.jesjobom.ararai.knowledge.WikipediaPageSummaryTool
import com.jesjobom.ararai.knowledge.WikipediaPagesResult
import com.jesjobom.ararai.knowledge.WikipediaPagesTool
import com.jesjobom.ararai.knowledge.validWebSearchRequest
import com.jesjobom.ararai.math.EvalExLocalMathEngine
import com.jesjobom.ararai.math.LocalMathEngine
import com.jesjobom.ararai.math.MathEvaluationResult
import com.jesjobom.ararai.model.LocalModel
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

fun defaultApplicationToolRegistry(
    instructionPreferences: InstructionPreferences,
    webSearchPreferences: WebSearchPreferences,
    wikipediaTool: KnowledgeTool,
    wikipediaPagesTool: WikipediaPagesTool,
    wikipediaOnThisDayTool: WikipediaOnThisDayTool,
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
        wikipediaPagesApplicationTool(
            tool = wikipediaPagesTool,
            state = {
                ApplicationToolOperationalState(
                    enabled = instructionPreferences.settings.value.wikipediaEnabled,
                    ready = true,
                )
            },
        ),
        wikipediaOnThisDayApplicationTool(
            tool = wikipediaOnThisDayTool,
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

internal fun wikipediaWidgetApplicationToolRegistry(
    pagesTool: WikipediaPagesTool,
    onThisDayTool: WikipediaOnThisDayTool,
    state: () -> ApplicationToolOperationalState,
    summaryTool: WikipediaPageSummaryTool? = null,
): ApplicationToolRegistry = ApplicationToolRegistry(
    listOf(
        wikipediaPagesApplicationTool(pagesTool, state),
        wikipediaOnThisDayApplicationTool(onThisDayTool, state),
    ) + listOfNotNull(summaryTool?.let { wikipediaPageSummaryApplicationTool(it, state) }),
)

internal fun wikipediaPageSummaryApplicationTool(
    tool: WikipediaPageSummaryTool,
    state: () -> ApplicationToolOperationalState,
): RegisteredApplicationTool = applicationToolBinding(
    contract = ApplicationToolContract(
        id = WIKIPEDIA_PAGES_TOOL_NAME,
        version = 2,
        displayName = WIKIPEDIA_PAGE_SUMMARY_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(ApplicationToolConsumer.Widget),
        inputSchemaJson = WIKIPEDIA_PAGE_SUMMARY_INPUT_SCHEMA,
        outputSchemaJson = WIKIPEDIA_PAGE_SUMMARY_OUTPUT_SCHEMA,
        authoringSummary = "Search 10 page candidates by topic; return one complete lead in pages[0].extract " +
            "and canonicalUrl. selection=first or seeded_random (requires runtime.seed). " +
            "Pages are not historical events.",
    ),
    state = state,
    executor = ApplicationTool(
        displayName = WIKIPEDIA_PAGE_SUMMARY_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
        execute = tool::fetch,
    ),
    decodeArguments = { arguments ->
        val selection = arguments.strictString("selection")
        val expected = if (selection == "seeded_random") {
            setOf("query", "language", "selection", "seed")
        } else {
            setOf("query", "language", "selection")
        }
        arguments.takeIf { it.keySet() == expected }
            ?.let {
                val query = it.strictString("query")
                    ?.takeIf { value -> value.isNotBlank() && value.length <= 200 && value.none(Char::isISOControl) }
                    ?: return@let null
                val seed = if (selection == "seeded_random") {
                    it.strictLong("seed")?.takeIf { value -> value in 0..9_007_199_254_740_991L }
                        ?: return@let null
                } else {
                    null
                }
                WikipediaPageSummaryRequest(
                    query = query,
                    language = it.strictString("language")?.takeIf(LANGUAGE_PATTERN::matches) ?: return@let null,
                    selection = selection ?: return@let null,
                    seed = seed,
                ).takeIf { request -> request.selection in setOf("first", "seeded_random") }
            }
    },
    encodeResult = ::encodeWikipediaPageSummaryResult,
)

internal fun wikipediaPagesApplicationTool(
    tool: WikipediaPagesTool,
    state: () -> ApplicationToolOperationalState,
): RegisteredApplicationTool = applicationToolBinding(
    contract = ApplicationToolContract(
        id = WIKIPEDIA_PAGES_TOOL_NAME,
        version = CURRENT_TOOL_CONTRACT_VERSION,
        displayName = WIKIPEDIA_PAGES_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(ApplicationToolConsumer.Model, ApplicationToolConsumer.Widget),
        inputSchemaJson = WIKIPEDIA_INPUT_SCHEMA,
        outputSchemaJson = WIKIPEDIA_PAGES_OUTPUT_SCHEMA,
        authoringSummary = "Query-based search returning up to three pages with title, extract, and URL. " +
            "Pages are not verified historical events; no random event or " +
            "topic-classification guarantee.",
    ),
    state = state,
    executor = ApplicationTool(
        displayName = WIKIPEDIA_PAGES_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
        execute = tool::fetch,
    ),
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
    encodeResult = ::encodeWikipediaPagesResult,
)

internal fun wikipediaOnThisDayApplicationTool(
    tool: WikipediaOnThisDayTool,
    state: () -> ApplicationToolOperationalState,
): RegisteredApplicationTool = applicationToolBinding(
    contract = ApplicationToolContract(
        id = WIKIPEDIA_ON_THIS_DAY_TOOL_NAME,
        version = CURRENT_TOOL_CONTRACT_VERSION,
        displayName = WIKIPEDIA_ON_THIS_DAY_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(ApplicationToolConsumer.Model, ApplicationToolConsumer.Widget),
        inputSchemaJson = WIKIPEDIA_ON_THIS_DAY_INPUT_SCHEMA,
        outputSchemaJson = WIKIPEDIA_ON_THIS_DAY_OUTPUT_SCHEMA,
        authoringSummary = "Historical events for one exact calendar month/day. No topic query, region filter, " +
            "or all-time event search. Use only when a date-based event feed satisfies the request.",
    ),
    state = state,
    executor = ApplicationTool(
        displayName = WIKIPEDIA_ON_THIS_DAY_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
        execute = tool::fetch,
    ),
    decodeArguments = { arguments ->
        arguments.takeIf { it.keySet() == setOf("month", "day", "language") }
            ?.let {
                val month = it.strictInt("month") ?: return@let null
                val day = it.strictInt("day") ?: return@let null
                val language = it.strictString("language")?.takeIf(LANGUAGE_PATTERN::matches)
                    ?: return@let null
                WikipediaOnThisDayRequest(month, day, language)
                    .takeIf { request -> validWikipediaCalendarDay(request.month, request.day) }
            }
    },
    encodeResult = ::encodeWikipediaOnThisDayResult,
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
internal fun ApplicationToolDispatchResult.Executed.wikipediaPagesResult(): WikipediaPagesResult = domainResult as WikipediaPagesResult

@Suppress("MaxLineLength")
internal fun ApplicationToolDispatchResult.Executed.wikipediaOnThisDayResult(): WikipediaOnThisDayResult = domainResult as WikipediaOnThisDayResult

@Suppress("MaxLineLength")
internal fun ApplicationToolDispatchResult.Executed.mathResult(): MathEvaluationResult = domainResult as MathEvaluationResult

private fun JsonObject.strictString(name: String): String? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString

private fun JsonObject.strictInt(name: String): Int? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    ?.asJsonPrimitive
    ?.let { value ->
        runCatching { value.asBigDecimal.toBigIntegerExact().toString().toIntOrNull() }
            .getOrNull()
    }

private fun JsonObject.strictLong(name: String): Long? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    ?.asJsonPrimitive
    ?.let { value -> runCatching { value.asBigDecimal.toBigIntegerExact().toString().toLongOrNull() }.getOrNull() }

private fun validWikipediaCalendarDay(month: Int, day: Int): Boolean = try {
    LocalDate.of(2000, month, day)
    true
} catch (_: DateTimeException) {
    false
}

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

private fun encodeWikipediaPagesResult(result: WikipediaPagesResult): String = when (result) {
    is WikipediaPagesResult.Success -> JsonObject().apply {
        addProperty("kind", "success")
        add("pages", JsonArray().apply { result.pages.forEach { add(it.toJson()) } })
    }.toString()
    is WikipediaPagesResult.Failure -> JsonObject().apply {
        addProperty("kind", "failure")
        addProperty("reason", result.reason.name)
    }.toString()
}

private fun encodeWikipediaPageSummaryResult(result: WikipediaPageSummaryResult): String = when (result) {
    is WikipediaPageSummaryResult.Success -> JsonObject().apply {
        addProperty("kind", "success")
        addProperty("defaultBodyField", "extract")
        add("pages", JsonArray().apply { add(result.page.toJson()) })
    }.toString()
    is WikipediaPageSummaryResult.Failure -> JsonObject().apply {
        addProperty("kind", "failure")
        addProperty("reason", result.reason.name)
    }.toString()
}

private fun encodeWikipediaOnThisDayResult(result: WikipediaOnThisDayResult): String = when (result) {
    is WikipediaOnThisDayResult.Success -> JsonObject().apply {
        addProperty("kind", "success")
        add("events", JsonArray().apply { result.events.forEach { add(it.toJson()) } })
        addProperty("language", result.language)
        addProperty("retrievedAtMillis", result.retrievedAtMillis)
    }.toString()
    is WikipediaOnThisDayResult.Failure -> JsonObject().apply {
        addProperty("kind", "failure")
        addProperty("reason", result.reason.name)
    }.toString()
}

private fun WikipediaPage.toJson(): JsonObject = JsonObject().apply {
    addProperty("title", title)
    addProperty("extract", extract)
    addProperty("canonicalUrl", canonicalUrl)
    addProperty("language", language)
    addProperty("retrievedAtMillis", retrievedAtMillis)
}

private fun WikipediaPageSummary.toJson(): JsonObject = JsonObject().apply {
    addProperty("title", title)
    addProperty("extract", extract)
    addProperty("canonicalUrl", canonicalUrl)
    addProperty("language", language)
    addProperty("retrievedAtMillis", retrievedAtMillis)
}

private fun WikipediaHistoricalEvent.toJson(): JsonObject = JsonObject().apply {
    addProperty("year", year)
    addProperty("text", text)
    addProperty("title", title)
    addProperty("canonicalUrl", canonicalUrl)
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
internal const val WIKIPEDIA_PAGES_TOOL_NAME = "wikipedia_pages"
internal const val WIKIPEDIA_PAGES_DISPLAY_NAME = "Wikipedia pages"
internal const val WIKIPEDIA_PAGE_SUMMARY_DISPLAY_NAME = "Wikipedia page summary"
internal const val WIKIPEDIA_ON_THIS_DAY_TOOL_NAME = "wikipedia_on_this_day"
internal const val WIKIPEDIA_ON_THIS_DAY_DISPLAY_NAME = "Wikipedia events by date"
internal const val WEB_SEARCH_DISPLAY_NAME = "Web search"
internal const val CALCULATOR_DISPLAY_NAME = "Local calculator"

private const val DEFAULT_LANGUAGE = "en"
private val LANGUAGE_PATTERN = Regex("[a-z]{2,3}")
private val UNSIGNED_INTEGER_PATTERN = Regex("0|[1-9][0-9]*")
private const val WIKIPEDIA_INPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string"},"language":{"type":"string","pattern":"^[a-z]{2,3}$"}},"required":["query","language"]}"""
private const val WIKIPEDIA_PAGE_SUMMARY_INPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string","maxLength":200},"language":{"type":"string","pattern":"^[a-z]{2,3}$"},"selection":{"type":"string","enum":["first","seeded_random"]},"seed":{"type":"integer","minimum":0,"maximum":9007199254740991}},"required":["query","language","selection"]}"""
private const val WIKIPEDIA_ON_THIS_DAY_INPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"month":{"type":"integer","minimum":1,"maximum":12},"day":{"type":"integer","minimum":1,"maximum":31},"language":{"type":"string","pattern":"^[a-z]{2,3}$"}},"required":["month","day","language"]}"""
private const val WEB_SEARCH_INPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string"}},"required":["query"]}"""
private const val CALCULATOR_INPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"expression":{"type":"string","maxLength":512}},"required":["expression"]}"""
private const val KNOWLEDGE_OUTPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"kind":{"type":"string"},"untrustedContext":{"type":"string"},"sources":{"type":"array"},"reason":{"type":"string"}},"required":["kind"]}"""
private const val WIKIPEDIA_PAGES_OUTPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"kind":{"type":"string"},"pages":{"type":"array","maxItems":3,"items":{"type":"object","additionalProperties":false,"properties":{"title":{"type":"string","maxLength":200},"extract":{"type":"string","maxLength":2000},"canonicalUrl":{"type":"string","maxLength":2048},"language":{"type":"string","pattern":"^[a-z]{2,3}$"},"retrievedAtMillis":{"type":"integer"}},"required":["title","extract","canonicalUrl","language","retrievedAtMillis"]}},"reason":{"type":"string"}},"required":["kind"]}"""
private const val WIKIPEDIA_PAGE_SUMMARY_OUTPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"kind":{"type":"string"},"defaultBodyField":{"type":"string","enum":["extract"]},"pages":{"type":"array","minItems":1,"maxItems":1,"items":{"type":"object","additionalProperties":false,"properties":{"title":{"type":"string","maxLength":200},"extract":{"type":"string","maxLength":16384},"canonicalUrl":{"type":"string","maxLength":2048},"language":{"type":"string","pattern":"^[a-z]{2,3}$"},"retrievedAtMillis":{"type":"integer"}},"required":["title","extract","canonicalUrl","language","retrievedAtMillis"]}},"reason":{"type":"string"}},"required":["kind"]}"""
private const val WIKIPEDIA_ON_THIS_DAY_OUTPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"kind":{"type":"string"},"events":{"type":"array","maxItems":64,"items":{"type":"object","additionalProperties":false,"properties":{"year":{"type":"integer","description":"Year when the event occurred"},"text":{"type":"string","maxLength":1000,"description":"Event description to show as body text"},"title":{"type":"string","maxLength":200,"description":"Linked article title, distinct from event description"},"canonicalUrl":{"type":"string","maxLength":2048}},"required":["year","text","title","canonicalUrl"]}},"language":{"type":"string","pattern":"^[a-z]{2,3}$"},"retrievedAtMillis":{"type":"integer"},"reason":{"type":"string"}},"required":["kind"]}"""
private const val CALCULATOR_OUTPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"kind":{"type":"string"},"value":{"type":"string"},"precision":{"type":"string"},"reason":{"type":"string"}},"required":["kind"]}"""
