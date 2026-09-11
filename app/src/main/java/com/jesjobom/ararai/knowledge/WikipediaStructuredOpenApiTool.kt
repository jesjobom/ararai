package com.jesjobom.ararai.knowledge

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolDispatchResult
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolInvocation
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.tools.CURRENT_TOOL_CONTRACT_VERSION
import com.jesjobom.ararai.tools.WIKIPEDIA_ON_THIS_DAY_DISPLAY_NAME
import com.jesjobom.ararai.tools.WIKIPEDIA_ON_THIS_DAY_TOOL_NAME
import com.jesjobom.ararai.tools.WIKIPEDIA_PAGES_DISPLAY_NAME
import com.jesjobom.ararai.tools.WIKIPEDIA_PAGES_TOOL_NAME
import com.jesjobom.ararai.tools.wikipediaOnThisDayResult
import com.jesjobom.ararai.tools.wikipediaPagesResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val MAX_MODEL_EVENTS = 3

internal class WikipediaModelToolBudget {
    private val calls = AtomicInteger(0)

    fun beginTurn() {
        calls.set(0)
    }

    fun tryAcquire(): Boolean = calls.incrementAndGet() <= MAX_CALLS_PER_TURN

    companion object {
        const val MAX_CALLS_PER_TURN = 3
    }
}

internal enum class StructuredWikipediaTool(
    val id: String,
    val displayName: String,
    val descriptionJson: String,
) {
    Pages(
        WIKIPEDIA_PAGES_TOOL_NAME,
        WIKIPEDIA_PAGES_DISPLAY_NAME,
        """{"name":"wikipedia_pages","description":"Use for a direct, stable encyclopedic page lookup """ +
            """such as a person, place, concept, notable work, date, capital, currency, or short biography. """ +
            """Do not use for historical events on a calendar month/day when wikipedia_on_this_day is """ +
            """available. Do not use for current news, changing facts, comparisons, recommendations, broad """ +
            """research, or multi-source evidence.","parameters":{"type":"object","additionalProperties":false,""" +
            """"properties":{"query":{"type":"string","description":"Specific stable encyclopedic topic."},""" +
            """"language":{"type":"string","pattern":"^[a-z]{2,3}$","description":"Lowercase Wikipedia """ +
            """language code matching the user's requested language."}},"required":["query","language"]}}""",
    ),
    OnThisDay(
        WIKIPEDIA_ON_THIS_DAY_TOOL_NAME,
        WIKIPEDIA_ON_THIS_DAY_DISPLAY_NAME,
        """{"name":"wikipedia_on_this_day","description":"Use only when the user asks for one or more """ +
            """historical events that occurred on a specific calendar month and day, including 'today in """ +
            """history'. Use the current date supplied by the system for relative dates. This returns actual """ +
            """event items, not a date-index page. Use wikipedia_pages for people, places, concepts, works, """ +
            """or other direct encyclopedic lookups.","parameters":{"type":"object","additionalProperties":false,""" +
            """"properties":{"month":{"type":"integer","minimum":1,"maximum":12},"day":{"type":"integer",""" +
            """"minimum":1,"maximum":31},"language":{"type":"string","pattern":"^[a-z]{2,3}$",""" +
            """"description":"Lowercase Wikipedia language code matching the user's requested language."}},""" +
            """"required":["month","day","language"]}}""",
    ),
}

internal class WikipediaStructuredOpenApiTool(
    kind: StructuredWikipediaTool,
    dispatcher: ApplicationToolDispatcher,
    verifiedModelToolIds: Set<String>,
    budget: WikipediaModelToolBudget = WikipediaModelToolBudget(),
) : OpenApiTool {
    private val turn = WikipediaStructuredToolTurn(kind, dispatcher, verifiedModelToolIds, budget)
    val toolId: String = kind.id
    val displayName: String = kind.displayName

    override fun getToolDescriptionJsonString(): String = turn.getToolDescriptionJsonString()

    override fun execute(paramsJsonString: String): String = turn.execute(paramsJsonString)

    fun beginTurn(observer: (ApplicationToolExecutionEvent) -> Unit = {}) = turn.beginTurn(observer)

    fun consumeCapturedSources(): List<KnowledgeSource> = turn.consumeCapturedSources()
}

