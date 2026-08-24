package com.jesjobom.ararai.reporting

import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DiagnosticErrorReportTest {
    @Test
    fun `creates a bounded allowlisted report without raw error or user content`() {
        val coordinator = coordinator()
        val raw = "Failed to parse tool calls: call:web_search{focus:velocidade da luz na água? token=secret}"

        coordinator.offer(IllegalArgumentException(raw), context())

        val state = coordinator.state.value as DiagnosticErrorReportState.AwaitingConsent
        val payload = state.payload
        assertEquals(DiagnosticErrorCategory.ToolCallParsing, payload.category)
        assertEquals("The local runtime could not parse a model tool call.", payload.exceptionSummary)
        assertFalse(payload.toCallableData().toString().contains("velocidade"))
        assertFalse(payload.toCallableData().toString().contains("secret"))
        assertEquals(listOf("calculator", "web_search"), payload.enabledToolNames)
        assertEquals(6144, payload.contextTokens)
    }

    @Test
    fun `suppresses duplicate presentations and clears on dismiss`() {
        val coordinator = coordinator()
        coordinator.offer(IllegalStateException("first"), context())
        val first = coordinator.state.value?.payload

        coordinator.offer(IllegalStateException("second"), context())

        assertEquals(first, coordinator.state.value?.payload)
        coordinator.dismiss()
        assertNull(coordinator.state.value)
    }

    @Test
    fun `creates a tool parsing report from a failed generation event`() {
        val coordinator = coordinator()

        coordinator.offerGenerationFailure(
            GenerationEvent.Failed(
                message = "Failed to parse tool calls: private protocol output",
                kind = GenerationFailureKind.ToolCallParsing,
            ),
            context(),
        )

        val payload = (coordinator.state.value as DiagnosticErrorReportState.AwaitingConsent).payload
        assertEquals(DiagnosticErrorCategory.ToolCallParsing, payload.category)
        assertEquals("GenerationFailure", payload.exceptionType)
        assertEquals(emptyList<String>(), payload.stackSummary)
        assertFalse(payload.toCallableData().toString().contains("private protocol output"))
    }

    @Test
    fun `ignores expected failed generation events`() {
        val coordinator = coordinator()

        coordinator.offerGenerationFailure(
            GenerationEvent.Failed(
                message = "Model is not loaded",
                kind = GenerationFailureKind.Expected,
            ),
            context(),
        )

        assertNull(coordinator.state.value)
    }

    @Test
    fun `makes one transport call and never retries a failure`() = runTest {
        var calls = 0
        val coordinator = coordinator(
            transport = DiagnosticErrorReportTransport {
                calls += 1
                false
            },
        )
        coordinator.offer(IllegalStateException("boom"), context())

        coordinator.submit()
        coordinator.submit()

        assertEquals(1, calls)
        assertTrue(coordinator.state.value is DiagnosticErrorReportState.Failed)
        coordinator.dismiss()
        assertNull(coordinator.state.value)
    }

    @Test
    fun `does not offer expected cancellation or network failures`() {
        val coordinator = coordinator()

        coordinator.offer(IOException("offline"), context())
        assertNull(coordinator.state.value)
        coordinator.offer(kotlinx.coroutines.CancellationException("cancelled"), context())
        assertNull(coordinator.state.value)
    }

    private fun coordinator(
        transport: DiagnosticErrorReportTransport = DiagnosticErrorReportTransport { true },
    ) = DiagnosticErrorReportCoordinator(
        transport = transport,
        reportIdProvider = { "123e4567-e89b-42d3-a456-426614174000" },
        clock = { 1_787_500_000_000 },
        appVersionProvider = { "test" },
        androidApiLevelProvider = { 36 },
        localeProvider = { "pt-BR" },
    )

    private fun context() = DiagnosticOperationContext(
        stage = "chat_generation",
        modelId = "gemma-4-e2b-it-litert-lm",
        runtime = "litert_lm",
        contextTokens = 6144,
        reasoningEnabled = true,
        enabledToolNames = setOf("web_search", "calculator"),
    )
}
