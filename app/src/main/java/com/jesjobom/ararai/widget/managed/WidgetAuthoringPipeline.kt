@file:Suppress("MaxLineLength", "ReturnCount", "TooManyFunctions", "LongMethod", "CyclomaticComplexMethod")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface WidgetAuthoringPipelineResult {
    data class DraftReady(val draft: ValidatedWidgetDraft) : WidgetAuthoringPipelineResult
    data class Unachievable(val reason: String) : WidgetAuthoringPipelineResult
    data class NeedsClarification(val question: String) : WidgetAuthoringPipelineResult
    data object ModelUnavailable : WidgetAuthoringPipelineResult
    data object ModelLoadFailed : WidgetAuthoringPipelineResult
    data class StageFailed(
        val stage: WidgetAuthoringStage,
        val code: WidgetAuthoringStageFailureCode,
    ) : WidgetAuthoringPipelineResult
    data object TimedOut : WidgetAuthoringPipelineResult
}

internal class ManagedWidgetAuthoringPipeline(
    private val modelController: WidgetAuthoringPipelineModelController,
    private val validator: WidgetAuthoringPipelineValidator,
    private val draftBuilder: WidgetDraftBuilder,
    private val registry: ApplicationToolRegistry,
    private val totalTimeoutMillis: Long = WidgetAuthoringPipelinePolicy.TOTAL_TIMEOUT_MILLIS,
) {
    private var activeArtifacts: MutableList<Any> = mutableListOf()

    suspend fun generate(
        model: LocalModel?,
        inference: InferenceConfig?,
        prompt: WidgetAuthoringPrompt,
        runtimeContext: WidgetRuntimeContext,
        onProgress: (WidgetAuthoringProgress) -> Unit = {},
    ): WidgetAuthoringPipelineResult {
        clear()
        if (model == null || inference == null) return WidgetAuthoringPipelineResult.ModelUnavailable
        return try {
            withTimeoutOrNull(totalTimeoutMillis) {
                generateWithinDeadline(model, inference, prompt, runtimeContext, onProgress)
            } ?: WidgetAuthoringPipelineResult.TimedOut
        } catch (cancelled: CancellationException) {
            clear()
            throw cancelled
        } catch (_: RuntimeException) {
            clear()
            WidgetAuthoringPipelineResult.StageFailed(
                WidgetAuthoringStage.AssemblyValidation,
                WidgetAuthoringStageFailureCode.RuntimeUnavailable,
            )
        }.also { clear() }
    }

    fun clear() {
        activeArtifacts.clear()
        modelController.clear()
    }

    private suspend fun generateWithinDeadline(
        model: LocalModel,
        inference: InferenceConfig,
        prompt: WidgetAuthoringPrompt,
        runtimeContext: WidgetRuntimeContext,
        onProgress: (WidgetAuthoringProgress) -> Unit,
    ): WidgetAuthoringPipelineResult {
        when (modelController.prepare(model, inference)) {
            WidgetAuthoringModelPreparationResult.Ineligible -> return WidgetAuthoringPipelineResult.ModelUnavailable
            WidgetAuthoringModelPreparationResult.LoadFailed -> return WidgetAuthoringPipelineResult.ModelLoadFailed
            WidgetAuthoringModelPreparationResult.Ready -> Unit
        }
        val budget = WidgetAuthoringAttemptBudget()
        val availableTools = widgetContracts().keys

        onProgress(WidgetAuthoringProgress.AnalyzingFeasibility)
        val feasibility = captureValidated(
            model = model,
            stage = WidgetAuthoringStage.Feasibility,
            toolName = SUBMIT_WIDGET_FEASIBILITY_TOOL,
            schema = WidgetAuthoringStageSchemas.feasibility,
            objective = "Decide feasibility and select the minimum fixed capability envelope for the user's widget.",
            baseContext = baseContext(prompt),
            budget = budget,
            onProgress = onProgress,
        ) { raw ->
            when (val parsed = WidgetFeasibilityParser.parse(raw, availableTools)) {
                is WidgetFeasibilityParseResult.Valid -> StageValidation.Valid(parsed.artifact)
                is WidgetFeasibilityParseResult.Invalid -> StageValidation.Invalid(parsed.code)
            }
        }.valueOrReturn { return it }
        activeArtifacts += feasibility
        when (feasibility.outcome) {
            WidgetFeasibilityOutcome.Unachievable -> {
                return WidgetAuthoringPipelineResult.Unachievable(requireNotNull(feasibility.reason))
            }
            WidgetFeasibilityOutcome.NeedsClarification -> {
                return WidgetAuthoringPipelineResult.NeedsClarification(
                    requireNotNull(feasibility.clarificationQuestion),
                )
            }
            WidgetFeasibilityOutcome.Achievable -> Unit
        }

        onProgress(WidgetAuthoringProgress.DesigningAlgorithm)
        val algorithm = captureValidated(
            model = model,
            stage = WidgetAuthoringStage.Algorithm,
            toolName = SUBMIT_WIDGET_ALGORITHM_TOOL,
            schema = WidgetAuthoringStageSchemas.algorithm,
            objective = "Design an ordered typed algorithm inside the frozen capability envelope. Tool calls may not depend on live tool results.",
            baseContext = algorithmContext(prompt, feasibility),
            budget = budget,
            onProgress = onProgress,
        ) { raw ->
            when (val parsed = WidgetAlgorithmParser.parse(raw, feasibility)) {
                is WidgetAlgorithmParseResult.Valid -> StageValidation.Valid(parsed.artifact)
                is WidgetAlgorithmParseResult.Invalid -> StageValidation.Invalid(parsed.code)
            }
        }.valueOrReturn { return it }
        activeArtifacts += algorithm

        val callFunctions = mutableListOf<WidgetSourceFragmentArtifact>()
        algorithm.toolCallSteps.forEachIndexed { index, step ->
            onProgress(WidgetAuthoringProgress.GeneratingCall(index + 1, algorithm.toolCallSteps.size))
            val functionName = callFunctionName(step.id)
            val fragment = captureValidated(
                model = model,
                stage = WidgetAuthoringStage.CallFunction,
                toolName = SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
                schema = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_CALL_FUNCTION_TOOL),
                objective = "Generate exactly function $functionName(runtime). It must return alias '${step.id}', fixed tool '${step.tool?.id}' version ${step.tool?.version}, and contract-valid arguments. Use runtime.currentLocalDateTime() for current date/time.",
                baseContext = callContext(prompt, feasibility, algorithm, step),
                budget = budget,
                onProgress = onProgress,
            ) { raw ->
                when (
                    val parsed = WidgetSourceFragmentParser.parse(
                        raw,
                        expectedArtifactId = step.id,
                        expectedFunctionName = functionName,
                        expectedInputNames = listOf("runtime"),
                    )
                ) {
                    is WidgetSourceFragmentParseResult.Invalid -> StageValidation.Invalid(parsed.code)
                    is WidgetSourceFragmentParseResult.Valid -> {
                        val failure = validator.validateCallFunction(
                            parsed.artifact,
                            step,
                            feasibility,
                            runtimeContext,
                        )
                        if (failure == null) StageValidation.Valid(parsed.artifact) else StageValidation.Invalid(failure)
                    }
                }
            }.valueOrReturn { return it }
            callFunctions += fragment
            activeArtifacts += fragment
        }

        onProgress(WidgetAuthoringProgress.GeneratingPlan)
        val planFunction = captureValidated(
            model = model,
            stage = WidgetAuthoringStage.PlanFunction,
            toolName = SUBMIT_WIDGET_PLAN_FUNCTION_TOOL,
            schema = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_PLAN_FUNCTION_TOOL),
            objective = "Generate exactly function plan(runtime). Return one ordered array that calls each frozen call-function signature exactly once; do not reproduce or rewrite those functions.",
            baseContext = planContext(prompt, feasibility, algorithm, callFunctions),
            budget = budget,
            onProgress = onProgress,
        ) { raw ->
            when (
                val parsed = WidgetSourceFragmentParser.parse(
                    raw,
                    expectedArtifactId = "plan",
                    expectedFunctionName = "plan",
                    expectedInputNames = listOf("runtime"),
                )
            ) {
                is WidgetSourceFragmentParseResult.Invalid -> StageValidation.Invalid(parsed.code)
                is WidgetSourceFragmentParseResult.Valid -> {
                    val failure = validator.validatePlanFunction(
                        callFunctions,
                        parsed.artifact,
                        algorithm,
                        feasibility,
                        runtimeContext,
                    )
                    if (failure == null) StageValidation.Valid(parsed.artifact) else StageValidation.Invalid(failure)
                }
            }
        }.valueOrReturn { return it }
        activeArtifacts += planFunction

        onProgress(WidgetAuthoringProgress.GeneratingPresentation)
        val renderFunction = captureValidated(
            model = model,
            stage = WidgetAuthoringStage.RenderFunction,
            toolName = SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
            schema = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_RENDER_FUNCTION_TOOL),
            objective = "Generate exactly function render(runtime, outcomes, state). Return one allowed presentation for success, empty, and failure outcomes with link provenance when a link is shown.",
            baseContext = renderContext(prompt, feasibility, algorithm),
            budget = budget,
            onProgress = onProgress,
        ) { raw ->
            when (
                val parsed = WidgetSourceFragmentParser.parse(
                    raw,
                    expectedArtifactId = "render",
                    expectedFunctionName = "render",
                    expectedInputNames = listOf("runtime", "outcomes", "state"),
                )
            ) {
                is WidgetSourceFragmentParseResult.Invalid -> StageValidation.Invalid(parsed.code)
                is WidgetSourceFragmentParseResult.Valid -> {
                    val failure = validator.validateRenderFunction(
                        callFunctions + planFunction,
                        parsed.artifact,
                        algorithm,
                        feasibility,
                        runtimeContext,
                    )
                    if (failure == null) StageValidation.Valid(parsed.artifact) else StageValidation.Invalid(failure)
                }
            }
        }.valueOrReturn { return it }
        activeArtifacts += renderFunction

        onProgress(WidgetAuthoringProgress.ValidatingAssembly)
        val assembly = validator.assemble(feasibility, algorithm, callFunctions, planFunction, renderFunction)
        activeArtifacts += assembly
        validator.validateExactAssembly(assembly, runtimeContext)?.let { failure ->
            return WidgetAuthoringPipelineResult.StageFailed(WidgetAuthoringStage.AssemblyValidation, failure)
        }
        val draft = when (val built = draftBuilder.build(assembly.toProposal(), runtimeContext)) {
            is WidgetDraftBuildResult.Valid -> built.draft
            is WidgetDraftBuildResult.Invalid -> return WidgetAuthoringPipelineResult.StageFailed(
                WidgetAuthoringStage.AssemblyValidation,
                built.code.toPipelineFailure(),
            )
        }
        onProgress(WidgetAuthoringProgress.DraftReady)
        return WidgetAuthoringPipelineResult.DraftReady(draft)
    }

    private suspend fun <T> captureValidated(
        model: LocalModel,
        stage: WidgetAuthoringStage,
        toolName: String,
        schema: String,
        objective: String,
        baseContext: String,
        budget: WidgetAuthoringAttemptBudget,
        onProgress: (WidgetAuthoringProgress) -> Unit,
        validate: suspend (String) -> StageValidation<T>,
    ): CaptureResult<T> {
        var repairCode: WidgetAuthoringStageFailureCode? = null
        var rejectedArtifact: String? = null
        while (budget.consumeGeneration(isRepair = repairCode != null)) {
            if (repairCode != null) {
                onProgress(
                    WidgetAuthoringProgress.Repairing(
                        stage,
                        budget.repairCount(),
                    ),
                )
            }
            val request = WidgetAuthoringRoundRequest(
                stage = stage,
                toolName = toolName,
                toolDescriptionJson = schema,
                objective = objective,
                contextJson = baseContext,
                repairCode = repairCode,
                rejectedArtifact = rejectedArtifact,
            )
            when (val round = modelController.capture(model, request)) {
                is WidgetAuthoringRoundResult.Captured -> when (val validated = validate(round.rawArgumentsJson)) {
                    is StageValidation.Valid -> return CaptureResult.Value(validated.value)
                    is StageValidation.Invalid -> {
                        repairCode = validated.code
                        rejectedArtifact = round.rawArgumentsJson
                    }
                }
                WidgetAuthoringRoundResult.NoArtifact,
                is WidgetAuthoringRoundResult.Failed,
                -> {
                    repairCode = WidgetAuthoringStageFailureCode.MissingArtifact
                    rejectedArtifact = "{}"
                }
                WidgetAuthoringRoundResult.TimedOut -> {
                    repairCode = WidgetAuthoringStageFailureCode.TimedOut
                    rejectedArtifact = "{}"
                }
                WidgetAuthoringRoundResult.InputTooLarge -> return CaptureResult.Failure(
                    WidgetAuthoringPipelineResult.StageFailed(
                        stage,
                        WidgetAuthoringStageFailureCode.ResourceLimit,
                    ),
                )
            }
            if (budget.repairCount() >= WidgetAuthoringPipelinePolicy.MAX_REPAIRS) break
        }
        return CaptureResult.Failure(
            WidgetAuthoringPipelineResult.StageFailed(
                stage,
                repairCode ?: WidgetAuthoringStageFailureCode.ResourceLimit,
            ),
        )
    }

    private fun widgetContracts(): Map<WidgetToolCapability, ApplicationToolContract> = registry.descriptors()
        .filter { ApplicationToolConsumer.Widget in it.consumers }
        .associateBy { WidgetToolCapability(it.id, it.version) }

    private sealed interface StageValidation<out T> {
        data class Valid<T>(val value: T) : StageValidation<T>
        data class Invalid(val code: WidgetAuthoringStageFailureCode) : StageValidation<Nothing>
    }

    private sealed interface CaptureResult<out T> {
        data class Value<T>(val value: T) : CaptureResult<T>
        data class Failure(val result: WidgetAuthoringPipelineResult) : CaptureResult<Nothing>
    }

    private inline fun <T> CaptureResult<T>.valueOrReturn(onFailure: (WidgetAuthoringPipelineResult) -> Nothing): T = when (this) {
        is CaptureResult.Value -> value
        is CaptureResult.Failure -> onFailure(result)
    }
}

