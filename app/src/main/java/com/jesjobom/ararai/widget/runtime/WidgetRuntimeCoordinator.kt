package com.jesjobom.ararai.widget.runtime

import com.jesjobom.ararai.tools.ApplicationToolRejection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface WidgetExecutionResult {
    data class Success(
        val presentation: WidgetPresentationNode,
        val outcomes: List<WidgetToolOutcome>,
        val plannedTools: List<WidgetToolCapability>,
    ) : WidgetExecutionResult

    data class Failure(
        val code: WidgetRuntimeFailureCode,
        val plannedTools: List<WidgetToolCapability> = emptyList(),
    ) : WidgetExecutionResult
}

internal class WidgetRuntimeCoordinator(
    private val engine: WidgetJavaScriptEngine,
    private val toolExecutor: WidgetProgramToolExecutor,
) {
    @Suppress("ReturnCount")
    suspend fun execute(
        program: WidgetProgram,
        grant: WidgetExecutionGrant,
        context: WidgetRuntimeContext,
        stateJson: String,
    ): WidgetExecutionResult {
        val capabilities = program.manifest.capabilities
        if (
            !grant.tools.all { it in capabilities.tools } ||
            !grant.runtimeValues.all { it in capabilities.runtimeValues } ||
            !grant.presentation.all { it in capabilities.presentation }
        ) {
            return failure(WidgetRuntimeFailureCode.CapabilityDenied)
        }
        if (
            !capabilities.tools.all { it in grant.tools } ||
            !capabilities.runtimeValues.all { it in grant.runtimeValues } ||
            !capabilities.presentation.all { it in grant.presentation }
        ) {
            return failure(WidgetRuntimeFailureCode.CapabilityDenied)
        }
        val contextJson = runCatching { context.toJson(capabilities.runtimeValues) }
            .getOrElse { return failure(WidgetRuntimeFailureCode.InvalidProgram) }
        val normalizedState = runCatching { canonicalState(stateJson) }
            .getOrElse { return failure(WidgetRuntimeFailureCode.InvalidProgram) }

        val planResult = callPhase(
            program,
            program.manifest.entrypoints.plan,
            listOf(contextJson),
        )
        val planJson = when (planResult) {
            is WidgetScriptResult.Success -> planResult.outputJson
            is WidgetScriptResult.Failure -> return failure(planResult.code)
        }
        val plan = runCatching {
            WidgetPlanParser.parse(planJson, capabilities, grant, program.manifest.limits)
        }.getOrElse { return failure(WidgetRuntimeFailureCode.InvalidPlan) }

        val outcomes = mutableListOf<WidgetToolOutcome>()
        for (call in plan) {
            currentCoroutineContext().ensureActive()
            outcomes += executeTool(call)
        }
        currentCoroutineContext().ensureActive()
        val outcomesJson = runCatching { outcomes.toJson() }
            .getOrElse { return failure(WidgetRuntimeFailureCode.ResourceLimit, plan) }

        val renderResult = callPhase(
            program,
            program.manifest.entrypoints.render,
            listOf(contextJson, outcomesJson, normalizedState),
        )
        val renderJson = when (renderResult) {
            is WidgetScriptResult.Success -> renderResult.outputJson
            is WidgetScriptResult.Failure -> return failure(renderResult.code, plan)
        }
        val presentation = runCatching {
            WidgetPresentationParser.parse(
                renderJson,
                capabilities,
                grant,
                outcomes,
                program.manifest.limits,
            )
        }.getOrElse { return failure(WidgetRuntimeFailureCode.InvalidPresentation, plan) }
        return WidgetExecutionResult.Success(presentation, outcomes, plan.map(PlannedWidgetToolCall::tool))
    }

    private suspend fun callPhase(
        program: WidgetProgram,
        entrypoint: String,
        argumentsJson: List<String>,
    ): WidgetScriptResult = try {
        withTimeoutOrNull(program.manifest.limits.executionMillis + OUTER_TIMEOUT_GRACE_MILLIS) {
            engine.call(program.source, entrypoint, argumentsJson, program.manifest.limits)
        } ?: WidgetScriptResult.Failure(WidgetRuntimeFailureCode.ResourceLimit)
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
        WidgetScriptResult.Failure(WidgetRuntimeFailureCode.Cancelled)
    } catch (_: RuntimeException) {
        WidgetScriptResult.Failure(WidgetRuntimeFailureCode.RuntimeUnavailable)
    }

    private suspend fun executeTool(call: PlannedWidgetToolCall): WidgetToolOutcome = try {
        toolExecutor.execute(call).takeIf { it.alias == call.alias }
            ?: WidgetToolOutcome(call.alias, null, ApplicationToolRejection.Unavailable)
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
        WidgetToolOutcome(call.alias, null, ApplicationToolRejection.Cancelled)
    } catch (_: RuntimeException) {
        WidgetToolOutcome(call.alias, null, ApplicationToolRejection.Unavailable)
    }

    private fun failure(
        code: WidgetRuntimeFailureCode,
        plan: List<PlannedWidgetToolCall> = emptyList(),
    ) = WidgetExecutionResult.Failure(code, plan.map(PlannedWidgetToolCall::tool))

    private companion object {
        const val OUTER_TIMEOUT_GRACE_MILLIS = 250L
    }
}
