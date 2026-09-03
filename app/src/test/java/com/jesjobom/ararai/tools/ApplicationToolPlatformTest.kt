package com.jesjobom.ararai.tools

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationToolPlatformTest {
    @Test
    fun `registry rejects duplicate contracts and invalid consumers`() {
        val binding = binding()

        val duplicate = runCatching { ApplicationToolRegistry(listOf(binding, binding)) }
        val noConsumers = runCatching {
            contract(consumers = emptySet())
        }

        assertTrue(duplicate.isFailure)
        assertTrue(noConsumers.isFailure)
    }

    @Test
    fun `registry exposes only non secret metadata`() {
        val secret = "private-token"
        val registry = ApplicationToolRegistry(
            listOf(
                binding(
                    state = { ApplicationToolOperationalState(enabled = true, ready = secret.isNotBlank()) },
                ),
            ),
        )

        val serialized = registry.descriptors().single().toString()

        assertFalse(serialized.contains(secret))
        assertEquals(setOf("uppercase"), registry.availableToolIds(ApplicationToolConsumer.Model))
        assertEquals(emptySet<String>(), registry.availableToolIds(ApplicationToolConsumer.Widget))
    }

    @Test
    fun `dispatcher executes one valid typed invocation and returns bounded json`() = runTest {
        val calls = AtomicInteger()
        val dispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(listOf(binding(calls = calls))),
        )

        val result = dispatcher.execute(invocation()) as ApplicationToolDispatchResult.Executed

        assertEquals(1, calls.get())
        assertEquals("HELLO", JsonParser.parseString(result.payloadJson).asJsonObject["value"].asString)
    }

    @Test
    fun `dispatcher rejects malformed and additional arguments before execution`() = runTest {
        val calls = AtomicInteger()
        val dispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(listOf(binding(calls = calls))),
        )

        val missing = dispatcher.execute(invocation(argumentsJson = "{}"))
        val additional = dispatcher.execute(invocation(argumentsJson = """{"value":"hello","url":"x"}"""))
        val nonObject = dispatcher.execute(invocation(argumentsJson = "[]"))

        assertRejected(ApplicationToolRejection.InvalidArguments, missing)
        assertRejected(ApplicationToolRejection.InvalidArguments, additional)
        assertRejected(ApplicationToolRejection.InvalidArguments, nonObject)
        assertEquals(0, calls.get())
    }

    @Test
    fun `dispatcher distinguishes identity version readiness consumer and model capability`() = runTest {
        val dispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(
                listOf(
                    binding(id = "disabled", state = { ApplicationToolOperationalState(false, true) }),
                    binding(id = "unconfigured", state = { ApplicationToolOperationalState(true, false) }),
                    binding(id = "model_only"),
                    binding(
                        id = "widget_only",
                        consumers = setOf(ApplicationToolConsumer.Widget),
                    ),
                ),
            ),
        )

        assertRejected(ApplicationToolRejection.UnknownTool, dispatcher.execute(invocation(id = "missing")))
        assertRejected(
            ApplicationToolRejection.UnsupportedVersion,
            dispatcher.execute(invocation(id = "model_only", version = 2)),
        )
        assertRejected(
            ApplicationToolRejection.Disabled,
            dispatcher.execute(invocation(id = "disabled", verifiedModelToolIds = setOf("disabled"))),
        )
        assertRejected(
            ApplicationToolRejection.NotConfigured,
            dispatcher.execute(invocation(id = "unconfigured", verifiedModelToolIds = setOf("unconfigured"))),
        )
        assertRejected(
            ApplicationToolRejection.IneligibleConsumer,
            dispatcher.execute(
                invocation(
                    id = "model_only",
                    consumer = ApplicationToolConsumer.Widget,
                    verifiedModelToolIds = emptySet(),
                ),
            ),
        )
        assertRejected(
            ApplicationToolRejection.UnsupportedModel,
            dispatcher.execute(invocation(id = "model_only", verifiedModelToolIds = emptySet())),
        )
        assertTrue(
            dispatcher.execute(
                invocation(
                    id = "widget_only",
                    consumer = ApplicationToolConsumer.Widget,
                    verifiedModelToolIds = emptySet(),
                ),
            ) is ApplicationToolDispatchResult.Executed,
        )
    }

    @Test
    fun `dispatcher bounds execution time`() = runTest {
        val release = CompletableDeferred<Unit>()
        val dispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(
                listOf(binding(timeoutMillis = 10L, beforeResult = { release.await() })),
            ),
        )

        val result = dispatcher.execute(invocation())

        assertRejected(ApplicationToolRejection.TimedOut, result)
    }

    @Test
    fun `caller cancellation is not swallowed`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(
                listOf(
                    binding(
                        timeoutMillis = 10_000L,
                        beforeResult = {
                            started.complete(Unit)
                            release.await()
                        },
                    ),
                ),
            ),
        )

        val execution = async { dispatcher.execute(invocation()) }
        started.await()
        execution.cancelAndJoin()

        assertTrue(execution.isCancelled)
    }

    private fun binding(
        id: String = "uppercase",
        consumers: Set<ApplicationToolConsumer> = setOf(ApplicationToolConsumer.Model),
        state: () -> ApplicationToolOperationalState = { ApplicationToolOperationalState(true, true) },
        calls: AtomicInteger = AtomicInteger(),
        timeoutMillis: Long = 1_000L,
        beforeResult: suspend () -> Unit = {},
    ): RegisteredApplicationTool = applicationToolBinding(
        contract = contract(id, consumers),
        state = state,
        policy = ApplicationToolExecutionPolicy(timeoutMillis = timeoutMillis),
        executor = ApplicationTool(
            displayName = "Uppercase",
            category = ApplicationToolCategory.LocalCompute,
        ) { request: UppercaseRequest ->
            calls.incrementAndGet()
            beforeResult()
            UppercaseResult(request.value.uppercase())
        },
        decodeArguments = { json ->
            json.takeIf { it.keySet() == setOf("value") }
                ?.get("value")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.takeIf { it.length <= 32 }
                ?.let(::UppercaseRequest)
        },
        encodeResult = { result ->
            JsonObject().apply { addProperty("value", result.value) }.toString()
        },
    )

    private fun contract(
        id: String = "uppercase",
        consumers: Set<ApplicationToolConsumer> = setOf(ApplicationToolConsumer.Model),
    ) = ApplicationToolContract(
        id = id,
        version = 1,
        displayName = "Uppercase",
        category = ApplicationToolCategory.LocalCompute,
        consumers = consumers,
        inputSchemaJson =
        """{"type":"object","additionalProperties":false,"properties":""" +
            """{"value":{"type":"string","maxLength":32}},"required":["value"]}""",
        outputSchemaJson =
        """{"type":"object","additionalProperties":false,"properties":""" +
            """{"value":{"type":"string"}},"required":["value"]}""",
    )

    private fun invocation(
        id: String = "uppercase",
        version: Int = 1,
        consumer: ApplicationToolConsumer = ApplicationToolConsumer.Model,
        argumentsJson: String = """{"value":"hello"}""",
        verifiedModelToolIds: Set<String> = setOf(id),
    ) = ApplicationToolInvocation(
        id = id,
        version = version,
        consumer = consumer,
        argumentsJson = argumentsJson,
        verifiedModelToolIds = verifiedModelToolIds,
    )

    private fun assertRejected(
        expected: ApplicationToolRejection,
        actual: ApplicationToolDispatchResult,
    ) {
        assertEquals(expected, (actual as ApplicationToolDispatchResult.Rejected).reason)
    }

    private data class UppercaseRequest(val value: String)
    private data class UppercaseResult(val value: String)
}
