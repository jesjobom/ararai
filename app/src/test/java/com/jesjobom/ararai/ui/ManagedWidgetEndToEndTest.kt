@file:Suppress("MaxLineLength", "LongMethod")

package com.jesjobom.ararai.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonArray
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
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.applicationToolBinding
import com.jesjobom.ararai.widget.WidgetToolExecutionGateway
import com.jesjobom.ararai.widget.managed.AcceptWidgetAuthoringStageResult
import com.jesjobom.ararai.widget.managed.DispatcherManagedWidgetRepository
import com.jesjobom.ararai.widget.managed.DispatcherWidgetAuthoringWorkflowRepository
import com.jesjobom.ararai.widget.managed.JavaScriptWidgetDraftPlanner
import com.jesjobom.ararai.widget.managed.MANAGED_WIDGET_DATABASE_NAME
import com.jesjobom.ararai.widget.managed.ManagedWidgetApplicationServices
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import com.jesjobom.ararai.widget.managed.ManagedWidgetExecutionCoordinator
import com.jesjobom.ararai.widget.managed.ManagedWidgetExecutionStatus
import com.jesjobom.ararai.widget.managed.ManagedWidgetManualRefresh
import com.jesjobom.ararai.widget.managed.ManagedWidgetScheduleController
import com.jesjobom.ararai.widget.managed.ManagedWidgetScheduler
import com.jesjobom.ararai.widget.managed.ResumableWidgetAuthoringController
import com.jesjobom.ararai.widget.managed.RunWidgetAuthoringStageResult
import com.jesjobom.ararai.widget.managed.RuntimeManagedWidgetProgramExecutor
import com.jesjobom.ararai.widget.managed.SUBMIT_WIDGET_ALGORITHM_TOOL
import com.jesjobom.ararai.widget.managed.SUBMIT_WIDGET_CALL_FUNCTION_TOOL
import com.jesjobom.ararai.widget.managed.SUBMIT_WIDGET_FEASIBILITY_TOOL
import com.jesjobom.ararai.widget.managed.SUBMIT_WIDGET_PLAN_FUNCTION_TOOL
import com.jesjobom.ararai.widget.managed.SUBMIT_WIDGET_RENDER_FUNCTION_TOOL
import com.jesjobom.ararai.widget.managed.SqliteManagedWidgetRepository
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAssemblyBuilder
import com.jesjobom.ararai.widget.managed.WidgetAuthoringAssemblyResult
import com.jesjobom.ararai.widget.managed.WidgetAuthoringPipelineModelController
import com.jesjobom.ararai.widget.managed.WidgetAuthoringPipelineValidator
import com.jesjobom.ararai.widget.managed.WidgetAuthoringSessionStatus
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageRunner
import com.jesjobom.ararai.widget.managed.WidgetAuthoringWorkflowCoordinator
import com.jesjobom.ararai.widget.managed.WidgetConfirmationMode
import com.jesjobom.ararai.widget.managed.WidgetConfirmationResult
import com.jesjobom.ararai.widget.managed.WidgetDraftBuilder
import com.jesjobom.ararai.widget.managed.WidgetProgramRevision
import com.jesjobom.ararai.widget.runtime.GatewayWidgetProgramToolExecutor
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRequestedLimits
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeCoordinator
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ManagedWidgetEndToEndTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: SqliteManagedWidgetRepository
    private lateinit var repository: DispatcherManagedWidgetRepository
    private val scheduler = UniqueRecordingScheduler()
    private var toolEnabled = true
    private var providerCalls = 0
    private var providerLanguage = "en"

    @Before
    fun setUp() {
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
        var nextId = 0
        store = SqliteManagedWidgetRepository(context, nowMillis = { now }, newId = { "id-${++nextId}" })
        repository = DispatcherManagedWidgetRepository(store, UnconfinedTestDispatcher())
        toolEnabled = true
        providerCalls = 0
        providerLanguage = "en"
        now = 1_000
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @Test
    fun `prompt consent refresh stale edit disable and deletion remain one local lifecycle`() = runTest {
        val registry = registry()
        val runtimeEngine = WikipediaProgramEngine()
        val coordinator = ManagedWidgetExecutionCoordinator(
            repository = repository,
            executor = RuntimeManagedWidgetProgramExecutor(
                WidgetRuntimeCoordinator(
                    runtimeEngine,
                    GatewayWidgetProgramToolExecutor(
                        WidgetToolExecutionGateway(ApplicationToolDispatcher(registry)),
                    ),
                ),
            ),
            contextProvider = { CONTEXT },
            nowMillis = { now++ },
        )
        val schedules = ManagedWidgetScheduleController(repository, scheduler)
        val modelEngine = PipelineStageEngine()
        val controller = ManagedWidgetsController(
            services = ManagedWidgetApplicationServices(
                repository,
                schedules,
                ManagedWidgetManualRefresh(coordinator),
                registry,
            ),
            localLlmEngine = modelEngine,
            widgetJavaScriptEngine = runtimeEngine,
            runtimeContextProvider = { CONTEXT },
        )

        val generated = controller.generateDraft(MODEL, INFERENCE, "Show a historical event for today", null)
        val draft = (generated as ManagedWidgetDraftGenerationResult.Ready).value
        assertTrue(repository.listDefinitions().isEmpty())
        assertEquals(0, providerCalls)

        val confirmed = controller.confirm(draft, WidgetConfirmationMode.CreateAndEnable)
        val widget = (confirmed as WidgetConfirmationResult.Confirmed).definition
        assertEquals(setOf(widget.id), scheduler.scheduled)
        assertEquals(0, providerCalls)

        assertEquals(ManagedWidgetExecutionStatus.Completed, controller.refresh(widget.id))
        assertEquals(1, providerCalls)
        val successfulDetail = controller.loadDetail(widget.id)!!
        assertTrue(successfulDetail.presentation != null)
        assertFalse(successfulDetail.stale)

        toolEnabled = false
        assertEquals(ManagedWidgetExecutionStatus.Completed, controller.refresh(widget.id))
        assertEquals(1, providerCalls)
        val staleDetail = controller.loadDetail(widget.id)!!
        assertTrue(staleDetail.presentation != null)
        assertTrue(staleDetail.stale)

        toolEnabled = true
        val editResult = controller.generateDraft(MODEL, INFERENCE, "Use a clearer title", widget.id)
        val edit = (editResult as ManagedWidgetDraftGenerationResult.Ready).value
        assertTrue(edit.diff?.source?.changed == true)
        val edited = controller.confirm(edit, WidgetConfirmationMode.CreateAndEnable)
            as WidgetConfirmationResult.Confirmed
        assertEquals(2, edited.definition.activeRevision)
        assertEquals(setOf(widget.id), scheduler.scheduled)

        controller.setEnabled(widget.id, false)
        assertEquals(ManagedWidgetExecutionStatus.Disabled, controller.refresh(widget.id))
        assertEquals(1, providerCalls)
        controller.delete(widget.id)
        assertNull(controller.loadDetail(widget.id))
        assertTrue(widget.id in scheduler.cancelled)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `resumable authoring stops for review at every stage before consent and execution`() = runTest {
        val registry = registry()
        val javascript = WikipediaProgramEngine()
        val model = PipelineStageEngine()
        var runtimeContext = CONTEXT
        val schedules = ManagedWidgetScheduleController(repository, scheduler)
        val workflowRepository = DispatcherWidgetAuthoringWorkflowRepository(store, UnconfinedTestDispatcher())
        val validator = WidgetAuthoringPipelineValidator(registry, javascript)
        val stageController = ResumableWidgetAuthoringController(
            this,
            workflowRepository,
            WidgetAuthoringStageRunner(
                workflowRepository,
                WidgetAuthoringPipelineModelController(model),
                recoveryGate = { true },
                validator,
                registry,
            ),
        )
        stageController.initialize()
        val workflow = WidgetAuthoringWorkflowCoordinator(
            stageController,
            repository,
            schedules,
            registry,
            WidgetAuthoringAssemblyBuilder(
                validator,
                WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
                registry,
            ),
            { runtimeContext },
        )
        val execution = ManagedWidgetExecutionCoordinator(
            repository,
            RuntimeManagedWidgetProgramExecutor(
                WidgetRuntimeCoordinator(
                    javascript,
                    GatewayWidgetProgramToolExecutor(WidgetToolExecutionGateway(ApplicationToolDispatcher(registry))),
                ),
            ),
            contextProvider = { runtimeContext },
            nowMillis = { now++ },
        )
        var repairsObserved = 0

        suspend fun author(
            prompt: String,
            target: String?,
            actionPrefix: String,
            displayName: String,
            renderSource: String,
        ): ManagedWidgetDefinition {
            model.displayNameOverride = displayName
            model.renderSourceOverride = renderSource
            val providerCallsBeforeAuthoring = providerCalls
            workflow.begin(MODEL, INFERENCE, DIGEST, prompt, target)
            var preview: AcceptWidgetAuthoringStageResult.PreviewReady? = null
            for (index in 0 until 6) {
                val before = requireNotNull(workflow.activeSession.value)
                assertEquals(WidgetAuthoringSessionStatus.AwaitingStage, before.session.status)
                assertTrue(workflow.startCurrent(MODEL, INFERENCE, DIGEST, "$actionPrefix-start-$index") is RunWidgetAuthoringStageResult.Submitted)
                runCurrent()
                val candidate = requireNotNull(workflow.activeSession.value)
                if (candidate.attempts.last().status == com.jesjobom.ararai.widget.managed.WidgetAuthoringAttemptStatus.Failed) {
                    assertEquals(
                        if (runtimeContext.locale == "pt-BR") {
                            com.jesjobom.ararai.widget.managed.WidgetAuthoringStageKey.Feasibility
                        } else {
                            com.jesjobom.ararai.widget.managed.WidgetAuthoringStageKey.Algorithm
                        },
                        candidate.session.currentStage,
                    )
                    repairsObserved++
                    assertEquals(WidgetAuthoringSessionStatus.AwaitingRetry, candidate.session.status)
                    assertTrue(workflow.retryCurrent(MODEL, INFERENCE, DIGEST, "$actionPrefix-repair-$index") is RunWidgetAuthoringStageResult.Submitted)
                    runCurrent()
                }
                assertEquals(WidgetAuthoringSessionStatus.AwaitingReview, workflow.activeSession.value?.session?.status)
                assertEquals(providerCallsBeforeAuthoring, providerCalls)
                if (target == null) {
                    assertTrue(repository.listDefinitions().isEmpty())
                } else {
                    assertEquals(1, repository.activeRevision(target)?.revision)
                }
                val accepted = workflow.acceptCurrent("$actionPrefix-accept-$index")
                if (accepted is AcceptWidgetAuthoringStageResult.PreviewReady) {
                    preview = accepted
                    break
                }
                assertTrue(accepted is AcceptWidgetAuthoringStageResult.Advanced)
            }
            val ready = requireNotNull(preview)
            assertEquals(providerCallsBeforeAuthoring, providerCalls)
            assertTrue(workflow.preview() is WidgetAuthoringAssemblyResult.Ready)
            val confirmed = workflow.confirm(WidgetConfirmationMode.CreateAndEnable)
            assertTrue(confirmed is WidgetConfirmationResult.Confirmed)
            assertNull(workflow.activeSession.value)
            val revision = requireNotNull(repository.activeRevision((confirmed as WidgetConfirmationResult.Confirmed).definition.id))
            assertEquals(ready.draft.program.source, revision.source)
            assertEquals(prompt, revision.initialPrompt)
            return confirmed.definition
        }

        for ((locale, prompts, title) in listOf(
            Triple("pt-BR", "Mostre um evento histórico de hoje" to "Use um título mais claro", "Hoje na história"),
            Triple("en-CA", "Show a historical event for today" to "Use a clearer title", "Today in history"),
        )) {
            runtimeContext = CONTEXT.copy(locale = locale)
            javascript.language = locale.substringBefore('-')
            providerLanguage = javascript.language
            val priorCalls = providerCalls
            model.failNextFeasibility = locale == "pt-BR"
            model.failNextAlgorithm = locale == "en-CA"
            val created = author(
                prompts.first,
                null,
                "$locale-create",
                title,
                if (locale == "pt-BR") RENDER_SOURCE_PT else RENDER_SOURCE,
            )
            assertEquals(1, created.activeRevision)
            assertEquals(setOf(created.id), scheduler.scheduled)
            val createdSource = requireNotNull(repository.activeRevision(created.id)).source
            val loadsBeforeExecution = model.loadCount
            assertEquals(ManagedWidgetExecutionStatus.Completed, execution.execute(created.id))
            assertEquals(priorCalls + 1, providerCalls)
            assertEquals(loadsBeforeExecution, model.loadCount)
            val firstCard = requireNotNull(repository.cachedPresentation(created.id))
            assertTrue(firstCard.presentationJson.contains(if (locale == "pt-BR") "Um evento histórico." else "A historical event."))
            toolEnabled = false
            assertEquals(ManagedWidgetExecutionStatus.Completed, execution.execute(created.id))
            assertEquals(priorCalls + 1, providerCalls)
            assertEquals(firstCard, repository.cachedPresentation(created.id))
            toolEnabled = true
            val edited = author(
                prompts.second,
                created.id,
                "$locale-edit",
                "$title — edit",
                if (locale == "pt-BR") EDIT_RENDER_SOURCE_PT else EDIT_RENDER_SOURCE,
            )
            assertEquals(2, edited.activeRevision)
            assertNotEquals(createdSource, repository.activeRevision(created.id)?.source)
            assertEquals(ManagedWidgetExecutionStatus.Completed, execution.execute(created.id))
            assertEquals(priorCalls + 2, providerCalls)
            assertTrue(
                requireNotNull(repository.cachedPresentation(created.id)).presentationJson.contains(
                    if (locale == "pt-BR") "Um evento histórico mais claro." else "A clearer historical event.",
                ),
            )
            assertEquals(loadsBeforeExecution + 4, model.loadCount)
            assertEquals(setOf(created.id), scheduler.scheduled)
            schedules.delete(created.id)
            assertTrue(repository.listDefinitions().none { it.id == created.id })
            assertTrue(created.id in scheduler.cancelled)
        }
        assertEquals(model.loadCount, model.unloadCount)
        assertEquals(18, model.loadCount)
        assertEquals(2, repairsObserved)
    }

    private fun registry(): ApplicationToolRegistry = ApplicationToolRegistry(
        listOf(
            applicationToolBinding(
                contract = CONTRACT,
                state = { ApplicationToolOperationalState(toolEnabled, ready = true) },
                executor = ApplicationTool("Wikipedia events by date", ApplicationToolCategory.ExternalKnowledge) { _: Unit ->
                    providerCalls++
                    WIKIPEDIA_RESULT.replace("\"language\":\"en\"", "\"language\":\"$providerLanguage\"")
                },
                decodeArguments = { Unit },
                encodeResult = { it },
            ),
        ),
    )

    private class UniqueRecordingScheduler : ManagedWidgetScheduler {
        val scheduled = mutableSetOf<String>()
        val cancelled = mutableSetOf<String>()

        override fun reconcile(definition: ManagedWidgetDefinition, revision: WidgetProgramRevision) {
            if (definition.enabled) scheduled += definition.id else cancel(definition.id)
        }

        override fun cancel(widgetId: String) {
            scheduled -= widgetId
            cancelled += widgetId
        }

        override fun cancelUnknown(knownWidgetIds: Set<String>) {
            scheduled.retainAll(knownWidgetIds)
        }
    }

    private class PipelineStageEngine : LocalLlmEngine {
        private var completedPipelines = 0
        var displayNameOverride: String? = null
        var renderSourceOverride: String? = null
        var failNextFeasibility = false
        var failNextAlgorithm = false
        var loadCount = 0
        var unloadCount = 0

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            loadCount++
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            val tool = request.ephemeralTools.single()
            val artifact = when (tool.name) {
                SUBMIT_WIDGET_FEASIBILITY_TOOL -> if (failNextFeasibility) {
                    failNextFeasibility = false
                    "{}"
                } else {
                    feasibility(completedPipelines)
                }
                SUBMIT_WIDGET_ALGORITHM_TOOL -> if (failNextAlgorithm) {
                    failNextAlgorithm = false
                    invalidAdaptiveAlgorithm()
                } else {
                    algorithm()
                }
                SUBMIT_WIDGET_CALL_FUNCTION_TOOL -> fragment("events", "buildCallEvents", listOf("runtime"), CALL_SOURCE)
                SUBMIT_WIDGET_PLAN_FUNCTION_TOOL -> fragment("plan", "plan", listOf("runtime"), PLAN_SOURCE)
                SUBMIT_WIDGET_RENDER_FUNCTION_TOOL -> fragment(
                    "render",
                    "render",
                    listOf("runtime", "outcomes", "state"),
                    renderSourceOverride ?: if (completedPipelines++ == 0) RENDER_SOURCE else EDIT_RENDER_SOURCE,
                )
                else -> error("Unexpected stage ${tool.name}")
            }
            tool.execute(artifact)
            emit(GenerationEvent.Completed)
        }

        override suspend fun unload() {
            unloadCount++
        }

        private fun feasibility(edit: Int): String = JsonObject().apply {
            addProperty("outcome", "achievable")
            addProperty("message", displayNameOverride ?: if (edit == 0) "Today in history" else "A historical event today")
            addProperty("periodicIntervalHours", 24)
            add("toolIds", JsonArray().apply { add("wikipedia_on_this_day") })
        }.toString()

        private fun algorithm(): String = JsonObject().apply {
            addProperty("protocolVersion", 1)
            add(
                "steps",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("id", "events")
                            addProperty("kind", "tool_call")
                            addProperty("objective", "Load current-date events")
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
            addProperty("presentationObjective", "Show one historical event")
            addProperty("renderCapability", "custom_source")
        }.toString()

        private fun invalidAdaptiveAlgorithm(): String = JsonParser.parseString(algorithm()).asJsonObject.apply {
            addProperty("renderCapability", "collection_card")
            getAsJsonArray("steps").add(
                JsonObject().apply {
                    addProperty("id", "filter_events")
                    addProperty("kind", "transform")
                    addProperty("objective", "Filter events after the Wikipedia result")
                    add("dependsOn", JsonArray().apply { add("events") })
                },
            )
        }.toString()

        private fun fragment(id: String, name: String, inputs: List<String>, source: String): String = JsonObject().apply {
            addProperty("protocolVersion", 1)
            addProperty("artifactId", id)
            addProperty("functionName", name)
            add("inputNames", JsonArray().apply { inputs.forEach(::add) })
            addProperty("source", source)
        }.toString()
    }

    private class WikipediaProgramEngine : WidgetJavaScriptEngine {
        var language = "en"

        override suspend fun call(
            source: String,
            entrypoint: String,
            argumentsJson: List<String>,
            limits: WidgetRequestedLimits,
        ): WidgetScriptResult = when (entrypoint) {
            "buildCallEvents" -> WidgetScriptResult.Success(
                """{"alias":"events","toolId":"wikipedia_on_this_day","contractVersion":1,"arguments":{"month":9,"day":7,"language":"$language"}}""",
            )
            "plan" -> WidgetScriptResult.Success(
                """[{"alias":"events","toolId":"wikipedia_on_this_day","contractVersion":1,"arguments":{"month":9,"day":7,"language":"$language"}}]""",
            )
            "render" -> WidgetScriptResult.Success(
                if (argumentsJson[1].contains("\"status\":\"failure\"")) {
                    """{"type":"text","text":"Unavailable","tone":"warning"}"""
                } else if (argumentsJson[1].contains("\"events\":[]")) {
                    """{"type":"text","text":"No event","tone":"muted"}"""
                } else {
                    val display = when {
                        source.contains("Um evento histórico mais claro.") -> "Um evento histórico mais claro."
                        source.contains("Um evento histórico.") -> "Um evento histórico."
                        source.contains("A clearer historical event.") -> "A clearer historical event."
                        else -> "A historical event."
                    }
                    """{"type":"text","text":"$display","tone":"neutral"}"""
                },
            )
            else -> error("Unexpected entrypoint")
        }
    }

    companion object {
        private val DIGEST = "a".repeat(64)
        private var now = 1_000L
        private val INFERENCE = InferenceConfig(2_048, 512, 0.7f, 0.9f)
        private val CONTEXT = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-07T12:00:00-04:00", 42)
        private val MODEL = LocalModel(
            id = "e2b",
            name = "E2B",
            filePath = "/models/e2b.litertlm",
            toolCapabilities = ModelToolCapabilities(
                authoringProtocolNames = setOf(WIDGET_AUTHORING_PIPELINE_V1),
            ),
        )
        private val CONTRACT = ApplicationToolContract(
            id = "wikipedia_on_this_day",
            version = 1,
            displayName = "Wikipedia events by date",
            category = ApplicationToolCategory.ExternalKnowledge,
            consumers = setOf(ApplicationToolConsumer.Model, ApplicationToolConsumer.Widget),
            inputSchemaJson = "{}",
            outputSchemaJson = """{"type":"object","properties":{"kind":{"type":"string"},"events":{"type":"array","items":{"type":"object","properties":{"year":{"type":"integer"},"text":{"type":"string"},"canonicalUrl":{"type":"string"}}}}}}""",
        )
        private const val WIKIPEDIA_RESULT =
            """{"kind":"success","events":[{"year":1815,"text":"A historical event.","title":"Ada Lovelace","canonicalUrl":"https://en.wikipedia.org/wiki/Ada_Lovelace"}],"language":"en","retrievedAtMillis":1000}"""
        private const val CALL_SOURCE = """function buildCallEvents(runtime) {
  const now = runtime.currentLocalDateTime();
  return {alias:'events',toolId:'wikipedia_on_this_day',contractVersion:1,arguments:{month:now.month,day:now.day,language:runtime.language}};
}"""
        private const val PLAN_SOURCE = """function plan(runtime) {
  return [buildCallEvents(runtime)];
}"""
        private const val RENDER_SOURCE = """function render(runtime, outcomes, state) {
  return {type:'text',text:'A historical event.',tone:'neutral'};
}"""
        private const val EDIT_RENDER_SOURCE = """function render(runtime, outcomes, state) {
  return {type:'text',text:'A clearer historical event.',tone:'neutral'};
}"""
        private const val RENDER_SOURCE_PT = """function render(runtime, outcomes, state) {
  return {type:'text',text:'Um evento histórico.',tone:'neutral'};
}"""
        private const val EDIT_RENDER_SOURCE_PT = """function render(runtime, outcomes, state) {
  return {type:'text',text:'Um evento histórico mais claro.',tone:'neutral'};
}"""
    }
}
