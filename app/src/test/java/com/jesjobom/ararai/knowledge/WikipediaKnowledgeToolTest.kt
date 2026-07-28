package com.jesjobom.ararai.knowledge

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaKnowledgeToolTest {
    @Test
    fun `returns bounded untrusted context and source`() = runTest {
        var requestedUrl = ""
        val tool =
            WikipediaKnowledgeTool(
                transport = KnowledgeHttpTransport { url ->
                    requestedUrl = url
                    KnowledgeHttpResponse(
                        200,
                        "application/json; charset=utf-8",
                        """
                        {"query":{"pages":{"1":{"title":"Arara","extract":"Uma ave brasileira.","canonicalurl":"https://pt.wikipedia.org/wiki/Arara"}}}}
                        """.trimIndent().encodeToByteArray(),
                    )
                },
                clock = { 123L },
            )

        val result = tool.execute(ToolRequest("arara", "pt")) as ToolResult.Success

        assertTrue(requestedUrl.startsWith("https://pt.wikipedia.org/w/api.php?"))
        assertTrue(result.untrustedContext.startsWith("UNTRUSTED EXTERNAL REFERENCE DATA"))
        assertEquals("Arara", result.sources.single().title)
        assertEquals(123L, result.sources.single().retrievedAtMillis)
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

        assertEquals(
            ToolResult.Failure(ToolFailureReason.InvalidArguments),
            tool.execute(ToolRequest("", "en")),
        )
        assertEquals(
            ToolResult.Failure(ToolFailureReason.InvalidArguments),
            tool.execute(ToolRequest("query", "fr")),
        )
        assertEquals(0, calls)
    }

    @Test
    fun `rejects malformed oversized and non official responses`() = runTest {
        val malformed =
            WikipediaKnowledgeTool(
                transport = KnowledgeHttpTransport {
                    KnowledgeHttpResponse(200, "application/json", "{}".encodeToByteArray())
                },
            )
        assertEquals(ToolResult.Failure(ToolFailureReason.NoResults), malformed.execute(ToolRequest("x")))

        val oversized =
            WikipediaKnowledgeTool(
                transport = KnowledgeHttpTransport {
                    KnowledgeHttpResponse(200, "application/json", ByteArray(256 * 1024 + 1))
                },
            )
        assertEquals(ToolResult.Failure(ToolFailureReason.Unavailable), oversized.execute(ToolRequest("x")))
    }
}