internal class WikipediaStructuredToolTurn(
    private val kind: StructuredWikipediaTool,
    private val dispatcher: ApplicationToolDispatcher,
    private val verifiedModelToolIds: Set<String>,
    private val budget: WikipediaModelToolBudget = WikipediaModelToolBudget(),
) {
    val toolId: String = kind.id
    val displayName: String = kind.displayName

    private val capturedSources = AtomicReference<List<KnowledgeSource>>(emptyList())
    private val observer = AtomicReference<(ApplicationToolExecutionEvent) -> Unit>({})

    fun getToolDescriptionJsonString(): String = kind.descriptionJson

    fun execute(paramsJsonString: String): String {
        if (!budget.tryAcquire()) {
            observer.get().invoke(ApplicationToolExecutionEvent.Failed(reason = null))
            return failureJson("CALL_LIMIT_REACHED", "Wikipedia tools reached the per-turn call limit.")
        }
        observer.get().invoke(ApplicationToolExecutionEvent.Started)
        val result = try {
            runBlocking {
                dispatcher.execute(
                    ApplicationToolInvocation(
                        id = kind.id,
                        version = CURRENT_TOOL_CONTRACT_VERSION,
                        consumer = ApplicationToolConsumer.Model,
                        argumentsJson = paramsJsonString,
                        verifiedModelToolIds = verifiedModelToolIds,
                    ),
                )
            }
        } catch (_: CancellationException) {
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.Cancelled)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.Cancelled)
        } catch (_: RuntimeException) {
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.Unavailable)
        }
        return when (result) {
            is ApplicationToolDispatchResult.Executed -> serialize(result)
            is ApplicationToolDispatchResult.Rejected -> observedFailure(result.reason.toFailureReason())
        }
    }

    fun beginTurn(observer: (ApplicationToolExecutionEvent) -> Unit = {}) {
        capturedSources.set(emptyList())
        budget.beginTurn()
        this.observer.set(observer)
    }

    fun consumeCapturedSources(): List<KnowledgeSource> = capturedSources.getAndSet(emptyList())

    private fun serialize(result: ApplicationToolDispatchResult.Executed): String = when (kind) {
        StructuredWikipediaTool.Pages -> when (val pages = result.wikipediaPagesResult()) {
            is WikipediaPagesResult.Success -> observedSuccess(
                result.payloadJson,
                pages.pages.map {
                    KnowledgeSource("Wikipedia", it.title, it.canonicalUrl, it.language, it.retrievedAtMillis)
                },
            )
            is WikipediaPagesResult.Failure -> observedFailure(pages.reason)
        }
        StructuredWikipediaTool.OnThisDay -> when (val events = result.wikipediaOnThisDayResult()) {
            is WikipediaOnThisDayResult.Success -> observedSuccess(
                result.payloadJson.withBoundedEventProjection(),
                events.events.take(MAX_MODEL_EVENTS).map {
                    KnowledgeSource("Wikipedia", it.title, it.canonicalUrl, events.language, events.retrievedAtMillis)
                },
            )
            is WikipediaOnThisDayResult.Failure -> observedFailure(events.reason)
        }
    }

    private fun observedSuccess(payloadJson: String, sources: List<KnowledgeSource>): String {
        val distinctSources = sources.distinctBy(KnowledgeSource::canonicalUrl)
        capturedSources.updateAndGet { existing ->
            (existing + distinctSources).distinctBy(KnowledgeSource::canonicalUrl)
        }
        observer.get().invoke(ApplicationToolExecutionEvent.Succeeded(distinctSources))
        return JsonObject().apply {
            addProperty("ok", true)
            addProperty("untrusted", true)
            add("result", JsonParser.parseString(payloadJson))
        }.toString()
    }

    private fun observedFailure(reason: ToolFailureReason): String {
        observer.get().invoke(ApplicationToolExecutionEvent.Failed(reason))
        return failureJson(reason.code(), reason.message())
    }

    private fun ApplicationToolRejection.toFailureReason(): ToolFailureReason = when (this) {
        ApplicationToolRejection.InvalidArguments -> ToolFailureReason.InvalidArguments
        ApplicationToolRejection.TimedOut -> ToolFailureReason.TimedOut
        ApplicationToolRejection.Cancelled -> ToolFailureReason.Cancelled
        else -> ToolFailureReason.Unavailable
    }

    private fun ToolFailureReason.code(): String = when (this) {
        ToolFailureReason.InvalidArguments -> "INVALID_ARGUMENTS"
        ToolFailureReason.NoResults -> "NO_RESULTS"
        ToolFailureReason.MalformedResponse -> "INVALID_RESPONSE"
        ToolFailureReason.TimedOut -> "SEARCH_TIMED_OUT"
        ToolFailureReason.Cancelled -> "SEARCH_CANCELLED"
        else -> "SEARCH_UNAVAILABLE"
    }

    private fun ToolFailureReason.message(): String = when (this) {
        ToolFailureReason.InvalidArguments -> "The Wikipedia tool arguments are invalid."
        ToolFailureReason.NoResults -> "Wikipedia returned no matching reference."
        ToolFailureReason.MalformedResponse -> "Wikipedia returned an unsupported response."
        ToolFailureReason.TimedOut -> "Wikipedia retrieval timed out."
        ToolFailureReason.Cancelled -> "Wikipedia retrieval was cancelled."
        else -> "Wikipedia is currently unavailable."
    }

    private fun failureJson(code: String, message: String): String = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", code)
        addProperty("message", message)
    }.toString()
}

private fun String.withBoundedEventProjection(): String {
    val payload = JsonParser.parseString(this).asJsonObject
    val events = payload["events"].asJsonArray
    payload.add(
        "events",
        com.google.gson.JsonArray().apply {
            events.take(MAX_MODEL_EVENTS).forEach { add(it.deepCopy()) }
        },
    )
    return payload.toString()
}
