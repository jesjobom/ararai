package com.jesjobom.ararai.knowledge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class WebSearchProviderToolsTest {
    @Test
    fun `tavily searches then extracts focused chunks without requesting an answer`() = runTest {
        val transport =
            QueueWebSearchTransport(
                jsonResponse(
                    """
                    {"results":[
                      {"title":"Tocantins","url":"https://example.com/tocantins","content":"fallback"},
                      {"title":"Brazil","url":"https://example.com/brazil","content":"fallback 2"}
                    ]}
                    """,
                ),
                jsonResponse(
                    """
                    {"results":[
                      {"url":"https://example.com/tocantins","raw_content":"Created in 1988 [...] Installed in 1989."},
                      {"url":"https://example.com/brazil","raw_content":"Brazil has 26 states."}
                    ]}
                    """,
                ),
            )
        val result =
            TavilyKnowledgeTool(token = { "tvly-test" }, transport = transport)
                .execute(ToolRequest("latest Brazilian state", "en", "name and creation date"))

        assertTrue(result is ToolResult.Success)
        result as ToolResult.Success
        assertEquals(2, result.sources.size)
        assertTrue(result.untrustedContext.contains("Created in 1988"))
        assertEquals(listOf("https://api.tavily.com/search", "https://api.tavily.com/extract"), transport.urls)
        assertTrue(transport.requests.first().jsonBody.contains("\"include_answer\":false"))
        assertFalse(transport.requests.first().jsonBody.contains("tvly-test"))
        assertEquals("Bearer tvly-test", transport.requests.first().headers["Authorization"])
    }

    @Test
    fun `exa requests highlights and normalizes evidence`() = runTest {
        val transport =
            QueueWebSearchTransport(
                jsonResponse(
                    """
                    {"results":[{
                      "title":"Release notes",
                      "url":"https://example.com/release",
                      "highlights":["Version 2 shipped today.","It adds focused retrieval."]
                    }]}
                    """,
                ),
            )
        val result =
            ExaKnowledgeTool(token = { "exa-test" }, transport = transport)
                .execute(ToolRequest("latest release", "en", "version and date"))

        assertTrue(result is ToolResult.Success)
        result as ToolResult.Success
        assertEquals("Exa Web Search", result.sources.single().provider)
        assertTrue(result.untrustedContext.contains("Version 2 shipped today."))
        assertTrue(transport.requests.single().jsonBody.contains("\"highlights\""))
        assertFalse(transport.requests.single().jsonBody.contains("\"summary\""))
        assertEquals("exa-test", transport.requests.single().headers["x-api-key"])
    }

    @Test
    fun `provider errors map to controlled failures without parsing their body`() = runTest {
        listOf(
            401 to ToolFailureReason.AuthenticationFailed,
            402 to ToolFailureReason.QuotaExceeded,
            429 to ToolFailureReason.RateLimited,
            500 to ToolFailureReason.Unavailable,
        ).forEach { (status, expected) ->
            val tool =
                ExaKnowledgeTool(
                    token = { "secret-token" },
                    transport =
                    QueueWebSearchTransport(
                        KnowledgeHttpResponse(
                            status,
                            "application/json",
                            """{"error":"secret-token"}""".toByteArray(),
                        ),
                    ),
                )
            assertEquals(
                ToolResult.Failure(expected),
                tool.execute(ToolRequest("query", "en", "focus")),
            )
        }
    }

    @Test
    fun `missing token and invalid request fail before network`() = runTest {
        val transport = QueueWebSearchTransport()
        assertEquals(
            ToolResult.Failure(ToolFailureReason.AuthenticationFailed),
            ExaKnowledgeTool(token = { null }, transport = transport)
                .execute(ToolRequest("query", "en", "focus")),
        )
        assertEquals(
            ToolResult.Failure(ToolFailureReason.InvalidArguments),
            TavilyKnowledgeTool(token = { "token" }, transport = transport)
                .execute(ToolRequest("", "en", "focus")),
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `malformed response is controlled`() = runTest {
        val result =
            ExaKnowledgeTool(
                token = { "token" },
                transport = QueueWebSearchTransport(jsonResponse("""{"unexpected":[]}""")),
            ).execute(ToolRequest("query", "en", "focus"))

        assertEquals(ToolResult.Failure(ToolFailureReason.MalformedResponse), result)
    }

    @Test
    fun `invalid utf8 and oversized bodies fail closed`() = runTest {
        val invalidUtf8 =
            ExaKnowledgeTool(
                token = { "token" },
                transport =
                QueueWebSearchTransport(
                    KnowledgeHttpResponse(200, "application/json", byteArrayOf(0xC3.toByte(), 0x28)),
                ),
            ).execute(ToolRequest("query", "en", "focus"))
        val oversized =
            ExaKnowledgeTool(
                token = { "token" },
                transport =
                QueueWebSearchTransport(
                    KnowledgeHttpResponse(
                        200,
                        "application/json",
                        ByteArray(256 * 1024 + 1) { 'a'.code.toByte() },
                    ),
                ),
            ).execute(ToolRequest("query", "en", "focus"))

        assertEquals(ToolResult.Failure(ToolFailureReason.MalformedResponse), invalidUtf8)
        assertEquals(ToolResult.Failure(ToolFailureReason.Unavailable), oversized)
    }

    @Test
    fun `timeout and cancellation map identically for both providers`() = runTest {
        listOf<(WebSearchHttpTransport) -> KnowledgeTool>(
            { transport -> TavilyKnowledgeTool(token = { "token" }, transport = transport) },
            { transport -> ExaKnowledgeTool(token = { "token" }, transport = transport) },
        ).forEach { factory ->
            assertEquals(
                ToolResult.Failure(ToolFailureReason.TimedOut),
                factory(WebSearchHttpTransport { throw SocketTimeoutException() })
                    .execute(ToolRequest("query", "en", "focus")),
            )
            assertEquals(
                ToolResult.Failure(ToolFailureReason.Cancelled),
                factory(
                    WebSearchHttpTransport { throw CancellationException() },
                )
                    .execute(ToolRequest("query", "en", "focus")),
            )
        }
    }
}

private class QueueWebSearchTransport(
    vararg responses: KnowledgeHttpResponse,
) : WebSearchHttpTransport {
    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<WebSearchHttpRequest>()
    val urls: List<String> get() = requests.map(WebSearchHttpRequest::url)

    override suspend fun post(request: WebSearchHttpRequest): KnowledgeHttpResponse {
        requests += request
        return queue.removeFirst()
    }
}

private fun jsonResponse(body: String) = KnowledgeHttpResponse(
    200,
    "application/json; charset=utf-8",
    body.trimIndent().toByteArray(),
)
