package com.jesjobom.ararai.knowledge

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

private val WIKIPEDIA_YEAR_PATTERN = Regex("-?(0|[1-9][0-9]*)")

data class WikipediaOnThisDayRequest(
    val month: Int,
    val day: Int,
    val language: String,
)

data class WikipediaHistoricalEvent(
    val year: Int,
    val text: String,
    val title: String,
    val canonicalUrl: String,
) {
    init {
        require(text.isNotBlank() && text.length <= MAX_EVENT_TEXT_LENGTH)
        require(title.isNotBlank() && title.length <= MAX_EVENT_TITLE_LENGTH)
        require(canonicalUrl.length <= MAX_EVENT_URL_LENGTH)
        require(validWikipediaArticleUrl(canonicalUrl))
    }

    companion object {
        const val MAX_EVENT_TEXT_LENGTH = 1_000
        const val MAX_EVENT_TITLE_LENGTH = 200
        const val MAX_EVENT_URL_LENGTH = 2_048
    }
}

sealed interface WikipediaOnThisDayResult {
    data class Success(
        val events: List<WikipediaHistoricalEvent>,
        val language: String,
        val retrievedAtMillis: Long,
    ) : WikipediaOnThisDayResult {
        init {
            require(events.size in 1..MAX_EVENTS)
            require(validWikipediaLanguage(language))
            require(retrievedAtMillis >= 0)
        }
    }

    data class Failure(val reason: ToolFailureReason) : WikipediaOnThisDayResult

    companion object {
        const val MAX_EVENTS = 64
    }
}

fun interface WikipediaOnThisDayTool {
    suspend fun fetch(request: WikipediaOnThisDayRequest): WikipediaOnThisDayResult
}