private fun baseContext(prompt: WidgetAuthoringPrompt): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("widgetApi", JsonParser.parseString(prompt.apiContextJson))
    },
)

private fun algorithmContext(
    prompt: WidgetAuthoringPrompt,
    feasibility: WidgetFeasibilityArtifact,
): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("feasibility", feasibility.toJson())
        add("widgetApi", JsonParser.parseString(prompt.apiContextJson))
    },
)

private fun callContext(
    prompt: WidgetAuthoringPrompt,
    feasibility: WidgetFeasibilityArtifact,
    algorithm: WidgetAlgorithmArtifact,
    step: WidgetAlgorithmStep,
): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("runtimeApi", runtimeApi(feasibility.runtimeValues))
        add("algorithm", algorithm.toJson())
        add("currentStep", step.toJson())
        val tool = requireNotNull(step.tool)
        val contract = JsonParser.parseString(prompt.apiContextJson).asJsonObject
            .getAsJsonArray("tools")
            .single { item ->
                item.asJsonObject.get("id").asString == tool.id &&
                    item.asJsonObject.get("version").asInt == tool.version
            }
        add("toolContract", contract.deepCopy())
    },
)

private fun planContext(
    prompt: WidgetAuthoringPrompt,
    feasibility: WidgetFeasibilityArtifact,
    algorithm: WidgetAlgorithmArtifact,
    callFunctions: List<WidgetSourceFragmentArtifact>,
): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("runtimeApi", runtimeApi(feasibility.runtimeValues))
        add("algorithm", algorithm.toJson())
        add(
            "frozenCallSignatures",
            JsonArray().apply {
                callFunctions.forEach { artifact ->
                    add(
                        JsonObject().apply {
                            addProperty("artifactId", artifact.artifactId)
                            addProperty("functionName", artifact.functionName)
                            add("inputNames", JsonArray().apply { artifact.inputNames.forEach(::add) })
                        },
                    )
                }
            },
        )
    },
)

