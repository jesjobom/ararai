package com.jesjobom.ararai.tools

import com.google.gson.JsonParser
import com.jesjobom.ararai.knowledge.WebSearchProvider
import com.jesjobom.ararai.knowledge.WebSearchResultItem
import com.jesjobom.ararai.knowledge.WebSearchResultsRequest
import com.jesjobom.ararai.knowledge.WebSearchResultsResult
import com.jesjobom.ararai.knowledge.WebSearchResultsTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WebSearchResultsApplicationToolTest {
    @Test
    fun `top results tool accepts bounded count and exposes ranked links without content`() = runTest {
        var observed: WebSearchResultsRequest? = null
        val dispatcher = dispatcher(
            WebSearchResultsTool { request ->
                observed = request
                WebSearchResultsResult.Success(
                    listOf(WebSearchResultItem("First", "https://example.com/first")),
                    WebSearchProvider.Exa,
                    42,
                )
            },
        )

        val result = dispatcher.execute(invocation("""{"query":"Petrobras stocks","limit":10}"""))
            as ApplicationToolDispatchResult.Executed
        val payload = JsonParser.parseString(result.payloadJson).asJsonObject
        val first = payload.getAsJsonArray("results")[0].asJsonObject

        assertEquals(WebSearchResultsRequest("Petrobras stocks", 10), observed)
        assertEquals("success", payload["kind"].asString)
        assertEquals("First", first["title"].asString)
        assertEquals("https://example.com/first", first["canonicalUrl"].asString)
        assertEquals(false, payload.has("untrustedContext"))
    }

    @Test
    fun `top results tool rejects invalid limits and model consumer`() = runTest {
        val dispatcher = dispatcher(WebSearchResultsTool { error("provider must not run") })

        listOf(
            """{"query":"a","limit":0}""",
            """{"query":"a","limit":11}""",
            """{"query":"a","limit":1,"url":"https://example.com"}""",
        ).forEach { arguments ->
            assertEquals(
                ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.InvalidArguments),
                dispatcher.execute(invocation(arguments)),
            )
        }
        assertEquals(
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.IneligibleConsumer),
            dispatcher.execute(
                invocation("""{"query":"a","limit":1}""").copy(consumer = ApplicationToolConsumer.Model),
            ),
        )
    }

    @Test
    fun `widget switch and credential readiness block dispatch before provider execution`() = runTest {
        var enabled = false
        var ready = false
        val registry = ApplicationToolRegistry(
            listOf(
                webSearchResultsApplicationTool(
                    tool = { WebSearchResultsTool { error("provider must not run") } },
                    state = { ApplicationToolOperationalState(enabled, ready) },
                ),
            ),
        )
        val dispatcher = ApplicationToolDispatcher(registry)
        val request = invocation("""{"query":"election results","limit":1}""")

        assertEquals(emptySet<String>(), registry.availableToolIds(ApplicationToolConsumer.Widget))
        assertEquals(
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.Disabled),
            dispatcher.execute(request),
        )

        enabled = true
        assertEquals(
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.NotConfigured),
            dispatcher.execute(request),
        )

        ready = true
        assertEquals(setOf(WEB_SEARCH_TOP_RESULTS_TOOL_NAME), registry.availableToolIds(ApplicationToolConsumer.Widget))
        assertEquals(emptySet<String>(), registry.availableToolIds(ApplicationToolConsumer.Model))
    }

    private fun dispatcher(tool: WebSearchResultsTool) = ApplicationToolDispatcher(
        ApplicationToolRegistry(
            listOf(webSearchResultsApplicationTool({ tool }) { ApplicationToolOperationalState(true, true) }),
        ),
    )

    private fun invocation(arguments: String) = ApplicationToolInvocation(
        id = WEB_SEARCH_TOP_RESULTS_TOOL_NAME,
        version = 1,
        consumer = ApplicationToolConsumer.Widget,
        argumentsJson = arguments,
    )
}
