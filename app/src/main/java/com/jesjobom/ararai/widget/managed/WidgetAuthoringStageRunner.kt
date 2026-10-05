@file:Suppress("LongMethod", "MaxLineLength", "ReturnCount", "TooManyFunctions", "CyclomaticComplexMethod")

package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.LocalLlmRecoveryGate
import com.jesjobom.ararai.engine.LocalLlmRecoveryRequirement
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal fun interface WidgetAuthoringAttemptExecutor {
    suspend fun run(
        model: LocalModel,
        inference: InferenceConfig,
        prompt: WidgetAuthoringPrompt,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringSessionSnapshot
}

internal class WidgetAuthoringStageRunner(
    private val repository: WidgetAuthoringWorkflowRepository,
    private val modelController: WidgetAuthoringPipelineModelController,
    private val recoveryGate: LocalLlmRecoveryGate,
    private val validator: WidgetAuthoringPipelineValidator,
    private val registry: ApplicationToolRegistry,
) : WidgetAuthoringAttemptExecutor {
    private val stageValidators = WidgetAuthoringStageValidators(validator)

    override suspend fun run(
        model: LocalModel,
        inference: InferenceConfig,
        prompt: WidgetAuthoringPrompt,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringSessionSnapshot {
        val initial = requireNotNull(repository.activeSession.value) { "No active authoring session" }
        val attemptId = requireNotNull(initial.session.activeAttemptId) { "No active authoring attempt" }
        val attempt = initial.attempts.single { it.id == attemptId }
        var modelLoadAttempted = false
        return try {
            require(attempt.status in setOf(WidgetAuthoringAttemptStatus.Queued, WidgetAuthoringAttemptStatus.Deferred))
            require(initial.session.modelId == model.id) { "The selected authoring model changed" }
            require(attempt.upstreamDigest == authoringUpstreamDigest(initial, attempt.stage)) {
                "Accepted authoring dependencies changed"
            }
            repository.markAttemptDeferred(initial.session.id, attempt.id)
            if (attempt.stage == WidgetAuthoringStageKey.Plan) {
                repository.markAttemptRunning(initial.session.id, attempt.id)
                return repository.completeAttempt(
                    deterministicPlanCompletion(requireNotNull(repository.activeSession.value), attempt, runtimeContext),
                )
            }
            val current = requireNotNull(repository.activeSession.value)
            val plan = try {
                stagePlan(current, attempt, prompt, runtimeContext)
            } catch (_: RuntimeException) {
                return repository.completeAttempt(
                    attempt.failure(WidgetAuthoringStageFailureCode.InvalidSchema),
                )
            }
            if (!recoveryGate.awaitReady(LocalLlmRecoveryRequirement.forModel(model))) {
                return repository.completeAttempt(
                    attempt.failure(WidgetAuthoringStageFailureCode.DeviceRecoveryTimedOut),
                )
            }
            repository.markAttemptRunning(initial.session.id, attempt.id)
            modelLoadAttempted = true
            when (modelController.prepare(model, inference)) {
                WidgetAuthoringModelPreparationResult.Ready -> Unit
                WidgetAuthoringModelPreparationResult.Ineligible,
                WidgetAuthoringModelPreparationResult.LoadFailed,
                WidgetAuthoringModelPreparationResult.RecoveryTimedOut,
                -> return repository.completeAttempt(
                    attempt.failure(WidgetAuthoringStageFailureCode.RuntimeUnavailable),
                )
            }
            val completion = when (val result = modelController.capture(model, plan.request)) {
                is WidgetAuthoringRoundResult.Captured -> {
                    val failure = plan.validate(result.rawArgumentsJson)
                    if (failure == null) {
                        CompleteWidgetAuthoringAttempt(
                            initial.session.id,
                            attempt.id,
                            WidgetAuthoringAttemptStatus.Succeeded,
                            result.rawArgumentsJson,
                            null,
                        )
                    } else {
                        attempt.failure(failure, result.rawArgumentsJson)
                    }
                }
                WidgetAuthoringRoundResult.NoArtifact -> attempt.failure(
                    WidgetAuthoringStageFailureCode.MissingArtifact,
                )
                WidgetAuthoringRoundResult.TimedOut -> attempt.failure(WidgetAuthoringStageFailureCode.TimedOut)
                WidgetAuthoringRoundResult.InputTooLarge -> attempt.failure(
                    WidgetAuthoringStageFailureCode.ResourceLimit,
                )
                is WidgetAuthoringRoundResult.Failed -> attempt.failure(result.controlledFailure())
            }
            repository.completeAttempt(completion)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                val current = repository.activeSession.value
                if (current?.session?.activeAttemptId == attempt.id) {
                    repository.completeAttempt(
                        CompleteWidgetAuthoringAttempt(
                            initial.session.id,
                            attempt.id,
                            WidgetAuthoringAttemptStatus.Cancelled,
                            null,
                            null,
                        ),
                    )
                }
            }
            throw cancelled
        } catch (_: RuntimeException) {
            val current = repository.activeSession.value
            if (current?.session?.activeAttemptId == attempt.id) {
                repository.completeAttempt(attempt.failure(WidgetAuthoringStageFailureCode.RuntimeUnavailable))
            } else {
                requireNotNull(current)
            }
        } finally {
            withContext(NonCancellable) {
                if (modelLoadAttempted) runCatching { modelController.unload() }
                modelController.clear()
            }
        }
    }

    private suspend fun deterministicPlanCompletion(
        snapshot: WidgetAuthoringSessionSnapshot,
        attempt: WidgetAuthoringAttempt,
        runtimeContext: WidgetRuntimeContext,
    ): CompleteWidgetAuthoringAttempt {
        val accepted = AcceptedArtifacts(snapshot, registry)
        val feasibility = accepted.feasibility()
        val algorithm = accepted.algorithm(feasibility)
        val calls = accepted.callFunctions(algorithm)
        val raw = deterministicPlanArtifact(calls, algorithm).toModelArtifactJson()
        val failure = stageValidators.plan(raw, calls, algorithm, feasibility, runtimeContext).failureOrNull()
        return if (failure == null) {
            CompleteWidgetAuthoringAttempt(
                snapshot.session.id,
                attempt.id,
                WidgetAuthoringAttemptStatus.Succeeded,
                raw,
                null,
            )
        } else {
            attempt.failure(failure, raw)
        }
    }

    private fun stagePlan(
        snapshot: WidgetAuthoringSessionSnapshot,
        attempt: WidgetAuthoringAttempt,
        prompt: WidgetAuthoringPrompt,
        runtimeContext: WidgetRuntimeContext,
    ): StagePlan {
        val accepted = AcceptedArtifacts(snapshot, registry)
        val repair = snapshot.repairContext(attempt)
        return when (val stage = attempt.stage) {
            WidgetAuthoringStageKey.Feasibility -> StagePlan(
                WidgetAuthoringRoundRequest(
                    WidgetAuthoringStage.Feasibility,
                    SUBMIT_WIDGET_FEASIBILITY_TOOL,
                    WidgetAuthoringStageSchemas.feasibility,
                    "Decide feasibility and select the minimum fixed capability envelope for the user's widget.",
                    baseContext(prompt),
                    repair.first,
                    repair.second,
                ),
            ) { raw -> stageValidators.feasibility(raw, accepted.availableTools).failureOrNull() }
            WidgetAuthoringStageKey.Algorithm -> {
                val feasibility = accepted.feasibility()
                StagePlan(
                    WidgetAuthoringRoundRequest(
                        WidgetAuthoringStage.Algorithm,
                        SUBMIT_WIDGET_ALGORITHM_TOOL,
                        WidgetAuthoringStageSchemas.algorithm,
                        "Design an ordered typed algorithm inside the frozen capability envelope.",
                        algorithmContext(prompt, feasibility),
                        repair.first,
                        repair.second,
                    ),
                ) { raw -> stageValidators.algorithm(raw, feasibility).failureOrNull() }
            }
            is WidgetAuthoringStageKey.CallFunction -> {
                val feasibility = accepted.feasibility()
                val algorithm = accepted.algorithm(feasibility)
                val step = algorithm.toolCallSteps.single { it.id == stage.stepId }
                val functionName = callFunctionName(step.id)
                StagePlan(
                    WidgetAuthoringRoundRequest(
                        WidgetAuthoringStage.CallFunction,
                        SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
                        WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_CALL_FUNCTION_TOOL),
                        callFunctionObjective(step, functionName),
                        callContext(prompt, feasibility, algorithm, step),
                        repair.first,
                        repair.second,
                    ),
                ) { raw -> stageValidators.callFunction(raw, step, feasibility, runtimeContext).failureOrNull() }
            }
            WidgetAuthoringStageKey.Plan -> {
                val feasibility = accepted.feasibility()
                val algorithm = accepted.algorithm(feasibility)
                val calls = accepted.callFunctions(algorithm)
                StagePlan(
                    WidgetAuthoringRoundRequest(
                        WidgetAuthoringStage.PlanFunction,
                        SUBMIT_WIDGET_PLAN_FUNCTION_TOOL,
                        WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_PLAN_FUNCTION_TOOL),
                        planFunctionObjective(calls),
                        planContext(prompt, feasibility, algorithm, calls),
                        repair.first,
                        repair.second,
                    ),
                ) { raw ->
                    stageValidators.plan(raw, calls, algorithm, feasibility, runtimeContext).failureOrNull()
                }
            }
            WidgetAuthoringStageKey.Render -> {
                val feasibility = accepted.feasibility()
                val algorithm = accepted.algorithm(feasibility)
                val calls = accepted.callFunctions(algorithm)
                val plan = accepted.plan()
                StagePlan(
                    WidgetAuthoringRoundRequest(
                        WidgetAuthoringStage.RenderFunction,
                        SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
                        WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_RENDER_FUNCTION_TOOL),
                        renderFunctionObjective(calls),
                        renderContext(prompt, feasibility, algorithm),
                        repair.first,
                        repair.second,
                    ),
                ) { raw ->
                    stageValidators.render(
                        raw,
                        calls + plan,
                        algorithm,
                        feasibility,
                        runtimeContext,
                    ).failureOrNull()
                }
            }
            WidgetAuthoringStageKey.Assembly -> error("Assembly validation does not use the model")
        }
    }
}

