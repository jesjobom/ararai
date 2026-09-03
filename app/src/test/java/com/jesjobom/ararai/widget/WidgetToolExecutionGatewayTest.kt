package com.jesjobom.ararai.widget

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.tools.ApplicationTool
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.tools.applicationToolBinding
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class WidgetToolExecutionGatewayTest {
    @Test
    fun `executes a widget tool directly and returns only structured payload`() = runTest {
        val calls = AtomicInteger()
        val gateway = gateway(consumers = setOf(ApplicationToolConsumer.Widget), calls = calls)

        val result = gateway.execute(WidgetToolInvocation("widget_data", 1, """{"key":"temperature"}"""))
            as WidgetToolExecutionResult.Success

        assertEquals(1, calls.get())
        assertEquals("temperature-value", JsonParser.parseString(result.payloadJson).asJsonObject["value"].asString)
    }

    @Test
    fun `rejects a model only tool without invoking it`() = runTest {
        val calls = AtomicInteger()
        val gateway = gateway(consumers = setOf(ApplicationToolConsumer.Model), calls = calls)

        val result = gateway.execute(WidgetToolInvocation("widget_data", 1, """{"key":"temperature"}"""))
            as WidgetToolExecutionResult.Failure

        assertEquals(ApplicationToolRejection.IneligibleConsumer, result.reason)
        assertEquals(0, calls.get())
    }

    @Test
    fun `gateway has no scheduler or model dependency`() {
        val constructorTypes = WidgetToolExecutionGateway::class.java.declaredConstructors
            .flatMap { constructor -> constructor.parameterTypes.toList() }

        assertEquals(listOf(ApplicationToolDispatcher::class.java), constructorTypes)
        assertTrue(
            WidgetToolExecutionGateway::class.java.declaredFields.none { field ->
                field.type.name.contains("WorkManager") || field.type.name.contains("LocalLlm")
            },
        )
    }

    private fun gateway(
        consumers: Set<ApplicationToolConsumer>,
        calls: AtomicInteger,
    ): WidgetToolExecutionGateway {
        val binding = applicationToolBinding(
            contract = ApplicationToolContract(
                id = "widget_data",
                version = 1,
                displayName = "Widget data",
                category = ApplicationToolCategory.ExternalKnowledge,
                consumers = consumers,
                inputSchemaJson = """{"type":"object","additionalProperties":false}""",
                outputSchemaJson = """{"type":"object","additionalProperties":false}""",
            ),
            state = { ApplicationToolOperationalState(enabled = true, ready = true) },
            executor = ApplicationTool(
                displayName = "Widget data",
                category = ApplicationToolCategory.ExternalKnowledge,
            ) { request: String ->
                calls.incrementAndGet()
                "$request-value"
            },
            decodeArguments = { json ->
                json.takeIf { it.keySet() == setOf("key") }
                    ?.get("key")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
            },
            encodeResult = { result -> JsonObject().apply { addProperty("value", result) }.toString() },
        )
        return WidgetToolExecutionGateway(
            ApplicationToolDispatcher(ApplicationToolRegistry(listOf(binding))),
        )
    }
}
