package com.jesjobom.ararai.weather

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Keeps the full hourly result for widgets, but bounds the context sent to a local model. */
@Suppress("ReturnCount") // Malformed or unexpected tool results pass through unchanged.
internal fun weatherPayloadForModel(payload: String): String {
    val data = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: return payload
    val hours = data.get("hours")?.takeIf { it.isJsonArray }?.asJsonArray ?: return payload
    val windows = JsonArray()
    hours.toList().chunked(6).forEach { group ->
        val first = group.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return payload
        val last = group.lastOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return payload
        val start = first.get("time")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString ?: return payload
        val end = last.get("time")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString ?: return payload
        val observations = group.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }
        val probabilities = observations.mapNotNull { it.number("precipitationProbabilityPercent") }
        val precipitation = observations.mapNotNull { it.number("precipitationMm") }
        windows.add(
            JsonObject().apply {
                addProperty("startTime", start)
                addProperty("lastHour", end)
                probabilities.maxOrNull()?.let { addProperty("maxRainProbabilityPercent", it) }
                if (precipitation.isNotEmpty()) addProperty("precipitationSumMm", precipitation.sum())
            },
        )
    }
    data.remove("hours")
    data.add("rainWindows", windows)
    return data.toString()
}

private fun JsonObject.number(name: String): Double? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
    ?.asDouble
