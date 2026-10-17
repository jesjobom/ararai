package com.jesjobom.ararai.knowledge

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.Random

/** A widget-only, two-request lookup. Search metadata stays out of the tool result. */
data class WikipediaPageSummaryRequest(
    val query: String,
    val language: String,
    val selection: String,
    val seed: Long? = null,
)

data class WikipediaPageSummary(
    val title: String,
    val extract: String,
    val canonicalUrl: String,
    val language: String,
    val retrievedAtMillis: Long,
)

enum class WikipediaPageSummaryFailure {
    InvalidArguments,
    NoResults,
    RateLimited,
    Unavailable,
    MalformedResponse,
    TimedOut,
    Cancelled,
    SummaryTooLarge,
}

sealed interface WikipediaPageSummaryResult {
    data class Success(val page: WikipediaPageSummary) : WikipediaPageSummaryResult
    data class Failure(val reason: WikipediaPageSummaryFailure) : WikipediaPageSummaryResult
}

fun interface WikipediaPageSummaryTool {
    suspend fun fetch(request: WikipediaPageSummaryRequest): WikipediaPageSummaryResult
}

@Suppress("TooManyFunctions")
class WikipediaPageSummaryKnowledgeTool(
    private val transport: KnowledgeHttpTransport = UrlConnectionKnowledgeHttpTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val totalTimeoutMillis: Long = 12_000L,
) : WikipediaPageSummaryTool {
    init {
        require(totalTimeoutMillis > 0)
    }

    @Suppress("ReturnCount")
    override suspend fun fetch(request: WikipediaPageSummaryRequest): WikipediaPageSummaryResult {
        val query = request.query.trim()
        val language = request.language.trim().lowercase(Locale.ROOT)
        if (!validRequest(request, query, language)) {
            return WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.InvalidArguments)
        }

        return try {
            withTimeout(totalTimeoutMillis) { fetchSelectedPage(request, query, language) }
        } catch (_: TimeoutCancellationException) {
            WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.TimedOut)
        } catch (_: SocketTimeoutException) {
            WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.TimedOut)
        } catch (_: CancellationException) {
            WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.Cancelled)
        } catch (_: JsonParseException) {
            WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.MalformedResponse)
        } catch (_: Exception) {
            WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.Unavailable)
        }
    }

    private fun validRequest(request: WikipediaPageSummaryRequest, query: String, language: String): Boolean {
        val validSelection = when (request.selection) {
            "first" -> request.seed == null
            "seeded_random" -> request.seed != null && request.seed in 0..MAX_SAFE_SEED
            else -> false
        }
        return query.isNotEmpty() && query.length <= MAX_QUERY_LENGTH &&
            query.none(Char::isISOControl) && validWikipediaLanguage(language) && validSelection
    }

    @Suppress("ReturnCount")
    private suspend fun fetchSelectedPage(
        request: WikipediaPageSummaryRequest,
        query: String,
        language: String,
    ): WikipediaPageSummaryResult {
        val search = when (val response = requestJson(searchUrl(language, query))) {
            is JsonFetch.Failure -> return WikipediaPageSummaryResult.Failure(response.reason)
            is JsonFetch.Success -> response.raw
        }
        val candidates = parseCandidates(search, language)
            ?: return WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.MalformedResponse)
        if (candidates.isEmpty()) return WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.NoResults)
        val index = if (request.selection == "first") 0 else Random(request.seed!!).nextInt(candidates.size)
        val selected = candidates[index]
        val detail = when (val response = requestJson(detailUrl(language, selected.pageId))) {
            is JsonFetch.Failure -> return WikipediaPageSummaryResult.Failure(response.reason)
            is JsonFetch.Success -> response.raw
        }
        return parseDetail(detail, selected, language)
    }

    private sealed interface JsonFetch {
        data class Success(val raw: String) : JsonFetch
        data class Failure(val reason: WikipediaPageSummaryFailure) : JsonFetch
    }

    @Suppress("ReturnCount")
    private suspend fun requestJson(url: String): JsonFetch {
        val response = transport.get(url)
        if (response.status == 429) return JsonFetch.Failure(WikipediaPageSummaryFailure.RateLimited)
        if (response.status != 200) return JsonFetch.Failure(WikipediaPageSummaryFailure.Unavailable)
        if (response.body.size > MAX_RESPONSE_BYTES) {
            return JsonFetch.Failure(WikipediaPageSummaryFailure.SummaryTooLarge)
        }
        if (response.contentType?.substringBefore(';')?.trim()
                ?.equals("application/json", ignoreCase = true) != true
        ) {
            return JsonFetch.Failure(WikipediaPageSummaryFailure.MalformedResponse)
        }
        val raw = runCatching {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(response.body)).toString()
        }.getOrNull() ?: return JsonFetch.Failure(WikipediaPageSummaryFailure.MalformedResponse)
        if (raw.length > MAX_DECODED_CHARS) {
            return JsonFetch.Failure(WikipediaPageSummaryFailure.SummaryTooLarge)
        }
        return JsonFetch.Success(raw)
    }

    private data class Candidate(val pageId: Long, val title: String, val canonicalUrl: String, val index: Int)

    @Suppress("ReturnCount")
    private fun parseCandidates(raw: String, language: String): List<Candidate>? {
        val root = JsonParser.parseString(raw)
        if (!root.isJsonObject) return null
        val query = root.asJsonObject.get("query") ?: return emptyList()
        if (!query.isJsonObject) return null
        val pages = query.asJsonObject.get("pages") ?: return emptyList()
        if (!pages.isJsonArray || pages.asJsonArray.size() > MAX_CANDIDATES) return null
        return pages.asJsonArray.map { element ->
            parseCandidate(element, language) ?: return null
        }.sortedBy(Candidate::index).also { candidates ->
            if (candidates.map(Candidate::pageId).distinct().size != candidates.size ||
                candidates.map(Candidate::index).distinct().size != candidates.size
            ) {
                return null
            }
        }
    }

    @Suppress("ReturnCount")
    private fun parseCandidate(element: com.google.gson.JsonElement, language: String): Candidate? {
        if (!element.isJsonObject) return null
        val page = element.asJsonObject
        val pageId = page.longValue("pageid")?.takeIf { it > 0 } ?: return null
        val title = page.stringValue("title")?.takeIf { it.isNotBlank() && it.length <= MAX_TITLE_LENGTH }
            ?: return null
        val url = page.stringValue("canonicalurl")
            ?.takeIf { it.length <= MAX_URL_LENGTH && validWikipediaPageUrl(it, language) } ?: return null
        val index = page.intValue("index")?.takeIf { it >= 0 } ?: return null
        return Candidate(pageId, title, url, index)
    }

    @Suppress("ReturnCount")
    private fun parseDetail(raw: String, selected: Candidate, language: String): WikipediaPageSummaryResult {
        val root = JsonParser.parseString(raw)
        val query = root.takeIf { it.isJsonObject }?.asJsonObject?.get("query")
        val pages = query?.takeIf { it.isJsonObject }?.asJsonObject?.get("pages")
        if (pages?.isJsonArray != true || pages.asJsonArray.size() != 1 || !pages.asJsonArray[0].isJsonObject) {
            return WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.MalformedResponse)
        }
        val page = pages.asJsonArray[0].asJsonObject
        if (page.longValue("pageid") != selected.pageId || page.stringValue("title") != selected.title ||
            page.stringValue("canonicalurl") != selected.canonicalUrl
        ) {
            return WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.MalformedResponse)
        }
        val extract = page.stringValue("extract")?.takeIf(String::isNotBlank)
            ?: return WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.NoResults)
        val encodedExtractBytes = JsonObject().apply { addProperty("extract", extract) }.toString()
            .toByteArray(StandardCharsets.UTF_8).size
        if (extract.length > MAX_EXTRACT_CHARS ||
            extract.toByteArray(StandardCharsets.UTF_8).size > MAX_EXTRACT_BYTES ||
            encodedExtractBytes > MAX_ENCODED_EXTRACT_BYTES
        ) {
            return WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.SummaryTooLarge)
        }
        return WikipediaPageSummaryResult.Success(
            WikipediaPageSummary(selected.title, extract, selected.canonicalUrl, language, clock()),
        )
    }

    private fun JsonObject.stringValue(name: String): String? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()

    private fun JsonObject.longValue(name: String): Long? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asJsonPrimitive?.let { value ->
            runCatching { value.asBigDecimal.toBigIntegerExact().toString().toLongOrNull() }.getOrNull()
        }

    private fun JsonObject.intValue(name: String): Int? = longValue(name)
        ?.takeIf { it in 0..Int.MAX_VALUE }
        ?.toInt()

    private fun searchUrl(language: String, query: String): String {
        val base = "https://$language.wikipedia.org/w/api.php"
        return "$base?action=query&generator=search" +
            "&gsrsearch=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}" +
            "&gsrlimit=$MAX_CANDIDATES&prop=info&inprop=url&format=json&formatversion=2"
    }

    private fun detailUrl(language: String, pageId: Long): String {
        val base = "https://$language.wikipedia.org/w/api.php"
        return "$base?action=query&pageids=$pageId" +
            "&prop=extracts%7Cinfo&exintro=1&explaintext=1&inprop=url&format=json&formatversion=2"
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 200
        const val MAX_TITLE_LENGTH = 200
        const val MAX_URL_LENGTH = 2_048
        const val MAX_CANDIDATES = 10
        const val MAX_RESPONSE_BYTES = 256 * 1024
        const val MAX_DECODED_CHARS = 128 * 1024
        const val MAX_EXTRACT_CHARS = 16_384
        const val MAX_EXTRACT_BYTES = 32 * 1024
        const val MAX_ENCODED_EXTRACT_BYTES = 48 * 1024
        const val MAX_SAFE_SEED = 9_007_199_254_740_991L
    }
}