private fun renderContext(
    prompt: WidgetAuthoringPrompt,
    feasibility: WidgetFeasibilityArtifact,
    algorithm: WidgetAlgorithmArtifact,
): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("runtimeApi", runtimeApi(feasibility.runtimeValues))
        add("algorithm", algorithm.toJson())
        add(
            "presentationCapabilities",
            JsonArray().apply { feasibility.presentation.map { it.wireName }.sorted().forEach(::add) },
        )
        val allTools = JsonParser.parseString(prompt.apiContextJson).asJsonObject.getAsJsonArray("tools")
        add(
            "toolResults",
            JsonArray().apply {
                algorithm.toolCallSteps.forEach { step ->
                    val tool = requireNotNull(step.tool)
                    val descriptor = allTools.single { item ->
                        item.asJsonObject.get("id").asString == tool.id &&
                            item.asJsonObject.get("version").asInt == tool.version
                    }.asJsonObject
                    add(
                        JsonObject().apply {
                            addProperty("alias", step.id)
                            addProperty("toolId", tool.id)
                            addProperty("version", tool.version)
                            add("outputSchema", descriptor.get("outputSchema").deepCopy())
                        },
                    )
                }
            },
        )
    },
)

private fun runtimeApi(values: Set<WidgetRuntimeValue>): JsonObject = JsonObject().apply {
    addProperty("apiVersion", 1)
    add("grants", JsonArray().apply { values.map { it.wireName }.sorted().forEach(::add) })
    addProperty("clock", "runtime.currentLocalDateTime() -> immutable {iso,year,month,day,hour,minute,second,timezone}")
    addProperty("language", "runtime.language when locale is granted")
    addProperty("seed", "runtime.seed and runtime.seededIndex(length) when seed is granted")
    addProperty("ambient", "Date, Math.random, network, storage, imports, async, callbacks and host objects are unavailable")
}

