@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
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
        assertEquals(4, modelEngine.loadedModels)
        assertEquals(3, modelEngine.unloadedModels)
        assertEquals(4, modelEngine.requests.size)
        assertEquals(
            listOf(
                "load",
                "generate:$SUBMIT_WIDGET_FEASIBILITY_TOOL",
                "unload",
                "load",
                "generate:$SUBMIT_WIDGET_ALGORITHM_TOOL",
            ),
            modelEngine.operations.take(5),
        )
        val feasibilityPrompt = modelEngine.requests.first().plainChatPrompt
        assertTrue(feasibilityPrompt.contains("\"outputFields\":[\"events\",\"kind\"]"))
        assertFalse(feasibilityPrompt.contains("\"canonicalUrl\""))
        assertTrue(modelEngine.requests[1].plainChatPrompt.contains("\"canonicalUrl\""))
        val renderPrompt = modelEngine.requests[3].plainChatPrompt
        assertTrue(renderPrompt.contains("\"renderApi\""))
        assertTrue(renderPrompt.contains("\"outcomeShape\""))
        assertTrue(renderPrompt.contains("outcomes[alias]"))
        assertTrue(renderPrompt.contains("plain JavaScript object; it is not a Map"))
        assertTrue(renderPrompt.contains("never call outcomes.get()"))
        assertFalse(renderPrompt.contains("\"programApi\""))
        assertTrue(renderPrompt.contains("\"presentationContract\""))
        assertTrue(renderPrompt.contains("{type:'card',child:N}"))
        assertTrue(renderPrompt.contains("{type:'text',text:'...',tone:T}"))
        modelEngine.requests.forEach { request ->
            assertEquals(null, request.chatSessionId)
            assertEquals(1, request.ephemeralTools.size)
            assertEquals(request.advertisedToolNames.single(), request.ephemeralTools.single().name)
        }
        assertTrue(progress.contains(WidgetAuthoringProgress.ValidatingAssembly))
        assertEquals(3, progress.count { it == WidgetAuthoringProgress.WaitingForDeviceRecovery })
        assertEquals(WidgetAuthoringProgress.DraftReady, progress.last())
        assertFalse(modelEngine.requests.any { it.advertisedToolNames.contains("wikipedia_on_this_day") })
    }

    @Test
    fun `render context keeps generic API separate from concrete aliases`() {
        val context = JsonParser.parseString(
            renderContext(PROMPT, feasibilityArtifact(), algorithmArtifact()),
        ).asJsonObject

        assertFalse(context.has("programApi"))
        val renderApi = context.getAsJsonObject("renderApi").toString()
        assertTrue(renderApi.contains("outcomes[alias]"))
        assertTrue(renderApi.contains("never call outcomes.get()"))
        assertFalse(renderApi.contains("events_call"))
        assertFalse(renderApi.contains("wikipedia"))
        assertEquals(
            "events_call",
            context.getAsJsonArray("toolResults").single().asJsonObject.get("alias").asString,
        )
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
                fragment("render", "render", listOf("runtime", "outcomes", "state"), RENDER_SOURCE),
            ),
        )
        val javascript = DeterministicPipelineJavaScriptEngine()
        val registry = registry()
        val progress = mutableListOf<WidgetAuthoringProgress>()
        val failures = mutableListOf<WidgetAuthoringAttemptFailure>()
        val pipeline = ManagedWidgetAuthoringPipeline(
            WidgetAuthoringPipelineModelController(modelEngine),
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
            onAttemptFailure = failures::add,
        )

        val result = pipeline.generate(MODEL, INFERENCE, PROMPT, RUNTIME, progress::add)

        assertTrue(result is WidgetAuthoringPipelineResult.DraftReady)
        assertEquals(5, modelEngine.requests.size)
        assertEquals(5, modelEngine.loadedModels)
        assertEquals(4, modelEngine.unloadedModels)
        assertTrue(
            progress.contains(
                WidgetAuthoringProgress.Repairing(
                    WidgetAuthoringStage.Feasibility,
                    1,
                ),
            ),
        )
        assertTrue(modelEngine.requests[1].plainChatPrompt.contains("invalid_feasibility_fields"))
        assertTrue(modelEngine.requests[1].plainChatPrompt.contains("exactly outcome, message"))
        assertFalse(modelEngine.requests[1].plainChatPrompt.contains("password="))
        assertEquals(
            listOf(
                WidgetAuthoringAttemptFailure(
                    stage = WidgetAuthoringStage.Feasibility,
                    attempt = 1,
                    code = WidgetAuthoringStageFailureCode.InvalidFeasibilityFields,
                    argumentBytes = 2,
                ),
            ),
            failures,
        )
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
                WidgetAuthoringStageFailureCode.InvalidFeasibilityFields,
            ),
            result,
        )
        assertEquals(3, modelEngine.requests.size)
        assertTrue(modelEngine.requests.all { it.advertisedToolNames == setOf(SUBMIT_WIDGET_FEASIBILITY_TOOL) })
        assertTrue(modelEngine.requests[1].plainChatPrompt.contains("Rejected artifact (bounded evidence"))
        assertTrue(modelEngine.requests[2].plainChatPrompt.contains("Rejected artifact omitted to prevent anchoring"))
        assertFalse(modelEngine.requests[2].plainChatPrompt.contains("Rejected artifact (bounded evidence"))
        assertTrue(javascript.calls.isEmpty())
    }

    @Test
    fun `recovery timeout stops before the next stage while leaving the model unloaded`() = runTest {
        val modelEngine = QueueStageEngine(mutableListOf(feasibility()))
        val javascript = DeterministicPipelineJavaScriptEngine()
        val registry = registry()
        val progress = mutableListOf<WidgetAuthoringProgress>()
        val pipeline = ManagedWidgetAuthoringPipeline(
            WidgetAuthoringPipelineModelController(modelEngine, recoveryGate = { false }),
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
        )

        val result = pipeline.generate(MODEL, INFERENCE, PROMPT, RUNTIME, progress::add)

        assertEquals(WidgetAuthoringPipelineResult.TimedOut, result)
        assertEquals(1, modelEngine.loadedModels)
        assertEquals(1, modelEngine.unloadedModels)
        assertEquals(1, modelEngine.requests.size)
        assertTrue(progress.contains(WidgetAuthoringProgress.WaitingForDeviceRecovery))
        assertFalse(progress.contains(WidgetAuthoringProgress.ReloadingModel))
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
        var unloadedModels = 0
        val requests = mutableListOf<PromptRequest>()
        val operations = mutableListOf<String>()

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            loadedModels++
            operations += "load"
            assertEquals(MAX_WIDGET_AUTHORING_CONTEXT_TOKENS, config.contextTokens)
            assertEquals(MAX_WIDGET_AUTHORING_TEMPERATURE, config.temperature)
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            requests += request
            operations += "generate:${request.advertisedToolNames.single()}"
            request.ephemeralTools.single().execute(artifacts.removeAt(0))
            emit(GenerationEvent.Completed)
        }

        override suspend fun unload() {
            unloadedModels++
            operations += "unload"
        }
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
                "buildCallEventsCall" -> WidgetScriptResult.Success(VALID_ARGUMENTS)
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
            """{"programApi":{"outcomeShape":"outcomes[alias] is a result object","presentationNodes":"https_link:{label,url,sourceAlias,sourceField}"},"tools":[{"id":"wikipedia_on_this_day","version":1,"displayName":"Wikipedia on this day","networkRequired":true,"inputSchema":{"type":"object"},"outputSchema":{"type":"object","properties":{"kind":{"type":"string"},"events":{"type":"array","items":{"type":"object","properties":{"year":{"type":"integer"},"text":{"type":"string"},"canonicalUrl":{"type":"string"}}}}}}}]}""",
        )

        private const val CALL_SOURCE = """function buildCallEventsCall(runtime) {
  const now = runtime.currentLocalDateTime();
  return {month:now.month,day:now.day,language:runtime.language};
}"""
        private const val PLAN_SOURCE = """function plan(runtime) {
  const value0 = buildCallEventsCall(runtime);
  return [{alias:"events_call",toolId:"wikipedia_on_this_day",contractVersion:1,arguments:value0&&value0.alias==="events_call"&&value0.toolId==="wikipedia_on_this_day"&&value0.contractVersion===1?value0.arguments:value0}];
}"""
        private const val RENDER_SOURCE = """function render(runtime, outcomes, state) {
  return {type:'text',text:'Synthetic',tone:'neutral'};
}"""
        private const val VALID_CALL = """{"alias":"events_call","toolId":"wikipedia_on_this_day","contractVersion":1,"arguments":{"month":9,"day":10,"language":"en"}}"""
        private const val VALID_ARGUMENTS = """{"month":9,"day":10,"language":"en"}"""

        private fun feasibilityArtifact() = when (
            val parsed = WidgetFeasibilityParser.parse(feasibility(), setOf(WidgetToolCapability("wikipedia_on_this_day", 1)))
        ) {
            is WidgetFeasibilityParseResult.Valid -> parsed.artifact
            is WidgetFeasibilityParseResult.Invalid -> error("Invalid feasibility fixture")
        }

        private fun algorithmArtifact(): WidgetAlgorithmArtifact = when (
            val parsed = WidgetAlgorithmParser.parse(algorithm(), feasibilityArtifact())
        ) {
            is WidgetAlgorithmParseResult.Valid -> parsed.artifact
            is WidgetAlgorithmParseResult.Invalid -> error("Invalid algorithm fixture")
        }

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
            addProperty("outcome", "achievable")
            addProperty("message", "Today in history")
            addProperty("periodicIntervalHours", 24)
            add("toolIds", JsonArray().apply { add("wikipedia_on_this_day") })
        }.toString()

        private fun unachievable(): String = JsonObject().apply {
            addProperty("outcome", "unachievable")
            addProperty("message", "No registered operation")
            add("periodicIntervalHours", JsonNull.INSTANCE)
            add("toolIds", JsonArray())
        }.toString()

        private fun clarification(): String = JsonObject().apply {
            addProperty("outcome", "needs_clarification")
            addProperty("message", "Qual idioma deve ser usado?")
            add("periodicIntervalHours", JsonNull.INSTANCE)
            add("toolIds", JsonArray())
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
