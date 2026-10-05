@file:Suppress("MaxLineLength")

package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelToolCapabilities
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetAuthoringStageRunnerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: SqliteManagedWidgetRepository
    private var nextId = 0

    @Before
    fun setUp() {
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
        store = SqliteManagedWidgetRepository(context, newId = { "runner-${++nextId}" })
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @Test
    fun `one action performs exactly one generation persists candidate and unloads model`() = runTest {
        val engine = CapturingEngine(FEASIBILITY)
        val fixture = fixture(engine)
        val started = fixture.startInitialAttempt()

        val result = fixture.runner.run(MODEL, INFERENCE, PROMPT, RUNTIME)

        assertEquals(1, engine.loadCount)
        assertEquals(1, engine.generationCount)
        assertEquals(1, engine.unloadCount)
        assertFalse(engine.loaded)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingReview, result.session.status)
        assertEquals(WidgetAuthoringAttemptStatus.Succeeded, result.attempts.single().status)
        assertEquals(FEASIBILITY, result.attempts.single().artifact)
        assertEquals(started.attempt.id, result.attempts.single().id)
    }

    @Test
    fun `invalid artifact stops at review boundary without automatic repair`() = runTest {
        val engine = CapturingEngine("{}")
        val fixture = fixture(engine)
        fixture.startInitialAttempt()

        val result = fixture.runner.run(MODEL, INFERENCE, PROMPT, RUNTIME)

        assertEquals(1, engine.generationCount)
        assertEquals(1, engine.unloadCount)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingRetry, result.session.status)
        assertEquals(WidgetAuthoringAttemptStatus.Failed, result.attempts.single().status)
        assertEquals(WidgetAuthoringStageFailureCode.InvalidFeasibilityFields, result.attempts.single().failureCode)
        assertEquals("{}", result.attempts.single().artifact)
    }

    @Test
    fun `recovery timeout persists retryable failure without loading or generating`() = runTest {
        val engine = CapturingEngine(FEASIBILITY)
        val fixture = fixture(engine, recoveryReady = false)
        fixture.startInitialAttempt()

        val result = fixture.runner.run(MODEL, INFERENCE, PROMPT, RUNTIME)

        assertEquals(0, engine.loadCount)
        assertEquals(0, engine.generationCount)
        assertFalse(engine.loaded)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingRetry, result.session.status)
        assertEquals(WidgetAuthoringStageFailureCode.DeviceRecoveryTimedOut, result.attempts.single().failureCode)
    }

    @Test
    fun `invalid local stage request fails before recovery model load or generation`() = runTest {
        val engine = CapturingEngine("model must not be called")
        val fixture = fixture(engine, recoveryReady = false)
        val snapshot = fixture.acceptFeasibility(
            """{"outcome":"achievable","message":"Current date","periodicIntervalHours":null,"toolIds":["missing"]}""",
        )
        val algorithm = fixture.repository.startAttempt(
            StartWidgetAuthoringAttempt(
                snapshot.session.id,
                snapshot.session.revision,
                "start-algorithm",
                WidgetAuthoringStageKey.Algorithm,
                WidgetAuthoringAttemptKind.Initial,
                authoringUpstreamDigest(snapshot, WidgetAuthoringStageKey.Algorithm),
            ),
        ) as StartWidgetAuthoringAttemptResult.Started

        val result = fixture.runner.run(
            MODEL,
            INFERENCE,
            PROMPT,
            RUNTIME,
        )

        assertEquals(0, engine.loadCount)
        assertEquals(0, engine.generationCount)
        assertEquals(0, engine.unloadCount)
        val failed = result.attempts.single { it.id == algorithm.attempt.id }
        assertEquals(WidgetAuthoringAttemptStatus.Failed, failed.status)
        assertEquals(WidgetAuthoringStageFailureCode.InvalidSchema, failed.failureCode)
    }

    @Test
    fun `model load failure is controlled persisted and followed by unload`() = runTest {
        val engine = CapturingEngine(FEASIBILITY, loadFailure = true)
        val fixture = fixture(engine)
        fixture.startInitialAttempt()

        val result = fixture.runner.run(MODEL, INFERENCE, PROMPT, RUNTIME)

        assertEquals(1, engine.loadCount)
        assertEquals(0, engine.generationCount)
        assertEquals(1, engine.unloadCount)
        assertFalse(engine.loaded)
        val persisted = result.attempts.single()
        assertEquals(WidgetAuthoringStageFailureCode.RuntimeUnavailable, persisted.failureCode)
        assertEquals(null, persisted.artifact)
        assertFalse(result.toString().contains("sanitized load failure fixture"))
    }

    @Test
    fun `cancellation persists cancelled attempt and unloads model`() = runTest {
        val engine = CapturingEngine(FEASIBILITY, blockGeneration = true)
        val fixture = fixture(engine)
        fixture.startInitialAttempt()
        val job = launch {
            fixture.runner.run(MODEL, INFERENCE, PROMPT, RUNTIME)
        }
        runCurrent()
        assertEquals(
            WidgetAuthoringSessionStatus.AttemptRunning,
            fixture.repository.activeSession.value?.session?.status,
        )

        job.cancelAndJoin()

        val restored = requireNotNull(fixture.repository.activeSession.value)
        assertEquals(WidgetAuthoringAttemptStatus.Cancelled, restored.attempts.single().status)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingRetry, restored.session.status)
        assertEquals(1, engine.unloadCount)
        assertFalse(engine.loaded)
    }

    @Test
    fun `retry sends only controlled preceding failure and performs one new generation`() = runTest {
        val firstEngine = CapturingEngine("{}")
        var fixture = fixture(firstEngine)
        fixture.startInitialAttempt()
        var snapshot = fixture.runner.run(MODEL, INFERENCE, PROMPT, RUNTIME)
        advanceTimeBy(24 * 60 * 60 * 1_000L)
        val retryEngine = CapturingEngine(FEASIBILITY)
        fixture = fixture(retryEngine, initialize = false)
        val retry = fixture.repository.startAttempt(
            StartWidgetAuthoringAttempt(
                snapshot.session.id,
                snapshot.session.revision,
                "retry",
                WidgetAuthoringStageKey.Feasibility,
                WidgetAuthoringAttemptKind.Repair,
                authoringUpstreamDigest(snapshot, WidgetAuthoringStageKey.Feasibility),
            ),
        ) as StartWidgetAuthoringAttemptResult.Started

        snapshot = fixture.runner.run(MODEL, INFERENCE, PROMPT, RUNTIME)

        assertEquals(1, retryEngine.generationCount)
        assertTrue(retryEngine.requests.single().plainChatPrompt.contains("invalid_feasibility_fields"))
        assertTrue(retryEngine.requests.single().plainChatPrompt.contains("{}"))
        assertEquals(2, snapshot.attempts.size)
        assertEquals(retry.attempt.id, snapshot.attempts.last().id)
        assertEquals(WidgetAuthoringAttemptStatus.Succeeded, snapshot.attempts.last().status)
    }

    @Test
    fun `plan stage is derived and validated without recovery model load or generation`() = runTest {
        val engine = CapturingEngine("model must not be called")
        val javascript = WidgetJavaScriptEngine { _, entrypoint, _, _ ->
            assertEquals("plan", entrypoint)
            com.jesjobom.ararai.widget.runtime.WidgetScriptResult.Success("[]")
        }
        val fixture = fixture(engine, recoveryReady = false, javascript = javascript)
        var snapshot = fixture.repository.create(
            NewWidgetAuthoringSession(
                instruction = "Show the current date",
                targetWidgetId = null,
                modelId = MODEL.id,
                modelArtifactDigest = "a".repeat(64),
                inferenceConfigJson = "{}",
                toolContractDigest = "b".repeat(64),
            ),
        )

        suspend fun accept(stage: WidgetAuthoringStageKey, artifact: String, next: WidgetAuthoringStageKey) {
            val started = fixture.repository.startAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "accept-${stage.wireValue}",
                    stage,
                    WidgetAuthoringAttemptKind.Initial,
                    authoringUpstreamDigest(snapshot, stage),
                ),
            ) as StartWidgetAuthoringAttemptResult.Started
            fixture.repository.markAttemptDeferred(snapshot.session.id, started.attempt.id)
            fixture.repository.markAttemptRunning(snapshot.session.id, started.attempt.id)
            val completed = fixture.repository.completeAttempt(
                CompleteWidgetAuthoringAttempt(
                    snapshot.session.id,
                    started.attempt.id,
                    WidgetAuthoringAttemptStatus.Succeeded,
                    artifact,
                    null,
                ),
            )
            snapshot = (
                fixture.repository.acceptCandidate(
                    AcceptWidgetAuthoringCandidate(
                        snapshot.session.id,
                        completed.session.revision,
                        "continue-${stage.wireValue}",
                        started.attempt.id,
                        next,
                    ),
                ) as WidgetAuthoringMutationResult.Applied
                ).snapshot
        }

        accept(
            WidgetAuthoringStageKey.Feasibility,
            """{"outcome":"achievable","message":"Current date","periodicIntervalHours":null,"toolIds":[]}""",
            WidgetAuthoringStageKey.Algorithm,
        )
        accept(
            WidgetAuthoringStageKey.Algorithm,
            """{"steps":[{"id":"date","kind":"runtime_input","objective":"Read date","dependsOn":[]}],"presentationObjective":"Show date"}""",
            WidgetAuthoringStageKey.Plan,
        )
        val started = fixture.repository.startAttempt(
            StartWidgetAuthoringAttempt(
                snapshot.session.id,
                snapshot.session.revision,
                "start-plan",
                WidgetAuthoringStageKey.Plan,
                WidgetAuthoringAttemptKind.Initial,
                authoringUpstreamDigest(snapshot, WidgetAuthoringStageKey.Plan),
            ),
        ) as StartWidgetAuthoringAttemptResult.Started

        val result = fixture.runner.run(MODEL, INFERENCE, PROMPT, RUNTIME)

        assertEquals(0, engine.loadCount)
        assertEquals(0, engine.generationCount)
        assertEquals(0, engine.unloadCount)
        val attempt = result.attempts.single { it.id == started.attempt.id }
        assertEquals(WidgetAuthoringAttemptStatus.Succeeded, attempt.status)
        val artifact = requireNotNull(attempt.artifact)
        assertTrue(artifact.contains("function plan(runtime)"))
        assertTrue(artifact.contains("return []"))
    }

    @Test
    fun `call function objective delegates frozen metadata to the application`() {
        val objective = callFunctionObjective(
            WidgetAlgorithmStep(
                id = "get_event",
                kind = WidgetAlgorithmStepKind.ToolCall,
                objective = "Fetch today's event",
                dependencies = emptyList(),
                tool = WidgetToolCapability("wikipedia_on_this_day", 1),
            ),
        )

        assertTrue(objective.contains("function buildCallGetEvent(runtime)"))
        assertTrue(objective.contains("return only the arguments object"))
        assertTrue(objective.contains("tool 'wikipedia_on_this_day' version 1"))
        assertTrue(objective.contains("application adds alias, toolId, and contractVersion"))
    }

    @Test
    fun `plan is local and render objective stays bounded for valid aliases`() {
        val plan = planFunctionObjective(
            listOf(
                WidgetSourceFragmentArtifact(
                    artifactId = "get_event",
                    functionName = "buildCallGetEvent",
                    inputNames = listOf("runtime"),
                    source = "function buildCallGetEvent(runtime) { return {}; }",
                ),
            ),
        )
        val render = renderFunctionObjective(
            listOf(
                WidgetSourceFragmentArtifact(
                    artifactId = "get_event",
                    functionName = "buildCallGetEvent",
                    inputNames = listOf("runtime"),
                    source = "function buildCallGetEvent(runtime) { return {}; }",
                ),
            ),
        )

        assertTrue(plan.contains("application derives"))
        assertTrue(plan.contains("function plan(runtime)"))
        assertTrue(plan.contains("buildCallGetEvent(runtime)"))
        assertTrue(plan.contains("No model artifact or protocol metadata"))
        assertTrue(render.contains("function render(runtime, outcomes, state)"))
        assertTrue(render.contains("aliases only from toolResults[].alias"))
        assertTrue(render.contains("use outcomes[alias]"))
        assertTrue(render.contains("never outcomes.get()"))
        assertFalse(render.contains("get_event"))
        assertTrue(render.contains("presentationContract"))
        assertTrue(render.contains("never use type:'text/value'"))
        assertTrue(render.length <= WidgetAuthoringPipelinePolicy.MAX_TEXT_CHARS)

        listOf("fetch_wikipedia_events", "a".repeat(32)).forEach { alias ->
            val bounded = renderFunctionObjective(
                listOf(
                    WidgetSourceFragmentArtifact(
                        artifactId = alias,
                        functionName = "buildCall",
                        inputNames = listOf("runtime"),
                        source = "function buildCall(runtime) { return {}; }",
                    ),
                ),
            )
            assertTrue("render objective must be bounded for $alias", bounded.length <= WidgetAuthoringPipelinePolicy.MAX_TEXT_CHARS)
        }
    }

    private suspend fun fixture(
        engine: CapturingEngine,
        recoveryReady: Boolean = true,
        initialize: Boolean = true,
        javascript: WidgetJavaScriptEngine = WidgetJavaScriptEngine { _, _, _, _ ->
            error("JavaScript must not run for feasibility")
        },
    ): Fixture {
        val dispatcher = UnconfinedTestDispatcher()
        val repository = DispatcherWidgetAuthoringWorkflowRepository(store, dispatcher)
        if (initialize) repository.initialize() else repository.initialize()
        val registry = ApplicationToolRegistry(emptyList())
        val validator = WidgetAuthoringPipelineValidator(
            registry,
            javascript,
        )
        return Fixture(
            repository,
            WidgetAuthoringStageRunner(
                repository,
                WidgetAuthoringPipelineModelController(engine),
                recoveryGate = { recoveryReady },
                validator,
                registry,
            ),
        )
    }

    private inner class Fixture(
        val repository: DispatcherWidgetAuthoringWorkflowRepository,
        val runner: WidgetAuthoringStageRunner,
    ) {
        suspend fun acceptFeasibility(artifact: String): WidgetAuthoringSessionSnapshot {
            val snapshot = repository.create(
                NewWidgetAuthoringSession(
                    instruction = "Show the current date",
                    targetWidgetId = null,
                    modelId = MODEL.id,
                    modelArtifactDigest = "a".repeat(64),
                    inferenceConfigJson = "{}",
                    toolContractDigest = "b".repeat(64),
                ),
            )
            val started = repository.startAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "start-feasibility",
                    WidgetAuthoringStageKey.Feasibility,
                    WidgetAuthoringAttemptKind.Initial,
                    authoringUpstreamDigest(snapshot, WidgetAuthoringStageKey.Feasibility),
                ),
            ) as StartWidgetAuthoringAttemptResult.Started
            repository.markAttemptDeferred(snapshot.session.id, started.attempt.id)
            repository.markAttemptRunning(snapshot.session.id, started.attempt.id)
            val completed = repository.completeAttempt(
                CompleteWidgetAuthoringAttempt(
                    snapshot.session.id,
                    started.attempt.id,
                    WidgetAuthoringAttemptStatus.Succeeded,
                    artifact,
                    null,
                ),
            )
            return (
                repository.acceptCandidate(
                    AcceptWidgetAuthoringCandidate(
                        snapshot.session.id,
                        completed.session.revision,
                        "accept-feasibility",
                        started.attempt.id,
                        WidgetAuthoringStageKey.Algorithm,
                    ),
                ) as WidgetAuthoringMutationResult.Applied
                ).snapshot
        }

        suspend fun startInitialAttempt(): StartWidgetAuthoringAttemptResult.Started {
            val snapshot = repository.activeSession.value ?: repository.create(
                NewWidgetAuthoringSession(
                    instruction = "Create a status widget",
                    targetWidgetId = null,
                    modelId = MODEL.id,
                    modelArtifactDigest = "a".repeat(64),
                    inferenceConfigJson = "{}",
                    toolContractDigest = "b".repeat(64),
                ),
            )
            return repository.startAttempt(
                StartWidgetAuthoringAttempt(
                    snapshot.session.id,
                    snapshot.session.revision,
                    "initial",
                    WidgetAuthoringStageKey.Feasibility,
                    WidgetAuthoringAttemptKind.Initial,
                    authoringUpstreamDigest(snapshot, WidgetAuthoringStageKey.Feasibility),
                ),
            ) as StartWidgetAuthoringAttemptResult.Started
        }
    }

    private class CapturingEngine(
        private val artifact: String,
        private val loadFailure: Boolean = false,
        private val blockGeneration: Boolean = false,
    ) : LocalLlmEngine {
        var loadCount = 0
        var generationCount = 0
        var unloadCount = 0
        var loaded = false
        val requests = mutableListOf<PromptRequest>()

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            loadCount++
            if (loadFailure) error("sanitized load failure fixture")
            loaded = true
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            check(loaded)
            generationCount++
            requests += request
            if (blockGeneration) awaitCancellation()
            request.ephemeralTools.single().execute(artifact)
            emit(GenerationEvent.Completed)
        }

        override suspend fun unload() {
            unloadCount++
            loaded = false
        }
    }

    private companion object {
        val MODEL = LocalModel(
            id = "e4b",
            name = "E4B",
            filePath = "/model/e4b.task",
            toolCapabilities = ModelToolCapabilities(
                authoringProtocolNames = setOf(WIDGET_AUTHORING_PIPELINE_V1),
            ),
        )
        val INFERENCE = InferenceConfig(2_048, 512, 0.7f, 0.9f)
        val PROMPT = WidgetAuthoringPrompt("Create a status widget", "{\"tools\":[]}")
        val RUNTIME = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-27T12:00:00-04:00", 7)
        const val FEASIBILITY =
            "{\"outcome\":\"achievable\",\"message\":\"Status\",\"periodicIntervalHours\":null,\"toolIds\":[]}"
    }
}