internal fun authoringUpstreamDigest(
    snapshot: WidgetAuthoringSessionSnapshot,
    stage: WidgetAuthoringStageKey,
): String {
    val accepted = snapshot.checkpoints.filter { it.status == WidgetAuthoringCheckpointStatus.Accepted }
    val dependencies = when (stage) {
        WidgetAuthoringStageKey.Feasibility -> emptyList()
        WidgetAuthoringStageKey.Algorithm -> accepted.filter { it.stage == WidgetAuthoringStageKey.Feasibility }
        is WidgetAuthoringStageKey.CallFunction -> accepted.filter {
            it.stage == WidgetAuthoringStageKey.Feasibility || it.stage == WidgetAuthoringStageKey.Algorithm
        }
        WidgetAuthoringStageKey.Plan -> accepted.filter {
            it.stage == WidgetAuthoringStageKey.Feasibility ||
                it.stage == WidgetAuthoringStageKey.Algorithm ||
                it.stage is WidgetAuthoringStageKey.CallFunction
        }
        WidgetAuthoringStageKey.Render, WidgetAuthoringStageKey.Assembly -> accepted.filter {
            it.stage != stage && it.stage != WidgetAuthoringStageKey.Assembly
        }
    }
    return authoringDigest(
        dependencies.sortedBy { it.stage.wireValue }
            .joinToString(separator = "\n") { "${it.stage.wireValue}:${it.artifactDigest}" },
    )
}

