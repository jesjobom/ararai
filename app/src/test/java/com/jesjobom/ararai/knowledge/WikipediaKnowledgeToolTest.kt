package com.jesjobom.ararai.knowledge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.URI

class WikipediaKnowledgeToolTest {
    @Test
    fun `returns bounded multilingual untrusted context and official sources`() = runTest {
        var requestedUrl = ""
        val tool =
            WikipediaKnowledgeTool(
                transport = KnowledgeHttpTransport { url ->
                    requestedUrl = url
                    jsonResponse(
                        """
                        {
                          "query": {
                            "pages": [
                              {
                                "title": "Arara",
                                "extract": "Uma ave brasileira.",
                                "canonicalurl": "https://pt.wikipedia.org/wiki/Arara"
                              }
                            ]
                          }
                        }
                        """,
                    )
                },
                clock = { 123L },
            )

        val result = tool.execute(ToolRequest(" arara azul ", " PT ")) as ToolResult.Success

        assertTrue(requestedUrl.startsWith("https://pt.wikipedia.org/w/api.php?"))
        assertTrue(requestedUrl.contains("gsrsearch=arara+azul"))
        assertTrue(requestedUrl.contains("prop=extracts%7Cinfo"))
        assertEquals("pt.wikipedia.org", URI(requestedUrl).host)
        assertTrue(result.untrustedContext.startsWith("UNTRUSTED EXTERNAL REFERENCE DATA"))
        assertEquals("Arara", result.sources.single().title)
        assertEquals("pt", result.sources.single().language)
        assertEquals(123L, result.sources.single().retrievedAtMillis)
    }

    @Test
    fun `supports detected question languages beyond English and Portuguese`() = runTest {
        var requestedUrl = ""
        val tool =
            WikipediaKnowledgeTool(
                transport = KnowledgeHttpTransport { url ->
                    requestedUrl = url
                    jsonResponse(
                        """
                        {"query":{"pages":[{
                          "title":"Miguel de Cervantes",
                          "extract":"Novelista español.",
                          "canonicalurl":"https://es.wikipedia.org/wiki/Miguel_de_Cervantes"
                        }]}}
                        """,
                    )
                },
            )

        val result = tool.execute(ToolRequest("Miguel de Cervantes", "ES")) as ToolResult.Success

        assertEquals("es.wikipedia.org", URI(requestedUrl).host)
        assertEquals("es", result.sources.single().language)
    }

    @Test
    fun `rejects invalid arguments without network`() = runTest {
        var calls = 0
        val tool =
            WikipediaKnowledgeTool(
                transport = KnowledgeHttpTransport {
                    calls += 1
                    error("must not run")
                },
            )

        val invalidRequests =
            listOf(
                ToolRequest("", "en"),
                ToolRequest("query", "e"),
                ToolRequest("query", "english"),
                ToolRequest("query", "en.evil"),
                ToolRequest("line\nbreak", "en"),
                ToolRequest("x".repeat(201), "en"),
            )

        invalidRequests.forEach { request ->
            assertEquals(
                ToolResult.Failure(ToolFailureReason.InvalidArguments),
                tool.execute(request),
            )
        }
        assertEquals(0, calls)
    }

    @Test
    fun `distinguishes no results from malformed responses`() = runTest {
        val noResults = toolReturning("""{"batchcomplete":true}""")
        assertEquals(
            ToolResult.Failure(ToolFailureReason.NoResults),
            noResults.execute(ToolRequest("missing")),
        )

        val malformedResponses =
            listOf(
                "{",
                "[]",
                """{"query":[]}""",
                """{"query":{"pages":{}}}""",
                """{"query":{"pages":[{}, {}, {}, {}]}}""",
            )
        malformedResponses.forEach { response ->
            assertEquals(
                ToolResult.Failure(ToolFailureReason.MalformedResponse),
                toolReturning(response).execute(ToolRequest("x")),
            )
        }
    }

    @Test
    fun `rejects redirects http errors media types and oversized bodies`() = runTest {
        val rejectedResponses =
            listOf(
                KnowledgeHttpResponse(301, "application/json", "{}".encodeToByteArray()),
                KnowledgeHttpResponse(503, "application/json", "{}".encodeToByteArray()),
                KnowledgeHttpResponse(200, "text/html", "{}".encodeToByteArray()),
                KnowledgeHttpResponse(200, null, "{}".encodeToByteArray()),
                KnowledgeHttpResponse(200, "application/json", ByteArray(256 * 1024 + 1)),
            )

        rejectedResponses.forEach { response ->
            val tool = WikipediaKnowledgeTool(transport = KnowledgeHttpTransport { response })
            assertEquals(
                ToolResult.Failure(ToolFailureReason.Unavailable),
                tool.execute(ToolRequest("x")),
            )
        }

        val excessiveDecodedContent =
            """{"padding":"${"x".repeat(129 * 1024)}"}"""
        assertEquals(
            ToolResult.Failure(ToolFailureReason.MalformedResponse),
            toolReturning(excessiveDecodedContent).execute(ToolRequest("x")),
        )
    }

