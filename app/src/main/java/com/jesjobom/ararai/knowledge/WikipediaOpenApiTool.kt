package com.jesjobom.ararai.knowledge

import com.google.ai.edge.litertlm.OpenApiTool
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
    knowledgeTool: KnowledgeTool,
) : OpenApiTool {
    private val turn = WikipediaToolTurn(knowledgeTool)

    override fun getToolDescriptionJsonString(): String = WikipediaToolTurn.TOOL_DESCRIPTION

    override fun execute(paramsJsonString: String): String = turn.execute(paramsJsonString)

    fun consumeCapturedSources(): List<KnowledgeSource> = turn.consumeCapturedSources()

    /**
     * Opens a fresh invocation allowance before submitting the next user turn.
     *
     * LiteRT-LM retains the registered tool instance with its conversation, so callers reusing a
     * conversation must mark each user-turn boundary explicitly.
     */
    fun beginTurn(observer: (KnowledgeToolExecutionEvent) -> Unit = {}) = turn.beginTurn(observer)
}

sealed interface KnowledgeToolExecutionEvent {
    data object Started : KnowledgeToolExecutionEvent

    data class Succeeded(
        val sources: List<KnowledgeSource>,
    ) : KnowledgeToolExecutionEvent

    data class Failed(
        val reason: ToolFailureReason?,
    ) : KnowledgeToolExecutionEvent
}

/**
 * JVM-testable turn state kept separate from LiteRT-LM's Java 21-compiled interface.
 *
 * The Android adapter above remains deliberately thin; physical characterization exercises that
 * runtime boundary.
 */
internal class WikipediaToolTurn(
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
            return failureJson(ToolAdapterFailure.CallLimitReached)
        }
        observer.get().invoke(KnowledgeToolExecutionEvent.Started)

        val request =
            parseRequest(paramsJsonString)
                ?: return observedFailure(
                    adapterFailure = ToolAdapterFailure.InvalidArguments,
                    reason = ToolFailureReason.InvalidArguments,
                )
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
                observer.get().invoke(KnowledgeToolExecutionEvent.Succeeded(result.sources.toList()))
            is ToolResult.Failure ->
                observer.get().invoke(KnowledgeToolExecutionEvent.Failed(result.reason))
        }
        return serialize(result)
    }

    private fun observedFailure(
        adapterFailure: ToolAdapterFailure,
        reason: ToolFailureReason,
    ): String {
        observer.get().invoke(KnowledgeToolExecutionEvent.Failed(reason))
        return failureJson(adapterFailure)
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
        val query = root.strictString("query") ?: return null
        val language = root.strictString("language") ?: return null
        return ToolRequest(query = query, language = language)
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

    @Suppress("ReturnCount")
    private fun JsonObject.strictString(name: String): String? {
        val value = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString
    }

    private fun ToolFailureReason.toAdapterFailure(): ToolAdapterFailure = when (this) {
        ToolFailureReason.InvalidArguments -> ToolAdapterFailure.InvalidArguments
        ToolFailureReason.NoResults -> ToolAdapterFailure.NoResults
        ToolFailureReason.Unavailable -> ToolAdapterFailure.Unavailable
        ToolFailureReason.MalformedResponse -> ToolAdapterFailure.MalformedResponse
        ToolFailureReason.TimedOut -> ToolAdapterFailure.TimedOut
        ToolFailureReason.Cancelled -> ToolAdapterFailure.Cancelled
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
            """{"name":"wikipedia_search","description":"Search a Wikipedia language edition for """ +
                """encyclopedic facts. Search English first; if its result is unsatisfactory, use the """ +
                """detected language of the user's question.","parameters":""" +
                """{"type":"object","additionalProperties":false,"properties":{"query":{"type":"string"},""" +
                """"language":{"type":"string","pattern":"^[a-z]{2,3}$","description":"Lowercase ISO """ +
                """language code for the Wikipedia edition."}},"required":["query","language"]}}"""
    }
}
