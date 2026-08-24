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
                    assertEquals(ToolRequest("specific focus", "pt", "specific focus"), it)
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
                assertEquals(ToolRequest("query", "en", "query"), it)
                ToolResult.Failure(ToolFailureReason.NoResults)
            },
            languageProvider = { "" },
        )

        tool.execute("""{"query":"query"}""")
    }
}
