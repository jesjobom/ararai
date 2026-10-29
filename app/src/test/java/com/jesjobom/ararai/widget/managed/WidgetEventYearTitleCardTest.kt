@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonParser
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetEventYearTitleCardTest {
    private val capability = WidgetToolCapability("wikipedia_on_this_day", 1)
    private val feasibility = (
        WidgetFeasibilityParser.parse(
            """{"outcome":"achievable","message":"Eventos","periodicIntervalHours":24,"toolIds":["wikipedia_on_this_day"]}""",
            setOf(capability),
        ) as WidgetFeasibilityParseResult.Valid
        ).artifact
    private val prompt = WidgetAuthoringPrompt(
        "Mostre um evento aleatório da Wikipedia para o dia de hoje. Adicione ao título o ano do evento.",
        "{}",
    )
    private val algorithm = WidgetAlgorithmArtifact(
        steps = listOf(
            WidgetAlgorithmStep(
                id = "events",
                kind = WidgetAlgorithmStepKind.ToolCall,
                objective = "Get today's events",
                dependencies = emptyList(),
                tool = capability,
            ),
        ),
        presentationObjective = "Show event with year in title",
        renderCapability = WidgetRenderCapability.CollectionCard,
    )

    @Test
    fun `event year request generates a deterministic card with the year suffix`() {
        assertTrue(requestedEventYearTitle(prompt, feasibility))
        val schema = JsonParser.parseString(algorithmCaptureSchema(feasibility, prompt)).asJsonObject
        val choices = schema.getAsJsonObject("parameters").getAsJsonObject("properties")
            .getAsJsonObject("renderCapability").getAsJsonArray("enum")
        assertEquals(listOf("collection_card"), choices.map { it.asString })

        val raw = requireNotNull(WidgetCollectionCardArtifact.eventYearTitle(algorithm, prompt, feasibility))
        val parsed = WidgetCollectionCardArtifact.parse(raw, algorithm) as WidgetSourceFragmentParseResult.Valid
        assertTrue(parsed.artifact.source.contains("\"titleSuffixField\":\"year\""))
        assertTrue(parsed.artifact.source.contains("\"selection\":\"seeded_random\""))
        assertTrue(parsed.artifact.source.contains("\"body\":\"text\""))
        assertTrue(parsed.artifact.source.contains("\"url\":\"canonicalUrl\""))
    }

    @Test
    fun `event year request rejects a direct card without the deterministic suffix route`() {
        val validators = WidgetAuthoringStageValidators(
            WidgetAuthoringPipelineValidator(
                ApplicationToolRegistry(emptyList()),
                WidgetJavaScriptEngine { _, _, _, _ -> error("No JavaScript expected") },
            ),
        )
        val custom = """{"steps":[{"id":"events","kind":"tool_call","objective":"Get events","dependsOn":[],"toolId":"wikipedia_on_this_day"}],"presentationObjective":"Add year","renderCapability":"custom_source"}"""
        val direct = custom.replace("custom_source", "collection_card")
        val transformed = """{"steps":[{"id":"events","kind":"tool_call","objective":"Get events","dependsOn":[],"toolId":"wikipedia_on_this_day"},{"id":"format","kind":"transform","objective":"Append year","dependsOn":["events"]}],"presentationObjective":"Add year","renderCapability":"collection_card"}"""
        assertEquals(
            WidgetAuthoringArtifactValidation.Invalid(WidgetAuthoringStageFailureCode.InvalidAlgorithm),
            validators.algorithm(custom, feasibility, prompt),
        )
        assertTrue(validators.algorithm(direct, feasibility, prompt) is WidgetAuthoringArtifactValidation.Valid)
        assertEquals(
            WidgetAuthoringArtifactValidation.Invalid(WidgetAuthoringStageFailureCode.InvalidAlgorithm),
            validators.algorithm(transformed, feasibility, prompt),
        )
    }

    @Test
    fun `year suffix route does not activate for unrelated or negated requests`() {
        assertFalse(requestedEventYearTitle(prompt.copy(userInstruction = "Mostre um evento aleatório de hoje"), feasibility))
        assertFalse(requestedEventYearTitle(prompt.copy(userInstruction = "Mostre um evento; não adicione o ano no título"), feasibility))
        assertFalse(requestedEventYearTitle(prompt.copy(userInstruction = "Mostre um evento sem o ano no título"), feasibility))
        assertFalse(requestedEventYearTitle(prompt.copy(userInstruction = "Mostre um evento chamado 'ano no título'"), feasibility))
        assertTrue(requestedEventYearTitle(prompt.copy(userInstruction = "Show a random event. Include the event year in its title"), feasibility))
        assertEquals(
            null,
            WidgetCollectionCardArtifact.eventYearTitle(
                algorithm.copy(renderCapability = WidgetRenderCapability.CustomSource),
                prompt,
                feasibility,
            ),
        )
    }
}
