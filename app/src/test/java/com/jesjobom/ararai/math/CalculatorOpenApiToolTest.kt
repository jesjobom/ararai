@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.math

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorOpenApiToolTest {
    @Test
    fun `declares narrow local calculator schema`() {
        val schema = JsonParser.parseString(CalculatorOpenApiTool.DESCRIPTION).asJsonObject
        assertEquals("calculator", schema["name"].asString)
        assertFalse(schema["parameters"].asJsonObject["additionalProperties"].asBoolean)
        assertEquals(listOf("expression"), schema["parameters"].asJsonObject["required"].asJsonArray.map { it.asString })
    }

    @Test
    fun `serializes value and precision without provenance`() {
        var captured = ""
        val tool = CalculatorToolTurn { expression ->
            captured = expression
            MathEvaluationResult.Success("4", MathResultKind.Exact)
        }
        val events = mutableListOf<CalculatorExecutionEvent>()
        tool.beginTurn(events::add)

        val response = JsonParser.parseString(tool.execute("""{"expression":"2+2"}""")).asJsonObject

        assertEquals("2+2", captured)
        assertTrue(response["ok"].asBoolean)
        assertEquals("4", response["value"].asString)
        assertEquals("exact", response["precision"].asString)
        assertEquals(setOf("ok", "value", "precision"), response.keySet())
        assertEquals(listOf(CalculatorExecutionEvent.Started, CalculatorExecutionEvent.Finished()), events)
    }

    @Test
    fun `invalid arguments and per-turn limit are controlled`() {
        val tool = CalculatorToolTurn { MathEvaluationResult.Success("1", MathResultKind.Exact) }
        tool.beginTurn()
        assertEquals("INVALID_ARGUMENTS", error(tool.execute("{}")))
        tool.execute("""{"expression":"1"}""")
        tool.execute("""{"expression":"1"}""")
        assertEquals("CALL_LIMIT_REACHED", error(tool.execute("""{"expression":"1"}""")))
        tool.beginTurn()
        assertTrue(JsonParser.parseString(tool.execute("""{"expression":"1"}""")).asJsonObject["ok"].asBoolean)
    }

    private fun error(raw: String): String = JsonParser.parseString(raw).asJsonObject["error"].asString
}
