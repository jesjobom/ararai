package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.requireFields
import com.jesjobom.ararai.widget.runtime.requiredObject

internal data class WidgetScheduleArtifact(val periodicIntervalHours: Long?)

internal fun parseWidgetSchedule(
    raw: String,
    allowedIntervals: Set<Long> = ManagedWidgetPolicy.MODEL_PERIODIC_INTERVAL_HOURS,
): WidgetScheduleArtifact = StrictJson.parse(
    raw,
    maxStringChars = WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES,
).requiredObject().let { root ->
    root.requireFields("periodicIntervalHours")
    val value = root.get("periodicIntervalHours")
    val interval = if (value == null || value.isJsonNull) {
        null
    } else {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        value.asBigDecimal.longValueExact()
    }
    require(interval == null || interval in allowedIntervals)
    WidgetScheduleArtifact(interval)
}

internal fun currentWidgetIntervalHours(prompt: WidgetAuthoringPrompt): Long? = StrictJson.parse(prompt.apiContextJson)
    .requiredObject().get("currentWidget")?.takeUnless { it.isJsonNull }
    ?.asJsonObject?.get("periodicIntervalHours")?.takeUnless { it.isJsonNull }?.asLong

internal fun scheduleContext(prompt: WidgetAuthoringPrompt): String {
    val current = currentWidgetIntervalHours(prompt)
    return StrictJson.canonical(
        JsonObject().apply {
            addProperty("userInstruction", prompt.userInstruction)
            add("currentPeriodicIntervalHours", current?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
        },
    )
}

internal const val WIDGET_SCHEDULE_OBJECTIVE =
    "Choose the closest supported periodic refresh interval in hours for the user's request. " +
        "Interpret the complete instruction, including occurrences per day or week. " +
        "If several intervals are equally close, prefer the slower refresh. " +
        "If no refresh was requested, preserve the current interval when editing; otherwise return null. " +
        "Always choose one supplied option or null. Do not ask a question or invent an interval."
