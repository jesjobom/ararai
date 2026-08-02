package com.jesjobom.ararai.knowledge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class TavilyKnowledgeTool(
    private val token: () -> String?,
    private val transport: WebSearchHttpTransport = UrlConnectionWebSearchHttpTransport(),
    private val normalizer: FocusedWebEvidenceNormalizer = FocusedWebEvidenceNormalizer(),
    private val totalTimeoutMillis: Long = DEFAULT_TOTAL_TIMEOUT_MILLIS,
) : KnowledgeTool {
    override val displayName: String = WebSearchProvider.Tavily.displayName

    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    override suspend fun execute(request: ToolRequest): ToolResult {
        if (!validWebSearchRequest(request)) return ToolResult.Failure(ToolFailureReason.InvalidArguments)
        val apiToken = token()?.trim().takeUnless(String?::isNullOrEmpty)
            ?: return ToolResult.Failure(ToolFailureReason.AuthenticationFailed)
        return guardedProviderCall(totalTimeoutMillis) {
            val search =
                transport.post(
                    WebSearchHttpRequest(
                        url = TAVILY_SEARCH_URL,
                        headers = mapOf("Authorization" to "Bearer $apiToken"),
                        jsonBody =
                        JsonObject().apply {
                            addProperty("query", request.query.trim())
                            addProperty("search_depth", "basic")
                            addProperty("topic", "general")
                            addProperty("max_results", FocusedWebEvidenceNormalizer.MAX_SOURCES)
                            addProperty("include_answer", false)
                            addProperty("include_raw_content", false)
                        }.toString(),
                    ),
                )
            providerFailure(search)?.let { return@guardedProviderCall it }
            val searchRoot = parseJsonObject(search) ?: return@guardedProviderCall malformed()
            val searchResults = searchRoot.array("results") ?: return@guardedProviderCall malformed()
            val pages =
                searchResults.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.asJsonObject?.let { item ->
                        SearchPage(
                            title = item.string("title"),
                            url = item.string("url"),
                            fallbackExcerpt = item.string("content"),
                        )
                    }
                }.filter { it.title.isNotEmpty() && it.url.isNotEmpty() }
                    .take(FocusedWebEvidenceNormalizer.MAX_SOURCES)
            if (pages.isEmpty()) return@guardedProviderCall ToolResult.Failure(ToolFailureReason.NoResults)

            val extract =
                transport.post(
                    WebSearchHttpRequest(
                        url = TAVILY_EXTRACT_URL,
                        headers = mapOf("Authorization" to "Bearer $apiToken"),
                        jsonBody =
                        JsonObject().apply {
                            add(
                                "urls",
                                JsonArray().also { urls -> pages.forEach { urls.add(it.url) } },
                            )
                            addProperty("query", request.focus.trim())
                            addProperty(
                                "chunks_per_source",
                                FocusedWebEvidenceNormalizer.MAX_EXCERPTS_PER_SOURCE,
                            )
                            addProperty("extract_depth", "basic")
                            addProperty("format", "text")
                        }.toString(),
                    ),
                )
            providerFailure(extract)?.let { return@guardedProviderCall it }
            val extractRoot = parseJsonObject(extract) ?: return@guardedProviderCall malformed()
            val extractedByUrl =
                extractRoot.array("results")
                    ?.mapNotNull { element ->
                        element.takeIf { it.isJsonObject }?.asJsonObject?.let { item ->
                            item.string("url").takeIf(String::isNotEmpty)?.let { url ->
                                url to splitProviderChunks(item.string("raw_content"))
                            }
                        }
                    }?.toMap()
                    ?: return@guardedProviderCall malformed()
            normalizer.normalize(
                provider = WebSearchProvider.Tavily,
                language = request.language,
                candidates =
                pages.map { page ->
                    FocusedEvidenceCandidate(
                        title = page.title,
                        canonicalUrl = page.url,
                        excerpts =
                        extractedByUrl[page.url]
                            .orEmpty()
                            .ifEmpty { listOf(page.fallbackExcerpt) },
                    )
                },
            )
        }
    }

    private data class SearchPage(
        val title: String,
        val url: String,
        val fallbackExcerpt: String,
    )

    private companion object {
        const val TAVILY_SEARCH_URL = "https://api.tavily.com/search"
        const val TAVILY_EXTRACT_URL = "https://api.tavily.com/extract"
        const val DEFAULT_TOTAL_TIMEOUT_MILLIS = 20_000L
    }
}

