package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonParser
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRankedSearchAlgorithmTest {
    @Test
    fun `random result is a collection card selection and not a custom source step`() {
        val feasibility = (
            WidgetFeasibilityParser.parse(
                """{
                    "outcome":"achievable", "message":"Random result",
                    "periodicIntervalHours":24, "toolIds":["web_search_content"]
                }""",
                setOf(WidgetToolCapability("web_search_content", 1)),
            ) as WidgetFeasibilityParseResult.Valid
            ).artifact
        val prompt = WidgetAuthoringPrompt(
            "Show a random result for the search for \"Best Smartphone\"",
            """{"tools":[]}""",
        )
        val context = JsonParser.parseString(algorithmContext(prompt, feasibility)).asJsonObject
        val selectionPolicy = context.getAsJsonObject("selectionPolicy")
        assertEquals("collection_card", selectionPolicy.get("renderCapability").asString)
        assertEquals("seeded_random", selectionPolicy.get("selectionInCardStage").asString)
        assertEquals(false, selectionPolicy.get("postResultStepForPresentationOnly").asBoolean)
        val schema = JsonParser.parseString(algorithmCaptureSchema(feasibility, prompt)).asJsonObject
        val properties = schema.getAsJsonObject("parameters").getAsJsonObject("properties")
        val stepKind = properties.getAsJsonObject("steps").getAsJsonObject("items")
            .getAsJsonObject("properties").getAsJsonObject("kind")
        assertTrue(stepKind.get("description").asString.contains("not a renderCapability"))

        val invalid = """{
            "steps":[
                {"id":"search","kind":"tool_call","objective":"Search","dependsOn":[],"toolId":"web_search_content"},
                {"id":"pick","kind":"custom_source","objective":"Select random result","dependsOn":["search"]}
            ],
            "presentationObjective":"Show random result","renderCapability":"collection_card"
        }"""
        assertEquals(
            WidgetAlgorithmParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidAlgorithm),
            WidgetAlgorithmParser.parse(invalid, feasibility, requireRenderCapability = true),
        )
        val repair = WidgetAuthoringRoundRequest(
            WidgetAuthoringStage.Algorithm,
            SUBMIT_WIDGET_ALGORITHM_TOOL,
            algorithmCaptureSchema(feasibility, prompt),
            WIDGET_ALGORITHM_OBJECTIVE,
            algorithmContext(prompt, feasibility),
            WidgetAuthoringStageFailureCode.InvalidAlgorithm,
            invalid,
            attemptNumber = 4,
        ).toUserText()
        assertTrue(repair.contains("Attempt 4 for this stage"))
        assertTrue(repair.contains("renderCapability accepts only collection_card, collection_list, custom_source"))
        assertTrue(repair.contains("seeded_random is chosen in the later card stage"))
    }

    @Test
    fun `random web result can use seeded random collection card`() {
        val feasibility = (
            WidgetFeasibilityParser.parse(
                """{
                    "outcome":"achievable", "message":"Random result",
                    "periodicIntervalHours":24, "toolIds":["web_search_content"]
                }""",
                setOf(WidgetToolCapability("web_search_content", 1)),
            ) as WidgetFeasibilityParseResult.Valid
            ).artifact
        val valid = """{
            "steps":[{"id":"search","kind":"tool_call","objective":"Search","dependsOn":[],"toolId":"web_search_content"}],
            "presentationObjective":"Show random result","renderCapability":"collection_card"
        }"""
        val algorithm = WidgetAlgorithmParser.parse(valid, feasibility, requireRenderCapability = true)
        assertTrue(algorithm is WidgetAlgorithmParseResult.Valid)
        val card = WidgetCollectionCardArtifact.parse(
            """{
                "alias":"search", "items":"results", "title":"title",
                "fallback":"No results", "selection":"seeded_random"
            }""",
            (algorithm as WidgetAlgorithmParseResult.Valid).artifact,
        )
        assertTrue(card is WidgetSourceFragmentParseResult.Valid)
    }

    @Test
    fun `ranked result list rejects a post-call formatting step without losing the valid direct list`() {
        val feasibility = (
            WidgetFeasibilityParser.parse(
                """{
                    "outcome":"achievable",
                    "message":"Ranked results",
                    "periodicIntervalHours":6,
                    "toolIds":["web_search_top_results"]
                }""",
                setOf(WidgetToolCapability("web_search_top_results", 1)),
            ) as WidgetFeasibilityParseResult.Valid
            ).artifact
        val candidate = JsonParser.parseString(
            """{
                "steps": [
                    {"id":"search","kind":"tool_call","objective":"Fetch ranked results",
                     "dependsOn":[],"toolId":"web_search_top_results"},
                    {"id":"format","kind":"transform","objective":"Format results for display",
                     "dependsOn":["search"]}
                ],
                "presentationObjective":"List five ranked results",
                "renderCapability":"collection_list"
            }""",
        ).asJsonObject
        assertEquals(
            WidgetAlgorithmParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidAlgorithm),
            WidgetAlgorithmParser.parse(candidate.toString(), feasibility, requireRenderCapability = true),
        )
        candidate.getAsJsonArray("steps").remove(1)
        assertTrue(
            WidgetAlgorithmParser.parse(candidate.toString(), feasibility, requireRenderCapability = true) is
                WidgetAlgorithmParseResult.Valid,
        )
    }
}
