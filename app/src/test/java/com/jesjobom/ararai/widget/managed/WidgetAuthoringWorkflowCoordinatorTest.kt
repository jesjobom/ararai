package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeFailureCode
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetAuthoringWorkflowCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: SqliteManagedWidgetRepository
    private var nextId = 0

    @Before
    fun setUp() {
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
        store = SqliteManagedWidgetRepository(context, newId = { "coordinator-${++nextId}" })
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @Test
    fun `one explicit action persists one attempt and stops for feasibility review`() = runTest {
        val workflowRepository = DispatcherWidgetAuthoringWorkflowRepository(store, UnconfinedTestDispatcher())
        var executions = 0
        val controller = ResumableWidgetAuthoringController(
            this,
            workflowRepository,
            WidgetAuthoringAttemptExecutor { _, _, _, _ ->
                executions++
                val active = requireNotNull(workflowRepository.activeSession.value)
                val attemptId = requireNotNull(active.session.activeAttemptId)
                workflowRepository.markAttemptRunning(active.session.id, attemptId)
                workflowRepository.completeAttempt(
                    CompleteWidgetAuthoringAttempt(
                        active.session.id,
                        attemptId,
                        WidgetAuthoringAttemptStatus.Succeeded,
                        UNACHIEVABLE,
                        null,
                    ),
                )
            },
        )
        controller.initialize()
        val coordinator = coordinator(controller)

        val begin = coordinator.begin(MODEL, INFERENCE, DIGEST, "Build a widget", null)
        assertTrue(begin is BeginWidgetAuthoringWorkflowResult.Created)
        val oldNotificationSnapshot = requireNotNull(coordinator.activeSession.value)
        val submitted = coordinator.startCurrent(MODEL, INFERENCE, DIGEST, "start")
        assertTrue(submitted is RunWidgetAuthoringStageResult.Submitted)
        runCurrent()

        val snapshot = requireNotNull(coordinator.activeSession.value)
        assertEquals(1, executions)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingReview, snapshot.session.status)
        assertEquals(1, snapshot.attempts.size)
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { coordinator.acceptCurrent("stale-continue", oldNotificationSnapshot) }
        }
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                coordinator.retryCurrent(MODEL, INFERENCE, DIGEST, "stale-retry", oldNotificationSnapshot)
            }
        }
        assertEquals(snapshot.session.revision, coordinator.activeSession.value?.session?.revision)
        val stopped = coordinator.advanceAndStartNext(MODEL, INFERENCE, DIGEST, "accept", "next")
        assertTrue(stopped.accepted is AcceptWidgetAuthoringStageResult.FeasibilityStopped)
        assertEquals(null, stopped.nextAttempt)
        assertTrue(snapshot.checkpoints.isEmpty())
    }

    @Test
    fun `one advance accepts a successful stage and starts the next attempt`() = runTest {
        val workflowRepository = DispatcherWidgetAuthoringWorkflowRepository(store, UnconfinedTestDispatcher())
        var executions = 0
        val controller = ResumableWidgetAuthoringController(
            this,
            workflowRepository,
            WidgetAuthoringAttemptExecutor { _, _, _, _ ->
                executions++
                val active = requireNotNull(workflowRepository.activeSession.value)
                val attemptId = requireNotNull(active.session.activeAttemptId)
                workflowRepository.markAttemptRunning(active.session.id, attemptId)
                val firstStage = executions == 1
                workflowRepository.completeAttempt(
                    CompleteWidgetAuthoringAttempt(
                        active.session.id,
                        attemptId,
                        if (firstStage) WidgetAuthoringAttemptStatus.Succeeded else WidgetAuthoringAttemptStatus.Failed,
                        if (firstStage) ACHIEVABLE else null,
                        if (firstStage) null else WidgetAuthoringStageFailureCode.InvalidSchedule,
                    ),
                )
            },
        )
        controller.initialize()
        val coordinator = coordinator(controller)
        coordinator.begin(MODEL, INFERENCE, DIGEST, "Build a widget", null)
        coordinator.startCurrent(MODEL, INFERENCE, DIGEST, "first")
        runCurrent()

        val advanced = coordinator.advanceAndStartNext(MODEL, INFERENCE, DIGEST, "accept", "next")
        assertTrue(advanced.accepted is AcceptWidgetAuthoringStageResult.Advanced)
        assertTrue(advanced.nextAttempt is RunWidgetAuthoringStageResult.Submitted)
        runCurrent()

        val snapshot = requireNotNull(coordinator.activeSession.value)
        assertEquals(2, executions)
        assertEquals(WidgetAuthoringStageKey.Schedule, snapshot.session.currentStage)
        assertEquals(2, snapshot.attempts.size)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingRetry, snapshot.session.status)
    }

    @Test
    fun `successful stage advances automatically and stops at the first failed stage`() = runTest {
        val workflowRepository = DispatcherWidgetAuthoringWorkflowRepository(store, UnconfinedTestDispatcher())
        lateinit var coordinator: WidgetAuthoringWorkflowCoordinator
        var executions = 0
        val controller = ResumableWidgetAuthoringController(
            this,
            workflowRepository,
            WidgetAuthoringAttemptExecutor { _, _, _, _ ->
                executions++
                val active = requireNotNull(workflowRepository.activeSession.value)
                val attemptId = requireNotNull(active.session.activeAttemptId)
                workflowRepository.markAttemptRunning(active.session.id, attemptId)
                workflowRepository.completeAttempt(
                    CompleteWidgetAuthoringAttempt(
                        active.session.id,
                        attemptId,
                        if (executions == 1) {
                            WidgetAuthoringAttemptStatus.Succeeded
                        } else {
                            WidgetAuthoringAttemptStatus.Failed
                        },
                        if (executions == 1) ACHIEVABLE else null,
                        if (executions == 1) null else WidgetAuthoringStageFailureCode.InvalidSchedule,
                    ),
                )
            },
            onSuccessfulAttempt = { snapshot, execution ->
                coordinator.advanceAndStartNext(
                    execution.model,
                    execution.inference,
                    DIGEST,
                    "accept-${snapshot.session.revision}",
                    "start-${snapshot.session.revision}",
                    snapshot,
                )
            },
        )
        controller.initialize()
        coordinator = coordinator(controller)
        coordinator.begin(MODEL, INFERENCE, DIGEST, "Build a widget", null)

        coordinator.startCurrent(MODEL, INFERENCE, DIGEST, "first")
        runCurrent()

        val snapshot = requireNotNull(coordinator.activeSession.value)
        assertEquals(2, executions)
        assertEquals(2, snapshot.attempts.size)
        assertEquals(WidgetAuthoringStageKey.Schedule, snapshot.session.currentStage)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingRetry, snapshot.session.status)
        assertEquals(WidgetAuthoringStageKey.Feasibility, snapshot.checkpoints.single().stage)
    }

    @Test
    fun `dedicated schedule supplies interval after schedule-free feasibility`() = runTest {
        val workflowRepository = DispatcherWidgetAuthoringWorkflowRepository(store, UnconfinedTestDispatcher())
        lateinit var coordinator: WidgetAuthoringWorkflowCoordinator
        val controller = ResumableWidgetAuthoringController(
            this,
            workflowRepository,
            WidgetAuthoringAttemptExecutor { _, _, _, _ ->
                val active = requireNotNull(workflowRepository.activeSession.value)
                val attemptId = requireNotNull(active.session.activeAttemptId)
                workflowRepository.markAttemptRunning(active.session.id, attemptId)
                val stage = active.session.currentStage
                val failed = stage == WidgetAuthoringStageKey.Algorithm
                workflowRepository.completeAttempt(
                    CompleteWidgetAuthoringAttempt(
                        active.session.id,
                        attemptId,
                        if (failed) WidgetAuthoringAttemptStatus.Failed else WidgetAuthoringAttemptStatus.Succeeded,
                        when (stage) {
                            WidgetAuthoringStageKey.Feasibility -> ACHIEVABLE
                            WidgetAuthoringStageKey.Schedule -> """{"periodicIntervalHours":6}"""
                            else -> null
                        },
                        if (failed) WidgetAuthoringStageFailureCode.InvalidAlgorithm else null,
                    ),
                )
            },
            onSuccessfulAttempt = { snapshot, execution ->
                coordinator.advanceAndStartNext(
                    execution.model,
                    execution.inference,
                    DIGEST,
                    "accept-${snapshot.session.revision}",
                    "start-${snapshot.session.revision}",
                    snapshot,
                )
            },
        )
        controller.initialize()
        coordinator = coordinator(controller)
        coordinator.begin(MODEL, INFERENCE, DIGEST, "Atualize 4 vezes ao dia", null)

        coordinator.startCurrent(MODEL, INFERENCE, DIGEST, "start")
        runCurrent()

        val snapshot = requireNotNull(coordinator.activeSession.value)
        assertEquals(WidgetAuthoringStageKey.Algorithm, snapshot.session.currentStage)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingRetry, snapshot.session.status)
        assertEquals(
            6L,
            AcceptedArtifacts(snapshot, ApplicationToolRegistry(emptyList())).feasibility().periodicIntervalHours,
        )
        assertEquals(
            listOf(WidgetAuthoringStageKey.Feasibility, WidgetAuthoringStageKey.Schedule),
            snapshot.checkpoints.map { it.stage },
        )
    }

    @Test
    fun `unachievable feasibility does not start another attempt`() = runTest {
        val workflowRepository = DispatcherWidgetAuthoringWorkflowRepository(store, UnconfinedTestDispatcher())
        lateinit var coordinator: WidgetAuthoringWorkflowCoordinator
        var executions = 0
        val controller = ResumableWidgetAuthoringController(
            this,
            workflowRepository,
            WidgetAuthoringAttemptExecutor { _, _, _, _ ->
                executions++
                val active = requireNotNull(workflowRepository.activeSession.value)
                val attemptId = requireNotNull(active.session.activeAttemptId)
                workflowRepository.markAttemptRunning(active.session.id, attemptId)
                workflowRepository.completeAttempt(
                    CompleteWidgetAuthoringAttempt(
                        active.session.id,
                        attemptId,
                        WidgetAuthoringAttemptStatus.Succeeded,
                        UNACHIEVABLE,
                        null,
                    ),
                )
            },
            onSuccessfulAttempt = { snapshot, execution ->
                coordinator.advanceAndStartNext(
                    execution.model,
                    execution.inference,
                    DIGEST,
                    "accept-${snapshot.session.revision}",
                    "start-${snapshot.session.revision}",
                    snapshot,
                )
            },
        )
        controller.initialize()
        coordinator = coordinator(controller)
        coordinator.begin(MODEL, INFERENCE, DIGEST, "Build a widget", null)

        coordinator.startCurrent(MODEL, INFERENCE, DIGEST, "first")
        runCurrent()

        assertEquals(1, executions)
        assertTrue(requireNotNull(coordinator.activeSession.value).checkpoints.isEmpty())
    }

    @Test
    fun `frozen inference codec round trips and configuration mismatch blocks generation`() = runTest {
        assertEquals(INFERENCE, WidgetAuthoringInferenceCodec.decode(WidgetAuthoringInferenceCodec.encode(INFERENCE)))
        val workflowRepository = DispatcherWidgetAuthoringWorkflowRepository(store, UnconfinedTestDispatcher())
        var executions = 0
        val controller = ResumableWidgetAuthoringController(
            this,
            workflowRepository,
            WidgetAuthoringAttemptExecutor { _, _, _, _ ->
                executions++
                requireNotNull(workflowRepository.activeSession.value)
            },
        )
        controller.initialize()
        val coordinator = coordinator(controller)
        coordinator.begin(MODEL, INFERENCE, DIGEST, "Build a widget", null)

        val result = coordinator.startCurrent(
            MODEL,
            INFERENCE.copy(temperature = 0.1f),
            DIGEST,
            "mismatch",
        )

        assertEquals(RunWidgetAuthoringStageResult.ConfigurationChanged, result)
        assertEquals(0, executions)
        assertTrue(requireNotNull(coordinator.activeSession.value).attempts.isEmpty())
    }

    private fun coordinator(controller: ResumableWidgetAuthoringController): WidgetAuthoringWorkflowCoordinator {
        val registry = ApplicationToolRegistry(emptyList())
        val widgetRepository = DispatcherManagedWidgetRepository(store, UnconfinedTestDispatcher())
        val schedules = ManagedWidgetScheduleController(widgetRepository, NoOpScheduler)
        val engine = WidgetJavaScriptEngine { _, _, _, _ ->
            WidgetScriptResult.Failure(WidgetRuntimeFailureCode.RuntimeUnavailable)
        }
        val validator = WidgetAuthoringPipelineValidator(registry, engine)
        val draftBuilder = WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(engine))
        return WidgetAuthoringWorkflowCoordinator(
            controller,
            widgetRepository,
            schedules,
            registry,
            WidgetAuthoringAssemblyBuilder(validator, draftBuilder, registry),
            { RUNTIME_CONTEXT },
        )
    }

    private object NoOpScheduler : ManagedWidgetScheduler {
        override fun reconcile(
            definition: ManagedWidgetDefinition,
            revision: WidgetProgramRevision,
        ) = Unit

        override fun resetBaseline(definition: ManagedWidgetDefinition, revision: WidgetProgramRevision) = Unit

        override fun cancel(widgetId: String) = Unit

        override fun cancelUnknown(knownWidgetIds: Set<String>) = Unit
    }

    private companion object {
        val MODEL = LocalModel("model", "Model", "/model.task")
        val INFERENCE = InferenceConfig(2_048, 512, 0.7f, 0.9f)
        val RUNTIME_CONTEXT = WidgetRuntimeContext(
            "en-CA",
            "America/Toronto",
            "2026-09-27T12:00:00-04:00",
            7,
        )
        val DIGEST = "a".repeat(64)
        const val UNACHIEVABLE =
            "{\"outcome\":\"unachievable\",\"message\":\"No tools\",\"toolIds\":[]}"
        const val ACHIEVABLE =
            "{\"outcome\":\"achievable\",\"message\":\"Use local context\",\"toolIds\":[]}"
    }
}
