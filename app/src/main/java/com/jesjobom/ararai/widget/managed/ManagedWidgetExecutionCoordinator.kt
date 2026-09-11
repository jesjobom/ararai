package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.tools.ApplicationToolRejection
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetExecutionGrant
import com.jesjobom.ararai.widget.runtime.WidgetExecutionResult
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCodec
import com.jesjobom.ararai.widget.runtime.WidgetProgram
import com.jesjobom.ararai.widget.runtime.WidgetProgramCapabilities
import com.jesjobom.ararai.widget.runtime.WidgetProgramParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramValidationResult
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeCoordinator
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeFailureCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.math.BigDecimal

internal data class ManagedWidgetProgramResult(
    val execution: WidgetExecutionResult,
    val observations: List<NewWidgetObservation> = emptyList(),
) {
    init {
        require(execution is WidgetExecutionResult.Success || observations.isEmpty())
    }
}

internal fun interface ManagedWidgetProgramExecutor {
    suspend fun execute(
        program: WidgetProgram,
        grant: WidgetExecutionGrant,
        context: WidgetRuntimeContext,
        stateJson: String,
    ): ManagedWidgetProgramResult
}

internal class RuntimeManagedWidgetProgramExecutor(
    private val runtime: WidgetRuntimeCoordinator,
) : ManagedWidgetProgramExecutor {
    override suspend fun execute(
        program: WidgetProgram,
        grant: WidgetExecutionGrant,
        context: WidgetRuntimeContext,
        stateJson: String,
    ): ManagedWidgetProgramResult = ManagedWidgetProgramResult(
        runtime.execute(program, grant, context, stateJson),
    )
}

internal enum class ManagedWidgetExecutionStatus { Completed, Coalesced, Disabled, Missing, Discarded }

