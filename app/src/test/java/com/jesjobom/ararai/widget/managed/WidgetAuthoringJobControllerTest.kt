package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetAuthoringJobControllerTest {

    @Test
    fun `runs a submitted job through progress to succeeded`() = runTest {
        val presenter = RecordingPresenter()
        val controller = controller(
            scope = this,
            deviceState = FakeDeviceState(WidgetAuthoringDeviceEvaluation.Acceptable),
            presenter = presenter,
            executor = { _, onProgress ->
                onProgress(WidgetAuthoringProgress.AnalyzingFeasibility)
                onProgress(WidgetAuthoringProgress.DesigningAlgorithm)
                WidgetAuthoringJobOutcome.Ready("draft")
            },
        )
        val submit = controller.submit(request())

        advanceUntilIdle()

        assertTrue(submit is WidgetAuthoringJobController.SubmitResult.Accepted)
        val final = controller.state.value
        assertTrue(final is WidgetAuthoringJobState.Succeeded<*>)
        assertEquals("draft", (final as WidgetAuthoringJobState.Succeeded<*>).value)
        assertFalse(controller.active)
        assertTrue(
            presenter.states.any {
                it is WidgetAuthoringJobState.Running &&
                    it.progress == WidgetAuthoringProgress.DesigningAlgorithm
            },
        )
    }

    @Test
    fun `refuses a second submit while a job is active`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val controller = controller(
            scope = this,
            deviceState = FakeDeviceState(WidgetAuthoringDeviceEvaluation.Acceptable),
            executor = { _, _ ->
                gate.await()
                WidgetAuthoringJobOutcome.Ready("ok")
            },
        )
        controller.submit(request())
        advanceUntilIdle()

        assertTrue(controller.active)
        val second = controller.submit(request())

        assertTrue(second is WidgetAuthoringJobController.SubmitResult.BusyWithActiveJob)
        controller.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `cancel stops the job and allows a new request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val controller = controller(
            scope = this,
            deviceState = FakeDeviceState(WidgetAuthoringDeviceEvaluation.Acceptable),
            executor = { _, _ ->
                gate.await()
                WidgetAuthoringJobOutcome.Ready("ok")
            },
        )
        controller.submit(request())
        advanceUntilIdle()

        assertTrue(controller.cancel())
        advanceUntilIdle()

        assertTrue(controller.state.value is WidgetAuthoringJobState.Cancelled)
        assertFalse(controller.active)
        assertTrue(controller.submit(request()) is WidgetAuthoringJobController.SubmitResult.Accepted)
        controller.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `defers while device state is unacceptable then runs`() = runTest {
        val presenter = RecordingPresenter()
        val controller = controller(
            scope = this,
            deviceState = FakeDeviceState(
                WidgetAuthoringDeviceEvaluation(
                    acceptable = false,
                    deferralReason = WidgetAuthoringDeferralReason.ThermalState,
                ),
                WidgetAuthoringDeviceEvaluation.Acceptable,
            ),
            presenter = presenter,
            executor = { _, _ -> WidgetAuthoringJobOutcome.Ready("ok") },
            deferral = WidgetAuthoringDeferralPolicy(
                maximumAttempts = 3,
                baseBackoffMillis = 10,
                maximumBackoffMillis = 40,
            ),
        )
        controller.submit(request())

        advanceUntilIdle()

        assertTrue(controller.state.value is WidgetAuthoringJobState.Succeeded<*>)
        assertTrue(
            presenter.states.any {
                it is WidgetAuthoringJobState.Deferred &&
                    it.reason == WidgetAuthoringDeferralReason.ThermalState &&
                    it.attempt == 1
            },
        )
    }

    @Test
    fun `fails after exhausting the deferral budget`() = runTest {
        val controller = controller(
            scope = this,
            deviceState = FakeDeviceState(
                WidgetAuthoringDeviceEvaluation(
                    acceptable = false,
                    deferralReason = WidgetAuthoringDeferralReason.LowMemory,
                ),
            ),
            executor = { _, _ -> WidgetAuthoringJobOutcome.Ready("ok") },
            deferral = WidgetAuthoringDeferralPolicy(
                maximumAttempts = 2,
                baseBackoffMillis = 10,
                maximumBackoffMillis = 20,
            ),
        )
        controller.submit(request())

        advanceUntilIdle()

        val final = controller.state.value
        assertTrue(final is WidgetAuthoringJobState.Failed)
        assertEquals(
            WidgetAuthoringJobFailureReason.DeferredBudgetExhausted,
            (final as WidgetAuthoringJobState.Failed).reason,
        )
        assertFalse(controller.active)
    }

    @Test
    fun `maps executor failure into failed state`() = runTest {
        val controller = controller(
            scope = this,
            deviceState = FakeDeviceState(WidgetAuthoringDeviceEvaluation.Acceptable),
            executor = { _, _ ->
                WidgetAuthoringJobOutcome.Failure(
                    WidgetAuthoringJobFailureReason.StageFailed,
                    detail = "FEASIBILITY|PROTOCOL_MISMATCH",
                )
            },
        )
        controller.submit(request())

        advanceUntilIdle()

        val final = controller.state.value
        assertTrue(final is WidgetAuthoringJobState.Failed)
        assertEquals(
            WidgetAuthoringJobFailureReason.StageFailed,
            (final as WidgetAuthoringJobState.Failed).reason,
        )
        assertEquals("FEASIBILITY|PROTOCOL_MISMATCH", final.detail)
    }

    private fun controller(
        scope: kotlinx.coroutines.CoroutineScope,
        deviceState: WidgetAuthoringDeviceState,
        presenter: WidgetAuthoringJobPresenter? = null,
        executor: suspend (
            request: WidgetAuthoringJobRequest,
            onProgress: (WidgetAuthoringProgress) -> Unit,
        ) -> WidgetAuthoringJobOutcome<String> = { _, _ -> WidgetAuthoringJobOutcome.Ready("ok") },
        deferral: WidgetAuthoringDeferralPolicy = WidgetAuthoringDeferralPolicy(
            maximumAttempts = 6,
            baseBackoffMillis = 10,
            maximumBackoffMillis = 160,
        ),
    ) = WidgetAuthoringJobController(
        scope = scope,
        executor = executor,
        deviceState = deviceState,
        presenter = presenter,
        deferral = deferral,
    )

    private fun request() = WidgetAuthoringJobRequest(
        model = LocalModel("e2b", "E2B", "/model"),
        inference = InferenceConfig(contextTokens = 2048, temperature = 0.7f, topP = 0.9f),
        instruction = "mostre um evento de hoje",
        widgetId = null,
    )

    private class FakeDeviceState(
        private vararg val evaluations: WidgetAuthoringDeviceEvaluation,
    ) : WidgetAuthoringDeviceState {
        private var index = 0

        override fun evaluate(): WidgetAuthoringDeviceEvaluation =
            evaluations.getOrElse(index) { evaluations.last() }.also { index += 1 }
    }

    private class RecordingPresenter : WidgetAuthoringJobPresenter {
        val states = mutableListOf<WidgetAuthoringJobState>()

        override fun onStateChanged(state: WidgetAuthoringJobState) {
            states += state
        }
    }
}
