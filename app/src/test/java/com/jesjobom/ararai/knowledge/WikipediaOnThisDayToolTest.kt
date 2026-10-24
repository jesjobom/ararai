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

class WikipediaOnThisDayToolTest {
    @Test
    fun `returns bounded structured events from the official language feed`() = runTest {
        var requestedUrl = ""
        val tool = WikipediaOnThisDayKnowledgeTool(
            transport = KnowledgeHttpTransport { url ->
                requestedUrl = url
                jsonResponse(
                    """
                    {"events":[
                      {"year":2016,"text":"A historical event.","pages":[{
                        "title":"Related_article",
                        "content_urls":{"desktop":{"page":"https://pt.wikipedia.org/wiki/Related_article"}}
                      }]}
                    ]}
                    """,
                )
            },
            clock = { 42L },
        )

        val result = tool.fetch(WikipediaOnThisDayRequest(9, 9, "PT")) as WikipediaOnThisDayResult.Success

        assertEquals("pt.wikipedia.org", URI(requestedUrl).host)
        assertEquals("/api/rest_v1/feed/onthisday/events/09/09", URI(requestedUrl).path)
        assertEquals("pt", result.language)
        assertEquals(42L, result.retrievedAtMillis)
        assertEquals(
            WikipediaHistoricalEvent(
                year = 2016,
                text = "A historical event.",
                title = "Related article",
                canonicalUrl = "https://pt.wikipedia.org/wiki/Related_article",
            ),
            result.events.single(),
        )
    }

    @Test
    fun `rejects impossible dates and invalid languages before networking`() = runTest {
        var calls = 0
        val tool = WikipediaOnThisDayKnowledgeTool(
            transport = KnowledgeHttpTransport {
                calls++
                error("must not run")
            },
        )

        listOf(
            WikipediaOnThisDayRequest(2, 30, "en"),
            WikipediaOnThisDayRequest(13, 1, "en"),
            WikipediaOnThisDayRequest(9, 9, "english"),
            WikipediaOnThisDayRequest(9, 9, "en.evil"),
        ).forEach { request ->
            assertEquals(
                WikipediaOnThisDayResult.Failure(ToolFailureReason.InvalidArguments),
                tool.fetch(request),
            )
        }
        assertEquals(0, calls)
    }

    @Test
    fun `filters malformed events and canonicalizes bounded plain text`() = runTest {
        val events = (1..70).joinToString(",") { index ->
            """{"year":$index,"text":"  Event   $index  ","pages":[{"title":"Article_$index",""" +
                """"content_urls":{"desktop":{"page":"https://en.wikipedia.org/wiki/Article_$index"}}}]}"""
        }
        val raw = """{"events":[
            {"year":2000,"text":"Bad URL","pages":[{"title":"Bad","content_urls":{"desktop":{"page":"https://example.com/wiki/Bad"}}}]},
            {"year":2001,"text":"No page","pages":[]},
            {"year":1.5,"text":"Fractional year","pages":[{"title":"Bad year","content_urls":{"desktop":{"page":"https://en.wikipedia.org/wiki/Bad_year"}}}]},
            $events
        ]}"""

        val result = toolReturning(raw).fetch(WikipediaOnThisDayRequest(1, 1, "en")) as WikipediaOnThisDayResult.Success

        assertEquals(64, result.events.size)
        assertEquals("Event 1", result.events.first().text)
        assertEquals("Article 1", result.events.first().title)
        assertTrue(result.events.none { it.text == "Fractional year" })
        assertTrue(result.events.all { it.canonicalUrl.startsWith("https://en.wikipedia.org/wiki/") })
    }

