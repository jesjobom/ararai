package com.jesjobom.ararai.tools

import com.google.gson.JsonParser
import com.jesjobom.ararai.knowledge.ToolFailureReason
import com.jesjobom.ararai.knowledge.ToolRequest
import com.jesjobom.ararai.knowledge.WikipediaPage
import com.jesjobom.ararai.knowledge.WikipediaPagesResult
import com.jesjobom.ararai.knowledge.WikipediaPagesTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaPagesApplicationToolTest {
    @Test
    fun `declares strict version one model and widget contract`() {
        val contract = registry().descriptors().single()
        val input = JsonParser.parseString(contract.inputSchemaJson).asJsonObject
        val output = JsonParser.parseString(contract.outputSchemaJson).asJsonObject

        assertEquals(WIKIPEDIA_PAGES_TOOL_NAME, contract.id)
        assertEquals(1, contract.version)
        assertEquals(setOf(ApplicationToolConsumer.Model, ApplicationToolConsumer.Widget), contract.consumers)
        assertFalse(input["additionalProperties"].asBoolean)
        assertEquals(
            setOf("query", "language"),
            input["required"].asJsonArray.mapTo(mutableSetOf()) { it.asString },
        )
        assertEquals(3, output["properties"].asJsonObject["pages"].asJsonObject["maxItems"].asInt)
        val page = output["properties"].asJsonObject["pages"]
            .asJsonObject["items"]
            .asJsonObject
        assertFalse(page["additionalProperties"].asBoolean)
        assertEquals(
            setOf("title", "extract", "canonicalUrl", "language", "retrievedAtMillis"),
            page["required"].asJsonArray.mapTo(mutableSetOf()) { it.asString },
        )
        val descriptor = contract.toString()
        assertFalse(descriptor.contains("https://"))
        assertFalse(descriptor.contains("Authorization", ignoreCase = true))
        assertFalse(descriptor.contains("token", ignoreCase = true))
        assertFalse(descriptor.contains("timeout", ignoreCase = true))
    }

    @Test
    fun `returns bounded typed page fields without narrative reconstruction`() = runTest {
        var request: ToolRequest? = null
        val dispatcher = ApplicationToolDispatcher(
            registry(
                WikipediaPagesTool {
                    request = it
                    WikipediaPagesResult.Success(listOf(PAGE))
                },
            ),
        )

        val result = dispatcher.execute(invocation()) as ApplicationToolDispatchResult.Executed
        val payload = JsonParser.parseString(result.payloadJson).asJsonObject
        val page = payload["pages"].asJsonArray.single().asJsonObject

        assertEquals(ToolRequest("Ada Lovelace", "pt"), request)
        assertEquals("success", payload["kind"].asString)
        assertEquals(
            setOf("title", "extract", "canonicalUrl", "language", "retrievedAtMillis"),
            page.keySet(),
        )
        assertEquals("Structured extract", page["extract"].asString)
        assertFalse(result.payloadJson.contains("untrustedContext"))
    }

    @Test
    fun `rejects invalid consumer state version and arguments before provider`() = runTest {
        var calls = 0
        val tool = WikipediaPagesTool {
            calls++
            WikipediaPagesResult.Success(listOf(PAGE))
        }

        val valid = ApplicationToolDispatcher(registry(tool))
        assertRejected(
            ApplicationToolRejection.UnsupportedModel,
            valid.execute(invocation(consumer = ApplicationToolConsumer.Model, verifiedModelToolIds = emptySet())),
        )
        assertTrue(
            valid.execute(invocation(consumer = ApplicationToolConsumer.Model)) is
                ApplicationToolDispatchResult.Executed,
        )
        assertRejected(
            ApplicationToolRejection.UnsupportedVersion,
            valid.execute(invocation(version = 2)),
        )
        assertRejected(
            ApplicationToolRejection.InvalidArguments,
            valid.execute(invocation(argumentsJson = """{"query":"x","language":"en","url":"secret"}""")),
        )
        assertRejected(
            ApplicationToolRejection.Disabled,
            ApplicationToolDispatcher(registry(tool, enabled = false)).execute(invocation()),
        )
        assertRejected(
            ApplicationToolRejection.NotConfigured,
            ApplicationToolDispatcher(registry(tool, ready = false)).execute(invocation()),
        )
        assertEquals(1, calls)
    }

    @Test
    fun `serializes every provider failure as a controlled result`() = runTest {
        ToolFailureReason.entries.forEach { reason ->
            val dispatcher = ApplicationToolDispatcher(
                registry(WikipediaPagesTool { WikipediaPagesResult.Failure(reason) }),
            )

            val result = dispatcher.execute(invocation()) as ApplicationToolDispatchResult.Executed
            val payload = JsonParser.parseString(result.payloadJson).asJsonObject

            assertEquals("failure", payload["kind"].asString)
            assertEquals(reason.name, payload["reason"].asString)
            assertEquals(setOf("kind", "reason"), payload.keySet())
        }
    }

    private fun registry(
        tool: WikipediaPagesTool = WikipediaPagesTool { WikipediaPagesResult.Success(listOf(PAGE)) },
        enabled: Boolean = true,
        ready: Boolean = true,
    ) = ApplicationToolRegistry(
        listOf(
            wikipediaPagesApplicationTool(tool) {
                ApplicationToolOperationalState(enabled, ready)
            },
        ),
    )

    private fun invocation(
        version: Int = 1,
        consumer: ApplicationToolConsumer = ApplicationToolConsumer.Widget,
        argumentsJson: String = """{"query":"Ada Lovelace","language":"pt"}""",
        verifiedModelToolIds: Set<String> = setOf(WIKIPEDIA_PAGES_TOOL_NAME),
    ) = ApplicationToolInvocation(
        id = WIKIPEDIA_PAGES_TOOL_NAME,
        version = version,
        consumer = consumer,
        argumentsJson = argumentsJson,
        verifiedModelToolIds = verifiedModelToolIds,
    )

    private fun assertRejected(
        expected: ApplicationToolRejection,
        result: ApplicationToolDispatchResult,
    ) {
        assertTrue(result is ApplicationToolDispatchResult.Rejected)
        assertEquals(expected, (result as ApplicationToolDispatchResult.Rejected).reason)
    }

    private companion object {
        val PAGE = WikipediaPage(
            title = "Ada Lovelace",
            extract = "Structured extract",
            canonicalUrl = "https://pt.wikipedia.org/wiki/Ada_Lovelace",
            language = "pt",
            retrievedAtMillis = 42L,
        )
    }
}
