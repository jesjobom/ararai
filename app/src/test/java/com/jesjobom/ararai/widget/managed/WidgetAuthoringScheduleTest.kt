package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAuthoringScheduleTest {
    @Test
    fun `new feasibility omits cadence while older artifacts retain their contract`() {
        val withoutSchedule = """{"outcome":"achievable","message":"Daily facts","toolIds":[]}"""
        val withSchedule = withoutSchedule.replace("\"toolIds\"", "\"periodicIntervalHours\":null,\"toolIds\"")
        val accepted = WidgetFeasibilityParser.parse(withoutSchedule, emptySet(), includeScheduleField = false)

        assertTrue(accepted is WidgetFeasibilityParseResult.Valid)
        assertEquals(null, (accepted as WidgetFeasibilityParseResult.Valid).artifact.periodicIntervalHours)
        assertEquals(
            WidgetFeasibilityParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidFeasibilityFields),
            WidgetFeasibilityParser.parse(withSchedule, emptySet(), includeScheduleField = false),
        )
        assertEquals(
            WidgetFeasibilityParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidFeasibilityFields),
            WidgetFeasibilityParser.parse(withoutSchedule, emptySet()),
        )
        assertTrue(WidgetFeasibilityParser.parse(withSchedule, emptySet()) is WidgetFeasibilityParseResult.Valid)
    }

    @Test
    fun `schedule capture offers exactly the supported intervals`() {
        val schema = JsonParser.parseString(WidgetAuthoringStageSchemas.schedule(null)).asJsonObject
        val values = schema.getAsJsonObject("parameters").getAsJsonObject("properties")
            .getAsJsonObject("periodicIntervalHours").getAsJsonArray("enum")
        assertEquals(
            ManagedWidgetPolicy.MODEL_PERIODIC_INTERVAL_HOURS,
            values.filterNot { it.isJsonNull }.map { it.asLong }.toSet(),
        )
        assertEquals(6L, parseWidgetSchedule("""{"periodicIntervalHours":6}""").periodicIntervalHours)
        assertTrue(values.any { it.isJsonNull })
        val existing = JsonParser.parseString(WidgetAuthoringStageSchemas.schedule(3)).asJsonObject
            .getAsJsonObject("parameters").getAsJsonObject("properties")
            .getAsJsonObject("periodicIntervalHours").getAsJsonArray("enum")
        assertTrue(existing.any { !it.isJsonNull && it.asLong == 3L })
        assertEquals(3L, parseWidgetSchedule("""{"periodicIntervalHours":3}""", setOf(3L)).periodicIntervalHours)
    }

    @Test
    fun `schedule capture rejects unsupported or expanded values`() {
        listOf(
            """{"periodicIntervalHours":25}""",
            """{"periodicIntervalHours":3}""",
            """{"periodicIntervalHours":6.5}""",
            """{"periodicIntervalHours":6,"other":true}""",
        ).forEach { raw ->
            assertThrows(RuntimeException::class.java) { parseWidgetSchedule(raw) }
        }
    }

    @Test
    fun `schedule context contains only instruction and current interval`() {
        val prompt = WidgetAuthoringPrompt(
            "Atualize 4 vezes ao dia",
            """{"currentWidget":{"periodicIntervalHours":24,"source":"private"}}""",
        )
        val context = JsonParser.parseString(scheduleContext(prompt)).asJsonObject

        assertEquals(24L, context.get("currentPeriodicIntervalHours").asLong)
        assertEquals("Atualize 4 vezes ao dia", context.get("userInstruction").asString)
        assertTrue(!context.toString().contains("supportedIntervalsHours"))
        assertTrue(!context.toString().contains("private"))
        val feasibility = JsonParser.parseString(feasibilityContextWithSeparateSchedule(prompt)).asJsonObject
        assertTrue(!feasibility.toString().contains("supportedIntervalsHours"))
        val currentSchema = JsonParser.parseString(WidgetAuthoringStageSchemas.feasibilityWithoutSchedule)
            .asJsonObject.getAsJsonObject("parameters")
        assertTrue(!currentSchema.getAsJsonObject("properties").has("periodicIntervalHours"))
        assertEquals(
            listOf("outcome", "message", "toolIds"),
            currentSchema.getAsJsonArray("required").map { it.asString },
        )
        val legacySchema = JsonParser.parseString(WidgetAuthoringStageSchemas.feasibilityWithSeparateSchedule)
            .asJsonObject.getAsJsonObject("parameters").getAsJsonObject("properties")
            .getAsJsonObject("periodicIntervalHours").getAsJsonArray("enum")
        assertEquals(1, legacySchema.size())
        assertTrue(legacySchema.single().isJsonNull)
    }
}
