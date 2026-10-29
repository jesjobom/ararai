@file:Suppress("MaxLineLength", "ReturnCount", "TooManyFunctions", "LongMethod", "CyclomaticComplexMethod")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.WEB_SEARCH_CONTENT_TOOL_NAME
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

internal const val WIDGET_FEASIBILITY_OBJECTIVE =
    "Decide whether available tools can supply the requested data and the bounded runtime can perform the " +
        "required logic. Do not reject filtering or conditional display merely because collection_card is limited; " +
        "custom_source is available. Preserve every essential constraint. If required data is unavailable, " +
        "return unachievable or needs_clarification rather than substitute a different request."

internal const val WIDGET_ALGORITHM_OBJECTIVE =
    "Preserve user constraints in ordered steps. " +
        "Best/first web result: one web_search_content call and collection_card; provider order selects first. " +
        "Date-event year in title: one wikipedia_on_this_day call and collection_card; app appends year. " +
        "Use collection_list for several unmodified items, custom_source for other layouts or filtering. " +
        "Other post-result operations need dependent transform steps. " +
        "Runtime-conditioned queries may use collection modes; tool calls cannot read live results."

/** A single best/first web hit means the provider's highest-ranked item, not a model comparison. */
internal fun requestedRankedWebContentCard(
    prompt: WidgetAuthoringPrompt,
    feasibility: WidgetFeasibilityArtifact,
): Boolean = feasibility.tools.singleOrNull()?.capability?.id == WEB_SEARCH_CONTENT_TOOL_NAME &&
    RANKED_WEB_CONTENT_CARD_REQUEST.containsMatchIn(
        prompt.userInstruction.replace(QUOTED_WEB_QUERY, " "),
    )

private val QUOTED_WEB_QUERY = Regex("\"[^\"]*\"|'[^']*'")
private val RANKED_WEB_CONTENT_CARD_REQUEST =
    Regex("(?i)\\b(?:melhor|primeiro|best|first)\\s+(?:resultado|result)\\b|\\btop\\s*1\\b")

/** An event year is supplied by the date-event tool and can be appended without model-authored JS. */
internal fun requestedEventYearTitle(
    prompt: WidgetAuthoringPrompt,
    feasibility: WidgetFeasibilityArtifact,
): Boolean {
    if (feasibility.tools.singleOrNull()?.capability != WidgetToolCapability("wikipedia_on_this_day", 1)) return false
    val instruction = prompt.userInstruction.replace(QUOTED_WEB_QUERY, " ")
    if (EVENT_YEAR_TITLE_NEGATION.containsMatchIn(instruction)) return false
    return EVENT_YEAR_TITLE_ACTION.containsMatchIn(instruction) &&
        EVENT_YEAR_TITLE_YEAR.containsMatchIn(instruction) &&
        EVENT_YEAR_TITLE_FIELD.containsMatchIn(instruction)
}

private val EVENT_YEAR_TITLE_ACTION = Regex("(?i)\\b(?:adicione|acrescente|inclua|insira|coloque|mostre|add|append|include|put|show)\\b")
private val EVENT_YEAR_TITLE_YEAR = Regex("(?i)\\b(?:ano|year)\\b")
private val EVENT_YEAR_TITLE_FIELD = Regex("(?i)\\b(?:t[ií]tulo|title)\\b")
private val EVENT_YEAR_TITLE_NEGATION =
    Regex("(?i)\\b(?:(?:n[aã]o|do not|don't)\\s+(?:adicione|acrescente|inclua|insira|coloque|add|append|include|put)|(?:sem|without)\\s+(?:o\\s+|the\\s+)?(?:ano|year))\\b")

internal fun requestedWebResultsCount(prompt: WidgetAuthoringPrompt, feasibility: WidgetFeasibilityArtifact): Int? {
    if (feasibility.tools.none { it.capability.id == "web_search_top_results" }) return null
    val instruction = prompt.userInstruction
    if (!Regex("(?i)resultados|results|links").containsMatchIn(instruction)) return null
    val count = Regex("(?i)\\b(?:top|até|ate|up to|first|primeiros?)\\s*(\\d{1,2})\\b")
        .find(instruction)?.groupValues?.get(1)?.toIntOrNull()
    return count?.takeIf { it in 2..10 }
}

