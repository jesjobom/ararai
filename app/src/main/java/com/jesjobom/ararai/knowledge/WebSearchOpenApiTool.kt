package com.jesjobom.ararai.knowledge

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class WebSearchOpenApiTool(
    knowledgeTool: KnowledgeTool,
) : OpenApiTool {
    val displayName: String = knowledgeTool.displayName

    private val turn = WebSearchToolTurn(knowledgeTool)

    override fun getToolDescriptionJsonString(): String = WebSearchToolTurn.TOOL_DESCRIPTION

    override fun execute(paramsJsonString: String): String = turn.execute(paramsJsonString)

    fun consumeCapturedSources(): List<KnowledgeSource> = turn.consumeCapturedSources()

    fun beginTurn(observer: (KnowledgeToolExecutionEvent) -> Unit = {}) = turn.beginTurn(observer)
}

internal class WebSearchToolTurn(
    private val knowledgeTool: KnowledgeTool,
) {
    private val invocationCount = AtomicInteger(0)
    private val capturedSources = AtomicReference<List<KnowledgeSource>>(emptyList())
    private val observer = AtomicReference<(KnowledgeToolExecutionEvent) -> Unit>({})

    fun beginTurn(observer: (KnowledgeToolExecutionEvent) -> Unit = {}) {
        capturedSources.set(emptyList())
        invocationCount.set(0)
        this.observer.set(observer)
    }

    @Suppress("ReturnCount")
    fun execute(paramsJsonString: String): String {
        if (invocationCount.incrementAndGet() > MAX_CALLS_PER_TURN) {
            observer.get().invoke(KnowledgeToolExecutionEvent.Failed(reason = null))
            return failureJson("CALL_LIMIT_REACHED", "Web search reached the per-turn call limit.")
        }
        observer.get().invoke(KnowledgeToolExecutionEvent.Started)
        val request =
            parseRequest(paramsJsonString)
                ?: return observedFailure(ToolFailureReason.InvalidArguments)
        val result =
            try {
                runBlocking { knowledgeTool.execute(request) }
            } catch (_: CancellationException) {
                ToolResult.Failure(ToolFailureReason.Cancelled)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                ToolResult.Failure(ToolFailureReason.Cancelled)
            } catch (_: RuntimeException) {
                ToolResult.Failure(ToolFailureReason.Unavailable)
            }
        return serializeObserved(result)
    }

    fun consumeCapturedSources(): List<KnowledgeSource> = capturedSources.getAndSet(emptyList())

    private fun serializeObserved(result: ToolResult): String {
        when (result) {
            is ToolResult.Success ->
                observer.get().invoke(KnowledgeToolExecutionEvent.Succeeded(result.sources.toList()))
            is ToolResult.Failure ->
                observer.get().invoke(KnowledgeToolExecutionEvent.Failed(result.reason))
        }
        return when (result) {
            is ToolResult.Success -> {
                capturedSources.updateAndGet { existing ->
                    (existing + result.sources).distinctBy(KnowledgeSource::canonicalUrl)
                }
                JsonObject().apply {
                    addProperty("ok", true)
                    addProperty("untrustedReference", result.untrustedContext)
                }.toString()
            }
            is ToolResult.Failure -> failureJson(result.reason.code(), result.reason.message())
        }
    }

    private fun observedFailure(reason: ToolFailureReason): String {
        observer.get().invoke(KnowledgeToolExecutionEvent.Failed(reason))
        return failureJson(reason.code(), reason.message())
    }

    @Suppress("ReturnCount")
    private fun parseRequest(raw: String): ToolRequest? {
        val root =
            runCatching { JsonParser.parseString(raw) }
                .getOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: return null
        if (root.keySet() != EXPECTED_ARGUMENTS) return null
        return ToolRequest(
            query = root.strictString("query") ?: return null,
            language = root.strictString("language") ?: return null,
            focus = root.strictString("focus") ?: return null,
        ).takeIf(::validWebSearchRequest)
    }

    private fun JsonObject.strictString(name: String): String? {
        val value = get(name) ?: return null
        return value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
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

    companion object {
        const val MAX_CALLS_PER_TURN = 2
        val EXPECTED_ARGUMENTS = setOf("query", "language", "focus")
        const val TOOL_DESCRIPTION =
            """{"name":"web_search","description":"Search the current web for focused evidence """ +
                """when encyclopedic knowledge is insufficient. Use at most two calls, then answer from """ +
                """the best available evidence.","parameters":{"type":"object","additionalProperties":false,""" +
                """"properties":{"query":{"type":"string","description":"Short web search query."},""" +
                """"language":{"type":"string","pattern":"^[a-z]{2,3}$","description":"Lowercase ISO """ +
                """language code."},"focus":{"type":"string","description":"Specific question or evidence """ +
                """the search must resolve."}},"required":["query","language","focus"]}}"""
    }
}
