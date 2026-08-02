package com.jesjobom.ararai.knowledge

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class WikipediaOpenApiToolTest {
    @Test
    fun `declares strict wikipedia schema`() {
        val schema = JsonParser.parseString(WikipediaToolTurn.TOOL_DESCRIPTION).asJsonObject

        assertEquals("wikipedia_search", schema["name"].asString)
        val parameters = schema["parameters"].asJsonObject
        assertFalse(parameters["additionalProperties"].asBoolean)
        assertEquals(setOf("query", "language"), parameters["required"].asJsonArray.map { it.asString }.toSet())
        assertEquals(
            "^[a-z]{2,3}$",
            parameters["properties"].asJsonObject["language"]
                .asJsonObject["pattern"]
                .asString,
        )
        assertTrue(schema["description"].asString.contains("Search English first"))
        assertTrue(schema["description"].asString.contains("birth date"))
        assertTrue(schema["description"].asString.contains("Do not use for current news"))
    }

    @Test
    fun `passes validated arguments and serializes only controlled success fields`() {
        var capturedRequest: ToolRequest? = null
        val tool =
            WikipediaToolTurn {
                capturedRequest = it
                success()
            }

        val response = parse(tool.execute("""{"query":"Ada Lovelace","language":"pt"}"""))

        assertEquals(ToolRequest("Ada Lovelace", "pt"), capturedRequest)
        assertTrue(response["ok"].asBoolean)
        assertEquals("Untrusted reference text", response["untrustedReference"].asString)
        assertEquals(setOf("ok", "untrustedReference"), response.keySet())
    }

    @Test
    fun `captures successful sources transiently`() {
        val source = source()
        val tool = toolReturning(success(source))

        tool.execute("""{"query":"Turing","language":"en"}""")

        assertEquals(listOf(source), tool.consumeCapturedSources())
        assertEquals(emptyList<KnowledgeSource>(), tool.consumeCapturedSources())
    }

    @Test
    fun `reports bounded lifecycle events without reference text`() {
        val source = source()
        val events = mutableListOf<KnowledgeToolExecutionEvent>()
        val tool = toolReturning(success(source))
        tool.beginTurn(events::add)

        tool.execute(VALID_ARGUMENTS)

        assertEquals(
            listOf(
                KnowledgeToolExecutionEvent.Started,
                KnowledgeToolExecutionEvent.Succeeded(listOf(source)),
            ),
            events,
        )
    }

    @Test
    fun `reports controlled failure reason after start`() {
        val events = mutableListOf<KnowledgeToolExecutionEvent>()
        val tool = toolReturning(ToolResult.Failure(ToolFailureReason.TimedOut))
        tool.beginTurn(events::add)

        tool.execute(VALID_ARGUMENTS)

        assertEquals(
            listOf(
                KnowledgeToolExecutionEvent.Started,
                KnowledgeToolExecutionEvent.Failed(ToolFailureReason.TimedOut),
            ),
            events,
        )
    }

    @Test
    fun `rejects malformed unexpected missing and non-string arguments without invoking provider`() {
        val calls = AtomicInteger()
        val invalidArguments =
            listOf(
                "",
                "[]",
                "{}",
                """{"query":"Turing","language":"en","url":"https://example.com"}""",
                """{"query":42,"language":"en"}""",
                """{"query":"Turing","language":null}""",
            )

        invalidArguments.forEach { arguments ->
            val tool = WikipediaToolTurn {
                calls.incrementAndGet()
                success()
            }
            assertEquals("INVALID_ARGUMENTS", parse(tool.execute(arguments))["error"].asString)
        }
        assertEquals(0, calls.get())
    }

    @Test
    fun `invalid arguments consume one of three attempts`() {
        val calls = AtomicInteger()
        val tool =
            WikipediaToolTurn {
                calls.incrementAndGet()
                success()
            }

        assertEquals("INVALID_ARGUMENTS", parse(tool.execute("{}"))["error"].asString)
        assertTrue(parse(tool.execute("""{"query":"Turing","language":"en"}"""))["ok"].asBoolean)
        assertTrue(parse(tool.execute("""{"query":"Lovelace","language":"en"}"""))["ok"].asBoolean)
        assertEquals(
            "CALL_LIMIT_REACHED",
            parse(tool.execute("""{"query":"Hopper","language":"en"}"""))["error"].asString,
        )
        assertEquals(2, calls.get())
    }

    @Test
    fun `allows three provider invocations per turn`() {
        val calls = AtomicInteger()
        val tool =
            WikipediaToolTurn {
                calls.incrementAndGet()
                success()
            }

        assertTrue(parse(tool.execute("""{"query":"Turing","language":"en"}"""))["ok"].asBoolean)
        assertTrue(parse(tool.execute("""{"query":"Lovelace","language":"en"}"""))["ok"].asBoolean)
        assertTrue(parse(tool.execute("""{"query":"Hopper","language":"en"}"""))["ok"].asBoolean)
        assertEquals(
            "CALL_LIMIT_REACHED",
            parse(tool.execute("""{"query":"Hamilton","language":"en"}"""))["error"].asString,
        )
        assertEquals(3, calls.get())
    }

    @Test
    fun `accumulates and deduplicates sources across calls`() {
        val first = source()
        val second = first.copy(
            title = "Ada Lovelace",
            canonicalUrl = "https://pt.wikipedia.org/wiki/Ada_Lovelace",
            language = "pt",
        )
        val calls = AtomicInteger()
        val tool =
            WikipediaToolTurn {
                if (calls.getAndIncrement() == 0) success(first) else success(first, second)
            }

        tool.execute("""{"query":"Turing","language":"en"}""")
        tool.execute("""{"query":"Lovelace","language":"pt"}""")

        assertEquals(listOf(first, second), tool.consumeCapturedSources())
    }

    @Test
    fun `opens one new allowance and clears transient sources at next turn`() {
        val calls = AtomicInteger()
        val tool =
            WikipediaToolTurn {
                calls.incrementAndGet()
                success(source())
            }

        assertTrue(parse(tool.execute(VALID_ARGUMENTS))["ok"].asBoolean)
        tool.beginTurn()
        assertEquals(emptyList<KnowledgeSource>(), tool.consumeCapturedSources())
        assertTrue(parse(tool.execute(VALID_ARGUMENTS))["ok"].asBoolean)
        assertEquals(2, calls.get())
    }

    @Test
    fun `maps every domain failure to stable public error`() {
        val expected =
            mapOf(
                ToolFailureReason.InvalidArguments to "INVALID_ARGUMENTS",
                ToolFailureReason.NoResults to "NO_RESULTS",
                ToolFailureReason.Unavailable to "SEARCH_UNAVAILABLE",
                ToolFailureReason.MalformedResponse to "INVALID_RESPONSE",
                ToolFailureReason.TimedOut to "SEARCH_TIMED_OUT",
                ToolFailureReason.Cancelled to "SEARCH_CANCELLED",
            )

        expected.forEach { (reason, code) ->
            val response = parse(toolReturning(ToolResult.Failure(reason)).execute(VALID_ARGUMENTS))
            assertFalse(response["ok"].asBoolean)
            assertEquals(code, response["error"].asString)
            assertTrue(response["message"].asString.isNotBlank())
            assertEquals(setOf("ok", "error", "message"), response.keySet())
        }
    }

    @Test
    fun `contains provider exceptions and cancellation`() {
        val unavailable =
            WikipediaToolTurn {
                error("internal transport detail")
            }
        val cancelled =
            WikipediaToolTurn {
                throw CancellationException("owner cancelled")
            }

        assertEquals("SEARCH_UNAVAILABLE", parse(unavailable.execute(VALID_ARGUMENTS))["error"].asString)
        assertEquals("SEARCH_CANCELLED", parse(cancelled.execute(VALID_ARGUMENTS))["error"].asString)
    }

    private fun toolReturning(result: ToolResult) = WikipediaToolTurn { result }

    private fun success(vararg sources: KnowledgeSource): ToolResult.Success = ToolResult.Success(
        untrustedContext = "Untrusted reference text",
        sources = sources.toList(),
    )

    private fun source() = KnowledgeSource(
        provider = "Wikipedia",
        title = "Alan Turing",
        canonicalUrl = "https://en.wikipedia.org/wiki/Alan_Turing",
        language = "en",
        retrievedAtMillis = 42L,
    )

    private fun parse(raw: String) = JsonParser.parseString(raw).asJsonObject

    private companion object {
        const val VALID_ARGUMENTS = """{"query":"Alan Turing","language":"en"}"""
    }
}