internal fun algorithmCaptureSchema(feasibility: WidgetFeasibilityArtifact, prompt: WidgetAuthoringPrompt? = null): String = JsonParser.parseString(WidgetAuthoringStageSchemas.algorithm).asJsonObject.apply {
    val properties = getAsJsonObject("parameters").getAsJsonObject("properties")
    if (feasibility.tools.none { it.capability.id == "wikipedia_pages" && it.capability.version == 2 }) {
        properties.getAsJsonObject("steps").getAsJsonObject("items")
            .getAsJsonObject("properties").remove("selectionMode")
    }
    if (prompt != null && requestedWebResultsCount(prompt, feasibility) != null) {
        properties.getAsJsonObject("renderCapability").add("enum", JsonArray().apply { add("collection_list") })
    } else if (prompt != null && requestedRankedWebContentCard(prompt, feasibility)) {
        properties.getAsJsonObject("renderCapability").add("enum", JsonArray().apply { add("collection_card") })
    } else if (prompt != null && requestedEventYearTitle(prompt, feasibility)) {
        properties.getAsJsonObject("renderCapability").add("enum", JsonArray().apply { add("collection_card") })
    }
}.toString()

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
        val availableTools = registry.authoringToolCapabilities(prompt.existingWidgetTools)

        onProgress(WidgetAuthoringProgress.AnalyzingFeasibility)
        val feasibility = captureValidated(
            model = model,
            stage = WidgetAuthoringStage.Feasibility,
            stageProgress = WidgetAuthoringProgress.AnalyzingFeasibility,
            toolName = SUBMIT_WIDGET_FEASIBILITY_TOOL,
            schema = WidgetAuthoringStageSchemas.feasibility,
            objective = WIDGET_FEASIBILITY_OBJECTIVE,
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
            schema = algorithmCaptureSchema(feasibility, prompt),
            objective = WIDGET_ALGORITHM_OBJECTIVE,
            baseContext = algorithmContext(prompt, feasibility),
            budget = budget,
            generationRecovery = generationRecovery,
            onProgress = onProgress,
        ) { raw ->
            when (val validated = stageValidators.algorithm(raw, feasibility, prompt)) {
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
                when (
                    val validated = stageValidators.callFunction(
                        raw,
                        step,
                        feasibility,
                        runtimeContext,
                        requestedWebResultsCount(prompt, feasibility),
                    )
                ) {
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
        val deterministicRender = WidgetCollectionListArtifact.rankedWebResults(algorithm)
            ?: WidgetCollectionCardArtifact.rankedWebContent(algorithm, prompt, feasibility)
            ?: WidgetCollectionCardArtifact.eventYearTitle(algorithm, prompt, feasibility)
            ?: WidgetCollectionCardArtifact.selectedWikipediaPage(algorithm)
        val renderFunction = if (deterministicRender != null) {
            when (
                val validated = stageValidators.render(
                    deterministicRender,
                    callFunctions + planFunction,
                    algorithm,
                    feasibility,
                    runtimeContext,
                )
            ) {
                is WidgetAuthoringArtifactValidation.Valid -> validated.artifact
                is WidgetAuthoringArtifactValidation.Invalid -> return WidgetAuthoringPipelineResult.StageFailed(
                    WidgetAuthoringStage.RenderFunction,
                    validated.code,
                )
            }
        } else {
            val renderRequest = renderRoundRequest(prompt, feasibility, algorithm, callFunctions)
            captureValidated(
                model = model,
                stage = WidgetAuthoringStage.RenderFunction,
                stageProgress = WidgetAuthoringProgress.GeneratingPresentation,
                toolName = renderRequest.toolName,
                schema = renderRequest.toolDescriptionJson,
                objective = renderRequest.objective,
                baseContext = renderRequest.contextJson,
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
        }
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
        addProperty(
            "renderModes",
            "collection_card shows one first or seeded-random item from one result array, with an optional bounded item-field title suffix. " +
                "collection_list shows up to ten unmodified items from one result array in order. " +
                "custom_source supports filtering, comparisons, and conditional display over returned data only. " +
                "If no tool supplies required data, report unachievable rather than substitute data.",
        )
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
                            listOf("id", "version", "displayName", "authoringSummary", "networkRequired").forEach { name ->
                                tool.get(name)?.let { add(name, it.deepCopy()) }
                            }
                            add("inputFields", tool.schemaFieldNames("inputSchema"))
                            add("outputFields", tool.schemaFieldNames("outputSchema"))
                        },
                    )
                }
            },
        )
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
        if (requestedRankedWebContentCard(prompt, feasibility)) {
            addProperty(
                "rankedSearchPolicy",
                "Best/first web result is results[0] in provider rank order. Use one web_search_content " +
                    "tool_call and collection_card; do not add a transform after the call.",
            )
        }
        if (requestedEventYearTitle(prompt, feasibility)) {
            addProperty(
                "eventYearTitlePolicy",
                "The event tool returns a year for each event. Use one wikipedia_on_this_day tool_call " +
                    "and collection_card without a transform step; the application appends the selected event's year to its title.",
            )
        }
        if (feasibility.tools.none { it.capability.id == "wikipedia_pages" && it.capability.version == 2 }) {
            addProperty(
                "selectionPolicy",
                "Do not set selectionMode on a tool call. For a random item from a returned array, " +
                    "select it after the tool result in custom_source; this does not change the tool arguments.",
            )
        }
        val widgetApi = JsonParser.parseString(prompt.apiContextJson).asJsonObject.deepCopy()
        widgetApi.remove("programApi")
        widgetApi.remove("wikipediaBehaviorGuidance")
        val selectedTools = feasibility.tools.map { it.capability }.toSet()
        val tools = JsonArray()
        widgetApi.getAsJsonArray("tools")?.forEach { item ->
            val descriptor = item.asJsonObject
            val capability = WidgetToolCapability(
                descriptor.get("id").asString,
                descriptor.get("version").asInt,
            )
            if (capability !in selectedTools) return@forEach
            val schema = descriptor.remove("outputSchema")?.asJsonObject
            if (schema != null) descriptor.add("resultFields", conciseResultFields(schema))
            tools.add(descriptor)
        }
        widgetApi.add("tools", tools)
        add("widgetApi", widgetApi)
        add(
            "renderCapabilities",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("id", WidgetRenderCapability.CollectionCard.wireName)
                        addProperty("summary", "One item from one tool-result array, selected first or seeded-random; fixed title, optional bounded title suffix field, body, link and fallback. No post-result filtering or conditional selection.")
                    },
                )
                add(
                    JsonObject().apply {
                        addProperty("id", WidgetRenderCapability.CollectionList.wireName)
                        addProperty("summary", "Up to ten unmodified items from one tool-result array, in order; fixed title, body, link and fallback. No post-result filtering.")
                    },
                )
                add(
                    JsonObject().apply {
                        addProperty("id", WidgetRenderCapability.CustomSource.wireName)
                        addProperty("summary", "Bounded deterministic JavaScript render function for filtering, comparisons, conditional selection, or other layouts over returned data only.")
                    },
                )
            },
        )
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
        if (step.tool?.id == "web_search_top_results") {
            requestedWebResultsCount(prompt, feasibility)?.let { count ->
                addProperty("resultCountContract", "Set limit=$count exactly. The widget must receive up to $count results.")
            }
        }
        if (step.selectionMode != null) {
            addProperty(
                "selectionContract",
                "The algorithm froze selectionMode=${step.selectionMode.wireName}. Return that exact selection " +
                    "in the wikipedia_pages tool call; seeded_random requires seed=runtime.seed, first omits seed. " +
                    "The tool returns only one page, so card selection cannot change which page was fetched.",
            )
        }
        val tool = requireNotNull(step.tool)
        val contract = JsonParser.parseString(prompt.apiContextJson).asJsonObject
            .getAsJsonArray("tools")
            .single { item ->
                item.asJsonObject.get("id").asString == tool.id &&
                    item.asJsonObject.get("version").asInt == tool.version
            }
        add("toolContract", contract.asJsonObject.deepCopy().apply { remove("outputSchema") })
        addProperty(
            "callFunctionReturn",
            "Return only the properties of toolContract.inputSchema as the function result. " +
                "Do not return alias, toolId, contractVersion, args, or an arguments wrapper; " +
                "the application builds the complete tool call.",
        )
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
        add("toolResults", renderToolResults(prompt, algorithm))
    },
)

