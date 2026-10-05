@file:Suppress("TooManyFunctions", "ReturnCount")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

internal sealed interface BeginWidgetAuthoringWorkflowResult {
    data class Created(val snapshot: WidgetAuthoringSessionSnapshot) : BeginWidgetAuthoringWorkflowResult
    data class Existing(val snapshot: WidgetAuthoringSessionSnapshot) : BeginWidgetAuthoringWorkflowResult
    data object MissingTarget : BeginWidgetAuthoringWorkflowResult
}

internal sealed interface RunWidgetAuthoringStageResult {
    data class Submitted(val result: SubmitWidgetAuthoringAttemptResult) : RunWidgetAuthoringStageResult
    data object ConfigurationChanged : RunWidgetAuthoringStageResult
    data object MissingTarget : RunWidgetAuthoringStageResult
}

internal sealed interface AcceptWidgetAuthoringStageResult {
    data class Advanced(val result: WidgetAuthoringMutationResult) : AcceptWidgetAuthoringStageResult
    data class PreviewReady(
        val result: WidgetAuthoringMutationResult,
        val draft: ValidatedWidgetDraft,
    ) : AcceptWidgetAuthoringStageResult

    data class AssemblyInvalid(val code: WidgetAuthoringStageFailureCode) : AcceptWidgetAuthoringStageResult
    data class FeasibilityStopped(val outcome: WidgetFeasibilityOutcome) : AcceptWidgetAuthoringStageResult
}

