package com.jesjobom.ararai.weather

import com.jesjobom.ararai.knowledge.KnowledgeHttpResponse
import com.jesjobom.ararai.knowledge.KnowledgeHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** Dedicated Open-Meteo transport with fixed HTTPS endpoints and bounded responses. */
class UrlConnectionOpenMeteoTransport : KnowledgeHttpTransport {
    override suspend fun get(url: String): KnowledgeHttpResponse = runInterruptible(Dispatchers.IO) {
        val uri = URI(url)
        require(isAllowedOpenMeteoEndpoint(uri)) { "Unsupported Open-Meteo endpoint" }
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
            val body = stream?.use(::readBounded) ?: ByteArray(0)
            KnowledgeHttpResponse(status, connection.contentType, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (output.size() <= MAX_BYTES) {
            if (Thread.interrupted()) throw InterruptedException("Open-Meteo request cancelled")
            val remaining = MAX_BYTES + 1 - output.size()
            val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 128_000
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 8_000
    }
}

internal fun isAllowedOpenMeteoEndpoint(uri: URI): Boolean = uri.scheme == "https" &&
    uri.port == -1 && uri.userInfo == null && uri.fragment == null && uri.rawQuery != null &&
    (
        uri.host == "api.open-meteo.com" && uri.path == "/v1/forecast" ||
            uri.host == "geocoding-api.open-meteo.com" && uri.path == "/v1/search"
        )