internal fun renderRoundRequest(
    prompt: WidgetAuthoringPrompt,
    feasibility: WidgetFeasibilityArtifact,
    algorithm: WidgetAlgorithmArtifact,
    callFunctions: List<WidgetSourceFragmentArtifact>,
): WidgetAuthoringRoundRequest = when (algorithm.renderCapability) {
    WidgetRenderCapability.CollectionCard -> WidgetAuthoringRoundRequest(
        WidgetAuthoringStage.RenderFunction,
        SUBMIT_WIDGET_COLLECTION_CARD_TOOL,
        collectionCardSchema(prompt, algorithm),
        collectionCardObjective(),
        collectionCardContext(prompt, algorithm),
    )
    WidgetRenderCapability.CollectionList -> WidgetAuthoringRoundRequest(
        WidgetAuthoringStage.RenderFunction,
        SUBMIT_WIDGET_COLLECTION_LIST_TOOL,
        collectionListSchema(prompt, algorithm),
        collectionListObjective(),
        collectionListContext(prompt, algorithm),
    )
    else -> WidgetAuthoringRoundRequest(
        WidgetAuthoringStage.RenderFunction,
        SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
        WidgetAuthoringStageSchemas.sourceFragment(SUBMIT_WIDGET_RENDER_FUNCTION_TOOL),
        renderFunctionObjective(callFunctions),
        renderContext(prompt, feasibility, algorithm),
    )
}