internal class ManagedWidgetExecutionCoordinator(
    private val repository: ManagedWidgetRepository,
    private val executor: ManagedWidgetProgramExecutor,
    private val contextProvider: () -> WidgetRuntimeContext,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun execute(widgetId: String): ManagedWidgetExecutionStatus = when (
        val acquisition = repository.acquireRunLease(widgetId)
    ) {
        is ManagedWidgetLeaseResult.Acquired -> executeAcquired(acquisition.lease)
        ManagedWidgetLeaseResult.Busy -> ManagedWidgetExecutionStatus.Coalesced
        ManagedWidgetLeaseResult.Disabled -> ManagedWidgetExecutionStatus.Disabled
        ManagedWidgetLeaseResult.Missing -> ManagedWidgetExecutionStatus.Missing
    }

    private suspend fun executeAcquired(lease: ManagedWidgetRunLease): ManagedWidgetExecutionStatus = try {
        executeOwned(lease)
    } catch (cancelled: CancellationException) {
        withContext(NonCancellable) {
            repository.completeRun(
                lease,
                failureCompletion(
                    lease = lease,
                    outcome = WidgetRunOutcome.Cancelled,
                    diagnostic = WidgetRunDiagnostic.Cancelled,
                ),
            )
        }
        throw cancelled
    }

    private suspend fun executeOwned(lease: ManagedWidgetRunLease): ManagedWidgetExecutionStatus = when (
        val validation = WidgetProgramParser.parse(lease.program.manifestJson, lease.program.source)
    ) {
        is WidgetProgramValidationResult.Invalid -> commit(
            lease,
            failureCompletion(
                lease,
                outcome = WidgetRunOutcome.ControlledFailure,
                diagnostic = validation.code.toDiagnostic(),
                disableExecution = validation.code.disablesExecution(),
            ),
        )
        is WidgetProgramValidationResult.Valid -> executeValidated(lease, validation.program)
    }

    private suspend fun executeValidated(
        lease: ManagedWidgetRunLease,
        program: WidgetProgram,
    ): ManagedWidgetExecutionStatus {
        val stateJson = ManagedWidgetOwnedStateEncoder.encode(repository.listObservations(lease.widgetId))
        val effectiveGrant = program.manifest.capabilities.toEffectiveGrant()
        val result = try {
            executor.execute(program, effectiveGrant, contextProvider(), stateJson)
        } catch (_: RuntimeException) {
            null
        }
        return if (result == null) {
            commit(
                lease,
                failureCompletion(
                    lease = lease,
                    outcome = WidgetRunOutcome.ControlledFailure,
                    diagnostic = WidgetRunDiagnostic.RuntimeUnavailable,
                ),
            )
        } else {
            commitExecution(lease, result)
        }
    }

    private suspend fun commitExecution(
        lease: ManagedWidgetRunLease,
        result: ManagedWidgetProgramResult,
    ): ManagedWidgetExecutionStatus = when (val execution = result.execution) {
        is WidgetExecutionResult.Failure -> commit(
            lease,
            failureCompletion(
                lease = lease,
                outcome = if (execution.code == WidgetRuntimeFailureCode.Cancelled) {
                    WidgetRunOutcome.Cancelled
                } else {
                    WidgetRunOutcome.ControlledFailure
                },
                diagnostic = execution.code.toDiagnostic(),
                plannedToolIds = execution.plannedTools.map { it.id }.distinct(),
                disableExecution = execution.code.disablesExecution(),
            ),
        )
        is WidgetExecutionResult.Success -> {
            val toolDiagnostic = execution.outcomes.mapNotNull { it.rejection }.toDiagnostic()
            if (toolDiagnostic != null) {
                commit(
                    lease,
                    failureCompletion(
                        lease = lease,
                        outcome = if (toolDiagnostic == WidgetRunDiagnostic.Cancelled) {
                            WidgetRunOutcome.Cancelled
                        } else {
                            WidgetRunOutcome.ControlledFailure
                        },
                        diagnostic = toolDiagnostic,
                        plannedToolIds = execution.plannedTools.map { it.id }.distinct(),
                    ),
                )
            } else {
                commit(
                    lease,
                    ManagedWidgetRunCompletion(
                        completedAtMillis = completionTime(lease),
                        plannedToolIds = execution.plannedTools.map { it.id }.distinct(),
                        outcome = WidgetRunOutcome.Success,
                        diagnostic = null,
                        presentationJson = WidgetPresentationCodec.encode(execution.presentation),
                        observations = result.observations,
                    ),
                )
            }
        }
    }

    private suspend fun commit(
        lease: ManagedWidgetRunLease,
        completion: ManagedWidgetRunCompletion,
    ): ManagedWidgetExecutionStatus = when (repository.completeRun(lease, completion)) {
        ManagedWidgetCommitResult.Stored -> ManagedWidgetExecutionStatus.Completed
        ManagedWidgetCommitResult.Discarded -> ManagedWidgetExecutionStatus.Discarded
    }

    private fun failureCompletion(
        lease: ManagedWidgetRunLease,
        outcome: WidgetRunOutcome,
        diagnostic: WidgetRunDiagnostic,
        plannedToolIds: List<String> = emptyList(),
        disableExecution: Boolean = false,
    ) = ManagedWidgetRunCompletion(
        completedAtMillis = completionTime(lease),
        plannedToolIds = plannedToolIds,
        outcome = outcome,
        diagnostic = diagnostic,
        disableExecution = disableExecution,
    )

    private fun completionTime(lease: ManagedWidgetRunLease): Long = nowMillis().coerceAtLeast(lease.startedAtMillis)
}

internal object ManagedWidgetOwnedStateEncoder {
    fun encode(observations: List<WidgetObservation>): String {
        val selected = JsonArray()
        for (observation in observations.take(MAX_PROJECTED_OBSERVATIONS)) {
            selected.add(observation.toJson())
            val candidate = root(selected).toString()
            if (candidate.toByteArray(Charsets.UTF_8).size > MAX_PROJECTED_STATE_BYTES) {
                selected.remove(selected.size() - 1)
                break
            }
        }
        return StrictJson.canonical(root(selected))
    }