internal class WidgetAuthoringWorkflowCoordinator(
    private val controller: ResumableWidgetAuthoringController,
    private val widgetRepository: ManagedWidgetRepository,
    private val schedules: ManagedWidgetScheduleController,
    private val registry: ApplicationToolRegistry,
    private val assemblyBuilder: WidgetAuthoringAssemblyBuilder,
    private val runtimeContextProvider: () -> WidgetRuntimeContext,
) {
    val activeSession: StateFlow<WidgetAuthoringSessionSnapshot?> = controller.activeSession

    suspend fun begin(
        model: LocalModel,
        inference: InferenceConfig,
        modelArtifactDigest: String,
        instruction: String,
        targetWidgetId: String?,
    ): BeginWidgetAuthoringWorkflowResult {
        controller.activeSession.value?.let { return BeginWidgetAuthoringWorkflowResult.Existing(it) }
        if (targetWidgetId != null && existingTarget(targetWidgetId) == null) {
            return BeginWidgetAuthoringWorkflowResult.MissingTarget
        }
        return BeginWidgetAuthoringWorkflowResult.Created(
            controller.createSession(
                NewWidgetAuthoringSession(
                    instruction = instruction,
                    targetWidgetId = targetWidgetId,
                    modelId = model.id,
                    modelArtifactDigest = modelArtifactDigest,
                    inferenceConfigJson = WidgetAuthoringInferenceCodec.encode(inference),
                    toolContractDigest = authoringToolContractDigest(registry),
                ),
            ),
        )
    }

    suspend fun startCurrent(
        model: LocalModel,
        inference: InferenceConfig,
        modelArtifactDigest: String,
        actionId: String,
    ): RunWidgetAuthoringStageResult = submit(
        model,
        inference,
        modelArtifactDigest,
        actionId,
        requireNotNull(activeSession.value).session.currentStage,
        WidgetAuthoringAttemptKind.Initial,
    )

    suspend fun retryCurrent(
        model: LocalModel,
        inference: InferenceConfig,
        modelArtifactDigest: String,
        actionId: String,
    ): RunWidgetAuthoringStageResult = submit(
        model,
        inference,
        modelArtifactDigest,
        actionId,
        requireNotNull(activeSession.value).session.currentStage,
        WidgetAuthoringAttemptKind.Repair,
    )

    suspend fun reprocess(
        stage: WidgetAuthoringStageKey,
        model: LocalModel,
        inference: InferenceConfig,
        modelArtifactDigest: String,
        actionId: String,
    ): RunWidgetAuthoringStageResult = submit(
        model,
        inference,
        modelArtifactDigest,
        actionId,
        stage,
        WidgetAuthoringAttemptKind.Reprocess,
    )

    suspend fun acceptCurrent(actionId: String): AcceptWidgetAuthoringStageResult {
        val snapshot = requireNotNull(activeSession.value)
        WidgetAuthoringWorkflowStateMachine.requireAllowed(snapshot, WidgetAuthoringUserAction.Continue)
        val attempt = snapshot.attempts.last { attempt ->
            attempt.stage == snapshot.session.currentStage &&
                attempt.status == WidgetAuthoringAttemptStatus.Succeeded
        }
        val nextStage = nextStage(snapshot, attempt)
            ?: return acceptRenderOrStop(snapshot, attempt, actionId)
        return AcceptWidgetAuthoringStageResult.Advanced(
            controller.acceptCandidate(snapshot.acceptCommand(attempt, actionId, nextStage)),
        )
    }

    suspend fun preview(): WidgetAuthoringAssemblyResult {
        val snapshot = requireNotNull(activeSession.value)
        require(WidgetAuthoringUserAction.Confirm in WidgetAuthoringWorkflowStateMachine.allowedActions(snapshot))
        return assemblyBuilder.build(snapshot, runtimeContextProvider())
    }

    suspend fun confirm(mode: WidgetConfirmationMode): WidgetConfirmationResult {
        val snapshot = requireNotNull(activeSession.value)
        val draft = when (val preview = preview()) {
            is WidgetAuthoringAssemblyResult.Ready -> preview.draft
            is WidgetAuthoringAssemblyResult.Invalid -> return WidgetConfirmationResult.Failed
        }
        if (mode == WidgetConfirmationMode.CreateAndEnable && draft.blockedTools.isNotEmpty()) {
            return WidgetConfirmationResult.Blocked(draft.blockedTools)
        }
        val enabled = mode == WidgetConfirmationMode.CreateAndEnable
        val summary = draft.summary.copy(enabledIntent = enabled)
        val candidate = ConfirmedWidgetRevision(
            displayName = draft.proposal.displayName,
            enabled = enabled,
            periodicIntervalHours = draft.proposal.periodicIntervalHours,
            manifestJson = draft.program.canonicalManifestJson,
            source = draft.program.source,
            programDigest = draft.programDigest,
            consentDigest = consentDigest(draft.programDigest, summary),
        )
        return try {
            when (val result = controller.finalize(snapshot.session.id, snapshot.session.revision, candidate)) {
                is FinalizeWidgetAuthoringResult.Finalized -> {
                    schedules.reconcile(result.definition.id)
                    WidgetConfirmationResult.Confirmed(result.definition)
                }
                is FinalizeWidgetAuthoringResult.Stale,
                FinalizeWidgetAuthoringResult.Missing,
                -> WidgetConfirmationResult.Failed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            WidgetConfirmationResult.Failed
        }
    }

    fun cancelActiveAttempt(): Boolean = controller.cancelActiveAttempt()

    suspend fun discard(): Boolean {
        val snapshot = requireNotNull(activeSession.value)
        WidgetAuthoringWorkflowStateMachine.requireAllowed(snapshot, WidgetAuthoringUserAction.Discard)
        return controller.discard(snapshot.session.id, snapshot.session.revision)
    }

    private suspend fun submit(
        model: LocalModel,
        inference: InferenceConfig,
        modelArtifactDigest: String,
        actionId: String,
        stage: WidgetAuthoringStageKey,
        kind: WidgetAuthoringAttemptKind,
    ): RunWidgetAuthoringStageResult {
        val snapshot = requireNotNull(activeSession.value)
        if (!snapshot.configurationMatches(model, inference, modelArtifactDigest)) {
            return RunWidgetAuthoringStageResult.ConfigurationChanged
        }
        val prompt = prompt(snapshot) ?: return RunWidgetAuthoringStageResult.MissingTarget
        return RunWidgetAuthoringStageResult.Submitted(
            controller.submitAttempt(
                StartWidgetAuthoringAttempt(
                    sessionId = snapshot.session.id,
                    expectedSessionRevision = snapshot.session.revision,
                    actionId = actionId,
                    stage = stage,
                    kind = kind,
                    upstreamDigest = authoringUpstreamDigest(snapshot, stage),
                ),
                WidgetAuthoringAttemptExecution(model, inference, prompt, runtimeContextProvider()),
            ),
        )
    }

    private suspend fun acceptRenderOrStop(
        snapshot: WidgetAuthoringSessionSnapshot,
        attempt: WidgetAuthoringAttempt,
        actionId: String,
    ): AcceptWidgetAuthoringStageResult {
        if (attempt.stage == WidgetAuthoringStageKey.Feasibility) {
            val feasibility = parseFeasibility(requireNotNull(attempt.artifact))
            return AcceptWidgetAuthoringStageResult.FeasibilityStopped(feasibility.outcome)
        }
        require(attempt.stage == WidgetAuthoringStageKey.Render)
        val candidateSnapshot = snapshot.withCandidateCheckpoint(attempt)
        return when (val assembly = assemblyBuilder.build(candidateSnapshot, runtimeContextProvider())) {
            is WidgetAuthoringAssemblyResult.Invalid -> AcceptWidgetAuthoringStageResult.AssemblyInvalid(assembly.code)
            is WidgetAuthoringAssemblyResult.Ready -> AcceptWidgetAuthoringStageResult.PreviewReady(
                controller.acceptCandidate(snapshot.acceptCommand(attempt, actionId, null)),
                assembly.draft,
            )
        }
    }

    private fun nextStage(
        snapshot: WidgetAuthoringSessionSnapshot,
        attempt: WidgetAuthoringAttempt,
    ): WidgetAuthoringStageKey? = when (val stage = attempt.stage) {
        WidgetAuthoringStageKey.Feasibility -> {
            val feasibility = parseFeasibility(requireNotNull(attempt.artifact))
            if (feasibility.outcome == WidgetFeasibilityOutcome.Achievable) WidgetAuthoringStageKey.Algorithm else null
        }
        WidgetAuthoringStageKey.Algorithm -> callStages(snapshot, requireNotNull(attempt.artifact)).firstOrNull()
            ?: WidgetAuthoringStageKey.Plan
        is WidgetAuthoringStageKey.CallFunction -> {
            val calls = callStages(snapshot, acceptedArtifact(snapshot, WidgetAuthoringStageKey.Algorithm))
            val current = calls.indexOf(stage)
            require(current >= 0)
            calls.getOrNull(current + 1) ?: WidgetAuthoringStageKey.Plan
        }
        WidgetAuthoringStageKey.Plan -> WidgetAuthoringStageKey.Render
        WidgetAuthoringStageKey.Render -> null
        WidgetAuthoringStageKey.Assembly -> error("Assembly is local and cannot be accepted as a model candidate")
    }

    private fun callStages(
        snapshot: WidgetAuthoringSessionSnapshot,
        algorithmArtifact: String,
    ): List<WidgetAuthoringStageKey.CallFunction> {
        val feasibility = parseFeasibility(acceptedArtifact(snapshot, WidgetAuthoringStageKey.Feasibility))
        val algorithm = when (val parsed = WidgetAlgorithmParser.parse(algorithmArtifact, feasibility)) {
            is WidgetAlgorithmParseResult.Valid -> parsed.artifact
            is WidgetAlgorithmParseResult.Invalid -> error("Persisted algorithm candidate is invalid")
        }
        return algorithm.toolCallSteps.map { WidgetAuthoringStageKey.CallFunction(it.id) }
    }

    private fun parseFeasibility(raw: String): WidgetFeasibilityArtifact {
        val tools = registry.descriptors()
            .filter { ApplicationToolConsumer.Widget in it.consumers }
            .mapTo(mutableSetOf()) { com.jesjobom.ararai.widget.runtime.WidgetToolCapability(it.id, it.version) }
        return when (val parsed = WidgetFeasibilityParser.parse(raw, tools)) {
            is WidgetFeasibilityParseResult.Valid -> parsed.artifact
            is WidgetFeasibilityParseResult.Invalid -> error("Persisted feasibility candidate is invalid")
        }
    }

    private suspend fun prompt(snapshot: WidgetAuthoringSessionSnapshot): WidgetAuthoringPrompt? {
        val target = snapshot.session.targetWidgetId?.let { existingTarget(it) ?: return null }
        return WidgetAuthoringContextBuilder.build(
            userInstruction = snapshot.session.instruction,
            toolContracts = registry.descriptors(),
            currentDefinition = target?.first,
            currentRevision = target?.second,
        )
    }

    private suspend fun existingTarget(widgetId: String): Pair<ManagedWidgetDefinition, WidgetProgramRevision>? {
        val definition = widgetRepository.listDefinitions().firstOrNull { it.id == widgetId } ?: return null
        val revision = widgetRepository.activeRevision(widgetId) ?: return null
        return definition to revision
    }

    private fun WidgetAuthoringSessionSnapshot.configurationMatches(
        model: LocalModel,
        inference: InferenceConfig,
        modelArtifactDigest: String,
    ): Boolean = session.modelId == model.id &&
        session.modelArtifactDigest == modelArtifactDigest &&
        session.inferenceConfigJson == WidgetAuthoringInferenceCodec.encode(inference) &&
        session.toolContractDigest == authoringToolContractDigest(registry)
}

internal object WidgetAuthoringInferenceCodec {
    fun encode(config: InferenceConfig): String = StrictJson.canonical(
        JsonObject().apply {
            addProperty("contextTokens", config.contextTokens)
            addProperty("promptReserveTokens", config.promptReserveTokens)
            addProperty("temperature", config.temperature)
            addProperty("topP", config.topP)
            addProperty("topK", config.topK)
            addProperty("minP", config.minP)
            addProperty("repeatPenalty", config.repeatPenalty)
        },
    )

    fun decode(raw: String): InferenceConfig {
        val root = StrictJson.parse(raw).asJsonObject
        require(root.keySet() == INFERENCE_FIELDS)
        return InferenceConfig(
            contextTokens = root.get("contextTokens").asInt,
            promptReserveTokens = root.get("promptReserveTokens").asInt,
            temperature = root.get("temperature").asFloat,
            topP = root.get("topP").asFloat,
            topK = root.get("topK").asInt,
            minP = root.get("minP").asFloat,
            repeatPenalty = root.get("repeatPenalty").asFloat,
        )
    }

    private val INFERENCE_FIELDS = setOf(
        "contextTokens",
        "promptReserveTokens",
        "temperature",
        "topP",
        "topK",
        "minP",
        "repeatPenalty",
    )
}

internal fun authoringToolContractDigest(registry: ApplicationToolRegistry): String = authoringDigest(
    StrictJson.canonical(
        JsonArray().also { contracts ->
            registry.descriptors()
                .filter { ApplicationToolConsumer.Widget in it.consumers }
                .forEach { contract ->
                    contracts.add(
                        JsonObject().apply {
                            addProperty("id", contract.id)
                            addProperty("version", contract.version)
                            addProperty("displayName", contract.displayName)
                            addProperty("category", contract.category.name)
                            add(
                                "consumers",
                                JsonArray().also { consumers ->
                                    contract.consumers
                                        .map(ApplicationToolConsumer::name)
                                        .sorted()
                                        .forEach(consumers::add)
                                },
                            )
                            add("inputSchema", JsonParser.parseString(contract.inputSchemaJson))
                            add("outputSchema", JsonParser.parseString(contract.outputSchemaJson))
                        },
                    )
                }
        },
    ),
)

private fun WidgetAuthoringSessionSnapshot.acceptCommand(
    attempt: WidgetAuthoringAttempt,
    actionId: String,
    nextStage: WidgetAuthoringStageKey?,
) = AcceptWidgetAuthoringCandidate(
    sessionId = session.id,
    expectedSessionRevision = session.revision,
    actionId = actionId,
    attemptId = attempt.id,
    nextStage = nextStage,
)

private fun WidgetAuthoringSessionSnapshot.withCandidateCheckpoint(
    attempt: WidgetAuthoringAttempt,
): WidgetAuthoringSessionSnapshot {
    val artifact = requireNotNull(attempt.artifact)
    val checkpoint = WidgetAuthoringCheckpoint(
        sessionId = session.id,
        stage = attempt.stage,
        stageRevision = attempt.stageRevision,
        acceptedAttemptId = attempt.id,
        artifact = artifact,
        artifactDigest = requireNotNull(attempt.artifactDigest),
        upstreamDigest = attempt.upstreamDigest,
        status = WidgetAuthoringCheckpointStatus.Accepted,
        acceptedAtMillis = requireNotNull(attempt.completedAtMillis),
    )
    return copy(checkpoints = checkpoints.filterNot { it.stage == attempt.stage } + checkpoint)
}

private fun acceptedArtifact(
    snapshot: WidgetAuthoringSessionSnapshot,
    stage: WidgetAuthoringStageKey,
): String = snapshot.checkpoints.single {
    it.stage == stage && it.status == WidgetAuthoringCheckpointStatus.Accepted
}.artifact
