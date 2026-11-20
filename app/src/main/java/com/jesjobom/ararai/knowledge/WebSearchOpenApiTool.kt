package com.jesjobom.ararai.knowledge

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.chat.WEB_SEARCH_TOOL_NAME
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolDispatchResult
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolInvocation
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.tools.CURRENT_TOOL_CONTRACT_VERSION
import com.jesjobom.ararai.tools.knowledgeResult
import com.jesjobom.ararai.tools.singleWebSearchDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class WebSearchResultMetrics(
    val resultChars: Int,
    val budgetChars: Int,
    val contextTokens: Int,
    val sourceCount: Int,
)

class WebSearchOpenApiTool(
    dispatcher: ApplicationToolDispatcher,
    verifiedModelToolIds: Set<String>,
    val displayName: String,
    contextTokens: Int = 2_048,
    onResultMetrics: (WebSearchResultMetrics) -> Unit = {},
) : OpenApiTool {
    constructor(
        knowledgeTool: KnowledgeTool,
        languageProvider: () -> String = { Locale.getDefault().language },
        contextTokens: Int = 2_048,
        onResultMetrics: (WebSearchResultMetrics) -> Unit = {},
    ) : this(
        dispatcher = singleWebSearchDispatcher(knowledgeTool, languageProvider),
        verifiedModelToolIds = setOf(WEB_SEARCH_TOOL_NAME),
        displayName = knowledgeTool.displayName,
        contextTokens = contextTokens,
        onResultMetrics = onResultMetrics,
    )

    private val turn = WebSearchToolTurn(dispatcher, verifiedModelToolIds, contextTokens, onResultMetrics)

    override fun getToolDescriptionJsonString(): String = WebSearchToolTurn.TOOL_DESCRIPTION

    override fun execute(paramsJsonString: String): String = turn.execute(paramsJsonString)

    fun consumeCapturedSources(): List<KnowledgeSource> = turn.consumeCapturedSources()

    fun beginTurn(observer: (ApplicationToolExecutionEvent) -> Unit = {}) = turn.beginTurn(observer)
}

internal class WebSearchToolTurn(
    private val dispatcher: ApplicationToolDispatcher,
    private val verifiedModelToolIds: Set<String>,
    contextTokens: Int = 2_048,
    private val onResultMetrics: (WebSearchResultMetrics) -> Unit = {},
) {
    constructor(
        knowledgeTool: KnowledgeTool,
        languageProvider: () -> String = { Locale.getDefault().language },
        contextTokens: Int = 2_048,
        onResultMetrics: (WebSearchResultMetrics) -> Unit = {},
    ) : this(
        dispatcher = singleWebSearchDispatcher(knowledgeTool, languageProvider),
        verifiedModelToolIds = setOf(WEB_SEARCH_TOOL_NAME),
        contextTokens = contextTokens,
        onResultMetrics = onResultMetrics,
    )

    private val invocationCount = AtomicInteger(0)
    private val evidencePolicy = WebEvidencePolicy.Chat(contextTokens)
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
            return failureJson("CALL_LIMIT_REACHED", "Web search reached the per-turn call limit.")
        }
        observer.get().invoke(ApplicationToolExecutionEvent.Started)
        val result =
            try {
                runBlocking {
                    dispatcher.execute(
                        ApplicationToolInvocation(
                            id = WEB_SEARCH_TOOL_NAME,
                            version = CURRENT_TOOL_CONTRACT_VERSION,
                            consumer = ApplicationToolConsumer.Model,
                            argumentsJson = paramsJsonString,
                            verifiedModelToolIds = verifiedModelToolIds,
                            modelContextTokens = evidencePolicy.contextTokens,
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
                observedFailure(result.reason.toToolFailureReason())
            }
        }
    }

    fun consumeCapturedSources(): List<KnowledgeSource> = capturedSources.getAndSet(emptyList())

    private fun serializeObserved(result: ToolResult): String {
        val boundedResult = if (
            result is ToolResult.Success &&
            !evidencePolicy.fits(result.untrustedContext)
        ) {
            ToolResult.Failure(ToolFailureReason.NoResults)
        } else {
            result
        }
        when (boundedResult) {
            is ToolResult.Success ->
                observer.get().invoke(ApplicationToolExecutionEvent.Succeeded(boundedResult.sources.toList()))
            is ToolResult.Failure ->
                observer.get().invoke(ApplicationToolExecutionEvent.Failed(boundedResult.reason))
        }
        return when (boundedResult) {
            is ToolResult.Success -> {
                capturedSources.updateAndGet { existing ->
                    (existing + boundedResult.sources).distinctBy(KnowledgeSource::canonicalUrl)
                }
                WebEvidencePolicy.modelSuccessJson(boundedResult.untrustedContext).also { response ->
                    onResultMetrics(
                        WebSearchResultMetrics(
                            resultChars = response.length,
                            budgetChars = evidencePolicy.maxResultChars,
                            contextTokens = evidencePolicy.contextTokens,
                            sourceCount = boundedResult.sources.size,
                        ),
                    )
                }
            }
            is ToolResult.Failure -> failureJson(boundedResult.reason.code(), boundedResult.reason.message())
        }
    }

    private fun observedFailure(reason: ToolFailureReason): String {
        observer.get().invoke(ApplicationToolExecutionEvent.Failed(reason))
        return failureJson(reason.code(), reason.message())
    }

    private fun failureJson(
        code: String,
        message: String,
    ): String = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", code)
        addProperty("message", message)
    }.toString()

    private fun ToolFailureReason.code(): String = when (this) {
        ToolFailureReason.InvalidArguments -> "INVALID_ARGUMENTS"
        ToolFailureReason.NoResults -> "NO_RESULTS"
        ToolFailureReason.AuthenticationFailed -> "AUTHENTICATION_FAILED"
        ToolFailureReason.QuotaExceeded -> "QUOTA_EXCEEDED"
        ToolFailureReason.RateLimited -> "RATE_LIMITED"
        ToolFailureReason.Unavailable -> "SEARCH_UNAVAILABLE"
        ToolFailureReason.MalformedResponse -> "INVALID_RESPONSE"
        ToolFailureReason.TimedOut -> "SEARCH_TIMED_OUT"
        ToolFailureReason.Cancelled -> "SEARCH_CANCELLED"
    }

    private fun ToolFailureReason.message(): String = when (this) {
        ToolFailureReason.InvalidArguments -> "The web-search arguments are invalid."
        ToolFailureReason.NoResults -> "Web search returned no matching evidence."
        ToolFailureReason.AuthenticationFailed -> "The provider rejected its configured credential."
        ToolFailureReason.QuotaExceeded -> "The provider quota is exhausted."
        ToolFailureReason.RateLimited -> "The provider rate limit was reached."
        ToolFailureReason.Unavailable -> "Web search is currently unavailable."
        ToolFailureReason.MalformedResponse -> "The provider returned an unsupported response."
        ToolFailureReason.TimedOut -> "Web search timed out."
        ToolFailureReason.Cancelled -> "Web search was cancelled."
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

    companion object {
        const val MAX_CALLS_PER_TURN = 2
        val EXPECTED_ARGUMENTS = setOf("query")
        const val TOOL_DESCRIPTION =
            """{"name":"web_search","description":"Search the current web for focused evidence """ +
                """when encyclopedic knowledge is insufficient. Use at most two calls, then answer from """ +
                """the best available evidence.","parameters":{"type":"object","additionalProperties":false,""" +
                """"properties":{"query":{"type":"string","description":"The specific question or """ +
                """evidence to search for."}},"required":["query"]}}"""
    }
}
