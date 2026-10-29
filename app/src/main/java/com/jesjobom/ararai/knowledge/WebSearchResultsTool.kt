package com.jesjobom.ararai.knowledge

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Locale

data class WebSearchResultsRequest(val query: String, val limit: Int)

data class WebSearchResultItem(val title: String, val canonicalUrl: String)

sealed interface WebSearchResultsResult {
    data class Success(
        val results: List<WebSearchResultItem>,
        val provider: WebSearchProvider,
        val retrievedAtMillis: Long,
    ) : WebSearchResultsResult

    data class Failure(val reason: ToolFailureReason) : WebSearchResultsResult
}

fun interface WebSearchResultsTool {
    suspend fun search(request: WebSearchResultsRequest): WebSearchResultsResult
}

class ProviderWebSearchResultsTool(
    private val provider: WebSearchProvider,
    private val token: () -> String?,
    private val transport: WebSearchHttpTransport = UrlConnectionWebSearchHttpTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
) : WebSearchResultsTool {
    @Suppress("ReturnCount")
    override suspend fun search(request: WebSearchResultsRequest): WebSearchResultsResult {
        if (!validRequest(request)) return WebSearchResultsResult.Failure(ToolFailureReason.InvalidArguments)
        val apiToken = token()?.trim().takeUnless(String?::isNullOrEmpty)
            ?: return WebSearchResultsResult.Failure(ToolFailureReason.AuthenticationFailed)
        return try {
            withTimeout(if (provider == WebSearchProvider.Tavily) 20_000L else 15_000L) {
                val response = transport.post(provider.request(request, apiToken))
                providerFailure(response)?.let { return@withTimeout WebSearchResultsResult.Failure(it.reason) }
                val entries = parseJsonObject(response)?.array("results")
                    ?: return@withTimeout WebSearchResultsResult.Failure(ToolFailureReason.MalformedResponse)
                val seen = mutableSetOf<String>()
                val results = entries.asSequence()
                    .filter { it.isJsonObject }
                    .map { it.asJsonObject }
                    .map { WebSearchResultItem(it.string("title").take(200), it.string("url")) }
                    .filter { it.title.isNotBlank() && validWidgetExternalUrl(it.canonicalUrl) }
                    .filter { seen.add(it.canonicalUrl) }
                    .take(request.limit)
                    .toList()
                if (results.isEmpty()) {
                    WebSearchResultsResult.Failure(ToolFailureReason.NoResults)
                } else {
                    WebSearchResultsResult.Success(results, provider, clock())
                }
            }
        } catch (_: TimeoutCancellationException) {
            WebSearchResultsResult.Failure(ToolFailureReason.TimedOut)
        } catch (_: SocketTimeoutException) {
            WebSearchResultsResult.Failure(ToolFailureReason.TimedOut)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            WebSearchResultsResult.Failure(ToolFailureReason.Cancelled)
        } catch (_: Exception) {
            WebSearchResultsResult.Failure(ToolFailureReason.Unavailable)
        }
    }

    private fun WebSearchProvider.request(
        request: WebSearchResultsRequest,
        apiToken: String,
    ): WebSearchHttpRequest = when (this) {
        WebSearchProvider.Exa -> WebSearchHttpRequest(
            url = "https://api.exa.ai/search",
            headers = mapOf("x-api-key" to apiToken),
            jsonBody = JsonObject().apply {
                addProperty("query", request.query.trim())
                addProperty("type", "auto")
                addProperty("numResults", request.limit)
            }.toString(),
        )
        WebSearchProvider.Tavily -> WebSearchHttpRequest(
            url = "https://api.tavily.com/search",
            headers = mapOf("Authorization" to "Bearer $apiToken"),
            jsonBody = JsonObject().apply {
                addProperty("query", request.query.trim())
                addProperty("search_depth", "basic")
                addProperty("topic", "general")
                addProperty("max_results", request.limit)
                addProperty("include_answer", false)
                addProperty("include_raw_content", false)
            }.toString(),
        )
    }
}

class FallbackWebSearchResultsTool(private val tools: List<WebSearchResultsTool>) : WebSearchResultsTool {
    init {
        require(tools.isNotEmpty())
    }

    override suspend fun search(request: WebSearchResultsRequest): WebSearchResultsResult {
        var lastFailure: WebSearchResultsResult.Failure? = null
        for (tool in tools) {
            when (val result = tool.search(request)) {
                is WebSearchResultsResult.Success -> return result
                is WebSearchResultsResult.Failure -> {
                    lastFailure = result
                    if (!result.reason.allowsProviderFallback()) break
                }
            }
        }
        return checkNotNull(lastFailure)
    }
}

private fun validRequest(request: WebSearchResultsRequest): Boolean = request.limit in 1..10 &&
    request.query.isNotBlank() &&
    request.query.length <= FocusedWebEvidenceNormalizer.MAX_QUERY_LENGTH &&
    request.query.none(Char::isISOControl)

/** Applied to provider results and again before opening a cached widget link. No in-app site fetch uses this URL. */
fun validPublicWebResultUrl(value: String): Boolean = runCatching {
    if (value.length !in 1..2_048) return@runCatching false
    val uri = URI(value)
    val host = uri.host?.lowercase(Locale.ROOT) ?: return@runCatching false
    val labels = host.split('.')
    uri.scheme.equals("https", ignoreCase = false) &&
        uri.port == -1 &&
        uri.userInfo == null &&
        uri.fragment == null &&
        labels.size >= 2 &&
        labels.all { it.matches(Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")) } &&
        labels.last().matches(Regex("[a-z]{2,63}|xn--[a-z0-9-]{2,59}")) &&
        host !in setOf("localhost", "local.host") &&
        !host.endsWith(".local") &&
        !host.endsWith(".internal")
}.getOrDefault(false)

fun validWidgetExternalUrl(value: String): Boolean {
    val host = runCatching { URI(value).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
    return if (host == "wikipedia.org" || host.endsWith(".wikipedia.org")) {
        validWikipediaArticleUrl(value)
    } else {
        validPublicWebResultUrl(value)
    }
}
