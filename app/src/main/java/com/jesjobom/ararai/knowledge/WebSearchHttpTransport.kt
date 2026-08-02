package com.jesjobom.ararai.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

data class WebSearchHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val jsonBody: String,
)

fun interface WebSearchHttpTransport {
    suspend fun post(request: WebSearchHttpRequest): KnowledgeHttpResponse
}

class UrlConnectionWebSearchHttpTransport : WebSearchHttpTransport {
    override suspend fun post(request: WebSearchHttpRequest): KnowledgeHttpResponse = runInterruptible(Dispatchers.IO) {
        val uri = URI(request.url)
        require(
            uri.scheme == "https" &&
                uri.host in ALLOWED_HOSTS &&
                uri.path in ALLOWED_PATHS.getValue(uri.host) &&
                uri.port == -1 &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null,
        )
        val connection =
            (uri.toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                instanceFollowRedirects = false
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(request.jsonBody.toByteArray(StandardCharsets.UTF_8).size)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "ArarAI/1.0 (local Android assistant)")
                request.headers.forEach(::setRequestProperty)
            }
        try {
            connection.outputStream.use { output ->
                output.write(request.jsonBody.toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            KnowledgeHttpResponse(
                status = status,
                contentType = connection.contentType,
                body = stream?.use(::readBounded) ?: ByteArray(0),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (output.size() <= MAX_RESPONSE_BYTES) {
            if (Thread.interrupted()) throw InterruptedException("Web-search request cancelled")
            val remaining = MAX_RESPONSE_BYTES + 1 - output.size()
            val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        val ALLOWED_HOSTS = setOf("api.tavily.com", "api.exa.ai")
        val ALLOWED_PATHS =
            mapOf(
                "api.tavily.com" to setOf("/search", "/extract"),
                "api.exa.ai" to setOf("/search"),
            )
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 12_000
        const val MAX_RESPONSE_BYTES = 256 * 1024
    }
}