internal sealed interface WidgetAuthoringAssemblyResult {
    data class Ready(val draft: ValidatedWidgetDraft) : WidgetAuthoringAssemblyResult
    data class Invalid(val code: WidgetAuthoringStageFailureCode) : WidgetAuthoringAssemblyResult
}

internal class WidgetAuthoringAssemblyBuilder(
    private val validator: WidgetAuthoringPipelineValidator,
    private val draftBuilder: WidgetDraftBuilder,
    private val registry: ApplicationToolRegistry,
) {
    suspend fun build(
        snapshot: WidgetAuthoringSessionSnapshot,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringAssemblyResult = try {
        require(snapshot.checkpoints.none { it.status == WidgetAuthoringCheckpointStatus.Stale })
        val accepted = AcceptedArtifacts(snapshot, registry)
        val feasibility = accepted.feasibility()
        val algorithm = accepted.algorithm(feasibility)
        val calls = accepted.callFunctions(algorithm)
        val plan = accepted.plan()
        val render = accepted.render()
        val assembly = validator.assemble(feasibility, algorithm, calls, plan, render)
        validator.validateExactAssembly(assembly, runtimeContext)?.let {
            return WidgetAuthoringAssemblyResult.Invalid(it)
        }
        when (val built = draftBuilder.build(assembly.toProposal(), runtimeContext)) {
            is WidgetDraftBuildResult.Valid -> WidgetAuthoringAssemblyResult.Ready(built.draft)
            is WidgetDraftBuildResult.Invalid -> WidgetAuthoringAssemblyResult.Invalid(built.code.toStageFailure())
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        WidgetAuthoringAssemblyResult.Invalid(WidgetAuthoringStageFailureCode.InvalidSchema)
    }
}

private data class StagePlan(
    val request: WidgetAuthoringRoundRequest,
    val validate: suspend (String) -> WidgetAuthoringStageFailureCode?,
)

private fun WidgetAuthoringArtifactValidation<*>.failureOrNull(): WidgetAuthoringStageFailureCode? = when (this) {
    is WidgetAuthoringArtifactValidation.Valid -> null
    is WidgetAuthoringArtifactValidation.Invalid -> code
}

private class AcceptedArtifacts(
    private val snapshot: WidgetAuthoringSessionSnapshot,
    registry: ApplicationToolRegistry,
) {
    val availableTools: Set<WidgetToolCapability> = registry.descriptors()
        .filter { ApplicationToolConsumer.Widget in it.consumers }
        .mapTo(mutableSetOf()) { WidgetToolCapability(it.id, it.version) }

    fun feasibility(): WidgetFeasibilityArtifact = when (
        val parsed = WidgetFeasibilityParser.parse(artifact(WidgetAuthoringStageKey.Feasibility), availableTools)
    ) {
        is WidgetFeasibilityParseResult.Valid -> parsed.artifact
        is WidgetFeasibilityParseResult.Invalid -> error("Persisted feasibility checkpoint is invalid")
    }

    fun algorithm(feasibility: WidgetFeasibilityArtifact): WidgetAlgorithmArtifact = when (
        val parsed = WidgetAlgorithmParser.parse(artifact(WidgetAuthoringStageKey.Algorithm), feasibility)
    ) {
        is WidgetAlgorithmParseResult.Valid -> parsed.artifact
        is WidgetAlgorithmParseResult.Invalid -> error("Persisted algorithm checkpoint is invalid")
    }

    fun callFunctions(
        algorithm: WidgetAlgorithmArtifact,
    ): List<WidgetSourceFragmentArtifact> = algorithm.toolCallSteps.map { step ->
        when (
            val parsed = WidgetSourceFragmentParser.parse(
                artifact(WidgetAuthoringStageKey.CallFunction(step.id)),
                step.id,
                callFunctionName(step.id),
                listOf("runtime"),
            )
        ) {
            is WidgetSourceFragmentParseResult.Valid -> parsed.artifact
            is WidgetSourceFragmentParseResult.Invalid -> error("Persisted call checkpoint is invalid")
        }
    }

    fun plan(): WidgetSourceFragmentArtifact = when (
        val parsed = WidgetSourceFragmentParser.parse(
            artifact(WidgetAuthoringStageKey.Plan),
            "plan",
            "plan",
            listOf("runtime"),
        )
    ) {
        is WidgetSourceFragmentParseResult.Valid -> parsed.artifact
        is WidgetSourceFragmentParseResult.Invalid -> error("Persisted plan checkpoint is invalid")
    }

    fun render(): WidgetSourceFragmentArtifact = when (
        val parsed = WidgetSourceFragmentParser.parse(
            artifact(WidgetAuthoringStageKey.Render),
            "render",
            "render",
            listOf("runtime", "outcomes", "state"),
        )
    ) {
        is WidgetSourceFragmentParseResult.Valid -> parsed.artifact
        is WidgetSourceFragmentParseResult.Invalid -> error("Persisted render checkpoint is invalid")
    }

    private fun artifact(stage: WidgetAuthoringStageKey): String = snapshot.checkpoints.single {
        it.stage == stage && it.status == WidgetAuthoringCheckpointStatus.Accepted
    }.artifact
}

internal fun callFunctionObjective(
    step: WidgetAlgorithmStep,
    functionName: String = callFunctionName(step.id),
): String {
    val tool = requireNotNull(step.tool)
    return "Generate exactly function $functionName(runtime) for frozen step ${step.id}. " +
        "It must return only the arguments object matching tool '${tool.id}' version ${tool.version}; " +
        "the application adds alias, toolId, and contractVersion. " +
        "Use runtime.currentLocalDateTime() for current date/time and runtime.language for language when granted."
}

internal fun planFunctionObjective(callFunctions: List<WidgetSourceFragmentArtifact>): String {
    val invocations = callFunctions.joinToString(", ") { "${it.functionName}(runtime)" }
    return "The application derives function plan(runtime) locally from [$invocations]. " +
        "No model artifact or protocol metadata is required for this stage."
}

internal fun renderFunctionObjective(
    callFunctions: List<WidgetSourceFragmentArtifact> = emptyList(),
): String {
    val aliasContract = if (callFunctions.isNotEmpty()) {
        "Copy aliases only from toolResults[].alias. outcomes is a plain object: use outcomes[alias], never " +
            "outcomes.get(); links reuse the selected alias as sourceAlias."
    } else {
        "Do not invent outcome aliases."
    }
    return "Submit only source declaring function render(runtime, outcomes, state). $aliasContract " +
        "Follow presentationContract exactly; handle failed and empty outcomes with a valid fallback; " +
        "never use type:'text/value'."
}

private fun WidgetAuthoringSessionSnapshot.repairContext(
    attempt: WidgetAuthoringAttempt,
): Pair<WidgetAuthoringStageFailureCode?, String?> {
    if (attempt.kind != WidgetAuthoringAttemptKind.Repair) return null to null
    val preceding = attempts.single {
        it.stage == attempt.stage &&
            it.stageRevision == attempt.stageRevision &&
            it.attemptNumber == attempt.attemptNumber - 1
    }
    val code = preceding.failureCode ?: WidgetAuthoringStageFailureCode.MissingArtifact
    return code to repairArtifactForPrompt(code, preceding.artifact, attempt.attemptNumber)
}

private fun WidgetAuthoringAttempt.failure(
    code: WidgetAuthoringStageFailureCode,
    artifact: String? = null,
): CompleteWidgetAuthoringAttempt = CompleteWidgetAuthoringAttempt(
    sessionId,
    id,
    WidgetAuthoringAttemptStatus.Failed,
    artifact,
    code,
)

private fun WidgetAuthoringRoundResult.Failed.controlledFailure(): WidgetAuthoringStageFailureCode = when (kind) {
    GenerationFailureKind.ToolCallParsing -> WidgetAuthoringStageFailureCode.InvalidSchema
    GenerationFailureKind.Expected -> WidgetAuthoringStageFailureCode.MissingArtifact
    GenerationFailureKind.Unexpected -> WidgetAuthoringStageFailureCode.RuntimeUnavailable
}

private fun WidgetDraftFailureCode.toStageFailure(): WidgetAuthoringStageFailureCode = when (this) {
    WidgetDraftFailureCode.InvalidProposal,
    WidgetDraftFailureCode.InvalidProgram,
    -> WidgetAuthoringStageFailureCode.InvalidSource
    WidgetDraftFailureCode.InvalidPlan -> WidgetAuthoringStageFailureCode.InvalidPlan
    WidgetDraftFailureCode.InvalidToolArguments -> WidgetAuthoringStageFailureCode.InvalidToolArguments
    WidgetDraftFailureCode.RuntimeUnavailable -> WidgetAuthoringStageFailureCode.RuntimeUnavailable
}
