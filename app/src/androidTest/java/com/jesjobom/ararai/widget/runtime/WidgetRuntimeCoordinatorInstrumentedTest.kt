package com.jesjobom.ararai.widget.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRuntimeCoordinatorInstrumentedTest {
    @Test
    fun executesRealTwoPhaseProgramWithFreshQuickJsIsolates() = runBlocking {
        val source = """
            var phaseState = 0;
            function plan(context) {
              phaseState = 99;
              return [{
                alias: 'today', toolId: 'fixture_data', contractVersion: 1,
                arguments: { locale: context.locale }
              }];
            }
            function render(context, outcomes, state) {
              let outcomeBlocked = false;
              let stateBlocked = false;
              try { outcomes.today.payload.value = 'changed'; } catch (_) { outcomeBlocked = true; }
              try { state.suffix = 'changed'; } catch (_) { stateBlocked = true; }
              return {
                type: 'text',
                text: [
                  String(phaseState), outcomes.today.payload.value, state.suffix,
                  String(outcomeBlocked), String(stateBlocked)
                ].join(':'),
                tone: 'neutral'
              };
            }
        """.trimIndent()
        val program = parseProgram(source)
        val calls = mutableListOf<PlannedWidgetToolCall>()
        val result = WidgetRuntimeCoordinator(QuickJsWidgetJavaScriptEngine()) { call ->
            calls += call
            WidgetToolOutcome(
                call.alias,
                JsonObject().apply { addProperty("value", "ok") },
                null,
            )
        }.execute(
            program,
            grant(),
            WidgetRuntimeContext("pt-BR", "America/Toronto", "2026-09-04T06:00:00", null),
            "{\"suffix\":\"done\"}",
        )

        assertEquals(1, calls.size)
        assertEquals("{\"locale\":\"pt-BR\"}", calls.single().argumentsJson)
        assertTrue(result is WidgetExecutionResult.Success)
        val text = (result as WidgetExecutionResult.Success).presentation as WidgetPresentationNode.Text
        assertEquals("0:ok:done:true:true", text.text)
    }

    private fun parseProgram(source: String): WidgetProgram {
        val manifest = """{
          "schemaVersion":1,"language":"javascript","apiVersion":1,
          "entrypoints":{"plan":"plan","render":"render"},
          "capabilities":{
            "tools":[{"id":"fixture_data","version":1}],
            "runtime":["locale"],"presentation":["text"]
          },
          "limits":{
            "memoryBytes":8388608,"stackBytes":524288,"executionMillis":250,
            "maxToolCalls":1,"maxOutputBytes":65536
          },
          "sourceSha256":"${sha256(source)}"
        }
        """.trimIndent()
        return (WidgetProgramParser.parse(manifest, source) as WidgetProgramValidationResult.Valid).program
    }

    private fun grant() = WidgetExecutionGrant(
        tools = setOf(WidgetToolCapability("fixture_data", 1)),
        runtimeValues = setOf(WidgetRuntimeValue.Locale),
        presentation = setOf(WidgetPresentationCapability.Text),
    )
}
