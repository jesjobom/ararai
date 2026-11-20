package com.jesjobom.ararai.knowledge

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchOpenApiToolTest {
    @Test
    fun `derives provider metadata from one query argument and captures sources`() {
        val source = KnowledgeSource("Exa", "Title", "https://example.com", "en", 1L)
        val tool =
            WebSearchToolTurn(
                KnowledgeTool {
                    assertEquals(
                        ToolRequest("specific focus", "pt", "specific focus", WebEvidencePolicy.Chat(2_048)),
                        it,
                    )
                    ToolResult.Success("evidence", listOf(source))
                },
                languageProvider = { "PT" },
            )
        val events = mutableListOf<ApplicationToolExecutionEvent>()
        tool.beginTurn(events::add)

        val response = JsonParser.parseString(
            tool.execute("""{"query":"specific focus"}"""),
        ).asJsonObject

        assertTrue(response.get("ok").asBoolean)
        assertEquals(listOf(source), tool.consumeCapturedSources())
        assertEquals(
            listOf(
                ApplicationToolExecutionEvent.Started,
                ApplicationToolExecutionEvent.Succeeded(listOf(source)),
            ),
            events,
        )
    }

    @Test
    fun `passes the normalized evidence through without a second model-specific truncation`() {
        val result = FocusedWebEvidenceNormalizer(clock = { 1L }).normalize(
            provider = WebSearchProvider.Exa,
            language = "en",
            candidates = (1..3).map { index ->
                FocusedEvidenceCandidate(
                    title = "Source $index",
                    canonicalUrl = "https://example.com/$index",
                    excerpts = listOf("Evidence $index " + "x".repeat(500)),
                )
            },
        ) as ToolResult.Success
        val tool = WebSearchToolTurn(KnowledgeTool { result })

        val response = JsonParser.parseString(tool.execute("""{"query":"coffee"}""")).asJsonObject

        assertTrue(result.untrustedContext.length > 600)
        assertEquals(result.untrustedContext, response.get("untrustedReference").asString)
        assertEquals(result.sources, tool.consumeCapturedSources())
    }

    @Test
    fun `rejects extra arguments before provider execution`() {
        var calls = 0
        val tool = WebSearchToolTurn(
            KnowledgeTool {
                calls += 1
                ToolResult.Failure(ToolFailureReason.NoResults)
            },
        )

        val response = tool.execute("""{"query":"q","language":"en"}""")

        assertFalse(JsonParser.parseString(response).asJsonObject.get("ok").asBoolean)
        assertEquals(0, calls)
    }

    @Test
    fun `enforces two calls per turn and resets on next turn`() {
        var calls = 0
        val tool = WebSearchToolTurn(
            KnowledgeTool {
                calls += 1
                ToolResult.Failure(ToolFailureReason.NoResults)
            },
        )
        val params = """{"query":"q"}"""

        repeat(2) { tool.execute(params) }
        val limited = JsonParser.parseString(tool.execute(params)).asJsonObject
        assertEquals("CALL_LIMIT_REACHED", limited.get("error").asString)
        assertEquals(2, calls)

        tool.beginTurn()
        tool.execute(params)
        assertEquals(3, calls)
    }

    @Test
    fun `advertises only one required query argument`() {
        val description = JsonParser.parseString(WebSearchToolTurn.TOOL_DESCRIPTION).asJsonObject
        val parameters = description.getAsJsonObject("parameters")

        assertEquals(setOf("query"), parameters.getAsJsonObject("properties").keySet())
        assertEquals(listOf("query"), parameters.getAsJsonArray("required").map { it.asString })
        assertFalse(parameters.get("additionalProperties").asBoolean)
    }

    @Test
    fun `falls back to English when local language is invalid`() {
        val tool = WebSearchToolTurn(
            KnowledgeTool {
                assertEquals(ToolRequest("query", "en", "query", WebEvidencePolicy.Chat(2_048)), it)
                ToolResult.Failure(ToolFailureReason.NoResults)
            },
            languageProvider = { "" },
        )

        tool.execute("""{"query":"query"}""")
    }

    @Test
    fun `two searches in one turn each receive the full configured budget`() {
        val policies = mutableListOf<WebEvidencePolicy>()
        val metrics = mutableListOf<WebSearchResultMetrics>()
        val normalizer = FocusedWebEvidenceNormalizer(clock = { 1L })
        val tool = WebSearchToolTurn(
            KnowledgeTool { request ->
                policies += request.evidencePolicy
                normalizer.normalize(
                    WebSearchProvider.Exa,
                    "en",
                    listOf(
                        FocusedEvidenceCandidate("Result", "https://example.com", listOf("evidence ".repeat(1_000))),
                    ),
                    request.evidencePolicy,
                )
            },
            contextTokens = 4_096,
            onResultMetrics = metrics::add,
        )

        val first = tool.execute("""{"query":"first"}""")
        val second = tool.execute("""{"query":"second"}""")

        assertEquals(listOf(WebEvidencePolicy.Chat(4_096), WebEvidencePolicy.Chat(4_096)), policies)
        assertEquals(first.length, second.length)
        assertTrue(first.length > 1_800)
        assertTrue(first.length <= 4_096)
        assertEquals(listOf(first.length, second.length), metrics.map { it.resultChars })
        assertEquals(listOf(4_096, 4_096), metrics.map { it.budgetChars })
        assertEquals(
            "CALL_LIMIT_REACHED",
            JsonParser.parseString(tool.execute("""{"query":"third"}""")).asJsonObject.get("error").asString,
        )
    }

    @Test
    fun `rejects an oversized success without capturing unsupported source claims`() {
        val tool = WebSearchToolTurn(
            KnowledgeTool {
                ToolResult.Success(
                    "x".repeat(3_000),
                    listOf(KnowledgeSource("Exa", "Title", "https://example.com", "en", 1L)),
                )
            },
        )

        val response = JsonParser.parseString(tool.execute("""{"query":"test"}""")).asJsonObject

        assertEquals("NO_RESULTS", response.get("error").asString)
        assertEquals(emptyList<KnowledgeSource>(), tool.consumeCapturedSources())
    }
}