    @Test
    fun `url connection transport rejects non official endpoints before networking`() = runTest {
        try {
            UrlConnectionKnowledgeHttpTransport().get("https://example.com/w/api.php")
            fail("Expected non-Wikipedia endpoint to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `rejects invalid utf8 and non official canonical urls`() = runTest {
        val invalidUtf8 =
            WikipediaKnowledgeTool(
                transport =
                KnowledgeHttpTransport {
                    KnowledgeHttpResponse(200, "application/json", byteArrayOf(0xC3.toByte(), 0x28))
                },
            )
        assertEquals(
            ToolResult.Failure(ToolFailureReason.MalformedResponse),
            invalidUtf8.execute(ToolRequest("x")),
        )

        val invalidUrls =
            listOf(
                "http://en.wikipedia.org/wiki/Test",
                "https://example.com/wiki/Test",
                "https://en.wikipedia.org:443/wiki/Test",
                "https://en.wikipedia.org/wiki/Test?redirect=yes",
                "https://en.wikipedia.org/wiki/",
            )
        invalidUrls.forEach { canonicalUrl ->
            val result =
                toolReturning(
                    """
                    {"query":{"pages":[
                      {"title":"Test","extract":"Evidence","canonicalurl":"$canonicalUrl"}
                    ]}}
                    """,
                ).execute(ToolRequest("x"))
            assertEquals(ToolResult.Failure(ToolFailureReason.NoResults), result)
        }
    }

    @Test
    fun `bounds source fields and total context`() = runTest {
        val response =
            """
            {"query":{"pages":[
              {
                "title":"${"T".repeat(250)}",
                "extract":"${"A".repeat(3_000)}",
                "canonicalurl":"https://en.wikipedia.org/wiki/First"
              },
              {
                "title":"Second",
                "extract":"${"B".repeat(3_000)}",
                "canonicalurl":"https://en.wikipedia.org/wiki/Second"
              }
            ]}}
            """

        val result = toolReturning(response).execute(ToolRequest("bounded")) as ToolResult.Success

        assertEquals(200, result.sources.first().title.length)
        assertTrue(result.untrustedContext.length <= 5_000)
        assertEquals(2, result.sources.size)
    }

    @Test
    fun `maps total and socket deadlines to timed out`() = runTest {
        var cancelledByDeadline = false
        val totalDeadline =
            WikipediaKnowledgeTool(
                transport =
                KnowledgeHttpTransport {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelledByDeadline = true
                    }
                },
                totalTimeoutMillis = 50,
            )

        assertEquals(
            ToolResult.Failure(ToolFailureReason.TimedOut),
            totalDeadline.execute(ToolRequest("slow")),
        )
        assertTrue(cancelledByDeadline)

        val socketDeadline =
            WikipediaKnowledgeTool(
                transport = KnowledgeHttpTransport { throw SocketTimeoutException("read") },
            )
        assertEquals(
            ToolResult.Failure(ToolFailureReason.TimedOut),
            socketDeadline.execute(ToolRequest("slow")),
        )
    }

    @Test
    fun `maps cooperative cancellation and transport failures`() = runTest {
        val cancelled =
            WikipediaKnowledgeTool(
                transport = KnowledgeHttpTransport { throw CancellationException("cancelled") },
            )
        assertEquals(
            ToolResult.Failure(ToolFailureReason.Cancelled),
            cancelled.execute(ToolRequest("x")),
        )

        val unavailable =
            WikipediaKnowledgeTool(
                transport = KnowledgeHttpTransport { error("network unavailable") },
            )
        assertEquals(
            ToolResult.Failure(ToolFailureReason.Unavailable),
            unavailable.execute(ToolRequest("x")),
        )
    }

    private fun toolReturning(body: String): WikipediaKnowledgeTool = WikipediaKnowledgeTool(
        transport = KnowledgeHttpTransport { jsonResponse(body) },
    )

    private fun jsonResponse(body: String): KnowledgeHttpResponse = KnowledgeHttpResponse(
        status = 200,
        contentType = "application/json; charset=utf-8",
        body = body.trimIndent().encodeToByteArray(),
    )
}
