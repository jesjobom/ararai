package com.jesjobom.ararai.knowledge

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonObject
import com.jesjobom.ararai.chat.WIKIPEDIA_SEARCH_TOOL_NAME
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolDispatchResult
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolInvocation
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.tools.CURRENT_TOOL_CONTRACT_VERSION
import com.jesjobom.ararai.tools.knowledgeResult
import com.jesjobom.ararai.tools.singleWikipediaDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * LiteRT-LM adapter for a bounded number of Wikipedia invocations in one user turn.
 *
 * Instances are intentionally turn-scoped. Calling [execute] consumes one of the turn's attempts,
 * including when the model supplies invalid arguments or the provider fails.
 */
class WikipediaOpenApiTool(
    dispatcher: ApplicationToolDispatcher,
    verifiedModelToolIds: Set<String>,
    val displayName: String = "Wikipedia",
) : OpenApiTool {
    constructor(knowledgeTool: KnowledgeTool) : this(
        dispatcher = singleWikipediaDispatcher(knowledgeTool),
        verifiedModelToolIds = setOf(WIKIPEDIA_SEARCH_TOOL_NAME),
        displayName = knowledgeTool.displayName,
    )

    private val turn = WikipediaToolTurn(dispatcher, verifiedModelToolIds)

    override fun getToolDescriptionJsonString(): String = WikipediaToolTurn.TOOL_DESCRIPTION

    override fun execute(paramsJsonString: String): String = turn.execute(paramsJsonString)

    fun consumeCapturedSources(): List<KnowledgeSource> = turn.consumeCapturedSources()

    /**
     * Opens a fresh invocation allowance before submitting the next user turn.
     *
     * LiteRT-LM retains the registered tool instance with its conversation, so callers reusing a
     * conversation must mark each user-turn boundary explicitly.
     */
    fun beginTurn(observer: (ApplicationToolExecutionEvent) -> Unit = {}) = turn.beginTurn(observer)
}

sealed interface ApplicationToolExecutionEvent {
    data object Started : ApplicationToolExecutionEvent

    data class Succeeded(
        val sources: List<KnowledgeSource>,
    ) : ApplicationToolExecutionEvent

    data class Failed(
        val reason: ToolFailureReason?,
    ) : ApplicationToolExecutionEvent
}

/**
 * JVM-testable turn state kept separate from LiteRT-LM's Java 21-compiled interface.
 *
 * The Android adapter above remains deliberately thin; physical characterization exercises that
 * runtime boundary.
 */
