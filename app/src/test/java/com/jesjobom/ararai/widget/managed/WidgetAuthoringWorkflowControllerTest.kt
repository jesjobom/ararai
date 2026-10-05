package com.jesjobom.ararai.widget.managed

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
class WidgetAuthoringWorkflowControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: SqliteManagedWidgetRepository
    private var nextId = 0

    @Before
    fun setUp() {
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
        store = SqliteManagedWidgetRepository(context, newId = { "controller-${++nextId}" })
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(MANAGED_WIDGET_DATABASE_NAME)
    }

    @Test
    fun `controller refuses overlapping attempt jobs`() = runTest {
        val repository = repository()
        val controller = ResumableWidgetAuthoringController(
            this,
            repository,
            WidgetAuthoringAttemptExecutor { _, _, _, _ -> awaitCancellation() },
        )
        controller.initialize()
        val session = controller.createSession(request())
        val command = command(session, "first")

        val first = controller.submitAttempt(command, EXECUTION)
        val second = controller.submitAttempt(command.copy(actionId = "second"), EXECUTION)

        assertTrue(first is SubmitWidgetAuthoringAttemptResult.Started)
        assertEquals(SubmitWidgetAuthoringAttemptResult.Busy, second)
        assertTrue(controller.cancelActiveAttempt())
    }

    @Test
    fun `duplicate action starts one executor and returns existing attempt`() = runTest {
        val repository = repository()
        var executions = 0
        val controller = ResumableWidgetAuthoringController(
            this,
            repository,
            WidgetAuthoringAttemptExecutor { _, _, _, _ ->
                executions++
                val active = requireNotNull(repository.activeSession.value)
                val attemptId = requireNotNull(active.session.activeAttemptId)
                repository.markAttemptRunning(active.session.id, attemptId)
                repository.completeAttempt(
                    CompleteWidgetAuthoringAttempt(
                        active.session.id,
                        attemptId,
                        WidgetAuthoringAttemptStatus.Failed,
                        "{}",
                        WidgetAuthoringStageFailureCode.InvalidFeasibilityShape,
                    ),
                )
            },
        )
        controller.initialize()
        val session = controller.createSession(request())
        val command = command(session, "same-action")

        val first = controller.submitAttempt(command, EXECUTION)
        val duplicate = controller.submitAttempt(command, EXECUTION)
        runCurrent()

        assertTrue(first is SubmitWidgetAuthoringAttemptResult.Started)
        assertTrue(duplicate is SubmitWidgetAuthoringAttemptResult.Duplicate)
        assertEquals(1, executions)
        assertEquals(
            (first as SubmitWidgetAuthoringAttemptResult.Started).attemptId,
            (duplicate as SubmitWidgetAuthoringAttemptResult.Duplicate).attemptId,
        )
    }

    @Test
    fun `controller recreation exposes interrupted durable session`() = runTest {
        val firstRepository = repository()
        firstRepository.initialize()
        val session = firstRepository.create(request())
        val started = firstRepository.startAttempt(
            command(session, "started"),
        ) as StartWidgetAuthoringAttemptResult.Started
        firstRepository.markAttemptRunning(session.session.id, started.attempt.id)
        val recreatedRepository = repository()
        val recreated = ResumableWidgetAuthoringController(
            this,
            recreatedRepository,
            WidgetAuthoringAttemptExecutor { _, _, _, _ -> error("not called") },
        )

        val restored = requireNotNull(recreated.initialize())

        assertEquals(WidgetAuthoringSessionStatus.AwaitingRetry, restored.session.status)
        assertEquals(WidgetAuthoringAttemptStatus.Interrupted, restored.attempts.single().status)
    }

    private fun repository() = DispatcherWidgetAuthoringWorkflowRepository(
        store,
        UnconfinedTestDispatcher(),
    )

    private fun request() = NewWidgetAuthoringSession(
        instruction = "Create status widget",
        targetWidgetId = null,
        modelId = MODEL.id,
        modelArtifactDigest = "a".repeat(64),
        inferenceConfigJson = "{}",
        toolContractDigest = "b".repeat(64),
    )

    private fun command(
        snapshot: WidgetAuthoringSessionSnapshot,
        actionId: String,
    ) = StartWidgetAuthoringAttempt(
        snapshot.session.id,
        snapshot.session.revision,
        actionId,
        WidgetAuthoringStageKey.Feasibility,
        WidgetAuthoringAttemptKind.Initial,
        authoringUpstreamDigest(snapshot, WidgetAuthoringStageKey.Feasibility),
    )

    private companion object {
        val MODEL = LocalModel("model", "Model", "/model.task")
        val EXECUTION = WidgetAuthoringAttemptExecution(
            MODEL,
            InferenceConfig(2_048, 512, 0.7f, 0.9f),
            WidgetAuthoringPrompt("Create status widget", "{\"tools\":[]}"),
            WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-27T12:00:00-04:00", 7),
        )
    }
}
