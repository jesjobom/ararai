package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.knowledge.WikipediaPageSummaryTool
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.wikipediaPageSummaryApplicationTool
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCapability
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAuthoringTemporalQueryTest {
    @Test
    fun `language-only query is not blocked by prompt keyword heuristics`() = runTest {
        val validator = validator(query = { _, language -> if (language == "pt") "Espaço" else "Space" })

        assertEquals(null, validator.validateCallFunction(CALL, STEP, FEASIBILITY, RUNTIME))
    }

    @Test
    fun `hour-routed query remains valid`() = runTest {
        val validator = validator(query = { hour, _ -> if (hour in 6..17) "Espaço" else "Oceano" })

        assertEquals(null, validator.validateCallFunction(CALL, STEP, FEASIBILITY, RUNTIME))
    }

    @Test
    fun `frozen seeded selection rejects first in the tool call`() = runTest {
        val validator = validator(
            query = { hour, _ -> if (hour in 6..17) "Espaço" else "Oceano" },
            selection = "first",
        )

        assertEquals(
            WidgetAuthoringStageFailureCode.InvalidToolSelection,
            validator.validateCallFunction(CALL, STEP, FEASIBILITY, RUNTIME),
        )
        assertEquals(
            WidgetAuthoringStageFailureCode.InvalidToolSelection,
            validator.validatePlanFunction(listOf(CALL), PLAN, ALGORITHM, FEASIBILITY, RUNTIME),
        )
    }

    @Test
    fun `frozen seeded selection requires the runtime seed rather than a constant`() = runTest {
        val validator = validator(
            query = { hour, _ -> if (hour in 6..17) "Espaço" else "Oceano" },
            seed = { 5L },
        )

        assertEquals(
            WidgetAuthoringStageFailureCode.InvalidToolSelection,
            validator.validateCallFunction(CALL, STEP, FEASIBILITY, RUNTIME),
        )
    }

    @Test
    fun `first page remains valid when randomness was not requested`() = runTest {
        val validator = validator(
            query = { hour, _ -> if (hour in 6..17) "Espaço" else "Oceano" },
            selection = "first",
        )

        val firstStep = STEP.copy(selectionMode = WidgetToolSelectionMode.First)
        assertEquals(
            null,
            validator.validateCallFunction(
                CALL,
                firstStep,
                FEASIBILITY,
                RUNTIME,
            ),
        )
    }

    private fun validator(
        query: (hour: Int, language: String) -> String,
        selection: String = "seeded_random",
        seed: (Long) -> Long = { it },
    ): WidgetAuthoringPipelineValidator {
        val registry = ApplicationToolRegistry(
            listOf(
                wikipediaPageSummaryApplicationTool(WikipediaPageSummaryTool { error("provider must not run") }) {
                    ApplicationToolOperationalState(enabled = true, ready = true)
                },
            ),
        )
        val engine = WidgetJavaScriptEngine { _, _, arguments, _ ->
            val runtime = JsonParser.parseString(arguments.single()).asJsonObject
            val language = runtime["language"].asString
            WidgetScriptResult.Success(
                JsonObject().apply {
                    addProperty("query", query(runtime["localHour"].asInt, language))
                    addProperty("language", language)
                    addProperty("selection", selection)
                    if (selection == "seeded_random") addProperty("seed", seed(runtime["seed"].asLong))
                }.toString(),
            )
        }
        return WidgetAuthoringPipelineValidator(registry, engine)
    }

    private companion object {
        val TOOL = WidgetToolCapability("wikipedia_pages", 2)
        val STEP = WidgetAlgorithmStep(
            id = "fetch_wikipedia_page",
            kind = WidgetAlgorithmStepKind.ToolCall,
            objective = "Fetch a page about the time-selected topic",
            dependencies = listOf("get_topic"),
            tool = TOOL,
            selectionMode = WidgetToolSelectionMode.SeededRandom,
        )
        val ALGORITHM = WidgetAlgorithmArtifact(
            steps = listOf(
                WidgetAlgorithmStep(
                    id = "get_topic",
                    kind = WidgetAlgorithmStepKind.Transform,
                    objective = "Determine topic based on current time of day",
                    dependencies = emptyList(),
                    tool = null,
                ),
                STEP,
            ),
            presentationObjective = "Display a page about Space during the day and Ocean at night",
            renderCapability = WidgetRenderCapability.CollectionCard,
        )
        val FEASIBILITY = WidgetFeasibilityArtifact(
            outcome = WidgetFeasibilityOutcome.Achievable,
            displayName = "Space or ocean",
            enabled = true,
            periodicIntervalHours = 1,
            tools = listOf(WidgetSelectedTool(TOOL, "topic search")),
            runtimeValues = WidgetRuntimeValue.entries.toSet(),
            presentation = WidgetPresentationCapability.entries.toSet(),
            reason = null,
            clarificationQuestion = null,
        )
        val RUNTIME = WidgetRuntimeContext("pt-BR", "America/Sao_Paulo", "2000-02-29T19:00:00-03:00", 5)
        val CALL = WidgetSourceFragmentArtifact(
            "fetch_wikipedia_page",
            "buildCallFetchWikipediaPage",
            listOf("runtime"),
            """function buildCallFetchWikipediaPage(runtime) {
  return {
    query: runtime.language === 'pt' ? 'Espaço' : 'Space',
    language: runtime.language,
    selection: 'seeded_random',
    seed: runtime.seed
  };
}""",
        )
        val PLAN = deterministicPlanArtifact(listOf(CALL), ALGORITHM)
    }
}