internal fun collectionListContext(prompt: WidgetAuthoringPrompt, algorithm: WidgetAlgorithmArtifact): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("toolResults", renderToolResults(prompt, algorithm))
        addProperty(
            "fields",
            "Copy alias, items, title, optional body and url from toolResults. " +
                "These are property names, not display text. The application renders each returned item in order, up to ten.",
        )
    },
)

internal fun collectionListSchema(prompt: WidgetAuthoringPrompt, algorithm: WidgetAlgorithmArtifact): String = collectionSchema(prompt, algorithm, WidgetAuthoringStageSchemas.collectionList)

internal fun collectionCardContext(prompt: WidgetAuthoringPrompt, algorithm: WidgetAlgorithmArtifact): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("userInstruction", prompt.userInstruction)
        add("toolResults", renderToolResults(prompt, algorithm))
        addProperty(
            "selection",
            "first selects index 0; seeded_random selects a deterministic seeded index on each execution. " +
                "Choose the mode requested by the user.",
        )
        addProperty(
            "fields",
            "Copy alias, items, title, optional titleSuffixField, body, and url from toolResults alias/path/itemFields. " +
                "These are property names, never display text or sample values. Only fallback is literal text. " +
                "For example, itemFields ['text:string', 'canonicalUrl:string'] means title='text' and " +
                "url='canonicalUrl'. A string or integer titleSuffixField is appended in parentheses; " +
                "for an event year use titleSuffixField='year'. The application handles selection, failure and links.",
        )
    },
)

internal fun collectionCardSchema(prompt: WidgetAuthoringPrompt, algorithm: WidgetAlgorithmArtifact): String = collectionSchema(prompt, algorithm, WidgetAuthoringStageSchemas.collectionCard)

private fun collectionSchema(prompt: WidgetAuthoringPrompt, algorithm: WidgetAlgorithmArtifact, baseSchema: String): String {
    val results = renderToolResults(prompt, algorithm)
    val arrays = results.flatMap { result ->
        result.asJsonObject.getAsJsonArray("resultFields").filter { field ->
            field.asJsonObject.get("type").asString == "array"
        }
    }
    val itemFields = arrays.flatMap { field ->
        field.asJsonObject.getAsJsonArray("itemFields")?.map { it.asString } ?: emptyList()
    }
    val textFields = itemFields.filter { it.endsWith(":string") }.map { it.substringBeforeLast(':') }.distinct()
    val suffixFields = itemFields.filter { it.endsWith(":string") || it.endsWith(":integer") }
        .map { it.substringBeforeLast(':') }.distinct()
    val schema = JsonParser.parseString(baseSchema).asJsonObject
    val properties = schema.getAsJsonObject("parameters").getAsJsonObject("properties")
    fun restrict(name: String, values: List<String>) {
        if (values.isNotEmpty()) {
            properties.getAsJsonObject(name).add("enum", JsonArray().apply { values.forEach(::add) })
        }
    }
    restrict("alias", results.map { it.asJsonObject.get("alias").asString })
    restrict("items", arrays.map { it.asJsonObject.get("path").asString }.distinct())
    listOf("title", "body", "url").forEach { restrict(it, textFields) }
    if (properties.has("titleSuffixField")) restrict("titleSuffixField", suffixFields)
    return schema.toString()
}

private fun renderToolResults(prompt: WidgetAuthoringPrompt, algorithm: WidgetAlgorithmArtifact): JsonArray {
    val allTools = JsonParser.parseString(prompt.apiContextJson).asJsonObject.getAsJsonArray("tools")
    return JsonArray().apply {
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
                    add("resultFields", conciseResultFields(descriptor.getAsJsonObject("outputSchema")))
                },
            )
        }
    }
}