    private fun root(observations: JsonArray) = JsonObject().apply { add("observations", observations) }

    private fun WidgetObservation.toJson(): JsonObject = JsonObject().apply {
        addProperty("name", name)
        addProperty("observedAtMillis", observedAtMillis)
        when (val item = value) {
            is WidgetObservationValue.Text -> {
                addProperty("type", "text")
                add("value", JsonPrimitive(item.value))
            }
            is WidgetObservationValue.Number -> {
                addProperty("type", "number")
                add("value", JsonPrimitive(BigDecimal(item.value)))
            }
            is WidgetObservationValue.BooleanValue -> {
                addProperty("type", "boolean")
                add("value", JsonPrimitive(item.value))
            }
        }
    }

    private const val MAX_PROJECTED_OBSERVATIONS = 100
    private const val MAX_PROJECTED_STATE_BYTES = 64 * 1024
}

private fun WidgetProgramCapabilities.toEffectiveGrant() = WidgetExecutionGrant(
    tools = tools,
    runtimeValues = runtimeValues,
    presentation = presentation,
)

private fun WidgetRuntimeFailureCode.toDiagnostic(): WidgetRunDiagnostic = when (this) {
    WidgetRuntimeFailureCode.InvalidProgram -> WidgetRunDiagnostic.InvalidProgram
    WidgetRuntimeFailureCode.IncompatibleProgram -> WidgetRunDiagnostic.IncompatibleProgram
    WidgetRuntimeFailureCode.IntegrityMismatch -> WidgetRunDiagnostic.IntegrityMismatch
    WidgetRuntimeFailureCode.CapabilityDenied -> WidgetRunDiagnostic.CapabilityDenied
    WidgetRuntimeFailureCode.PolicyViolation -> WidgetRunDiagnostic.PolicyViolation
    WidgetRuntimeFailureCode.InvalidPlan -> WidgetRunDiagnostic.InvalidPlan
    WidgetRuntimeFailureCode.InvalidPresentation -> WidgetRunDiagnostic.InvalidPresentation
    WidgetRuntimeFailureCode.ScriptError -> WidgetRunDiagnostic.ScriptError
    WidgetRuntimeFailureCode.ResourceLimit -> WidgetRunDiagnostic.ResourceLimit
    WidgetRuntimeFailureCode.Cancelled -> WidgetRunDiagnostic.Cancelled
    WidgetRuntimeFailureCode.RuntimeUnavailable -> WidgetRunDiagnostic.RuntimeUnavailable
}

private fun WidgetRuntimeFailureCode.disablesExecution(): Boolean = when (this) {
    WidgetRuntimeFailureCode.InvalidProgram,
    WidgetRuntimeFailureCode.IncompatibleProgram,
    WidgetRuntimeFailureCode.IntegrityMismatch,
    WidgetRuntimeFailureCode.CapabilityDenied,
    WidgetRuntimeFailureCode.PolicyViolation,
    -> true
    WidgetRuntimeFailureCode.InvalidPlan,
    WidgetRuntimeFailureCode.InvalidPresentation,
    WidgetRuntimeFailureCode.ScriptError,
    WidgetRuntimeFailureCode.ResourceLimit,
    WidgetRuntimeFailureCode.Cancelled,
    WidgetRuntimeFailureCode.RuntimeUnavailable,
    -> false
}

private fun List<ApplicationToolRejection>.toDiagnostic(): WidgetRunDiagnostic? = when {
    isEmpty() -> null
    ApplicationToolRejection.Cancelled in this -> WidgetRunDiagnostic.Cancelled
    ApplicationToolRejection.TimedOut in this -> WidgetRunDiagnostic.ResourceLimit
    else -> WidgetRunDiagnostic.ToolUnavailable
}