    @Test
    fun `distinguishes no events malformed responses and unavailable payloads`() = runTest {
        assertEquals(
            WikipediaOnThisDayResult.Failure(ToolFailureReason.NoResults),
            toolReturning("""{"events":[]}""").fetch(WikipediaOnThisDayRequest(1, 1, "en")),
        )
        listOf("{", "[]", "{}", """{"events":{}}""").forEach { raw ->
            assertEquals(
                WikipediaOnThisDayResult.Failure(ToolFailureReason.MalformedResponse),
                toolReturning(raw).fetch(WikipediaOnThisDayRequest(1, 1, "en")),
            )
        }
        val unavailable = listOf(
            KnowledgeHttpResponse(301, "application/json", "{}".encodeToByteArray()),
            KnowledgeHttpResponse(503, "application/json", "{}".encodeToByteArray()),
            KnowledgeHttpResponse(200, "text/html", "{}".encodeToByteArray()),
            KnowledgeHttpResponse(200, "application/json", ByteArray(1024 * 1024 + 1)),
        )
        unavailable.forEach { response ->
            assertEquals(
                WikipediaOnThisDayResult.Failure(ToolFailureReason.Unavailable),
                WikipediaOnThisDayKnowledgeTool(KnowledgeHttpTransport { response })
                    .fetch(WikipediaOnThisDayRequest(1, 1, "en")),
            )
        }
    }

    @Test
    fun `maps deadlines cancellation invalid utf8 and transport failures`() = runTest {
        val timeout = WikipediaOnThisDayKnowledgeTool(
            transport = KnowledgeHttpTransport { awaitCancellation() },
            totalTimeoutMillis = 50,
        )
        assertEquals(
            WikipediaOnThisDayResult.Failure(ToolFailureReason.TimedOut),
            timeout.fetch(WikipediaOnThisDayRequest(1, 1, "en")),
        )
        val socket = WikipediaOnThisDayKnowledgeTool(
            transport = KnowledgeHttpTransport { throw SocketTimeoutException("read") },
        )
        assertEquals(
            WikipediaOnThisDayResult.Failure(ToolFailureReason.TimedOut),
            socket.fetch(WikipediaOnThisDayRequest(1, 1, "en")),
        )
        val cancelled = WikipediaOnThisDayKnowledgeTool(
            transport = KnowledgeHttpTransport { throw CancellationException("cancelled") },
        )
        assertEquals(
            WikipediaOnThisDayResult.Failure(ToolFailureReason.Cancelled),
            cancelled.fetch(WikipediaOnThisDayRequest(1, 1, "en")),
        )
        val invalidUtf8 = WikipediaOnThisDayKnowledgeTool(
            transport = KnowledgeHttpTransport {
                KnowledgeHttpResponse(200, "application/json", byteArrayOf(0xC3.toByte(), 0x28))
            },
        )
        assertEquals(
            WikipediaOnThisDayResult.Failure(ToolFailureReason.MalformedResponse),
            invalidUtf8.fetch(WikipediaOnThisDayRequest(1, 1, "en")),
        )
        val failed = WikipediaOnThisDayKnowledgeTool(
            transport = KnowledgeHttpTransport { error("network") },
        )
        assertEquals(
            WikipediaOnThisDayResult.Failure(ToolFailureReason.Unavailable),
            failed.fetch(WikipediaOnThisDayRequest(1, 1, "en")),
        )
    }

    @Test
    fun `production transport rejects non official hosts and paths before networking`() = runTest {
        listOf(
            "https://example.com/api/rest_v1/feed/onthisday/events/09/09",
            "https://en.wikipedia.org/w/api.php",
            "https://en.wikipedia.org/api/rest_v1/feed/onthisday/births/09/09",
        ).forEach { url ->
            try {
                UrlConnectionWikipediaOnThisDayHttpTransport().get(url)
                fail("Expected endpoint rejection")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    private fun toolReturning(body: String) = WikipediaOnThisDayKnowledgeTool(
        transport = KnowledgeHttpTransport { jsonResponse(body) },
    )

    private fun jsonResponse(body: String) = KnowledgeHttpResponse(
        status = 200,
        contentType = "application/json; charset=utf-8",
        body = body.trimIndent().encodeToByteArray(),
    )
}
