package com.jesjobom.ararai.tools

import com.google.gson.JsonParser
import com.jesjobom.ararai.knowledge.KnowledgeHttpResponse
import com.jesjobom.ararai.knowledge.KnowledgeHttpTransport
import com.jesjobom.ararai.weather.OpenMeteoWeatherTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherApplicationToolTest {
    @Test
    fun `model and widget contracts require a target place`() {
        val weather = OpenMeteoWeatherTool()
        val modelBinding = weatherApplicationTool(weather, ApplicationToolConsumer.Model) {
            ApplicationToolOperationalState(enabled = true, ready = true)
        }
        val widgetBinding = weatherApplicationTool(weather, ApplicationToolConsumer.Widget) {
            ApplicationToolOperationalState(enabled = true, ready = true)
        }
        listOf(modelBinding, widgetBinding).forEach { binding ->
            val schema = JsonParser.parseString(binding.contract.inputSchemaJson).asJsonObject
            val required = schema.getAsJsonArray("required").map { it.asString }
            assertEquals(listOf("location", "period"), required)
        }
    }

    @Test
    fun `requires explicit activation independently for chat and widgets`() = runTest {
        var chatEnabled = false
        var widgetEnabled = false
        val weather = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport { url ->
                val data = if (url.contains("geocoding-api")) GEOCODING else FORECAST
                KnowledgeHttpResponse(200, "application/json", data.toByteArray())
            },
        )
        val chatDispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(
                listOf(
                    weatherApplicationTool(weather, ApplicationToolConsumer.Model) {
                        ApplicationToolOperationalState(enabled = chatEnabled, ready = true)
                    },
                ),
            ),
        )
        val widgetDispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(
                listOf(
                    weatherApplicationTool(weather, ApplicationToolConsumer.Widget) {
                        ApplicationToolOperationalState(enabled = widgetEnabled, ready = true)
                    },
                ),
            ),
        )

        assertRejected(
            ApplicationToolRejection.Disabled,
            widgetDispatcher.execute(invocation(ApplicationToolConsumer.Widget)),
        )
        assertRejected(
            ApplicationToolRejection.Disabled,
            chatDispatcher.execute(invocation(ApplicationToolConsumer.Model)),
        )
        widgetEnabled = true
        val widgetResult = widgetDispatcher.execute(invocation(ApplicationToolConsumer.Widget))
        assertTrue(widgetResult is ApplicationToolDispatchResult.Executed)
        val payload = JsonParser.parseString(
            (widgetResult as ApplicationToolDispatchResult.Executed).payloadJson,
        ).asJsonObject
        assertEquals("Open-Meteo", payload["source"].asString)
        assertRejected(
            ApplicationToolRejection.Disabled,
            chatDispatcher.execute(invocation(ApplicationToolConsumer.Model)),
        )
        chatEnabled = true
        val chatResult = chatDispatcher.execute(invocation(ApplicationToolConsumer.Model))
        assertTrue(chatResult is ApplicationToolDispatchResult.Executed)
    }

    @Test
    fun `missing location produces actionable failure without contacting provider`() = runTest {
        var contacted = false
        val weather = OpenMeteoWeatherTool(
            transport = KnowledgeHttpTransport {
                contacted = true
                KnowledgeHttpResponse(200, "application/json", FORECAST.toByteArray())
            },
        )
        val modelDispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(
                listOf(
                    weatherApplicationTool(weather, ApplicationToolConsumer.Model) {
                        ApplicationToolOperationalState(enabled = true, ready = true)
                    },
                ),
            ),
        )
        val widgetDispatcher = ApplicationToolDispatcher(
            ApplicationToolRegistry(
                listOf(
                    weatherApplicationTool(weather, ApplicationToolConsumer.Widget) {
                        ApplicationToolOperationalState(enabled = true, ready = true)
                    },
                ),
            ),
        )

        listOf("""{"period":"tomorrow","url":"x"}""", """{"period":"yesterday"}""").forEach { input ->
            assertRejected(
                ApplicationToolRejection.InvalidArguments,
                widgetDispatcher.execute(invocation(ApplicationToolConsumer.Widget, input)),
            )
        }
        assertRejected(
            ApplicationToolRejection.InvalidArguments,
            widgetDispatcher.execute(invocation(ApplicationToolConsumer.Widget, """{"period":"now"}""")),
        )
        val missing = modelDispatcher.execute(invocation(ApplicationToolConsumer.Model, """{"period":"now"}"""))
        assertTrue(missing is ApplicationToolDispatchResult.Executed)
        val failure = JsonParser.parseString(
            (missing as ApplicationToolDispatchResult.Executed).payloadJson,
        ).asJsonObject
        assertEquals("LocationRequired", failure["reason"].asString)
        assertTrue(failure["message"].asString.contains("target city"))
        assertFalse(contacted)
    }

    private fun invocation(
        consumer: ApplicationToolConsumer,
        arguments: String = """{"period":"now","location":"Brasília, BR"}""",
    ) = ApplicationToolInvocation(
        id = WEATHER_FORECAST_TOOL_NAME,
        version = 1,
        consumer = consumer,
        argumentsJson = arguments,
        verifiedModelToolIds = setOf(WEATHER_FORECAST_TOOL_NAME),
    )

    private fun assertRejected(expected: ApplicationToolRejection, actual: ApplicationToolDispatchResult) {
        assertTrue(actual is ApplicationToolDispatchResult.Rejected)
        assertEquals(expected, (actual as ApplicationToolDispatchResult.Rejected).reason)
    }

    private companion object {
        const val GEOCODING =
            """{"results":[{"name":"Brasília","country_code":"BR","latitude":-15.79,"longitude":-47.88}]}"""
        const val FORECAST = """{"timezone":"America/Sao_Paulo","current":{
            "time":"2026-11-07T12:00","temperature_2m":25}}"""
    }
}