class ExaKnowledgeTool(
    private val token: () -> String?,
    private val transport: WebSearchHttpTransport = UrlConnectionWebSearchHttpTransport(),
    private val normalizer: FocusedWebEvidenceNormalizer = FocusedWebEvidenceNormalizer(),
    private val totalTimeoutMillis: Long = DEFAULT_TOTAL_TIMEOUT_MILLIS,
) : KnowledgeTool {
    override val displayName: String = WebSearchProvider.Exa.displayName

    @Suppress("ReturnCount")
    override suspend fun execute(request: ToolRequest): ToolResult {
        if (!validWebSearchRequest(request)) return ToolResult.Failure(ToolFailureReason.InvalidArguments)
        val apiToken = token()?.trim().takeUnless(String?::isNullOrEmpty)
            ?: return ToolResult.Failure(ToolFailureReason.AuthenticationFailed)
        return guardedProviderCall(totalTimeoutMillis) {
            val response =
                transport.post(
                    WebSearchHttpRequest(
                        url = EXA_SEARCH_URL,
                        headers = mapOf("x-api-key" to apiToken),
                        jsonBody =
                        JsonObject().apply {
                            addProperty("query", request.query.trim())
                            addProperty("type", "auto")
                            addProperty("numResults", FocusedWebEvidenceNormalizer.MAX_SOURCES)
                            add(
                                "contents",
                                JsonObject().apply {
                                    add(
                                        "highlights",
                                        JsonObject().apply {
                                            addProperty("query", request.focus.trim())
                                            addProperty(
                                                "maxCharacters",
                                                FocusedWebEvidenceNormalizer.MAX_EXCERPT_LENGTH *
                                                    FocusedWebEvidenceNormalizer.MAX_EXCERPTS_PER_SOURCE,
                                            )
                                        },
                                    )
                                },
                            )
                        }.toString(),
                    ),
                )
            providerFailure(response)?.let { return@guardedProviderCall it }
            val root = parseJsonObject(response) ?: return@guardedProviderCall malformed()
            val results = root.array("results") ?: return@guardedProviderCall malformed()
            normalizer.normalize(
                provider = WebSearchProvider.Exa,
                language = request.language,
                candidates =
                results.mapNotNull { element ->
                    element.takeIf { it.isJsonObject }?.asJsonObject?.let { item ->
                        FocusedEvidenceCandidate(
                            title = item.string("title"),
                            canonicalUrl = item.string("url"),
                            excerpts =
                            item.array("highlights")
                                ?.mapNotNull { highlight ->
                                    highlight.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                                        ?.asString
                                }.orEmpty(),
                        )
                    }
                },
            )
        }
    }

    private companion object {
        const val EXA_SEARCH_URL = "https://api.exa.ai/search"
        const val DEFAULT_TOTAL_TIMEOUT_MILLIS = 15_000L
    }
}

private suspend fun guardedProviderCall(
    totalTimeoutMillis: Long,
    block: suspend () -> ToolResult,
): ToolResult = try {
    withTimeout(totalTimeoutMillis) { block() }
} catch (_: TimeoutCancellationException) {
    ToolResult.Failure(ToolFailureReason.TimedOut)
} catch (_: SocketTimeoutException) {
    ToolResult.Failure(ToolFailureReason.TimedOut)
} catch (_: CancellationException) {
    ToolResult.Failure(ToolFailureReason.Cancelled)
} catch (_: JsonParseException) {
    malformed()
} catch (_: InterruptedException) {
    Thread.currentThread().interrupt()
    ToolResult.Failure(ToolFailureReason.Cancelled)
} catch (_: Exception) {
    ToolResult.Failure(ToolFailureReason.Unavailable)
}

@Suppress("ReturnCount")
private fun providerFailure(response: KnowledgeHttpResponse): ToolResult.Failure? {
    if (response.body.size > MAX_PROVIDER_RESPONSE_BYTES) {
        return ToolResult.Failure(ToolFailureReason.Unavailable)
    }
    if (response.status in 200..299) {
        return if (response.contentType.isJsonContentType()) null else malformed()
    }
    return ToolResult.Failure(
        when (response.status) {
            401, 403 -> ToolFailureReason.AuthenticationFailed
            402 -> ToolFailureReason.QuotaExceeded
            429 -> ToolFailureReason.RateLimited
            else -> ToolFailureReason.Unavailable
        },
    )
}

@Suppress("ReturnCount")
private fun parseJsonObject(response: KnowledgeHttpResponse): JsonObject? {
    val decoded =
        runCatching {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(response.body))
                .toString()
        }.getOrNull() ?: return null
    if (decoded.length > MAX_PROVIDER_DECODED_CHARS) return null
    return JsonParser.parseString(decoded).takeIf { it.isJsonObject }?.asJsonObject
}

private fun String?.isJsonContentType(): Boolean = this
    ?.substringBefore(';')
    ?.trim()
    ?.equals("application/json", ignoreCase = true) == true

private fun JsonObject.string(name: String): String {
    val value = get(name) ?: return ""
    return value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?.trim()
        .orEmpty()
}

private fun JsonObject.array(name: String): JsonArray? = get(name)?.takeIf { it.isJsonArray }?.asJsonArray

private fun splitProviderChunks(raw: String): List<String> = raw
    .split(Regex("\\s*\\[\\.\\.\\.]\\s*"))
    .map(String::trim)
    .filter(String::isNotEmpty)

private fun malformed() = ToolResult.Failure(ToolFailureReason.MalformedResponse)

private const val MAX_PROVIDER_RESPONSE_BYTES = 256 * 1024
private const val MAX_PROVIDER_DECODED_CHARS = 128 * 1024
