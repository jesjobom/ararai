package com.jesjobom.ararai.knowledge

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchOpenApiToolTest {
    @Test
    fun `accepts exact semantic arguments and captures sources`() {
        val source = KnowledgeSource("Exa", "Title", "https://example.com", "en", 1L)
        val tool =
            WebSearchToolTurn(
                KnowledgeTool {
                    assertEquals(ToolRequest("q", "en", "specific focus"), it)
                    ToolResult.Success("evidence", listOf(source))
                },
            )
        val events = mutableListOf<ApplicationToolExecutionEvent>()
        tool.beginTurn(events::add)

        val response = JsonParser.parseString(
            tool.execute("""{"query":"q","language":"en","focus":"specific focus"}"""),
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

        val response = tool.execute("""{"query":"q","language":"en","focus":"f","url":"https://bad"}""")

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
        val params = """{"query":"q","language":"en","focus":"f"}"""

        repeat(2) { tool.execute(params) }
        val limited = JsonParser.parseString(tool.execute(params)).asJsonObject
        assertEquals("CALL_LIMIT_REACHED", limited.get("error").asString)
        assertEquals(2, calls)

        tool.beginTurn()
        tool.execute(params)
        assertEquals(3, calls)
    }
}