private fun renderApi(): JsonObject = JsonObject().apply {
    addProperty("entrypoint", "function render(runtime, outcomes, state) -> one presentation node")
    addProperty(
        "outcomes",
        "outcomes is a plain JavaScript object keyed by toolResults[].alias, not a Map; it has no get() method. " +
            "outcomes[alias] is {status:'success',payload:<tool result>} or {status:'failure',reason:<code>}. " +
            "Pass the entire outcomes object and alias string to runtime helpers. " +
            "Field paths are relative to payload.",
    )
    addProperty(
        "customSource",
        "For first/random selection call runtime.pickArrayItem(outcomes, alias, arrayPath, 'first' or " +
            "'seeded_random'); it returns {item,path} or null on failure/empty. Never use Math.random. " +
            "For comparisons check runtime.isSuccess(outcomes,alias), then " +
            "runtime.getArray(outcomes,alias,arrayPath), and use an indexed loop. " +
            "Return runtime.card('No results') for failure/empty. For success, call " +
            "runtime.card([title, body, runtime.linkFor(outcomes, alias, selectedPath+'.'+urlField, title)]). " +
            "card takes exactly ONE argument: a string or an array of display strings and linkFor nodes. " +
            "It returns the complete presentation node, not a mutable builder; never call " +
            "card.addText or card.addLinkFor. outcomes has no getArray method; use " +
            "runtime.getArray(outcomes, alias, arrayPath). Include both title and body/description when relevant. " +
            "Formatting a title must not replace a requested body or description. " +
            "card accepts display strings and linkFor nodes; do not create raw {type:'card'} or " +
            "{type:'https_link'} objects. linkFor supplies the exact URL and provenance path. " +
            "Do not call runtime.collectionCard for comparisons. No arrow functions or runtime.textNode.",
    )
    addProperty("state", "state.observations is a bounded array of this widget's typed observations")
    addProperty(
        "executionRules",
        "Use synchronous deterministic data-only JavaScript. No network, storage, imports, eval, async, timers, " +
            "host APIs, HTML, callbacks, or arbitrary endpoints.",
    )
}

private fun conciseResultFields(schema: JsonObject): JsonArray = JsonArray().also { fields ->
    fun collect(properties: JsonObject?, prefix: String, depth: Int) {
        if (properties == null || depth > 3) return
        properties.entrySet().forEach { (name, value) ->
            if (name == "kind" || name == "reason") return@forEach
            val field = value.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: return@forEach
            val path = if (prefix.isEmpty()) name else "$prefix.$name"
            val type = field.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: "unknown"
            when (type) {
                "object" -> collect(field.getAsJsonObject("properties"), path, depth + 1)
                else -> fields.add(
                    JsonObject().apply {
                        addProperty("path", path)
                        addProperty("type", type)
                        if (type == "array") {
                            val itemProperties = field.getAsJsonObject("items")?.getAsJsonObject("properties")
                            if (itemProperties != null) {
                                add(
                                    "itemFields",
                                    JsonArray().apply {
                                        itemProperties.entrySet().forEach { (itemName, itemValue) ->
                                            val itemType = itemValue.takeIf(JsonElement::isJsonObject)
                                                ?.asJsonObject?.get("type")?.takeIf(JsonElement::isJsonPrimitive)?.asString
                                                ?: "unknown"
                                            add("$itemName:$itemType")
                                        }
                                    },
                                )
                                val meanings = JsonObject().apply {
                                    itemProperties.entrySet().forEach { (itemName, itemValue) ->
                                        itemValue.takeIf(JsonElement::isJsonObject)?.asJsonObject
                                            ?.get("description")?.takeIf(JsonElement::isJsonPrimitive)
                                            ?.asString?.let { addProperty(itemName, it) }
                                    }
                                }
                                if (meanings.size() > 0) add("itemFieldMeanings", meanings)
                            }
                        }
                    },
                )
            }
        }
    }
    collect(schema.getAsJsonObject("properties"), "", 1)
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
    renderCapability?.let { addProperty("renderCapability", it.wireName) }
}

private fun WidgetAlgorithmStep.toJson(): JsonObject = JsonObject().apply {
    addProperty("id", id)
    addProperty("kind", kind.wireName)
    addProperty("objective", objective)
    add("dependsOn", JsonArray().apply { dependencies.forEach(::add) })
    add("toolId", tool?.id?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    add("contractVersion", tool?.version?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
    selectionMode?.let { addProperty("selectionMode", it.wireName) }
}

private fun WidgetDraftFailureCode.toPipelineFailure(): WidgetAuthoringStageFailureCode = when (this) {
    WidgetDraftFailureCode.InvalidProposal,
    WidgetDraftFailureCode.InvalidProgram,
    -> WidgetAuthoringStageFailureCode.InvalidSource
    WidgetDraftFailureCode.InvalidPlan -> WidgetAuthoringStageFailureCode.InvalidPlan
    WidgetDraftFailureCode.InvalidToolArguments -> WidgetAuthoringStageFailureCode.InvalidToolArguments
    WidgetDraftFailureCode.RuntimeUnavailable -> WidgetAuthoringStageFailureCode.RuntimeUnavailable
}