internal class WikipediaToolTurn(
    private val dispatcher: ApplicationToolDispatcher,
    private val verifiedModelToolIds: Set<String>,
) {
    constructor(knowledgeTool: KnowledgeTool) : this(
        dispatcher = singleWikipediaDispatcher(knowledgeTool),
        verifiedModelToolIds = setOf(WIKIPEDIA_SEARCH_TOOL_NAME),
    )

    private val invocationCount = AtomicInteger(0)
    private val capturedSources = AtomicReference<List<KnowledgeSource>>(emptyList())
    private val observer = AtomicReference<(ApplicationToolExecutionEvent) -> Unit>({})

    fun beginTurn(observer: (ApplicationToolExecutionEvent) -> Unit = {}) {
        capturedSources.set(emptyList())
        invocationCount.set(0)
        this.observer.set(observer)
    }

    @Suppress("ReturnCount")
    fun execute(paramsJsonString: String): String {
        if (invocationCount.incrementAndGet() > MAX_CALLS_PER_TURN) {
            observer.get().invoke(ApplicationToolExecutionEvent.Failed(reason = null))
            return failureJson(ToolAdapterFailure.CallLimitReached)
        }
        observer.get().invoke(ApplicationToolExecutionEvent.Started)

        val result =
            try {
                runBlocking {
                    dispatcher.execute(
                        ApplicationToolInvocation(
                            id = WIKIPEDIA_SEARCH_TOOL_NAME,
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
            is ApplicationToolDispatchResult.Executed -> serializeObserved(result.knowledgeResult())
            is ApplicationToolDispatchResult.Rejected -> {
                val failure = result.reason.toToolFailureReason()
                observedFailure(failure.toAdapterFailure(), failure)
            }
        }
    }

    /**
     * Returns and clears sources captured by a successful invocation.
     *
     * Raw reference text is never retained here; callers persist only this bounded metadata after
     * the final assistant answer succeeds.
     */
    fun consumeCapturedSources(): List<KnowledgeSource> = capturedSources.getAndSet(emptyList())

    private fun serializeObserved(result: ToolResult): String {
        when (result) {
            is ToolResult.Success ->
                observer.get().invoke(ApplicationToolExecutionEvent.Succeeded(result.sources.toList()))
            is ToolResult.Failure ->
                observer.get().invoke(ApplicationToolExecutionEvent.Failed(result.reason))
        }
        return serialize(result)
    }

    private fun observedFailure(
        adapterFailure: ToolAdapterFailure,
        reason: ToolFailureReason,
    ): String {
        observer.get().invoke(ApplicationToolExecutionEvent.Failed(reason))
        return failureJson(adapterFailure)
    }

    private fun serialize(result: ToolResult): String = when (result) {
        is ToolResult.Success -> {
            capturedSources.updateAndGet { existing ->
                (existing + result.sources)
                    .distinctBy { source -> source.canonicalUrl }
            }
            JsonObject()
                .apply {
                    addProperty("ok", true)
                    addProperty("untrustedReference", result.untrustedContext)
                }.toString()
        }

        is ToolResult.Failure -> failureJson(result.reason.toAdapterFailure())
    }

    private fun failureJson(failure: ToolAdapterFailure): String = JsonObject()
        .apply {
            addProperty("ok", false)
            addProperty("error", failure.code)
            addProperty("message", failure.message)
        }.toString()

    private fun ToolFailureReason.toAdapterFailure(): ToolAdapterFailure = when (this) {
        ToolFailureReason.InvalidArguments -> ToolAdapterFailure.InvalidArguments
        ToolFailureReason.NoResults -> ToolAdapterFailure.NoResults
        ToolFailureReason.AuthenticationFailed -> ToolAdapterFailure.Unavailable
        ToolFailureReason.QuotaExceeded -> ToolAdapterFailure.Unavailable
        ToolFailureReason.RateLimited -> ToolAdapterFailure.Unavailable
        ToolFailureReason.Unavailable -> ToolAdapterFailure.Unavailable
        ToolFailureReason.MalformedResponse -> ToolAdapterFailure.MalformedResponse
        ToolFailureReason.TimedOut -> ToolAdapterFailure.TimedOut
        ToolFailureReason.Cancelled -> ToolAdapterFailure.Cancelled
    }

    private fun ApplicationToolRejection.toToolFailureReason(): ToolFailureReason = when (this) {
        ApplicationToolRejection.InvalidArguments -> ToolFailureReason.InvalidArguments
        ApplicationToolRejection.TimedOut -> ToolFailureReason.TimedOut
        ApplicationToolRejection.Cancelled -> ToolFailureReason.Cancelled
        ApplicationToolRejection.UnknownTool,
        ApplicationToolRejection.UnsupportedVersion,
        ApplicationToolRejection.Disabled,
        ApplicationToolRejection.NotConfigured,
        ApplicationToolRejection.IneligibleConsumer,
        ApplicationToolRejection.UnsupportedModel,
        ApplicationToolRejection.Unavailable,
        -> ToolFailureReason.Unavailable
    }

    private enum class ToolAdapterFailure(
        val code: String,
        val message: String,
    ) {
        InvalidArguments("INVALID_ARGUMENTS", "The Wikipedia search arguments are invalid."),
        NoResults("NO_RESULTS", "Wikipedia returned no matching reference."),
        Unavailable("SEARCH_UNAVAILABLE", "Wikipedia search is currently unavailable."),
        MalformedResponse("INVALID_RESPONSE", "Wikipedia returned an unsupported response."),
        TimedOut("SEARCH_TIMED_OUT", "Wikipedia search timed out."),
        Cancelled("SEARCH_CANCELLED", "Wikipedia search was cancelled."),
        CallLimitReached("CALL_LIMIT_REACHED", "Wikipedia search reached the per-turn call limit."),
    }

    companion object {
        const val MAX_CALLS_PER_TURN = 3
        val EXPECTED_ARGUMENTS = setOf("query", "language")
        const val TOOL_DESCRIPTION =
            """{"name":"wikipedia_search","description":"Use only for direct, stable encyclopedic """ +
                """lookups such as a birth date, country capital or currency, short biography, or concise """ +
                """concept or notable-work summary. Do not use for current news, changing facts, comparisons, """ +
                """recommendations, troubleshooting, broad research, or multi-source evidence. Search English """ +
                """first; if its result is unsatisfactory, use the detected language of the user's """ +
                """question.","parameters":""" +
                """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string"},""" +
                """"language":{"type":"string","pattern":"^[a-z]{2,3}$","description":"Lowercase ISO """ +
                """language code for the Wikipedia edition."}},"required":["query","language"]}}"""
    }
}