class WikipediaOnThisDayKnowledgeTool(
    private val transport: KnowledgeHttpTransport = UrlConnectionWikipediaOnThisDayHttpTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val totalTimeoutMillis: Long = DEFAULT_TOTAL_TIMEOUT_MILLIS,
) : WikipediaOnThisDayTool {
    init {
        require(totalTimeoutMillis > 0)
    }

    @Suppress("ReturnCount")
    override suspend fun fetch(request: WikipediaOnThisDayRequest): WikipediaOnThisDayResult {
        val language = request.language.trim().lowercase(Locale.ROOT)
        if (!validWikipediaLanguage(language) || !validCalendarDay(request.month, request.day)) {
            return WikipediaOnThisDayResult.Failure(ToolFailureReason.InvalidArguments)
        }
        return try {
            val response = withTimeout(totalTimeoutMillis) {
                transport.get(endpoint(language, request.month, request.day))
            }
            if (response.status != HTTP_OK ||
                response.body.size > MAX_RESPONSE_BYTES ||
                !isJsonContentType(response.contentType)
            ) {
                return WikipediaOnThisDayResult.Failure(ToolFailureReason.Unavailable)
            }
            val decoded = decodeUtf8(response.body)
                ?: return WikipediaOnThisDayResult.Failure(ToolFailureReason.MalformedResponse)
            if (decoded.length > MAX_DECODED_CHARS) {
                return WikipediaOnThisDayResult.Failure(ToolFailureReason.MalformedResponse)
            }
            parse(decoded, language)
        } catch (_: TimeoutCancellationException) {
            WikipediaOnThisDayResult.Failure(ToolFailureReason.TimedOut)
        } catch (_: SocketTimeoutException) {
            WikipediaOnThisDayResult.Failure(ToolFailureReason.TimedOut)
        } catch (_: CancellationException) {
            WikipediaOnThisDayResult.Failure(ToolFailureReason.Cancelled)
        } catch (_: JsonParseException) {
            WikipediaOnThisDayResult.Failure(ToolFailureReason.MalformedResponse)
        } catch (_: Exception) {
            WikipediaOnThisDayResult.Failure(ToolFailureReason.Unavailable)
        }
    }

    @Suppress("ReturnCount")
    private fun parse(raw: String, language: String): WikipediaOnThisDayResult {
        val root = JsonParser.parseString(raw)
        if (!root.isJsonObject) return malformed()
        val eventsValue = root.asJsonObject.get("events") ?: return malformed()
        if (!eventsValue.isJsonArray) return malformed()
        if (eventsValue.asJsonArray.size() == 0) {
            return WikipediaOnThisDayResult.Failure(ToolFailureReason.NoResults)
        }
        val events = eventsValue.asJsonArray
            .asSequence()
            .mapNotNull { value -> value.takeIf { it.isJsonObject }?.asJsonObject?.event(language) }
            .take(WikipediaOnThisDayResult.MAX_EVENTS)
            .toList()
        return if (events.isEmpty()) {
            WikipediaOnThisDayResult.Failure(ToolFailureReason.NoResults)
        } else {
            WikipediaOnThisDayResult.Success(events, language, clock())
        }
    }

    private fun JsonObject.event(language: String): WikipediaHistoricalEvent? = runCatching {
        val yearValue = checkNotNull(get("year")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive)
        require(yearValue.isNumber)
        val year = checkNotNull(yearValue.asString.takeIf(WIKIPEDIA_YEAR_PATTERN::matches)?.toIntOrNull())
        val text = checkNotNull(strictString("text"))
            .normalizedPlainText(WikipediaHistoricalEvent.MAX_EVENT_TEXT_LENGTH)
        require(text.isNotBlank())
        val pages = checkNotNull(get("pages")?.takeIf { it.isJsonArray }?.asJsonArray)
        val page = checkNotNull(
            pages.asSequence()
                .mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject?.relatedPage(language) }
                .firstOrNull(),
        )
        WikipediaHistoricalEvent(year, text, page.first, page.second)
    }.getOrNull()

    private fun JsonObject.relatedPage(language: String): Pair<String, String>? = runCatching {
        val title = checkNotNull(strictString("title"))
            .replace('_', ' ')
            .normalizedPlainText(WikipediaHistoricalEvent.MAX_EVENT_TITLE_LENGTH)
        require(title.isNotBlank())
        val contentUrls = checkNotNull(get("content_urls")?.takeIf { it.isJsonObject }?.asJsonObject)
        val desktop = checkNotNull(contentUrls.get("desktop")?.takeIf { it.isJsonObject }?.asJsonObject)
        val canonicalUrl = checkNotNull(desktop.strictString("page"))
        require(canonicalUrl.length <= WikipediaHistoricalEvent.MAX_EVENT_URL_LENGTH)
        require(validWikipediaPageUrl(canonicalUrl, language))
        title to canonicalUrl
    }.getOrNull()

    private fun JsonObject.strictString(name: String): String? = get(name)
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString

    private fun String.normalizedPlainText(maxLength: Int): String = trim()
        .replace(WHITESPACE, " ")
        .take(maxLength)
        .trim()

    private fun endpoint(language: String, month: Int, day: Int): String {
        val host = "https://$language.wikipedia.org"
        val datePath = "%02d/%02d".format(Locale.ROOT, month, day)
        return host + EVENTS_PATH_PREFIX + datePath
    }

    private fun malformed() = WikipediaOnThisDayResult.Failure(ToolFailureReason.MalformedResponse)

    private fun validCalendarDay(month: Int, day: Int): Boolean = try {
        LocalDate.of(LEAP_YEAR, month, day)
        true
    } catch (_: DateTimeException) {
        false
    }

    private fun isJsonContentType(value: String?): Boolean = value
        ?.substringBefore(';')
        ?.trim()
        ?.equals("application/json", ignoreCase = true) == true

    private fun decodeUtf8(body: ByteArray): String? = runCatching {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(body))
            .toString()
    }.getOrNull()

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val LEAP_YEAR = 2000
        const val EVENTS_PATH_PREFIX = "/api/rest_v1/feed/onthisday/events/"
        const val HTTP_OK = 200
        const val MAX_RESPONSE_BYTES = 1024 * 1024
        const val MAX_DECODED_CHARS = 1024 * 1024
        const val DEFAULT_TOTAL_TIMEOUT_MILLIS = 12_000L
    }
}

class UrlConnectionWikipediaOnThisDayHttpTransport : KnowledgeHttpTransport {
    override suspend fun get(url: String): KnowledgeHttpResponse = runInterruptible(Dispatchers.IO) {
        val uri = URI(url)
        require(
            uri.scheme == "https" &&
                validWikipediaApiHost(uri.host) &&
                ON_THIS_DAY_PATH.matches(uri.path) &&
                uri.port == -1 &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null,
        )
        val connection = (uri.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ArarAI/1.0 (local Android assistant)")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            KnowledgeHttpResponse(status, connection.contentType, stream?.use(::readBounded) ?: ByteArray(0))
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (output.size() <= MAX_BYTES) {
            if (Thread.interrupted()) throw InterruptedException("Wikipedia request cancelled")
            val remaining = MAX_BYTES + 1 - output.size()
            val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        val ON_THIS_DAY_PATH = Regex("^/api/rest_v1/feed/onthisday/events/(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])$")
        const val MAX_BYTES = 1024 * 1024
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 8_000
    }
}
