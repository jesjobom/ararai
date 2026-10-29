package com.jesjobom.ararai.tools

import com.google.gson.JsonParser
import com.jesjobom.ararai.chat.WEB_SEARCH_TOOL_NAME
import com.jesjobom.ararai.knowledge.FocusedEvidenceCandidate
import com.jesjobom.ararai.knowledge.FocusedWebEvidenceNormalizer
import com.jesjobom.ararai.knowledge.KnowledgeTool
import com.jesjobom.ararai.knowledge.ToolFailureReason
import com.jesjobom.ararai.knowledge.ToolRequest
import com.jesjobom.ararai.knowledge.ToolResult
import com.jesjobom.ararai.knowledge.WebSearchProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WebSearchContentApplicationToolTest {
    @Test
    fun `Chat contract does not expose widget structured evidence`() = runTest {
        val tool = KnowledgeTool { request ->
            FocusedWebEvidenceNormalizer().normalize(
                WebSearchProvider.Exa,
                request.language,
                listOf(FocusedEvidenceCandidate("Result", "https://example.com/1", listOf("Excerpt"))),
            )
        }
        val dispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(
                listOf(webSearchApplicationTool({ tool }, { ApplicationToolOperationalState(true, true) }) { "en" }),
            ),
        )
        val result = dispatcher.execute(
            ApplicationToolInvocation(
                id = WEB_SEARCH_TOOL_NAME,
                version = 1,
                consumer = ApplicationToolConsumer.Model,
                argumentsJson = """{"query":"example"}""",
                verifiedModelToolIds = setOf(WEB_SEARCH_TOOL_NAME),
            ),
        ) as ApplicationToolDispatchResult.Executed
        val payload = JsonParser.parseString(result.payloadJson).asJsonObject

        assertEquals(setOf("kind", "untrustedContext", "sources"), payload.keySet())
        assertFalse(payload.has("results"))
    }

    @Test
    fun `content tool exposes up to three existing Chat excerpts as structured results`() = runTest {
        var received: ToolRequest? = null
        val tool = KnowledgeTool { request ->
            received = request
            FocusedWebEvidenceNormalizer(clock = { 42L }).normalize(
                WebSearchProvider.Exa,
                request.language,
                (1..5).map { index ->
                    FocusedEvidenceCandidate(
                        "Result $index",
                        "https://example.com/$index",
                        listOf("Excerpt $index"),
                    )
                },
            )
        }
        val dispatcher = dispatcher(tool)

        val result = dispatcher.execute(invocation("""{"query":"election results"}"""))
            as ApplicationToolDispatchResult.Executed
        val payload = JsonParser.parseString(result.payloadJson).asJsonObject
        val results = payload.getAsJsonArray("results")

        assertEquals(ToolRequest("election results", "en", "election results"), received)
        assertEquals("success", payload["kind"].asString)
        assertEquals(3, results.size())
        assertEquals("Result 1", results[0].asJsonObject["title"].asString)
        assertEquals("Excerpt 1", results[0].asJsonObject["excerpt"].asString)
        assertEquals("Excerpt 1", results[0].asJsonObject.getAsJsonArray("excerpts")[0].asString)
        assertEquals("https://example.com/1", results[0].asJsonObject["canonicalUrl"].asString)
        assertFalse(payload.has("untrustedContext"))
    }

    @Test
    fun `content tool rejects invalid arguments and Model consumer`() = runTest {
        val dispatcher = dispatcher(KnowledgeTool { error("must not execute") })
        assertEquals(
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.InvalidArguments),
            dispatcher.execute(invocation("""{"query":"x","limit":3}""")),
        )
        assertEquals(
            ApplicationToolDispatchResult.Rejected(ApplicationToolRejection.IneligibleConsumer),
            dispatcher.execute(invocation("""{"query":"x"}""").copy(consumer = ApplicationToolConsumer.Model)),
        )
    }

    @Test
    fun `content tool turns empty or non-openable evidence into controlled failure`() = runTest {
        val tool = KnowledgeTool { request ->
            FocusedWebEvidenceNormalizer().normalize(
                WebSearchProvider.Exa,
                request.language,
                listOf(FocusedEvidenceCandidate("API", "https://en.wikipedia.org/w/api.php", listOf("Data"))),
            )
        }
        val result = dispatcher(tool).execute(invocation("""{"query":"wikipedia api"}"""))
            as ApplicationToolDispatchResult.Executed
        val payload = JsonParser.parseString(result.payloadJson).asJsonObject

        assertEquals("failure", payload["kind"].asString)
        assertEquals(ToolFailureReason.NoResults.name, payload["reason"].asString)
    }

    @Test
    fun `content tool respects widget enablement and credential readiness before provider execution`() = runTest {
        var enabled = false
        var ready = false
        val registry = ApplicationToolRegistry(
            listOf(
                webSearchContentApplicationTool(
                    tool = { KnowledgeTool { error("must not execute") } },
                    state = { ApplicationToolOperationalState(enabled, ready) },
                ) { "en" },
            ),
        )
        val dispatcher = ApplicationToolDispatcher(registry)
        val request = invocation("""{"query":"petrobras"}""")

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
        assertEquals(setOf(WEB_SEARCH_CONTENT_TOOL_NAME), registry.availableToolIds(ApplicationToolConsumer.Widget))
        assertEquals(emptySet<String>(), registry.availableToolIds(ApplicationToolConsumer.Model))
    }

    private fun dispatcher(tool: KnowledgeTool) = ApplicationToolDispatcher(
        ApplicationToolRegistry(
            listOf(webSearchContentApplicationTool({ tool }, { ApplicationToolOperationalState(true, true) }) { "en" }),
        ),
    )

    private fun invocation(arguments: String) = ApplicationToolInvocation(
        id = WEB_SEARCH_CONTENT_TOOL_NAME,
        version = 1,
        consumer = ApplicationToolConsumer.Widget,
        argumentsJson = arguments,
    )
}
