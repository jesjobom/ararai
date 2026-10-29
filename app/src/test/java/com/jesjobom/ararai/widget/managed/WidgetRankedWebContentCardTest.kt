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

class WidgetRankedWebContentCardTest {
    private val capability = WidgetToolCapability("web_search_content", 1)
    private val feasibility = (
        WidgetFeasibilityParser.parse(
            """{"outcome":"achievable","message":"Web","periodicIntervalHours":12,"toolIds":["web_search_content"]}""",
            setOf(capability),
        ) as WidgetFeasibilityParseResult.Valid
        ).artifact
    private val prompt = WidgetAuthoringPrompt(
        "Mostre o melhor resultado para a busca sobre smartphone",
        "{}",
    )
    private val algorithm = WidgetAlgorithmArtifact(
        steps = listOf(
            WidgetAlgorithmStep(
                id = "search",
                kind = WidgetAlgorithmStepKind.ToolCall,
                objective = "Search",
                dependencies = emptyList(),
                tool = capability,
            ),
        ),
        presentationObjective = "Show the best result",
        renderCapability = WidgetRenderCapability.CollectionCard,
    )

    @Test
    fun `best ranked web hit is a first-item card with excerpt and proven link`() {
        assertTrue(requestedRankedWebContentCard(prompt, feasibility))
        val schema = JsonParser.parseString(algorithmCaptureSchema(feasibility, prompt)).asJsonObject
        val choices = schema.getAsJsonObject("parameters").getAsJsonObject("properties")
            .getAsJsonObject("renderCapability").getAsJsonArray("enum")
        assertEquals(listOf("collection_card"), choices.map { it.asString })

        val raw = requireNotNull(WidgetCollectionCardArtifact.rankedWebContent(algorithm, prompt, feasibility))
        val parsed = WidgetCollectionCardArtifact.parse(raw, algorithm) as WidgetSourceFragmentParseResult.Valid
        assertTrue(parsed.artifact.source.contains("runtime.collectionCard"))
        assertTrue(parsed.artifact.source.contains("\"body\":\"excerpt\""))
        assertTrue(parsed.artifact.source.contains("\"selection\":\"first\""))
        assertTrue(parsed.artifact.source.contains("\"url\":\"canonicalUrl\""))
    }

    @Test
    fun `card shortcut does not replace other web selections or custom transforms`() {
        assertFalse(requestedRankedWebContentCard(prompt.copy(userInstruction = "Show three results"), feasibility))
        assertFalse(requestedRankedWebContentCard(prompt.copy(userInstruction = "Show three pages for 'best result'"), feasibility))
        assertEquals(
            null,
            WidgetCollectionCardArtifact.rankedWebContent(
                algorithm.copy(renderCapability = WidgetRenderCapability.CustomSource),
                prompt,
                feasibility,
            ),
        )
        assertEquals(
            null,
            WidgetCollectionCardArtifact.rankedWebContent(
                algorithm.copy(steps = algorithm.steps.map { it.copy(tool = WidgetToolCapability("wikipedia_pages", 2)) }),
                prompt,
                feasibility,
            ),
        )
    }

    @Test
    fun `best-result algorithm rejects a custom transform and accepts direct card`() {
        val validators = WidgetAuthoringStageValidators(
            WidgetAuthoringPipelineValidator(
                ApplicationToolRegistry(emptyList()),
                WidgetJavaScriptEngine { _, _, _, _ -> error("No JavaScript expected") },
            ),
        )
        val custom = """{"steps":[{"id":"search","kind":"tool_call","objective":"Search","dependsOn":[],"toolId":"web_search_content"},{"id":"select","kind":"transform","objective":"Select best result","dependsOn":["search"]}],"presentationObjective":"Show best result","renderCapability":"custom_source"}"""
        val direct = """{"steps":[{"id":"search","kind":"tool_call","objective":"Search","dependsOn":[],"toolId":"web_search_content"}],"presentationObjective":"Show best result","renderCapability":"collection_card"}"""

        assertEquals(
            WidgetAuthoringArtifactValidation.Invalid(WidgetAuthoringStageFailureCode.InvalidAlgorithm),
            validators.algorithm(custom, feasibility, prompt),
        )
        assertTrue(validators.algorithm(direct, feasibility, prompt) is WidgetAuthoringArtifactValidation.Valid)
    }
}
