@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.tools.ApplicationTool
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.applicationToolBinding
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCapability
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeFailureCode
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAuthoringPipelineValidationTest {
    @Test
    fun `physical map style outcome access is classified as render execution failure`() = runTest {
        val render = RENDER.copy(
            source = """function render(runtime, outcomes, state) {
  const fetchEventsOutcome = outcomes.get('fetch_wikipedia_events');
  return {type:'text',text:fetchEventsOutcome.status,tone:'neutral'};
}""",
        )
        val validator = WidgetAuthoringPipelineValidator(
            registry(),
            WidgetJavaScriptEngine { source, _, _, _ ->
                assertTrue(source.contains("outcomes.get('fetch_wikipedia_events')"))
                WidgetScriptResult.Failure(WidgetRuntimeFailureCode.ScriptError)
            },
        )

        assertEquals(
            WidgetAuthoringStageFailureCode.InvalidRenderExecution,
            validator.validateRenderFunction(emptyList(), render, ALGORITHM, FEASIBILITY, RUNTIME),
        )
    }

    @Test
    fun `render preserves runtime unavailable and resource limit failures`() = runTest {
        listOf(
            WidgetRuntimeFailureCode.RuntimeUnavailable to WidgetAuthoringStageFailureCode.RuntimeUnavailable,
            WidgetRuntimeFailureCode.ResourceLimit to WidgetAuthoringStageFailureCode.ResourceLimit,
        ).forEach { (runtimeFailure, expected) ->
            assertEquals(
                expected,
                validator { WidgetScriptResult.Failure(runtimeFailure) }
                    .validateRenderFunction(emptyList(), RENDER, ALGORITHM, FEASIBILITY, RUNTIME),
            )
        }
    }

    @Test
    fun `render execution failure on empty fixture is classified separately`() = runTest {
        val validator = validator { outcomes ->
            if (outcomes.contains("\"events\":[]")) {
                WidgetScriptResult.Failure(WidgetRuntimeFailureCode.ScriptError)
            } else {
                validText()
            }
        }

        assertEquals(
            WidgetAuthoringStageFailureCode.InvalidPresentationEmptyHandling,
            validator.validateRenderFunction(emptyList(), RENDER, ALGORITHM, FEASIBILITY, RUNTIME),
        )
    }

    @Test
    fun `render execution failure on failed outcome is classified separately`() = runTest {
        val validator = validator { outcomes ->
            if (outcomes.contains("\"status\":\"failure\"")) {
                WidgetScriptResult.Failure(WidgetRuntimeFailureCode.ScriptError)
            } else {
                validText()
            }
        }

        assertEquals(
            WidgetAuthoringStageFailureCode.InvalidPresentationFailureHandling,
            validator.validateRenderFunction(emptyList(), RENDER, ALGORITHM, FEASIBILITY, RUNTIME),
        )
    }

    @Test
    fun `render link with unproven URL is classified as provenance`() = runTest {
        val validator = validator {
            WidgetScriptResult.Success(
                """{"type":"https_link","label":"Read","url":"https://example.test","sourceAlias":"events","sourceField":"events.0.canonicalUrl"}""",
            )
        }

        assertEquals(
            WidgetAuthoringStageFailureCode.InvalidPresentationProvenance,
            validator.validateRenderFunction(emptyList(), RENDER, ALGORITHM, FEASIBILITY, RUNTIME),
        )
    }

    private fun validator(render: (String) -> WidgetScriptResult): WidgetAuthoringPipelineValidator = WidgetAuthoringPipelineValidator(
        registry(),
        WidgetJavaScriptEngine { _, entrypoint, arguments, _ ->
            assertEquals("render", entrypoint)
            render(arguments[1])
        },
    )

    private fun registry() = ApplicationToolRegistry(
        listOf(
            applicationToolBinding(
                contract = ApplicationToolContract(
                    id = TOOL.id,
                    version = TOOL.version,
                    displayName = "Wikipedia on this day",
                    category = ApplicationToolCategory.ExternalKnowledge,
                    consumers = setOf(ApplicationToolConsumer.Widget),
                    inputSchemaJson = """{"type":"object"}""",
                    outputSchemaJson = """{"type":"object","properties":{"events":{"type":"array","items":{"type":"object","properties":{"text":{"type":"string"},"canonicalUrl":{"type":"string"}}}}}}""",
                ),
                state = { ApplicationToolOperationalState(enabled = true, ready = true) },
                executor = ApplicationTool(
                    "Wikipedia on this day",
                    ApplicationToolCategory.ExternalKnowledge,
                ) { _: Unit -> Unit },
                decodeArguments = { Unit },
                encodeResult = { "{}" },
            ),
        ),
    )

    private fun validText() = WidgetScriptResult.Success(
        """{"type":"text","text":"Fallback","tone":"neutral"}""",
    )

    private companion object {
        val TOOL = WidgetToolCapability("wikipedia_on_this_day", 1)
        val FEASIBILITY = WidgetFeasibilityArtifact(
            outcome = WidgetFeasibilityOutcome.Achievable,
            displayName = "Today in history",
            enabled = true,
            periodicIntervalHours = 24,
            tools = listOf(WidgetSelectedTool(TOOL, "fixture")),
            runtimeValues = WidgetRuntimeValue.entries.toSet(),
            presentation = WidgetPresentationCapability.entries.toSet(),
            reason = null,
            clarificationQuestion = null,
        )
        val ALGORITHM = WidgetAlgorithmArtifact(
            steps = listOf(
                WidgetAlgorithmStep(
                    id = "events",
                    kind = WidgetAlgorithmStepKind.ToolCall,
                    objective = "Fetch events",
                    dependencies = emptyList(),
                    tool = TOOL,
                ),
            ),
            presentationObjective = "Show one event",
        )
        val RENDER = WidgetSourceFragmentArtifact(
            artifactId = "render",
            functionName = "render",
            inputNames = listOf("runtime", "outcomes", "state"),
            source = "function render(runtime, outcomes, state) { return {}; }",
        )
        val RUNTIME = WidgetRuntimeContext(
            locale = "en-CA",
            timezone = "America/Toronto",
            localTime = "2026-10-03T12:00:00-04:00",
            seed = 7,
        )
    }
}
