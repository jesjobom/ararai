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
    private val onAttemptFailure: (WidgetAuthoringAttemptFailure) -> Unit = {},
) {
    private var activeArtifacts: MutableList<Any> = mutableListOf()
    private val stageValidators = WidgetAuthoringStageValidators(validator)

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
            WidgetAuthoringModelPreparationResult.RecoveryTimedOut -> return WidgetAuthoringPipelineResult.TimedOut
            WidgetAuthoringModelPreparationResult.Ready -> Unit
        }
        val budget = WidgetAuthoringAttemptBudget()
        val generationRecovery = WidgetAuthoringGenerationRecovery(modelController, model, inference)
        val availableTools = widgetContracts().keys

        onProgress(WidgetAuthoringProgress.AnalyzingFeasibility)
        val feasibility = captureValidated(
            model = model,
            stage = WidgetAuthoringStage.Feasibility,
            stageProgress = WidgetAuthoringProgress.AnalyzingFeasibility,
            toolName = SUBMIT_WIDGET_FEASIBILITY_TOOL,
            schema = WidgetAuthoringStageSchemas.feasibility,
            objective = "Decide feasibility and select the minimum fixed capability envelope for the user's widget.",
            baseContext = baseContext(prompt),
            budget = budget,
            generationRecovery = generationRecovery,
            onProgress = onProgress,
        ) { raw ->
            when (val validated = stageValidators.feasibility(raw, availableTools)) {
                is WidgetAuthoringArtifactValidation.Valid -> StageValidation.Valid(validated.artifact)
                is WidgetAuthoringArtifactValidation.Invalid -> StageValidation.Invalid(validated.code)
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
            stageProgress = WidgetAuthoringProgress.DesigningAlgorithm,
            toolName = SUBMIT_WIDGET_ALGORITHM_TOOL,
            schema = WidgetAuthoringStageSchemas.algorithm,
            objective = "Design an ordered typed algorithm inside the frozen capability envelope. Tool calls may not depend on live tool results.",
            baseContext = algorithmContext(prompt, feasibility),
            budget = budget,
            generationRecovery = generationRecovery,
            onProgress = onProgress,
        ) { raw ->
            when (val validated = stageValidators.algorithm(raw, feasibility)) {
                is WidgetAuthoringArtifactValidation.Valid -> StageValidation.Valid(validated.artifact)
                is WidgetAuthoringArtifactValidation.Invalid -> StageValidation.Invalid(validated.code)
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
                stageProgress = WidgetAuthoringProgress.GeneratingCall(index + 1, algorithm.toolCallSteps.size),
                toolName = SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
                schema = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_CALL_FUNCTION_TOOL),
                objective = callFunctionObjective(step, functionName),
                baseContext = callContext(prompt, feasibility, algorithm, step),
                budget = budget,
                generationRecovery = generationRecovery,
                onProgress = onProgress,
            ) { raw ->
                when (val validated = stageValidators.callFunction(raw, step, feasibility, runtimeContext)) {
                    is WidgetAuthoringArtifactValidation.Valid -> StageValidation.Valid(validated.artifact)
                    is WidgetAuthoringArtifactValidation.Invalid -> StageValidation.Invalid(validated.code)
                }
            }.valueOrReturn { return it }
            callFunctions += fragment
            activeArtifacts += fragment
        }

        onProgress(WidgetAuthoringProgress.GeneratingPlan)
        val planFunction = deterministicPlanArtifact(callFunctions, algorithm)
        when (
            val validated = stageValidators.plan(
                planFunction.toModelArtifactJson(),
                callFunctions,
                algorithm,
                feasibility,
                runtimeContext,
            )
        ) {
            is WidgetAuthoringArtifactValidation.Valid -> Unit
            is WidgetAuthoringArtifactValidation.Invalid -> return WidgetAuthoringPipelineResult.StageFailed(
                WidgetAuthoringStage.PlanFunction,
                validated.code,
            )
        }
        activeArtifacts += planFunction

        onProgress(WidgetAuthoringProgress.GeneratingPresentation)
        val renderFunction = captureValidated(
            model = model,
            stage = WidgetAuthoringStage.RenderFunction,
            stageProgress = WidgetAuthoringProgress.GeneratingPresentation,
            toolName = SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
            schema = WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_RENDER_FUNCTION_TOOL),
            objective = renderFunctionObjective(callFunctions),
            baseContext = renderContext(prompt, feasibility, algorithm),
            budget = budget,
            generationRecovery = generationRecovery,
            onProgress = onProgress,
        ) { raw ->
            when (
                val validated = stageValidators.render(
                    raw,
                    callFunctions + planFunction,
                    algorithm,
                    feasibility,
                    runtimeContext,
                )
            ) {
                is WidgetAuthoringArtifactValidation.Valid -> StageValidation.Valid(validated.artifact)
                is WidgetAuthoringArtifactValidation.Invalid -> StageValidation.Invalid(validated.code)
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
        stageProgress: WidgetAuthoringProgress,
        toolName: String,
        schema: String,
        objective: String,
        baseContext: String,
        budget: WidgetAuthoringAttemptBudget,
        generationRecovery: WidgetAuthoringGenerationRecovery,
        onProgress: (WidgetAuthoringProgress) -> Unit,
        validate: suspend (String) -> StageValidation<T>,
    ): CaptureResult<T> {
        var repairCode: WidgetAuthoringStageFailureCode? = null
        var rejectedArtifact: String? = null
        var attempt = 0
        while (budget.consumeGeneration(isRepair = repairCode != null)) {
            attempt++
            val roundProgress = if (repairCode == null) {
                stageProgress
            } else {
                WidgetAuthoringProgress.Repairing(stage, budget.repairCount())
            }
            generationRecovery.prepare(onProgress)?.let {
                return CaptureResult.Failure(it)
            }
            onProgress(roundProgress)
            val request = WidgetAuthoringRoundRequest(
                stage = stage,
                toolName = toolName,
                toolDescriptionJson = schema,
                objective = objective,
                contextJson = baseContext,
                repairCode = repairCode,
                rejectedArtifact = repairCode?.let {
                    repairArtifactForPrompt(it, rejectedArtifact, attempt)
                },
            )
            when (val round = modelController.capture(model, request)) {
                is WidgetAuthoringRoundResult.Captured -> when (val validated = validate(round.rawArgumentsJson)) {
                    is StageValidation.Valid -> return CaptureResult.Value(validated.value)
                    is StageValidation.Invalid -> {
                        recordAttemptFailure(stage, attempt, validated.code, round.rawArgumentsJson)
                        repairCode = validated.code
                        rejectedArtifact = round.rawArgumentsJson
                    }
                }
                WidgetAuthoringRoundResult.NoArtifact,
                is WidgetAuthoringRoundResult.Failed,
                -> {
                    recordAttemptFailure(stage, attempt, WidgetAuthoringStageFailureCode.MissingArtifact)
                    repairCode = WidgetAuthoringStageFailureCode.MissingArtifact
                    rejectedArtifact = "{}"
                }
                WidgetAuthoringRoundResult.TimedOut -> {
                    recordAttemptFailure(stage, attempt, WidgetAuthoringStageFailureCode.TimedOut)
                    repairCode = WidgetAuthoringStageFailureCode.TimedOut
                    rejectedArtifact = "{}"
                }
                WidgetAuthoringRoundResult.InputTooLarge -> {
                    recordAttemptFailure(stage, attempt, WidgetAuthoringStageFailureCode.ResourceLimit)
                    return CaptureResult.Failure(
                        WidgetAuthoringPipelineResult.StageFailed(
                            stage,
                            WidgetAuthoringStageFailureCode.ResourceLimit,
                        ),
                    )
                }
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

    private fun recordAttemptFailure(
        stage: WidgetAuthoringStage,
        attempt: Int,
        code: WidgetAuthoringStageFailureCode,
        rawArtifact: String? = null,
    ) {
        onAttemptFailure(
            WidgetAuthoringAttemptFailure(
                stage = stage,
                attempt = attempt,
                code = code,
                argumentBytes = rawArtifact?.toByteArray(Charsets.UTF_8)?.size?.toLong(),
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

private class WidgetAuthoringGenerationRecovery(
    private val modelController: WidgetAuthoringPipelineModelController,
    private val model: LocalModel,
    private val inference: InferenceConfig,
) {
    private var generationStarted = false

    suspend fun prepare(
        onProgress: (WidgetAuthoringProgress) -> Unit,
    ): WidgetAuthoringPipelineResult? {
        if (!generationStarted) {
            generationStarted = true
            return null
        }
        onProgress(WidgetAuthoringProgress.WaitingForDeviceRecovery)
        return when (
            modelController.recoverForNextGeneration(model, inference) {
                onProgress(WidgetAuthoringProgress.ReloadingModel)
            }
        ) {
            WidgetAuthoringModelPreparationResult.Ready -> null
            WidgetAuthoringModelPreparationResult.Ineligible -> WidgetAuthoringPipelineResult.ModelUnavailable
            WidgetAuthoringModelPreparationResult.LoadFailed -> WidgetAuthoringPipelineResult.ModelLoadFailed
            WidgetAuthoringModelPreparationResult.RecoveryTimedOut -> WidgetAuthoringPipelineResult.TimedOut
        }
    }
}

internal fun baseContext(prompt: WidgetAuthoringPrompt): String = diagnosticFeasibilityContext(prompt, compact = true)

internal fun diagnosticFeasibilityContext(
    prompt: WidgetAuthoringPrompt,
    compact: Boolean,
): String = if (!compact) {
    fullFeasibilityContext(prompt)
} else {
    compactFeasibilityContext(prompt)
}

private fun fullFeasibilityContext(prompt: WidgetAuthoringPrompt): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("widgetApi", JsonParser.parseString(prompt.apiContextJson))
    },
)

private fun compactFeasibilityContext(prompt: WidgetAuthoringPrompt): String {
    val api = JsonParser.parseString(prompt.apiContextJson).asJsonObject
    val compactApi = JsonObject().apply {
        listOf(
            "widgetApiVersion",
            "authoringTask",
            "supportedIntervalsHours",
            "supportedRuntimeValues",
            "supportedPresentation",
        ).forEach { name -> api.get(name)?.let { add(name, it.deepCopy()) } }
        api.getAsJsonObject("programApi")
            ?.get("randomSelection")
            ?.let { add("randomSelection", it.deepCopy()) }
        add(
            "tools",
            JsonArray().apply {
                api.getAsJsonArray("tools")?.forEach { element ->
                    val tool = element.asJsonObject
                    add(
                        JsonObject().apply {
                            listOf("id", "version", "displayName", "networkRequired").forEach { name ->
                                tool.get(name)?.let { add(name, it.deepCopy()) }
                            }
                            add("inputFields", tool.schemaFieldNames("inputSchema"))
                            add("outputFields", tool.schemaFieldNames("outputSchema"))
                        },
                    )
                }
            },
        )
        api.get("wikipediaBehaviorGuidance")?.let { add("wikipediaBehaviorGuidance", it.deepCopy()) }
        api.get("currentWidget")?.let { current ->
            add(
                "currentWidget",
                if (current.isJsonObject) {
                    current.asJsonObject.deepCopy().apply { remove("source") }
                } else {
                    current.deepCopy()
                },
            )
        }
    }
    return StrictJson.canonical(
        JsonObject().apply {
            addProperty("userInstruction", prompt.userInstruction)
            add("widgetApi", compactApi)
        },
    )
}

private fun JsonObject.schemaFieldNames(schemaName: String): JsonArray = JsonArray().apply {
    getAsJsonObject(schemaName)
        ?.getAsJsonObject("properties")
        ?.keySet()
        ?.sorted()
        ?.forEach(::add)
}

internal fun algorithmContext(
    prompt: WidgetAuthoringPrompt,
    feasibility: WidgetFeasibilityArtifact,
): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("feasibility", feasibility.toJson())
        add("widgetApi", JsonParser.parseString(prompt.apiContextJson))
    },
)

internal fun callContext(
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

internal fun planContext(
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

internal fun renderContext(
    prompt: WidgetAuthoringPrompt,
    feasibility: WidgetFeasibilityArtifact,
    algorithm: WidgetAlgorithmArtifact,
): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("runtimeApi", runtimeApi(feasibility.runtimeValues))
        add("renderApi", renderApi())
        add("algorithm", algorithm.toJson())
        add(
            "presentationCapabilities",
            JsonArray().apply { feasibility.presentation.map { it.wireName }.sorted().forEach(::add) },
        )
        add("presentationContract", renderPresentationContract())
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

private fun renderApi(): JsonObject = JsonObject().apply {
    addProperty("entrypoint", "function render(runtime, outcomes, state) -> one presentation node")
    addProperty("outcomesContainer", "plain JavaScript object; it is not a Map")
    addProperty(
        "outcomeAccess",
        "Read a result only with outcomes[alias], where alias is copied exactly from toolResults[].alias; " +
            "never call outcomes.get().",
    )
    addProperty(
        "outcomeShape",
        "A result is {status:'success',payload:<tool result>} or {status:'failure',reason:<code>}.",
    )
    addProperty(
        "aliasBinding",
        "Every outcome lookup and https_link.sourceAlias must use the same alias selected from toolResults[].alias.",
    )
    addProperty("state", "state.observations is a bounded array of this widget's typed observations")
    addProperty(
        "executionRules",
        "Use synchronous deterministic data-only JavaScript. No network, storage, imports, eval, async, timers, " +
            "host APIs, HTML, callbacks, or arbitrary endpoints.",
    )
}

private fun renderPresentationContract(): JsonObject = JsonObject().apply {
    addProperty("root", "{type:'card',child:N}")
    add(
        "containerNodes",
        JsonArray().apply {
            add("{type:'column',children:[N]}")
            add("{type:'row',children:[N]}")
        },
    )
    add(
        "contentNodes",
        JsonArray().apply {
            add("{type:'text',text:'...',tone:T}")
            add("{type:'value',text:'...',tone:T}")
            add("{type:'icon',name:'info'}")
            add("{type:'https_link',label:'...',url:U,sourceAlias:A,sourceField:P}")
        },
    )
    add(
        "tones",
        JsonArray().apply {
            listOf("neutral", "muted", "positive", "warning").forEach(::add)
        },
    )
    addProperty("outcomes", "Check status before payload; return a valid fallback for failure and empty arrays")
    addProperty("provenance", "Copy sourceAlias and sourceField from the exact tool result")
}

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
}

private fun WidgetDraftFailureCode.toPipelineFailure(): WidgetAuthoringStageFailureCode = when (this) {
    WidgetDraftFailureCode.InvalidProposal,
    WidgetDraftFailureCode.InvalidProgram,
    -> WidgetAuthoringStageFailureCode.InvalidSource
    WidgetDraftFailureCode.InvalidPlan -> WidgetAuthoringStageFailureCode.InvalidPlan
    WidgetDraftFailureCode.InvalidToolArguments -> WidgetAuthoringStageFailureCode.InvalidToolArguments
    WidgetDraftFailureCode.RuntimeUnavailable -> WidgetAuthoringStageFailureCode.RuntimeUnavailable
}
