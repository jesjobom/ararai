@file:Suppress("MaxLineLength") // JSON Schema and LiteRT contract literals are machine-readable single lines.

package com.jesjobom.ararai.tools

import com.google.gson.JsonObject
import com.jesjobom.ararai.chat.InstructionPreferences
import com.jesjobom.ararai.weather.OpenMeteoWeatherTool
import com.jesjobom.ararai.weather.WeatherPeriod
import com.jesjobom.ararai.weather.WeatherRequest
import com.jesjobom.ararai.weather.WeatherResult

const val WEATHER_FORECAST_TOOL_NAME = "weather_forecast"
private const val WEATHER_DISPLAY_NAME = "Weather forecast"

internal fun weatherModelBinding(
    tool: OpenMeteoWeatherTool,
    preferences: InstructionPreferences,
): RegisteredApplicationTool = weatherApplicationTool(tool, ApplicationToolConsumer.Model) {
    ApplicationToolOperationalState(enabled = preferences.settings.value.weatherEnabled, ready = true)
}

internal fun weatherApplicationTool(
    tool: OpenMeteoWeatherTool,
    consumer: ApplicationToolConsumer,
    state: () -> ApplicationToolOperationalState,
): RegisteredApplicationTool = applicationToolBinding(
    contract = ApplicationToolContract(
        id = WEATHER_FORECAST_TOOL_NAME,
        version = 1,
        displayName = WEATHER_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(consumer),
        inputSchemaJson = INPUT_SCHEMA,
        outputSchemaJson = OUTPUT_SCHEMA,
        authoringSummary = "Current or forecast weather for one explicitly named city or place. " +
            "Location is required; the app does not access device location. " +
            "Period: now, today, tomorrow, next_7_days. Includes Open-Meteo attribution.",
    ),
    state = state,
    executor = ApplicationTool(
        displayName = WEATHER_DISPLAY_NAME,
        category = ApplicationToolCategory.ExternalKnowledge,
        execute = tool::fetch,
    ),
    decodeArguments = { arguments -> decodeWeatherArguments(arguments, consumer) },
    encodeResult = { result ->
        when (result) {
            is WeatherResult.Success -> result.payload.toString()
            is WeatherResult.Failure -> JsonObject().apply {
                addProperty("kind", "failure")
                addProperty("reason", result.reason.name)
                if (result.reason == com.jesjobom.ararai.weather.WeatherFailure.LocationRequired) {
                    addProperty("message", "Specify a target city or place. The app cannot access current device location.")
                }
            }.toString()
        }
    },
)

@Suppress("ReturnCount") // Fail closed at each malformed input instead of constructing a partial request.
private fun decodeWeatherArguments(arguments: JsonObject, consumer: ApplicationToolConsumer): WeatherRequest? {
    if (arguments.keySet() !in setOf(setOf("period"), setOf("period", "location"))) return null
    val period = when (arguments.strictWeatherString("period")) {
        "now" -> WeatherPeriod.Now
        "today" -> WeatherPeriod.Today
        "tomorrow" -> WeatherPeriod.Tomorrow
        "next_7_days" -> WeatherPeriod.NextSevenDays
        else -> return null
    }
    val location = arguments.strictWeatherString("location")
    if ("location" in arguments.keySet() && location == null) return null
    if (consumer == ApplicationToolConsumer.Widget && location.isNullOrBlank()) return null
    return WeatherRequest(location, period)
}

private fun JsonObject.strictWeatherString(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

private const val INPUT_SCHEMA =
    """{"type":"object","additionalProperties":false,"properties":{"location":{"type":"string","minLength":1,"maxLength":120},"period":{"type":"string","enum":["now","today","tomorrow","next_7_days"]}},"required":["location","period"]}"""

private const val OUTPUT_SCHEMA =
    """{"type":"object","properties":{"kind":{"type":"string"},"source":{"type":"string"},"sourceUrl":{"type":"string"},"retrievedAtMillis":{"type":"integer"},"location":{"type":"string"},"timezone":{"type":"string"},"period":{"type":"string"},"current":{"type":"object","properties":{"time":{"type":"string"},"temperatureC":{"type":"number"},"humidityPercent":{"type":"number"},"feelsLikeC":{"type":"number"},"precipitationMm":{"type":"number"},"weatherCode":{"type":"integer"},"windSpeedKmh":{"type":"number"}}},"days":{"type":"array","maxItems":7,"items":{"type":"object","properties":{"date":{"type":"string"},"weatherCode":{"type":"integer"},"temperatureMaxC":{"type":"number"},"temperatureMinC":{"type":"number"},"precipitationSumMm":{"type":"number"},"precipitationProbabilityMaxPercent":{"type":"number"}}}},"hours":{"type":"array","maxItems":25,"items":{"type":"object","properties":{"time":{"type":"string"},"temperatureC":{"type":"number"},"precipitationProbabilityPercent":{"type":"number"},"precipitationMm":{"type":"number"},"weatherCode":{"type":"integer"}}}},"reason":{"type":"string"},"message":{"type":"string"}},"required":["kind"]}"""
