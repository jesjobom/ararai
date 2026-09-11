@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelToolCapabilities
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import com.jesjobom.ararai.tools.ApplicationTool
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.applicationToolBinding
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRequestedLimits
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAuthoringPipelineTest {
    @Test
    fun `runs five isolated stages assembles exact generated fragments and returns a validated draft`() = runTest {
        val modelEngine = QueueStageEngine(
            mutableListOf(
                feasibility(),
                algorithm(),
                fragment("events_call", "buildCallEventsCall", listOf("runtime"), CALL_SOURCE),
                fragment("plan", "plan", listOf("runtime"), PLAN_SOURCE),
                fragment("render", "render", listOf("runtime", "outcomes", "state"), RENDER_SOURCE),
            ),
        )
        val javascript = DeterministicPipelineJavaScriptEngine()
        val registry = registry()
        val pipeline = ManagedWidgetAuthoringPipeline(
            WidgetAuthoringPipelineModelController(modelEngine),
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
        )
        val progress = mutableListOf<WidgetAuthoringProgress>()

        val result = pipeline.generate(MODEL, INFERENCE, PROMPT, RUNTIME, progress::add)

        assertTrue(result is WidgetAuthoringPipelineResult.DraftReady)
        val draft = (result as WidgetAuthoringPipelineResult.DraftReady).draft
        assertEquals(listOf("events_call"), draft.plannedCalls.map { it.alias })
        assertEquals(listOf(CALL_SOURCE, PLAN_SOURCE, RENDER_SOURCE).joinToString("\n\n"), draft.program.source)
        assertEquals(1, modelEngine.loadedModels)
        assertEquals(5, modelEngine.requests.size)
        modelEngine.requests.forEach { request ->
            assertEquals(null, request.chatSessionId)
            assertEquals(1, request.ephemeralTools.size)
            assertEquals(request.advertisedToolNames.single(), request.ephemeralTools.single().name)
        }
        assertTrue(progress.contains(WidgetAuthoringProgress.ValidatingAssembly))
        assertEquals(WidgetAuthoringProgress.DraftReady, progress.last())
        assertFalse(modelEngine.requests.any { it.advertisedToolNames.contains("wikipedia_on_this_day") })
    }

    @Test
    fun `unachievable stops before algorithm or JavaScript generation`() = runTest {
        val modelEngine = QueueStageEngine(mutableListOf(unachievable()))
        val javascript = DeterministicPipelineJavaScriptEngine()
        val registry = registry()
        val pipeline = ManagedWidgetAuthoringPipeline(
            WidgetAuthoringPipelineModelController(modelEngine),
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
        )

        val result = pipeline.generate(MODEL, INFERENCE, PROMPT, RUNTIME)

        assertEquals(WidgetAuthoringPipelineResult.Unachievable("No registered operation"), result)
        assertEquals(1, modelEngine.requests.size)
        assertTrue(javascript.calls.isEmpty())
    }

    @Test
    fun `clarification stops before algorithm or JavaScript generation`() = runTest {
        val modelEngine = QueueStageEngine(mutableListOf(clarification()))
        val javascript = DeterministicPipelineJavaScriptEngine()
        val registry = registry()
        val pipeline = ManagedWidgetAuthoringPipeline(
            WidgetAuthoringPipelineModelController(modelEngine),
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
        )

        val result = pipeline.generate(MODEL, INFERENCE, PROMPT, RUNTIME)

        assertEquals(
            WidgetAuthoringPipelineResult.NeedsClarification("Qual idioma deve ser usado?"),
            result,
        )
        assertEquals(1, modelEngine.requests.size)
        assertTrue(javascript.calls.isEmpty())
    }

    @Test
    fun `invalid stage receives one scoped repair in a fresh conversation`() = runTest {
        val modelEngine = QueueStageEngine(
            mutableListOf(
                "{}",
                feasibility(),
                algorithm(),
                fragment("events_call", "buildCallEventsCall", listOf("runtime"), CALL_SOURCE),
                fragment("plan", "plan", listOf("runtime"), PLAN_SOURCE),
                fragment("render", "render", listOf("runtime", "outcomes", "state"), RENDER_SOURCE),
            ),
        )
        val javascript = DeterministicPipelineJavaScriptEngine()
        val registry = registry()
        val pipeline = ManagedWidgetAuthoringPipeline(
            WidgetAuthoringPipelineModelController(modelEngine),
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
        )
        val progress = mutableListOf<WidgetAuthoringProgress>()

        val result = pipeline.generate(MODEL, INFERENCE, PROMPT, RUNTIME, progress::add)

        assertTrue(result is WidgetAuthoringPipelineResult.DraftReady)
        assertEquals(6, modelEngine.requests.size)
        assertTrue(
            progress.contains(
                WidgetAuthoringProgress.Repairing(
                    WidgetAuthoringStage.Feasibility,
                    1,
                ),
            ),
        )
        assertTrue(modelEngine.requests[1].plainChatPrompt.contains("InvalidSchema"))
        assertFalse(modelEngine.requests[1].plainChatPrompt.contains("password="))
    }

    @Test
    fun `two failed repairs exhaust the attempt without advancing authority`() = runTest {
        val modelEngine = QueueStageEngine(mutableListOf("{}", "{}", "{}"))
        val javascript = DeterministicPipelineJavaScriptEngine()
        val registry = registry()
        val pipeline = ManagedWidgetAuthoringPipeline(
            WidgetAuthoringPipelineModelController(modelEngine),
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
        )

        val result = pipeline.generate(MODEL, INFERENCE, PROMPT, RUNTIME)

        assertEquals(
            WidgetAuthoringPipelineResult.StageFailed(
                WidgetAuthoringStage.Feasibility,
                WidgetAuthoringStageFailureCode.InvalidSchema,
            ),
            result,
        )
        assertEquals(3, modelEngine.requests.size)
        assertTrue(modelEngine.requests.all { it.advertisedToolNames == setOf(SUBMIT_WIDGET_FEASIBILITY_TOOL) })
        assertTrue(javascript.calls.isEmpty())
    }

    @Test
    fun `historical one-shot marker does not make the model eligible`() = runTest {
        val modelEngine = QueueStageEngine(mutableListOf(feasibility()))
        val javascript = DeterministicPipelineJavaScriptEngine()
        val registry = registry()
        val pipeline = ManagedWidgetAuthoringPipeline(
            WidgetAuthoringPipelineModelController(modelEngine),
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
        )
        val legacy = MODEL.copy(
            toolCapabilities = ModelToolCapabilities(authoringToolNames = setOf("propose_widget")),
        )

        assertEquals(
            WidgetAuthoringPipelineResult.ModelUnavailable,
            pipeline.generate(legacy, INFERENCE, PROMPT, RUNTIME),
        )
        assertEquals(0, modelEngine.loadedModels)
    }

    private class QueueStageEngine(private val artifacts: MutableList<String>) : LocalLlmEngine {
        var loadedModels = 0
        val requests = mutableListOf<PromptRequest>()

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            loadedModels++
            assertEquals(MAX_WIDGET_AUTHORING_CONTEXT_TOKENS, config.contextTokens)
            assertEquals(MAX_WIDGET_AUTHORING_TEMPERATURE, config.temperature)
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            requests += request
            request.ephemeralTools.single().execute(artifacts.removeAt(0))
            emit(GenerationEvent.Completed)
        }

        override suspend fun unload() = Unit
    }

    private class DeterministicPipelineJavaScriptEngine : WidgetJavaScriptEngine {
        data class Call(val source: String, val entrypoint: String)
        val calls = mutableListOf<Call>()

        override suspend fun call(
            source: String,
            entrypoint: String,
            argumentsJson: List<String>,
            limits: WidgetRequestedLimits,
        ): WidgetScriptResult {
            calls += Call(source, entrypoint)
            return when (entrypoint) {
                "buildCallEventsCall" -> WidgetScriptResult.Success(VALID_CALL)
                "plan" -> WidgetScriptResult.Success("[$VALID_CALL]")
                "render" -> WidgetScriptResult.Success("""{"type":"text","text":"Synthetic","tone":"neutral"}""")
                else -> error("Unexpected entrypoint $entrypoint")
            }
        }
    }

    companion object {
        private val MODEL = LocalModel(
            id = "pipeline-model",
            name = "Pipeline model",
            filePath = "/models/pipeline.litertlm",
            toolCapabilities = ModelToolCapabilities(
                authoringProtocolNames = setOf(WIDGET_AUTHORING_PIPELINE_V1),
            ),
        )
        private val INFERENCE = InferenceConfig(2_048, 512, 0.7f, 0.9f)
        private val RUNTIME = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-10T12:00:00-04:00", 42)
        private val PROMPT = WidgetAuthoringPrompt(
            "Show one random Wikipedia event for today",
            """{"tools":[{"id":"wikipedia_on_this_day","version":1,"displayName":"Wikipedia on this day","networkRequired":true,"inputSchema":{"type":"object"},"outputSchema":{"type":"object","properties":{"kind":{"type":"string"},"events":{"type":"array","items":{"type":"object","properties":{"year":{"type":"integer"},"text":{"type":"string"},"canonicalUrl":{"type":"string"}}}}}}}]}""",
        )

        private const val CALL_SOURCE = """function buildCallEventsCall(runtime) {
  const now = runtime.currentLocalDateTime();
  return {alias:'events_call',toolId:'wikipedia_on_this_day',contractVersion:1,arguments:{month:now.month,day:now.day,language:runtime.language}};
}"""
        private const val PLAN_SOURCE = """function plan(runtime) {
  return [buildCallEventsCall(runtime)];
}"""
        private const val RENDER_SOURCE = """function render(runtime, outcomes, state) {
  return {type:'text',text:'Synthetic',tone:'neutral'};
}"""
        private const val VALID_CALL = """{"alias":"events_call","toolId":"wikipedia_on_this_day","contractVersion":1,"arguments":{"month":9,"day":10,"language":"en"}}"""

        private fun registry() = ApplicationToolRegistry(
            listOf(
                applicationToolBinding(
                    contract = ApplicationToolContract(
                        id = "wikipedia_on_this_day",
                        version = 1,
                        displayName = "Wikipedia on this day",
                        category = ApplicationToolCategory.ExternalKnowledge,
                        consumers = setOf(ApplicationToolConsumer.Widget),
                        inputSchemaJson = """{"type":"object"}""",
                        outputSchemaJson = """{"type":"object","properties":{"kind":{"type":"string"},"events":{"type":"array","items":{"type":"object","properties":{"year":{"type":"integer"},"text":{"type":"string"},"canonicalUrl":{"type":"string"}}}}}}""",
                    ),
                    state = { ApplicationToolOperationalState(enabled = true, ready = true) },
                    executor = ApplicationTool(
                        "Wikipedia on this day",
                        ApplicationToolCategory.ExternalKnowledge,
                    ) { _: Unit -> Unit },
                    decodeArguments = { arguments ->
                        Unit.takeIf {
                            arguments.keySet() == setOf("month", "day", "language") &&
                                arguments.get("month")?.asInt in 1..12 &&
                                arguments.get("day")?.asInt in 1..31 &&
                                arguments.get("language")?.asString == "en"
                        }
                    },
                    encodeResult = { "{}" },
                ),
            ),
        )

        private fun feasibility(): String = JsonObject().apply {
            addProperty("protocolVersion", 1)
            addProperty("outcome", "achievable")
            addProperty("displayName", "Today in history")
            addProperty("enabled", true)
            addProperty("periodicIntervalHours", 24)
            add(
                "tools",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("id", "wikipedia_on_this_day")
                            addProperty("version", 1)
                            addProperty("purpose", "Load current-date historical events")
                        },
                    )
                },
            )
            add(
                "runtime",
                JsonArray().apply {
                    add("locale")
                    add("local_time")
                    add("seed")
                },
            )
            add("presentation", JsonArray().apply { add("text") })
            add("reason", JsonNull.INSTANCE)
            add("clarificationQuestion", JsonNull.INSTANCE)
        }.toString()

        private fun unachievable(): String = JsonObject().apply {
            addProperty("protocolVersion", 1)
            addProperty("outcome", "unachievable")
            addProperty("displayName", "Unsupported")
            addProperty("enabled", false)
            add("periodicIntervalHours", JsonNull.INSTANCE)
            add("tools", JsonArray())
            add("runtime", JsonArray())
            add("presentation", JsonArray())
            addProperty("reason", "No registered operation")
            add("clarificationQuestion", JsonNull.INSTANCE)
        }.toString()

        private fun clarification(): String = JsonObject().apply {
            addProperty("protocolVersion", 1)
            addProperty("outcome", "needs_clarification")
            addProperty("displayName", "Evento histórico")
            addProperty("enabled", false)
            add("periodicIntervalHours", JsonNull.INSTANCE)
            add("tools", JsonArray())
            add("runtime", JsonArray())
            add("presentation", JsonArray())
            add("reason", JsonNull.INSTANCE)
            addProperty("clarificationQuestion", "Qual idioma deve ser usado?")
        }.toString()

        private fun algorithm(): String = JsonObject().apply {
            addProperty("protocolVersion", 1)
            add(
                "steps",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("id", "events_call")
                            addProperty("kind", "tool_call")
                            addProperty("objective", "Load events using execution-time date")
                            add("dependsOn", JsonArray())
                            addProperty("toolId", "wikipedia_on_this_day")
                            addProperty("contractVersion", 1)
                            add(
                                "runtimeInputs",
                                JsonArray().apply {
                                    add("locale")
                                    add("local_time")
                                },
                            )
                        },
                    )
                },
            )
            addProperty("presentationObjective", "Show one event")
        }.toString()

        private fun fragment(id: String, functionName: String, inputs: List<String>, source: String): String = JsonObject().apply {
            addProperty("protocolVersion", 1)
            addProperty("artifactId", id)
            addProperty("functionName", functionName)
            add("inputNames", JsonArray().apply { inputs.forEach(::add) })
            addProperty("source", source)
        }.toString()
    }
}
