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
        val submitted = coordinator.startCurrent(MODEL, INFERENCE, DIGEST, "start")
        assertTrue(submitted is RunWidgetAuthoringStageResult.Submitted)
        runCurrent()

        val snapshot = requireNotNull(coordinator.activeSession.value)
        assertEquals(1, executions)
        assertEquals(WidgetAuthoringSessionStatus.AwaitingReview, snapshot.session.status)
        assertEquals(1, snapshot.attempts.size)
        assertTrue(coordinator.acceptCurrent("accept") is AcceptWidgetAuthoringStageResult.FeasibilityStopped)
        assertTrue(snapshot.checkpoints.isEmpty())
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
            "{\"outcome\":\"unachievable\",\"message\":\"No tools\",\"periodicIntervalHours\":null,\"toolIds\":[]}"
    }
}
