package com.jesjobom.ararai.knowledge

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class WikipediaPageSummaryToolTest {
    @Test
    fun `first searches ten metadata candidates then returns one complete introduction`() = runTest {
        val intro = "Complete introduction. " + "A".repeat(2_100)
        val urls = mutableListOf<String>()
        val tool = WikipediaPageSummaryKnowledgeTool(
            transport = KnowledgeHttpTransport { url ->
                urls += url
                response(if (urls.size == 1) candidates() else detail(1, "Brazil", intro))
            },
            clock = { 42L },
        )

        val result = tool.fetch(WikipediaPageSummaryRequest("Brazil", "en", "first"))

        assertEquals(
            WikipediaPageSummaryResult.Success(
                WikipediaPageSummary("Brazil", intro, "https://en.wikipedia.org/wiki/Brazil", "en", 42L),
            ),
            result,
        )
        assertEquals(2, urls.size)
        assertTrue(urls[0].contains("gsrlimit=10"))
        assertFalse(urls[0].contains("extracts"))
        assertTrue(urls[1].contains("pageids=1"))
        assertTrue(urls[1].contains("exintro=1"))
        assertFalse(urls[1].contains("exchars="))
    }

    @Test
    fun `seeded selection chooses from search order not transport array order`() = runTest {
        val urls = mutableListOf<String>()
        val tool = WikipediaPageSummaryKnowledgeTool(
            transport = KnowledgeHttpTransport { url ->
                urls += url
                response(if (urls.size == 1) candidates() else detail(2, "Brazil history", "Entire lead."))
            },
        )

        val result = tool.fetch(WikipediaPageSummaryRequest("Brazil", "en", "seeded_random", seed = 1))

        assertTrue(result is WikipediaPageSummaryResult.Success)
        assertTrue(urls[1].contains("pageids=2"))
    }

    @Test
    fun `seeded selection is not locked to one result by an hourly seed interval`() = runTest {
        val tool = WikipediaPageSummaryKnowledgeTool(
            transport = KnowledgeHttpTransport { url ->
                response(
                    when {
                        url.contains("generator=search") -> candidates()
                        url.contains("pageids=1") -> detail(1, "Brazil", "Brazil lead.")
                        else -> detail(2, "Brazil history", "History lead.")
                    },
                )
            },
        )

        val titles = (0L until 12L).map { hour ->
            val result = tool.fetch(
                WikipediaPageSummaryRequest("Brazil", "en", "seeded_random", seed = 5L + hour * 3_600_000L),
            )
            (result as WikipediaPageSummaryResult.Success).page.title
        }

        assertEquals(2, titles.toSet().size)
    }

    @Test
    fun `oversized introduction fails explicitly without truncation`() = runTest {
        val tool = WikipediaPageSummaryKnowledgeTool(
            transport = responseSequence(candidates(), detail(1, "Brazil", "A".repeat(16_385))),
        )

        assertEquals(
            WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.SummaryTooLarge),
            tool.fetch(WikipediaPageSummaryRequest("Brazil", "en", "first")),
        )
    }

    @Test
    fun `rejects a detail page that does not match selected search candidate`() = runTest {
        val tool = WikipediaPageSummaryKnowledgeTool(
            transport = responseSequence(candidates(), detail(3, "Other", "Wrong page.")),
        )

        assertEquals(
            WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.MalformedResponse),
            tool.fetch(WikipediaPageSummaryRequest("Brazil", "en", "first")),
        )
    }

    @Test
    fun `exposes a provider rate limit without claiming an empty search`() = runTest {
        val tool = WikipediaPageSummaryKnowledgeTool(
            transport = KnowledgeHttpTransport {
                KnowledgeHttpResponse(429, "application/json", ByteArray(0))
            },
        )

        assertEquals(
            WikipediaPageSummaryResult.Failure(WikipediaPageSummaryFailure.RateLimited),
            tool.fetch(WikipediaPageSummaryRequest("Brazil", "en", "first")),
        )
    }

    private fun responseSequence(vararg bodies: String): KnowledgeHttpTransport {
        var index = 0
        return KnowledgeHttpTransport { response(bodies[index++]) }
    }

    private fun response(body: String) = KnowledgeHttpResponse(
        status = 200,
        contentType = "application/json; charset=utf-8",
        body = body.toByteArray(StandardCharsets.UTF_8),
    )

    private fun candidates() = """{"query":{"pages":[""" +
        """{"pageid":2,"title":"Brazil history","index":2,""" +
        """"canonicalurl":"https://en.wikipedia.org/wiki/Brazil_history"},""" +
        """{"pageid":1,"title":"Brazil","index":1,""" +
        """"canonicalurl":"https://en.wikipedia.org/wiki/Brazil"}""" +
        "]}}"

    private fun detail(pageId: Int, title: String, intro: String): String {
        val canonicalUrl = "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}"
        return """{"query":{"pages":[{"pageid":$pageId,"title":"$title",""" +
            """"canonicalurl":"$canonicalUrl","extract":"$intro"}]}}"""
    }
}
