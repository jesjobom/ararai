package com.jesjobom.ararai.widget.runtime

import com.google.gson.JsonObject
import com.jesjobom.ararai.tools.ApplicationTool
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolExecutionPolicy
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.tools.applicationToolBinding
import com.jesjobom.ararai.widget.WidgetToolExecutionGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GatewayWidgetProgramToolExecutorTest {
    @Test
    fun `dispatches a widget-only tool once and canonicalizes its bounded payload`() = runTest {
        val calls = AtomicInteger()
        val executor = executor(calls = calls)

        val outcome = executor.execute(call())

        assertEquals(1, calls.get())
        assertEquals(
            "{\"today\":{\"payload\":{\"a\":1,\"b\":2},\"status\":\"success\"}}",
            listOf(outcome).toJson(),
        )
    }

    @Test
    fun `preserves dispatcher rejection contracts as canonical outcomes`() = runTest {
        assertRejected(
            ApplicationToolRejection.IneligibleConsumer,
            executor(consumers = setOf(ApplicationToolConsumer.Model)).execute(call()),
        )
        assertRejected(
            ApplicationToolRejection.Disabled,
            executor(enabled = false).execute(call()),
        )
        assertRejected(
            ApplicationToolRejection.NotConfigured,
            executor(ready = false).execute(call()),
        )
        assertRejected(
            ApplicationToolRejection.InvalidArguments,
            executor().execute(call(argumentsJson = "{}")),
        )
        assertRejected(
            ApplicationToolRejection.Unavailable,
            executor(beforeResult = { error("provider detail must remain internal") }).execute(call()),
        )
        assertRejected(
            ApplicationToolRejection.TimedOut,
            executor(timeoutMillis = 10L, beforeResult = { awaitCancellation() }).execute(call()),
        )
        assertRejected(
            ApplicationToolRejection.Cancelled,
            executor(beforeResult = { throw CancellationException("tool detail") }).execute(call()),
        )
    }

    @Test
    fun `rejects malformed gateway success payload without exposing it to render`() = runTest {
        val outcome = executor(encodeResult = { "[]" }).execute(call())

        assertRejected(ApplicationToolRejection.Unavailable, outcome)
        assertEquals(
            "{\"today\":{\"reason\":\"unavailable\",\"status\":\"failure\"}}",
            listOf(outcome).toJson(),
        )
    }

    private fun executor(
        consumers: Set<ApplicationToolConsumer> = setOf(ApplicationToolConsumer.Widget),
        enabled: Boolean = true,
        ready: Boolean = true,
        calls: AtomicInteger = AtomicInteger(),
        timeoutMillis: Long = 1_000L,
        beforeResult: suspend () -> Unit = {},
        encodeResult: (String) -> String = {
            JsonObject().apply {
                addProperty("b", 2)
                addProperty("a", 1)
            }.toString()
        },
    ): GatewayWidgetProgramToolExecutor {
        val binding = applicationToolBinding(
            contract = ApplicationToolContract(
                id = "fixture_data",
                version = 1,
                displayName = "Fixture data",
                category = ApplicationToolCategory.ExternalKnowledge,
                consumers = consumers,
                inputSchemaJson = """{"type":"object"}""",
                outputSchemaJson = """{"type":"object"}""",
            ),
            state = { ApplicationToolOperationalState(enabled, ready) },
            executor = ApplicationTool(
                displayName = "Fixture data",
                category = ApplicationToolCategory.ExternalKnowledge,
            ) { key: String ->
                calls.incrementAndGet()
                beforeResult()
                key
            },
            decodeArguments = { json ->
                json.takeIf { it.keySet() == setOf("key") }
                    ?.get("key")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
            },
            encodeResult = encodeResult,
            policy = ApplicationToolExecutionPolicy(timeoutMillis = timeoutMillis),
        )
        return GatewayWidgetProgramToolExecutor(
            WidgetToolExecutionGateway(ApplicationToolDispatcher(ApplicationToolRegistry(listOf(binding)))),
        )
    }

    private fun call(argumentsJson: String = """{"key":"today"}""") = PlannedWidgetToolCall(
        alias = "today",
        tool = WidgetToolCapability("fixture_data", 1),
        argumentsJson = argumentsJson,
    )

    private fun assertRejected(expected: ApplicationToolRejection, actual: WidgetToolOutcome) {
        assertEquals(null, actual.payload)
        assertEquals(expected, actual.rejection)
    }
}
