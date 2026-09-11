package com.jesjobom.ararai.tools

import com.google.gson.JsonParser
import com.jesjobom.ararai.knowledge.ToolFailureReason
import com.jesjobom.ararai.knowledge.WikipediaHistoricalEvent
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayRequest
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayResult
import com.jesjobom.ararai.knowledge.WikipediaOnThisDayTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaOnThisDayApplicationToolTest {
    @Test
    fun `declares strict dual consumer version one contract`() {
        val contract = registry().descriptors().single()
        val input = JsonParser.parseString(contract.inputSchemaJson).asJsonObject
        val output = JsonParser.parseString(contract.outputSchemaJson).asJsonObject

        assertEquals(WIKIPEDIA_ON_THIS_DAY_TOOL_NAME, contract.id)
        assertEquals(1, contract.version)
        assertEquals(setOf(ApplicationToolConsumer.Model, ApplicationToolConsumer.Widget), contract.consumers)
        assertFalse(input["additionalProperties"].asBoolean)
        assertEquals(
            setOf("month", "day", "language"),
            input["required"].asJsonArray.mapTo(mutableSetOf()) { it.asString },
        )
        assertEquals(64, output["properties"].asJsonObject["events"].asJsonObject["maxItems"].asInt)
        val event = output["properties"].asJsonObject["events"].asJsonObject["items"].asJsonObject
        assertEquals(
            setOf("year", "text", "title", "canonicalUrl"),
            event["required"].asJsonArray.mapTo(mutableSetOf()) { it.asString },
        )
        assertFalse(contract.toString().contains("/api/rest_v1/"))
    }

    @Test
    fun `dispatches strict calendar input and serializes bounded events`() = runTest {
        var request: WikipediaOnThisDayRequest? = null
        val dispatcher = ApplicationToolDispatcher(
            registry(
                WikipediaOnThisDayTool {
                    request = it
                    SUCCESS
                },
            ),
        )

        val result = dispatcher.execute(invocation()) as ApplicationToolDispatchResult.Executed
        val payload = JsonParser.parseString(result.payloadJson).asJsonObject

        assertEquals(WikipediaOnThisDayRequest(9, 9, "pt"), request)
        assertEquals("success", payload["kind"].asString)
        assertEquals("pt", payload["language"].asString)
        assertEquals(42L, payload["retrievedAtMillis"].asLong)
        val event = payload["events"].asJsonArray.single().asJsonObject
        assertEquals(setOf("year", "text", "title", "canonicalUrl"), event.keySet())
    }

    @Test
    fun `rejects invalid dates extras state version and unsupported model before provider`() = runTest {
        var calls = 0
        val tool = WikipediaOnThisDayTool {
            calls++
            SUCCESS
        }
        val dispatcher = ApplicationToolDispatcher(registry(tool))

        listOf(
            invocation(argumentsJson = """{"month":2,"day":30,"language":"en"}"""),
            invocation(argumentsJson = """{"month":9,"day":9,"language":"en","url":"x"}"""),
            invocation(argumentsJson = """{"month":"9","day":9,"language":"en"}"""),
            invocation(argumentsJson = """{"month":9.5,"day":9,"language":"en"}"""),
        ).forEach { assertRejected(ApplicationToolRejection.InvalidArguments, dispatcher.execute(it)) }
        assertRejected(ApplicationToolRejection.UnsupportedVersion, dispatcher.execute(invocation(version = 2)))
        assertRejected(
            ApplicationToolRejection.UnsupportedModel,
            dispatcher.execute(invocation(consumer = ApplicationToolConsumer.Model, verified = emptySet())),
        )
        assertRejected(
            ApplicationToolRejection.Disabled,
            ApplicationToolDispatcher(registry(tool, enabled = false)).execute(invocation()),
        )
        assertEquals(0, calls)
        assertTrue(
            dispatcher.execute(invocation(consumer = ApplicationToolConsumer.Model)) is
                ApplicationToolDispatchResult.Executed,
        )
    }

    @Test
    fun `serializes every provider failure as controlled data`() = runTest {
        ToolFailureReason.entries.forEach { reason ->
            val result = ApplicationToolDispatcher(
                registry(WikipediaOnThisDayTool { WikipediaOnThisDayResult.Failure(reason) }),
            ).execute(invocation()) as ApplicationToolDispatchResult.Executed
            val payload = JsonParser.parseString(result.payloadJson).asJsonObject
            assertEquals(setOf("kind", "reason"), payload.keySet())
            assertEquals(reason.name, payload["reason"].asString)
        }
    }

    private fun registry(
        tool: WikipediaOnThisDayTool = WikipediaOnThisDayTool { SUCCESS },
        enabled: Boolean = true,
    ) = ApplicationToolRegistry(
        listOf(
            wikipediaOnThisDayApplicationTool(tool) {
                ApplicationToolOperationalState(enabled = enabled, ready = true)
            },
        ),
    )

    private fun invocation(
        version: Int = 1,
        consumer: ApplicationToolConsumer = ApplicationToolConsumer.Widget,
        argumentsJson: String = """{"month":9,"day":9,"language":"pt"}""",
        verified: Set<String> = setOf(WIKIPEDIA_ON_THIS_DAY_TOOL_NAME),
    ) = ApplicationToolInvocation(
        id = WIKIPEDIA_ON_THIS_DAY_TOOL_NAME,
        version = version,
        consumer = consumer,
        argumentsJson = argumentsJson,
        verifiedModelToolIds = verified,
    )

    private fun assertRejected(expected: ApplicationToolRejection, result: ApplicationToolDispatchResult) {
        assertTrue(result is ApplicationToolDispatchResult.Rejected)
        assertEquals(expected, (result as ApplicationToolDispatchResult.Rejected).reason)
    }

    private companion object {
        val SUCCESS = WikipediaOnThisDayResult.Success(
            events = listOf(
                WikipediaHistoricalEvent(
                    2016,
                    "A historical event.",
                    "Related article",
                    "https://pt.wikipedia.org/wiki/Related_article",
                ),
            ),
            language = "pt",
            retrievedAtMillis = 42L,
        )
    }
}
