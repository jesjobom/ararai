@file:Suppress("MaxLineLength") // Inline JSON fixtures match the wire format checked by the parser.

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonParser
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetWeatherRenderRoutingTest {
    private val weather = WidgetToolCapability("weather_forecast", 1)
    private val feasibility = (
        WidgetFeasibilityParser.parse(
            """{"outcome":"achievable","message":"Weather","periodicIntervalHours":12,"toolIds":["weather_forecast"]}""",
            setOf(weather),
        ) as WidgetFeasibilityParseResult.Valid
        ).artifact
    private val prompt = WidgetAuthoringPrompt(
        "Mostre a previsão do tempo para Montréal",
        """{"tools":[{"id":"weather_forecast","version":1,"outputSchema":{"type":"object","properties":{}}}]}""",
    )

    @Test
    fun `weather algorithm offers custom render because collection card cannot show numeric measurements`() {
        val schema = JsonParser.parseString(algorithmCaptureSchema(feasibility, prompt)).asJsonObject
        val choices = schema.getAsJsonObject("parameters").getAsJsonObject("properties")
            .getAsJsonObject("renderCapability").getAsJsonArray("enum")
        assertEquals(listOf("custom_source"), choices.map { it.asString })
        assertTrue(algorithmContext(prompt, feasibility).contains("weatherRenderPolicy"))
    }

    @Test
    fun `weather algorithm rejects collection card and accepts custom render`() {
        val validators = WidgetAuthoringStageValidators(
            WidgetAuthoringPipelineValidator(
                ApplicationToolRegistry(emptyList()),
                WidgetJavaScriptEngine { _, _, _, _ -> error("No JavaScript expected") },
            ),
        )
        val raw = """{"steps":[{"id":"forecast","kind":"tool_call","objective":"Fetch forecast","dependsOn":[],"toolId":"weather_forecast"}],"presentationObjective":"Show forecast","renderCapability":"%s"}"""

        assertEquals(
            WidgetAuthoringArtifactValidation.Invalid(WidgetAuthoringStageFailureCode.InvalidAlgorithm),
            validators.algorithm(raw.format("collection_card"), feasibility, prompt),
        )
        assertTrue(validators.algorithm(raw.format("custom_source"), feasibility, prompt) is WidgetAuthoringArtifactValidation.Valid)
    }

    @Test
    fun `weather-only algorithm receives an application-owned render even with a presentation step`() {
        val algorithm = WidgetAlgorithmArtifact(
            steps = listOf(
                WidgetAlgorithmStep("forecast", WidgetAlgorithmStepKind.ToolCall, "Fetch forecast", emptyList(), weather),
                WidgetAlgorithmStep("show_weather", WidgetAlgorithmStepKind.Transform, "Show weather", listOf("forecast"), null),
            ),
            presentationObjective = "Show forecast",
            renderCapability = WidgetRenderCapability.CustomSource,
        )
        val raw = requireNotNull(WidgetWeatherRenderArtifact.forecast(algorithm))
        val parsed = WidgetSourceFragmentParser.parse(raw, "render", "render", listOf("runtime", "outcomes", "state"))

        assertTrue(parsed is WidgetSourceFragmentParseResult.Valid)
        val source = (parsed as WidgetSourceFragmentParseResult.Valid).artifact.source
        assertTrue(source.contains("runtime.card(rows)"))
        assertTrue(!source.contains("runtime.linkFor"))
    }
}
