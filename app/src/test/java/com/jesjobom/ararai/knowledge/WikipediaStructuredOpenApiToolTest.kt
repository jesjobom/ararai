package com.jesjobom.ararai.knowledge

import com.google.gson.JsonParser
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.WIKIPEDIA_ON_THIS_DAY_TOOL_NAME
import com.jesjobom.ararai.tools.WIKIPEDIA_PAGES_TOOL_NAME
import com.jesjobom.ararai.tools.wikipediaOnThisDayApplicationTool
import com.jesjobom.ararai.tools.wikipediaPagesApplicationTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaStructuredOpenApiToolTest {
    @Test
    fun `descriptions distinguish direct pages from historical events by date`() {
        val (pages, events) = tools()
        val pagesSchema = JsonParser.parseString(pages.getToolDescriptionJsonString()).asJsonObject
        val eventsSchema = JsonParser.parseString(events.getToolDescriptionJsonString()).asJsonObject

        assertEquals(WIKIPEDIA_PAGES_TOOL_NAME, pagesSchema["name"].asString)
        assertTrue(pagesSchema["description"].asString.contains("direct, stable encyclopedic page lookup"))
        assertTrue(pagesSchema["description"].asString.contains(WIKIPEDIA_ON_THIS_DAY_TOOL_NAME))
        assertEquals(setOf("query", "language"), requiredFields(pagesSchema))

        assertEquals(WIKIPEDIA_ON_THIS_DAY_TOOL_NAME, eventsSchema["name"].asString)
        assertTrue(eventsSchema["description"].asString.contains("historical events"))
        assertTrue(eventsSchema["description"].asString.contains("not a date-index page"))
        assertEquals(setOf("month", "day", "language"), requiredFields(eventsSchema))
    }

    @Test
    fun `returns structured untrusted events and captures canonical sources`() {
        val (_, events) = tools()
        val lifecycle = mutableListOf<ApplicationToolExecutionEvent>()
        events.beginTurn(lifecycle::add)

        val response = JsonParser.parseString(
            events.execute("""{"month":9,"day":9,"language":"pt"}"""),
        ).asJsonObject

        assertTrue(response["ok"].asBoolean)
        assertTrue(response["untrusted"].asBoolean)
        assertEquals("success", response["result"].asJsonObject["kind"].asString)
        assertEquals(3, response["result"].asJsonObject["events"].asJsonArray.size())
        assertEquals(
            EVENT.canonicalUrl,
            response["result"].asJsonObject["events"].asJsonArray[0]
                .asJsonObject["canonicalUrl"].asString,
        )
        assertEquals(EVENT_SOURCES, events.consumeCapturedSources())
        assertEquals(
            listOf(
                ApplicationToolExecutionEvent.Started,
                ApplicationToolExecutionEvent.Succeeded(EVENT_SOURCES),
            ),
            lifecycle,
        )
    }

    @Test
    fun `shares one three call budget across both wikipedia tools`() {
        val (pages, events) = tools()
        pages.beginTurn()
        events.beginTurn()

        assertTrue(parse(pages.execute(PAGE_ARGUMENTS))["ok"].asBoolean)
        assertTrue(parse(events.execute(EVENT_ARGUMENTS))["ok"].asBoolean)
        assertTrue(parse(pages.execute(PAGE_ARGUMENTS))["ok"].asBoolean)
        val limited = parse(events.execute(EVENT_ARGUMENTS))

        assertFalse(limited["ok"].asBoolean)
        assertEquals("CALL_LIMIT_REACHED", limited["error"].asString)
    }

    @Test
    fun `rejects unsupported model and invalid arguments with controlled failures`() {
        val registry = registry()
        val unsupported = WikipediaStructuredToolTurn(
            StructuredWikipediaTool.Pages,
            registry,
            verifiedModelToolIds = emptySet(),
        )
        val invalid = tools().second

        assertEquals("SEARCH_UNAVAILABLE", parse(unsupported.execute(PAGE_ARGUMENTS))["error"].asString)
        assertEquals("INVALID_ARGUMENTS", parse(invalid.execute("{}"))["error"].asString)
    }

    private fun tools(): Pair<WikipediaStructuredToolTurn, WikipediaStructuredToolTurn> {
        val dispatcher = registry()
        val budget = WikipediaModelToolBudget()
        val verified = setOf(WIKIPEDIA_PAGES_TOOL_NAME, WIKIPEDIA_ON_THIS_DAY_TOOL_NAME)
        return WikipediaStructuredToolTurn(StructuredWikipediaTool.Pages, dispatcher, verified, budget) to
            WikipediaStructuredToolTurn(StructuredWikipediaTool.OnThisDay, dispatcher, verified, budget)
    }

    private fun registry() = ApplicationToolDispatcher(
        ApplicationToolRegistry(
            listOf(
                wikipediaPagesApplicationTool(
                    WikipediaPagesTool { WikipediaPagesResult.Success(listOf(PAGE)) },
                ) { ApplicationToolOperationalState(enabled = true, ready = true) },
                wikipediaOnThisDayApplicationTool(
                    WikipediaOnThisDayTool { EVENT_RESULT },
                ) { ApplicationToolOperationalState(enabled = true, ready = true) },
            ),
        ),
    )

    private fun requiredFields(schema: com.google.gson.JsonObject): Set<String> = schema["parameters"]
        .asJsonObject["required"]
        .asJsonArray
        .mapTo(mutableSetOf()) { it.asString }

    private fun parse(raw: String) = JsonParser.parseString(raw).asJsonObject

    private companion object {
        const val PAGE_ARGUMENTS = """{"query":"Ada Lovelace","language":"en"}"""
        const val EVENT_ARGUMENTS = """{"month":9,"day":9,"language":"pt"}"""
        val PAGE = WikipediaPage(
            "Ada Lovelace",
            "Mathematician.",
            "https://en.wikipedia.org/wiki/Ada_Lovelace",
            "en",
            42L,
        )
        val EVENT = WikipediaHistoricalEvent(
            2016,
            "A historical event.",
            "Related article",
            "https://pt.wikipedia.org/wiki/Related_article",
        )
        val EXTRA_EVENTS = (2..4).map { index ->
            WikipediaHistoricalEvent(
                2000 + index,
                "Historical event $index.",
                "Related article $index",
                "https://pt.wikipedia.org/wiki/Related_article_$index",
            )
        }
        val EVENT_RESULT = WikipediaOnThisDayResult.Success(listOf(EVENT) + EXTRA_EVENTS, "pt", 42L)
        val EVENT_SOURCES = EVENT_RESULT.events.take(3).map {
            KnowledgeSource("Wikipedia", it.title, it.canonicalUrl, "pt", 42L)
        }
    }
}
