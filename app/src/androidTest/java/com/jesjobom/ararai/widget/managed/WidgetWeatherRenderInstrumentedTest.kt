package com.jesjobom.ararai.widget.managed

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import com.jesjobom.ararai.widget.runtime.QuickJsWidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRequestedLimits
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimePolicy
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import com.jesjobom.ararai.widget.runtime.toJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetWeatherRenderInstrumentedTest {
    private val source = JsonParser.parseString(
        requireNotNull(
            WidgetWeatherRenderArtifact.forecast(
                WidgetAlgorithmArtifact(
                    steps = listOf(
                        WidgetAlgorithmStep(
                            "forecast",
                            WidgetAlgorithmStepKind.ToolCall,
                            "Fetch weather",
                            emptyList(),
                            WidgetToolCapability("weather_forecast", 1),
                        ),
                    ),
                    presentationObjective = "Show forecast",
                    renderCapability = WidgetRenderCapability.CustomSource,
                ),
            ),
        ),
    ).asJsonObject.get("source").asString

    @Test
    fun currentAndDailyForecastsShowMeasurementsWithoutInventedLinks() = runBlocking {
        val current = render(
            """{"status":"success","payload":{"kind":"success","location":"Montréal","period":"now","current":{"temperatureC":12.4,"humidityPercent":64,"feelsLikeC":10.1,"precipitationMm":0.5}}}""",
        )
        assertTrue(current.contains("12.4 °C"))
        assertTrue(current.contains("Humidity 64%"))
        assertTrue(current.contains("10.1 °C"))
        assertTrue(!current.contains("https_link"))

        val tomorrow = render(
            """{"status":"success","payload":{"kind":"success","location":"Montréal","period":"tomorrow","days":[{"date":"2026-11-09","temperatureMaxC":18.2,"temperatureMinC":7.1,"precipitationProbabilityMaxPercent":65}]}}""",
        )
        assertTrue(tomorrow.contains("18.2 / 7.1 °C"))
        assertTrue(tomorrow.contains("rain 65%"))

        val week = render(
            """{"status":"success","payload":{"kind":"success","location":"Montréal","period":"next_7_days","days":[{"date":"2026-11-09","temperatureMaxC":18,"temperatureMinC":7},{"date":"2026-11-10","temperatureMaxC":14,"temperatureMinC":4}]}}""",
        )
        assertTrue(week.contains("18 / 7 °C"))
        assertTrue(week.contains("14 / 4 °C"))
    }

    @Test
    fun failedProviderResultShowsFallbackWithoutPretendingToHaveForecast() = runBlocking {
        assertEquals(
            "Weather forecast unavailable.",
            presentationText(render("""{"status":"failure","reason":"ProviderUnavailable"}""")),
        )
    }

    private suspend fun render(outcome: String): String {
        val result = QuickJsWidgetJavaScriptEngine().call(
            source = source,
            entrypoint = "render",
            argumentsJson = listOf(
                WidgetRuntimeContext("en-CA", "America/Toronto", "2026-11-08T14:00:00-05:00", 1)
                    .toJson(WidgetRuntimeValue.entries.toSet()),
                """{"forecast":$outcome}""",
                "{}",
            ),
            limits = LIMITS,
        )
        assertTrue(result is WidgetScriptResult.Success)
        return (result as WidgetScriptResult.Success).outputJson
    }

    private fun presentationText(output: String): String = JsonParser.parseString(output).asJsonObject
        .getAsJsonObject("child").getAsJsonArray("children")[0].asJsonObject.get("text").asString

    private companion object {
        val LIMITS = WidgetRequestedLimits(
            memoryBytes = WidgetRuntimePolicy.MAX_MEMORY_BYTES,
            stackBytes = WidgetRuntimePolicy.MAX_STACK_BYTES,
            executionMillis = WidgetRuntimePolicy.MAX_EXECUTION_MILLIS,
            maxToolCalls = WidgetRuntimePolicy.MAX_TOOL_CALLS,
            maxOutputBytes = WidgetRuntimePolicy.MAX_OUTPUT_BYTES,
        )
    }
}
