package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonParser
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAlgorithmPromptContractTest {
    @Test
    fun `full initial and repair requests keep random selection in the card stage`() {
        val prompt = prompt("Show a random result for the search for Best Smartphone")
        val initial = request(prompt)
        val schema = JsonParser.parseString(initial.toolDescriptionJson).asJsonObject
            .getAsJsonObject("parameters").getAsJsonObject("properties")
        val context = JsonParser.parseString(initial.contextJson).asJsonObject
        val stepKinds = schema.getAsJsonObject("steps").getAsJsonObject("items")
            .getAsJsonObject("properties").getAsJsonObject("kind").getAsJsonArray("enum")
            .map { it.asString }
        val renderModes = schema.getAsJsonObject("renderCapability").getAsJsonArray("enum")
            .map { it.asString }
        val catalog = context.getAsJsonArray("renderCapabilities").map { it.asJsonObject.get("id").asString }
        val selection = context.getAsJsonObject("selectionPolicy")

        assertEquals(listOf("runtime_input", "transform", "tool_call"), stepKinds)
        assertEquals(listOf("collection_card", "collection_list", "custom_source"), renderModes)
        assertEquals(renderModes, catalog)
        assertEquals("collection_card", selection.get("renderCapability").asString)
        assertEquals("seeded_random", selection.get("selectionInCardStage").asString)
        assertFalse(selection.get("postResultStepForPresentationOnly").asBoolean)
        assertFalse(initial.objective.contains("custom_source"))
        assertTrue(initial.toUserText().contains("Validated context:"))
        // Measured before centralization with this fixture: 3,892 input characters.
        assertTrue(initial.estimatedInputChars() <= 3_892)

        val rejected = """{"steps":[{"kind":"custom_source"}],"renderCapability":"collection_card"}"""
        val repair = initial.copy(
            repairCode = WidgetAuthoringStageFailureCode.InvalidAlgorithm,
            rejectedArtifact = rejected,
            attemptNumber = 2,
        )
        val correction = repair.toUserText().substringAfter("\nCorrection: ").substringBefore("\nAuthoritative")
        assertTrue(repair.toUserText().contains(rejected))
        assertTrue(correction.contains("steps[].kind accepts only runtime_input, transform, tool_call"))
        assertTrue(correction.contains("renderCapability accepts only collection_card, collection_list, custom_source"))
        assertTrue(correction.contains("seeded_random is chosen in the later card stage"))
        assertTrue(repair.estimatedInputChars() <= 5_000)
    }

    @Test
    fun `full ranked list request restricts schema and context to the same route`() {
        val prompt = prompt("Mostre uma lista de até 10 resultados da busca web", "web_search_top_results")
        val request = request(prompt, "web_search_top_results")
        val schema = JsonParser.parseString(request.toolDescriptionJson).asJsonObject
            .getAsJsonObject("parameters").getAsJsonObject("properties")
        val modes = schema.getAsJsonObject("renderCapability").getAsJsonArray("enum").map { it.asString }
        val policy = JsonParser.parseString(request.contextJson).asJsonObject.getAsJsonObject("rankedListPolicy")

        assertEquals(listOf("collection_list"), modes)
        assertEquals(modes.single(), policy.get("renderCapability").asString)
        assertEquals("web_search_top_results", policy.get("toolId").asString)
        assertEquals(10, policy.get("maxResults").asInt)
        assertFalse(policy.get("postResultStepForPresentationOnly").asBoolean)
        assertTrue(request.estimatedInputChars() <= 3_892)
    }

    private fun request(
        prompt: WidgetAuthoringPrompt,
        toolId: String = "web_search_content",
    ): WidgetAuthoringRoundRequest {
        val feasibility = (
            WidgetFeasibilityParser.parse(
                """{"outcome":"achievable","message":"Search result","toolIds":["$toolId"]}""",
                setOf(WidgetToolCapability(toolId, 1)),
                includeScheduleField = false,
            ) as WidgetFeasibilityParseResult.Valid
            ).artifact
        return WidgetAuthoringRoundRequest(
            WidgetAuthoringStage.Algorithm,
            SUBMIT_WIDGET_ALGORITHM_TOOL,
            algorithmCaptureSchema(feasibility, prompt),
            WIDGET_ALGORITHM_OBJECTIVE,
            algorithmContext(prompt, feasibility),
        )
    }

    private fun prompt(instruction: String, toolId: String = "web_search_content") = WidgetAuthoringPrompt(
        instruction,
        """{"tools":[{"id":"$toolId","version":1,"authoringSummary":"Search web content",""" +
            """"outputSchema":{"type":"object","properties":{"results":{"type":"array"}}}}]}""",
    )
}
