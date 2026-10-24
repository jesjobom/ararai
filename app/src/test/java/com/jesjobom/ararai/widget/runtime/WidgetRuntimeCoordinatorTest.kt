package com.jesjobom.ararai.widget.runtime

import com.google.gson.JsonObject
import com.jesjobom.ararai.tools.ApplicationToolRejection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetRuntimeCoordinatorTest {
    @Test
    fun `executes validated plan once and renders with canonical outcomes in a second phase`() = runTest {
        val engine = FakeEngine(
            WidgetScriptResult.Success(
                """[{"alias":"today","toolId":"weather_lookup","contractVersion":1,"arguments":{"city":"Toronto"}}]""",
            ),
            WidgetScriptResult.Success("""{"type":"text","text":"12 C","tone":"neutral"}"""),
        )
        val calls = mutableListOf<PlannedWidgetToolCall>()
        val executor = WidgetProgramToolExecutor { call ->
            calls += call
            WidgetToolOutcome(call.alias, JsonObject().apply { addProperty("temperature", 12) }, null)
        }

        val result = WidgetRuntimeCoordinator(engine, executor).execute(
            program(),
            grant(),
            WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-04T06:00:00", 42),
            "{\"count\":0}",
        )

        assertTrue(result is WidgetExecutionResult.Success)
        assertEquals(1, calls.size)
        assertEquals(2, engine.calls.size)
        assertEquals("plan", engine.calls[0].entrypoint)
        assertEquals("render", engine.calls[1].entrypoint)
        assertEquals(1, engine.calls[0].arguments.size)
        assertEquals(3, engine.calls[1].arguments.size)
        assertTrue(engine.calls[1].arguments[1].contains("\"today\""))
    }

    @Test
    fun `invalid plan is rejected as a whole before any tool call`() = runTest {
        val engine = FakeEngine(
            WidgetScriptResult.Success(
                """[
                    {"alias":"valid","toolId":"weather_lookup","contractVersion":1,"arguments":{}},
                    {"alias":"bad","toolId":"invented","contractVersion":1,"arguments":{}}
                ]
                """.trimIndent(),
            ),
        )
        var executionCount = 0

        val result = WidgetRuntimeCoordinator(engine) {
            executionCount++
            WidgetToolOutcome(it.alias, JsonObject(), null)
        }.execute(program(), grant(), context(), "{}")

        assertEquals(WidgetExecutionResult.Failure(WidgetRuntimeFailureCode.InvalidPlan), result)
        assertEquals(0, executionCount)
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `grant escalation or missing consent fails before script and tools`() = runTest {
        val engine = FakeEngine(WidgetScriptResult.Success("[]"))
        var executionCount = 0
        val coordinator = WidgetRuntimeCoordinator(engine) {
            executionCount++
            WidgetToolOutcome(it.alias, JsonObject(), null)
        }

        val missing = coordinator.execute(program(), grant().copy(tools = emptySet()), context(), "{}")
        val excessive = coordinator.execute(
            program(),
            grant().copy(tools = grant().tools + WidgetToolCapability("shell", 1)),
            context(),
            "{}",
        )

        assertEquals(WidgetExecutionResult.Failure(WidgetRuntimeFailureCode.CapabilityDenied), missing)
        assertEquals(WidgetExecutionResult.Failure(WidgetRuntimeFailureCode.CapabilityDenied), excessive)
        assertEquals(0, engine.calls.size)
        assertEquals(0, executionCount)
    }

    @Test
    fun `script failures expose only stable code`() = runTest {
        val engine = FakeEngine(WidgetScriptResult.Failure(WidgetRuntimeFailureCode.ScriptError))

        val result = WidgetRuntimeCoordinator(engine) {
            error("must not execute")
        }.execute(program(), grant(), context(), "{}")

        assertEquals(WidgetExecutionResult.Failure(WidgetRuntimeFailureCode.ScriptError), result)
        assertTrue(result.toString().contains("password=secret").not())
    }

    @Test
    fun `outer phase deadline maps non-cooperative adapter to stable resource failure`() = runTest {
        val engine = WidgetJavaScriptEngine { _, _, _, _ ->
            awaitCancellation()
        }

        val result = WidgetRuntimeCoordinator(engine) {
            error("must not execute")
        }.execute(program(), grant(), context(), "{}")

        assertEquals(WidgetExecutionResult.Failure(WidgetRuntimeFailureCode.ResourceLimit), result)
    }

    @Test
    fun `runtime adapter failures in either phase are sanitized and the coordinator recovers`() = runTest {
        var engineCalls = 0
        val engine = WidgetJavaScriptEngine { _, _, _, _ ->
            when (++engineCalls) {
                1 -> throw IllegalStateException("native-address=secret")
                2 -> WidgetScriptResult.Success(validPlan())
                3 -> throw IllegalStateException("render-source=secret")
                4 -> WidgetScriptResult.Success(validPlan())
                else -> WidgetScriptResult.Success(VALID_PRESENTATION)
            }
        }
        val coordinator = WidgetRuntimeCoordinator(engine) { call ->
            WidgetToolOutcome(call.alias, JsonObject(), null)
        }

        val planFailure = coordinator.execute(program(), grant(), context(), "{}")
        val renderFailure = coordinator.execute(program(), grant(), context(), "{}")
        val recovered = coordinator.execute(program(), grant(), context(), "{}")

        assertEquals(WidgetExecutionResult.Failure(WidgetRuntimeFailureCode.RuntimeUnavailable), planFailure)
        assertEquals(
            WidgetExecutionResult.Failure(WidgetRuntimeFailureCode.RuntimeUnavailable, listOf(tool)),
            renderFailure,
        )
        assertTrue(recovered is WidgetExecutionResult.Success)
        assertTrue(planFailure.toString().contains("secret").not())
        assertTrue(renderFailure.toString().contains("secret").not())
    }

    @Test
    fun `tool adapter failure becomes a bounded unavailable outcome and is not retried`() = runTest {
        val engine = FakeEngine(
            WidgetScriptResult.Success(validPlan()),
            WidgetScriptResult.Success(VALID_PRESENTATION),
        )
        var toolCalls = 0

        val result = WidgetRuntimeCoordinator(engine) {
            toolCalls++
            error("provider-token=secret")
        }.execute(program(), grant(), context(), "{}") as WidgetExecutionResult.Success

        assertEquals(1, toolCalls)
        assertEquals(ApplicationToolRejection.Unavailable, result.outcomes.single().rejection)
        assertEquals(2, engine.calls.size)
        assertTrue(engine.calls[1].arguments[1].contains("\"reason\":\"unavailable\""))
        assertTrue(result.toString().contains("secret").not())
    }

    @Test
    fun `aggregate tool outcomes are bounded before the render phase`() = runTest {
        val plan = """[
            {"alias":"first","toolId":"weather_lookup","contractVersion":1,"arguments":{}},
            {"alias":"second","toolId":"weather_lookup","contractVersion":1,"arguments":{}}
        ]
        """.trimIndent()
        val engine = FakeEngine(WidgetScriptResult.Success(plan))
        var toolCalls = 0

        val result = WidgetRuntimeCoordinator(engine) { call ->
            toolCalls++
            WidgetToolOutcome(
                call.alias,
                JsonObject().apply {
                    repeat(260) { index -> addProperty("value$index", index) }
                },
                null,
            )
        }.execute(program(), grant(), context(), "{}")

        assertEquals(
            WidgetExecutionResult.Failure(WidgetRuntimeFailureCode.ResourceLimit, listOf(tool, tool)),
            result,
        )
        assertEquals(2, toolCalls)
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `invalid render output cannot initiate another tool round`() = runTest {
        val engine = FakeEngine(
            WidgetScriptResult.Success(validPlan()),
            WidgetScriptResult.Success(validPlan()),
        )
        var toolCalls = 0

        val result = WidgetRuntimeCoordinator(engine) { call ->
            toolCalls++
            WidgetToolOutcome(call.alias, JsonObject(), null)
        }.execute(program(), grant(), context(), "{}")

        assertEquals(
            WidgetExecutionResult.Failure(WidgetRuntimeFailureCode.InvalidPresentation, listOf(tool)),
            result,
        )
        assertEquals(1, toolCalls)
        assertEquals(2, engine.calls.size)
    }

    @Test
    fun `cancellation during planning discards a late adapter result without dispatch`() = runTest {
        val engine = LateResultEngine()
        var toolCalls = 0
        val execution = async {
            WidgetRuntimeCoordinator(engine) { call ->
                toolCalls++
                WidgetToolOutcome(call.alias, JsonObject(), null)
            }.execute(program(), grant(), context(), "{}")
        }

        engine.started.await()
        execution.cancelAndJoin()
        engine.complete(WidgetScriptResult.Success(validPlan()))
        runCurrent()

        assertTrue(execution.isCancelled)
        assertEquals(0, toolCalls)
    }

    @Test
    fun `cancellation during render stops ownership after the single planned call`() = runTest {
        val renderStarted = CompletableDeferred<Unit>()
        var engineCalls = 0
        val engine = WidgetJavaScriptEngine { _, _, _, _ ->
            if (++engineCalls == 1) {
                WidgetScriptResult.Success(validPlan())
            } else {
                renderStarted.complete(Unit)
                awaitCancellation()
            }
        }
        var toolCalls = 0
        val execution = async {
            WidgetRuntimeCoordinator(engine) { call ->
                toolCalls++
                WidgetToolOutcome(call.alias, JsonObject(), null)
            }.execute(program(), grant(), context(), "{}")
        }

        renderStarted.await()
        execution.cancelAndJoin()

        assertTrue(execution.isCancelled)
        assertEquals(1, toolCalls)
        assertEquals(2, engineCalls)
    }

    @Test
    fun `cancellation during tool dispatch stops ownership without render or retry`() = runTest {
        val engine = FakeEngine(WidgetScriptResult.Success(validPlan()))
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val execution = async {
            WidgetRuntimeCoordinator(engine) { call ->
                calls++
                started.complete(Unit)
                awaitCancellation()
            }.execute(program(), grant(), context(), "{}")
        }

        started.await()
        execution.cancelAndJoin()

        assertEquals(1, calls)
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `coordinator owns no persistence worker model or UI dependency`() {
        val dependencyTypes = WidgetRuntimeCoordinator::class.java.declaredConstructors
            .flatMap { constructor -> constructor.parameterTypes.toList() }
            .map(Class<*>::getName)

        assertTrue(dependencyTypes.any { it.endsWith("WidgetJavaScriptEngine") })
        assertTrue(dependencyTypes.any { it.endsWith("WidgetProgramToolExecutor") })
        assertTrue(
            dependencyTypes.none { type ->
                listOf("WorkManager", "Repository", "LocalLlm", "ViewModel", "Compose").any(type::contains)
            },
        )
    }

    private data class Call(val entrypoint: String, val arguments: List<String>)

    private class FakeEngine(vararg results: WidgetScriptResult) : WidgetJavaScriptEngine {
        private val results = ArrayDeque(results.toList())
        val calls = mutableListOf<Call>()

        override suspend fun call(
            source: String,
            entrypoint: String,
            argumentsJson: List<String>,
            limits: WidgetRequestedLimits,
        ): WidgetScriptResult {
            calls += Call(entrypoint, argumentsJson)
            return results.removeFirst()
        }
    }

    private class LateResultEngine : WidgetJavaScriptEngine {
        val started = CompletableDeferred<Unit>()
        private var complete: ((WidgetScriptResult) -> Unit)? = null

        override suspend fun call(
            source: String,
            entrypoint: String,
            argumentsJson: List<String>,
            limits: WidgetRequestedLimits,
        ): WidgetScriptResult = suspendCancellableCoroutine { continuation ->
            complete = continuation::resume
            started.complete(Unit)
        }

        fun complete(result: WidgetScriptResult) {
            checkNotNull(complete).invoke(result)
        }
    }

    companion object {
        private val tool = WidgetToolCapability("weather_lookup", 1)

        private fun program(): WidgetProgram {
            val result = WidgetProgramParser.parse(
                WidgetProgramParserTest.validManifest(),
                WidgetProgramParserTest.SOURCE,
            )
            return (result as WidgetProgramValidationResult.Valid).program
        }

        private fun grant() = WidgetExecutionGrant(
            tools = setOf(tool),
            runtimeValues = WidgetRuntimeValue.entries.toSet(),
            presentation = WidgetPresentationCapability.entries.toSet(),
        )

        private fun context() = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-04T06:00:00", 42)

        private fun validPlan() = """[{
            "alias":"today","toolId":"weather_lookup",
            "contractVersion":1,"arguments":{}
        }]
        """.trimIndent()

        private const val VALID_PRESENTATION = """{"type":"text","text":"ok","tone":"neutral"}"""
    }
}