private fun WidgetFeasibilityArtifact.toJson(): JsonObject = JsonObject().apply {
    addProperty("protocolVersion", WIDGET_AUTHORING_PROTOCOL_VERSION)
    addProperty("outcome", outcome.wireName)
    addProperty("displayName", displayName)
    addProperty("enabled", enabled)
    add("periodicIntervalHours", periodicIntervalHours?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    add(
        "tools",
        JsonArray().apply {
            tools.forEach { selected ->
                add(
                    JsonObject().apply {
                        addProperty("id", selected.capability.id)
                        addProperty("version", selected.capability.version)
                        addProperty("purpose", selected.purpose)
                    },
                )
            }
        },
    )
    add("runtime", JsonArray().apply { runtimeValues.map { it.wireName }.sorted().forEach(::add) })
    add("presentation", JsonArray().apply { presentation.map { it.wireName }.sorted().forEach(::add) })
    add("reason", reason?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    add("clarificationQuestion", clarificationQuestion?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
}

private fun WidgetAlgorithmArtifact.toJson(): JsonObject = JsonObject().apply {
    addProperty("protocolVersion", WIDGET_AUTHORING_PROTOCOL_VERSION)
    add("steps", JsonArray().apply { steps.forEach { add(it.toJson()) } })
    addProperty("presentationObjective", presentationObjective)
}

private fun WidgetAlgorithmStep.toJson(): JsonObject = JsonObject().apply {
    addProperty("id", id)
    addProperty("kind", kind.wireName)
    addProperty("objective", objective)
    add("dependsOn", JsonArray().apply { dependencies.forEach(::add) })
    add("toolId", tool?.id?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    add("contractVersion", tool?.version?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    add("runtimeInputs", JsonArray().apply { runtimeInputs.map { it.wireName }.sorted().forEach(::add) })
}

private fun WidgetDraftFailureCode.toPipelineFailure(): WidgetAuthoringStageFailureCode = when (this) {
    WidgetDraftFailureCode.InvalidProposal,
    WidgetDraftFailureCode.InvalidProgram,
    -> WidgetAuthoringStageFailureCode.InvalidSource
    WidgetDraftFailureCode.InvalidPlan -> WidgetAuthoringStageFailureCode.InvalidPlan
    WidgetDraftFailureCode.InvalidToolArguments -> WidgetAuthoringStageFailureCode.InvalidToolArguments
    WidgetDraftFailureCode.RuntimeUnavailable -> WidgetAuthoringStageFailureCode.RuntimeUnavailable
}
