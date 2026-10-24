package com.jesjobom.ararai.tools

import com.google.gson.JsonParser
import com.jesjobom.ararai.knowledge.WikipediaPageSummary
import com.jesjobom.ararai.knowledge.WikipediaPageSummaryRequest
import com.jesjobom.ararai.knowledge.WikipediaPageSummaryResult
import com.jesjobom.ararai.knowledge.WikipediaPageSummaryTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaPageSummaryApplicationToolTest {
    @Test
    fun `widget v2 passes selection and returns exactly one linked page`() = runTest {
        var request: WikipediaPageSummaryRequest? = null
        val dispatcher = dispatcher(
            WikipediaPageSummaryTool {
                request = it
                WikipediaPageSummaryResult.Success(
                    WikipediaPageSummary("Brazil", "Full lead", "https://en.wikipedia.org/wiki/Brazil", "en", 42),
                )
            },
        )

        val result = dispatcher.execute(
            invocation("""{"query":"Brazil","language":"en","selection":"seeded_random","seed":5}"""),
        )
            as ApplicationToolDispatchResult.Executed
        val payload = JsonParser.parseString(result.payloadJson).asJsonObject

        assertEquals(WikipediaPageSummaryRequest("Brazil", "en", "seeded_random", 5), request)
        assertEquals(1, payload.getAsJsonArray("pages").size())
        assertEquals(
            "https://en.wikipedia.org/wiki/Brazil",
            payload.getAsJsonArray("pages")[0].asJsonObject["canonicalUrl"].asString,
        )
        assertEquals("Full lead", payload.getAsJsonArray("pages")[0].asJsonObject["extract"].asString)
        assertEquals("extract", payload["defaultBodyField"].asString)
    }

    @Test
    fun `v2 rejects missing seed and remains widget only`() = runTest {
        val dispatcher = dispatcher(WikipediaPageSummaryTool { error("provider must not run") })

        assertEquals(
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.InvalidArguments),
            dispatcher.execute(invocation("""{"query":"Brazil","language":"en","selection":"seeded_random"}""")),
        )
        assertEquals(
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.InvalidArguments),
            dispatcher.execute(
                invocation("""{"query":"Brazil","language":"en","selection":"seeded_random","seed":-1}"""),
            ),
        )
        assertEquals(
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.IneligibleConsumer),
            dispatcher.execute(
                invocation("""{"query":"Brazil","language":"en","selection":"first"}""")
                    .copy(consumer = ApplicationToolConsumer.Model),
            ),
        )
    }

    private fun dispatcher(tool: WikipediaPageSummaryTool) = ApplicationToolDispatcher(
        ApplicationToolRegistry(
            listOf(
                wikipediaPageSummaryApplicationTool(tool) { ApplicationToolOperationalState(true, true) },
            ),
        ),
    )

    private fun invocation(arguments: String) = ApplicationToolInvocation(
        id = WIKIPEDIA_PAGES_TOOL_NAME,
        version = 2,
        consumer = ApplicationToolConsumer.Widget,
        argumentsJson = arguments,
    )
}
