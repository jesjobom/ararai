@file:Suppress("MaxLineLength", "ReturnCount")

package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.engine.EphemeralLocalLlmTool
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.ImmediateLocalLlmRecoveryGate
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.LocalLlmRecoveryGate
import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.engine.awaitReady
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal data class WidgetAuthoringRoundRequest(
    val stage: WidgetAuthoringStage,
    val toolName: String,
    val toolDescriptionJson: String,
    val objective: String,
    val contextJson: String,
    val repairCode: WidgetAuthoringStageFailureCode? = null,
    val rejectedArtifact: String? = null,
) {
    init {
        require(toolName in STAGE_TOOL_NAMES)
        require(objective.isNotBlank() && objective.length <= WidgetAuthoringPipelinePolicy.MAX_TEXT_CHARS)
        require(contextJson.toByteArray(Charsets.UTF_8).size <= ManagedWidgetPolicy.MAX_PROPOSAL_CONTEXT_BYTES)
        require(rejectedArtifact == null || rejectedArtifact.toByteArray(Charsets.UTF_8).size <= WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES)
        require(repairCode != null || rejectedArtifact == null)
    }
}

internal sealed interface WidgetAuthoringModelPreparationResult {
    data object Ready : WidgetAuthoringModelPreparationResult
    data object Ineligible : WidgetAuthoringModelPreparationResult
    data object LoadFailed : WidgetAuthoringModelPreparationResult
    data object RecoveryTimedOut : WidgetAuthoringModelPreparationResult
}

internal sealed interface WidgetAuthoringRoundResult {
    data class Captured(val rawArgumentsJson: String) : WidgetAuthoringRoundResult
    data object NoArtifact : WidgetAuthoringRoundResult
    data object TimedOut : WidgetAuthoringRoundResult
    data object InputTooLarge : WidgetAuthoringRoundResult
    data class Failed(val kind: GenerationFailureKind) : WidgetAuthoringRoundResult
}

internal enum class WidgetAuthoringRoundOutcome(val diagnosticWireName: String) {
    Captured("captured"),
    NoArtifact("no_artifact"),
    TimedOut("timed_out"),
    InputTooLarge("input_too_large"),
    GenerationRejected("generation_rejected"),
    ToolCallParsing("tool_call_parsing"),
    GenerationFailed("generation_failed"),
}

internal data class WidgetAuthoringRoundLifecycle(
    val stage: WidgetAuthoringStage,
    val isRepair: Boolean,
    val outcome: WidgetAuthoringRoundOutcome,
    val firstGenerationEventMillis: Long?,
    val toolCaptureMillis: Long?,
    val terminalEventMillis: Long?,
    val watchdogMillis: Long?,
    val returnMillis: Long,
    val cleanupOverrunMillis: Long?,
) {
    init {
        listOfNotNull(
            firstGenerationEventMillis,
            toolCaptureMillis,
            terminalEventMillis,
            watchdogMillis,
            cleanupOverrunMillis,
        ).forEach { require(it >= 0) }
        require(returnMillis >= 0)
        require((watchdogMillis == null) == (cleanupOverrunMillis == null))
        require((outcome == WidgetAuthoringRoundOutcome.TimedOut) == (watchdogMillis != null))
    }
}

internal class WidgetAuthoringPipelineModelController(
    private val engine: LocalLlmEngine,
    private val maxContextTokens: Int = MAX_WIDGET_AUTHORING_CONTEXT_TOKENS,
    private val generationTimeoutMillis: Long = WidgetAuthoringPipelinePolicy.PER_STAGE_TIMEOUT_MILLIS,
    private val onCapture: (toolName: String, argumentBytes: Int) -> Unit = { _, _ -> },
    private val onRoundRequest: (WidgetAuthoringRoundRequest) -> Unit = {},
    private val onRawCapture: (toolName: String, argumentsJson: String) -> Unit = { _, _ -> },
    private val onRoundLifecycle: (WidgetAuthoringRoundLifecycle) -> Unit = {},
    private val requireDeclaredProtocol: Boolean = true,
    private val recoveryGate: LocalLlmRecoveryGate = ImmediateLocalLlmRecoveryGate,
) {
    private var preparedModelId: String? = null

    suspend fun prepare(
        model: LocalModel,
        inference: InferenceConfig,
    ): WidgetAuthoringModelPreparationResult {
        if (requireDeclaredProtocol && !model.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1)) {
            return WidgetAuthoringModelPreparationResult.Ineligible
        }
        val authoringInference = authoringInference(inference)
        return try {
            engine.load(model, authoringInference)
            preparedModelId = model.id
            WidgetAuthoringModelPreparationResult.Ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            preparedModelId = null
            WidgetAuthoringModelPreparationResult.LoadFailed
        }
    }

    suspend fun recoverForNextGeneration(
        model: LocalModel,
        inference: InferenceConfig,
        onReloading: () -> Unit = {},
    ): WidgetAuthoringModelPreparationResult {
        check(preparedModelId == model.id) { "Authoring model must be prepared before recovery" }
        preparedModelId = null
        return try {
            val ready = engine.reloadWhenReady(model, authoringInference(inference)) {
                recoveryGate.awaitReady().also { if (it) onReloading() }
            }
            if (!ready) return WidgetAuthoringModelPreparationResult.RecoveryTimedOut
            preparedModelId = model.id
            WidgetAuthoringModelPreparationResult.Ready
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            WidgetAuthoringModelPreparationResult.LoadFailed
        }
    }

    suspend fun capture(
        model: LocalModel,
        request: WidgetAuthoringRoundRequest,
    ): WidgetAuthoringRoundResult {
        check(preparedModelId == model.id) { "Authoring model must be prepared before a stage" }
        if (request.estimatedInputChars() > maximumStageInputChars()) {
            return WidgetAuthoringRoundResult.InputTooLarge.also {
                onRoundLifecycle(
                    WidgetAuthoringRoundLifecycle(
                        stage = request.stage,
                        isRepair = request.repairCode != null,
                        outcome = WidgetAuthoringRoundOutcome.InputTooLarge,
                        firstGenerationEventMillis = null,
                        toolCaptureMillis = null,
                        terminalEventMillis = null,
                        watchdogMillis = null,
                        returnMillis = 0,
                        cleanupOverrunMillis = null,
                    ),
                )
            }
        }
        val started = monotonicMillis()
        val firstEventAt = AtomicLong(UNSET_ROUND_MILLIS)
        val captureAt = AtomicLong(UNSET_ROUND_MILLIS)
        val terminalAt = AtomicLong(UNSET_ROUND_MILLIS)
        val result = try {
            withTimeoutOrNull(generationTimeoutMillis) {
                captureRound(request, started, firstEventAt, captureAt, terminalAt)
            } ?: WidgetAuthoringRoundResult.TimedOut
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            WidgetAuthoringRoundResult.Failed(GenerationFailureKind.Unexpected)
        }
        val returnedAt = monotonicMillis() - started
        val timedOut = result == WidgetAuthoringRoundResult.TimedOut
        onRoundLifecycle(
            WidgetAuthoringRoundLifecycle(
                stage = request.stage,
                isRepair = request.repairCode != null,
                outcome = result.diagnosticOutcome(),
                firstGenerationEventMillis = firstEventAt.valueOrNull(),
                toolCaptureMillis = captureAt.valueOrNull(),
                terminalEventMillis = terminalAt.valueOrNull(),
                watchdogMillis = generationTimeoutMillis.takeIf { timedOut },
                returnMillis = returnedAt,
                cleanupOverrunMillis = (returnedAt - generationTimeoutMillis).coerceAtLeast(0).takeIf { timedOut },
            ),
        )
        return result
    }

    private fun maximumStageInputChars(): Int = (maxContextTokens - WidgetAuthoringPipelinePolicy.OUTPUT_RESERVE_TOKENS)
        .coerceAtLeast(0) * WidgetAuthoringPipelinePolicy.ESTIMATED_INPUT_CHARS_PER_TOKEN

    private fun authoringInference(inference: InferenceConfig): InferenceConfig = inference.copy(
        contextTokens = maxContextTokens,
        promptReserveTokens = maxOf(
            inference.promptReserveTokens,
            WidgetAuthoringPipelinePolicy.OUTPUT_RESERVE_TOKENS,
        ),
        temperature = inference.temperature.coerceAtMost(MAX_WIDGET_AUTHORING_TEMPERATURE),
    )

    fun clear() {
        preparedModelId = null
    }

    suspend fun unload() {
        preparedModelId = null
        engine.unload()
    }

    private suspend fun captureRound(
        request: WidgetAuthoringRoundRequest,
        started: Long,
        firstEventAt: AtomicLong,
        captureAt: AtomicLong,
        terminalAt: AtomicLong,
    ): WidgetAuthoringRoundResult {
        onRoundRequest(request)
        val capture = WidgetAuthoringStageCaptureTool(
            request.toolName,
            request.toolDescriptionJson,
            onCapture = { toolName, argumentBytes ->
                captureAt.compareAndSet(UNSET_ROUND_MILLIS, monotonicMillis() - started)
                onCapture(toolName, argumentBytes)
            },
            onRawCapture = onRawCapture,
        )
        val userText = request.toUserText()
        val prompt = PromptRequest(
            content = MessageContent.TextPrompt(userText),
            chatMessages = listOf(
                PromptChatMessage(PromptChatRole.System, stageSystemInstruction(request)),
                PromptChatMessage(PromptChatRole.User, userText),
            ),
            chatSessionId = null,
            advertisedToolNames = setOf(request.toolName),
            ephemeralTools = listOf(capture),
        )
        val terminal = engine.generate(prompt)
            .onEach { event ->
                val elapsed = monotonicMillis() - started
                firstEventAt.compareAndSet(UNSET_ROUND_MILLIS, elapsed)
                if (event is GenerationEvent.Completed || event is GenerationEvent.Failed) {
                    terminalAt.compareAndSet(UNSET_ROUND_MILLIS, elapsed)
                }
            }
            .firstOrNull { it is GenerationEvent.Completed || it is GenerationEvent.Failed }
        return capture.capturedOrNull()?.let(WidgetAuthoringRoundResult::Captured)
            ?: if (terminal is GenerationEvent.Failed) {
                WidgetAuthoringRoundResult.Failed(terminal.kind)
            } else {
                WidgetAuthoringRoundResult.NoArtifact
            }
    }
}

private fun WidgetAuthoringRoundResult.diagnosticOutcome(): WidgetAuthoringRoundOutcome = when (this) {
    is WidgetAuthoringRoundResult.Captured -> WidgetAuthoringRoundOutcome.Captured
    WidgetAuthoringRoundResult.NoArtifact -> WidgetAuthoringRoundOutcome.NoArtifact
    WidgetAuthoringRoundResult.TimedOut -> WidgetAuthoringRoundOutcome.TimedOut
    WidgetAuthoringRoundResult.InputTooLarge -> WidgetAuthoringRoundOutcome.InputTooLarge
    is WidgetAuthoringRoundResult.Failed -> when (kind) {
        GenerationFailureKind.Expected -> WidgetAuthoringRoundOutcome.GenerationRejected
        GenerationFailureKind.ToolCallParsing -> WidgetAuthoringRoundOutcome.ToolCallParsing
        GenerationFailureKind.Unexpected -> WidgetAuthoringRoundOutcome.GenerationFailed
    }
}

private fun monotonicMillis(): Long = System.nanoTime() / 1_000_000

private fun AtomicLong.valueOrNull(): Long? = get().takeIf { it != UNSET_ROUND_MILLIS }

private const val UNSET_ROUND_MILLIS = -1L

internal class WidgetAuthoringStageCaptureTool(
    override val name: String,
    override val descriptionJson: String,
    private val onCapture: (toolName: String, argumentBytes: Int) -> Unit = { _, _ -> },
    private val onRawCapture: (toolName: String, argumentsJson: String) -> Unit = { _, _ -> },
) : EphemeralLocalLlmTool {
    private val calls = AtomicInteger()
    private val captured = AtomicReference<String?>()

    override fun execute(argumentsJson: String): String {
        onCapture(name, argumentsJson.toByteArray(Charsets.UTF_8).size)
        onRawCapture(name, argumentsJson)
        return if (calls.incrementAndGet() == 1) {
            captured.set(argumentsJson)
            """{"accepted":true}"""
        } else {
            """{"accepted":false,"error":"CALL_LIMIT_REACHED"}"""
        }
    }

    fun capturedOrNull(): String? = captured.get()
}

internal fun WidgetAuthoringRoundRequest.toUserText(): String = buildString {
    append("Objective: ")
    append(objective)
    append("\nValidated context: ")
    append(contextJson)
    if (repairCode != null) {
        append("\nRepair only the rejected ")
        append(stage.name)
        append(" artifact. Controlled failure: ")
        append(repairCode.diagnosticWireName)
        if (rejectedArtifact != null) {
            append("\nRejected artifact (bounded evidence; change only the diagnosed defect): ")
            append(rejectedArtifact)
        } else {
            append("\nRejected artifact omitted to prevent anchoring; regenerate from the validated context and contract.")
        }
        append("\nCorrection: ")
        append(repairCode.repairInstruction())
        append("\nAuthoritative replacement contract: ")
        append(objective)
        if (
            stage == WidgetAuthoringStage.RenderFunction &&
            repairCode in setOf(
                WidgetAuthoringStageFailureCode.InvalidSource,
                WidgetAuthoringStageFailureCode.InvalidRenderExecution,
            )
        ) {
            append("\nFinal source check: outcomes is a plain object. Select aliases only from toolResults[].alias, ")
            append("access results with outcomes[alias], never outcomes.get(), and reuse that alias as sourceAlias.")
        }
    }
}

internal fun WidgetAuthoringRoundRequest.estimatedInputChars(): Int = toUserText().length + stageSystemInstruction(this).length + toolDescriptionJson.length

internal fun stageSystemInstruction(request: WidgetAuthoringRoundRequest): String = buildString {
    append("You are executing one isolated stage of ArarAI widget authoring protocol v1. ")
    append("Use only the validated context. ")
    if (request.stage == WidgetAuthoringStage.Feasibility) {
        append("Select the minimum capabilities needed from the validated context; do not invent identifiers. ")
    } else {
        append("Keep every identifier and capability fixed. ")
    }
    append("Call ${request.toolName} exactly once and do not answer in plain text. ")
    append("Return every required field. ")
    if (request.stage == WidgetAuthoringStage.Feasibility) append(FEASIBILITY_OUTCOME_CONTRACT)
    append("This tool captures an untrusted artifact only; it does not execute JavaScript, invoke providers, ")
    append("save data, or grant authority.")
}

private fun WidgetAuthoringStageFailureCode.repairInstruction(): String = REPAIR_INSTRUCTIONS[this]
    ?: "Correct only the rejected artifact according to its tool schema and validated context."

private val REPAIR_INSTRUCTIONS = mapOf(
    WidgetAuthoringStageFailureCode.InvalidFeasibilityJsonRoot to
        "Return one valid JSON object through the tool call.",
    WidgetAuthoringStageFailureCode.InvalidFeasibilityFields to
        "Return exactly outcome, message, periodicIntervalHours, and toolIds with no other fields.",
    WidgetAuthoringStageFailureCode.InvalidFeasibilityProtocol to
        "Do not return protocol metadata; the application derives it.",
    WidgetAuthoringStageFailureCode.InvalidFeasibilityDisplayName to
        "For achievable, use message as a concise non-blank display title of at most 80 characters; do not copy the full instruction.",
    WidgetAuthoringStageFailureCode.InvalidFeasibilityEnabled to
        "Do not return enabled; the application derives it.",
    WidgetAuthoringStageFailureCode.InvalidFeasibilitySchedule to
        "Set periodicIntervalHours to null, 1, 6, 12, or 24.",
    WidgetAuthoringStageFailureCode.InvalidFeasibilityTools to
        "Set toolIds to unique available tool ids only; the application derives versions and purposes.",
    WidgetAuthoringStageFailureCode.InvalidFeasibilityRuntime to
        "Do not return runtime grants; the application derives the bounded deterministic runtime.",
    WidgetAuthoringStageFailureCode.InvalidFeasibilityPresentation to
        "Do not return presentation grants; the application derives the allowlisted presentation envelope.",
    WidgetAuthoringStageFailureCode.InvalidFeasibilityOutcome to
        "Keep outcome=achievable when the request is supported. Use message as its display name. For unachievable, " +
        "use message as the reason; for needs_clarification, use message as the question. " +
        FEASIBILITY_OUTCOME_CONTRACT,
    WidgetAuthoringStageFailureCode.InvalidSource to
        "Submit only source for the current stage. It must contain one named function declaration whose " +
        "name and parameters exactly match the authoritative stage signature, and it must parse with every brace closed; " +
        "do not use an anonymous function.",
    WidgetAuthoringStageFailureCode.InvalidRenderExecution to
        "Regenerate render from renderApi and presentationContract. outcomes is a plain JavaScript object: select an " +
        "alias from toolResults[].alias, access it only with outcomes[alias], never call outcomes.get(), and check " +
        "status before payload.",
    WidgetAuthoringStageFailureCode.InvalidPlan to
        "Return the frozen call object directly from a call function, or the ordered array of frozen call-function " +
        "results from plan(runtime). Do not wrap a call in toolCall or another field.",
    WidgetAuthoringStageFailureCode.InvalidToolArguments to
        "Keep the frozen alias, tool id, and contract version, and make arguments match the supplied tool contract.",
    WidgetAuthoringStageFailureCode.InvalidPresentation to
        "Return one bounded presentation object allowed by the supplied presentation capabilities. Every text or " +
        "value node must include tone set to neutral, muted, positive, or warning.",
    WidgetAuthoringStageFailureCode.InvalidPresentationNodeType to
        "Use one exact node type: card, column, row, text, value, icon, or https_link. Never combine names such as text/value.",
    WidgetAuthoringStageFailureCode.InvalidPresentationFields to
        "Use exactly the fields shown for each node. Text and value require type, text, and tone; every fallback must also be a complete node.",
    WidgetAuthoringStageFailureCode.InvalidPresentationTone to
        "Set tone on every text or value node to exactly neutral, muted, positive, or warning.",
    WidgetAuthoringStageFailureCode.InvalidPresentationProvenance to
        "For https_link, copy the exact URL from a successful outcome and provide the matching frozen sourceAlias and sourceField.",
    WidgetAuthoringStageFailureCode.InvalidPresentationEmptyHandling to
        "Check for an empty collection before indexing it and return a complete fallback presentation node.",
    WidgetAuthoringStageFailureCode.InvalidPresentationFailureHandling to
        "Check outcomes[alias].status === 'success' before reading payload; return a complete fallback node for failure.",
    WidgetAuthoringStageFailureCode.CapabilityExpansion to
        "Keep every frozen alias, tool id, contract version, dependency, and capability unchanged.",
    WidgetAuthoringStageFailureCode.MissingArtifact to
        "Call the advertised capture tool exactly once with the complete artifact; do not answer in plain text.",
    WidgetAuthoringStageFailureCode.TimedOut to
        "Call the advertised capture tool promptly with one complete bounded artifact.",
    WidgetAuthoringStageFailureCode.DeviceRecoveryTimedOut to
        "The previous attempt ended before generation. Produce the current stage normally when this retry starts.",
    WidgetAuthoringStageFailureCode.ResourceLimit to
        "Keep the artifact within the supplied size and context limits.",
)

private const val FEASIBILITY_OUTCOME_CONTRACT =
    "Return exactly four fields. achievable => message is a concise display title of at most 80 characters, not a " +
        "copy of the full instruction; periodicIntervalHours is supported or " +
        "null, and toolIds contains only the minimum available tool ids. unachievable => message is the reason. " +
        "needs_clarification => message is one question. The application derives every other field. "

internal fun repairArtifactForPrompt(
    code: WidgetAuthoringStageFailureCode,
    artifact: String?,
    repairAttemptNumber: Int,
): String? {
    require(repairAttemptNumber in 2..WidgetAuthoringPipelinePolicy.MAX_REPAIRS + 1)
    if (repairAttemptNumber > 2 || code in CLEAN_REGENERATION_FAILURES) return null
    return artifact
}

private val CLEAN_REGENERATION_FAILURES = setOf(
    WidgetAuthoringStageFailureCode.InvalidSchema,
    WidgetAuthoringStageFailureCode.InvalidSource,
    WidgetAuthoringStageFailureCode.InvalidRenderExecution,
    WidgetAuthoringStageFailureCode.ResourceLimit,
    WidgetAuthoringStageFailureCode.MissingArtifact,
    WidgetAuthoringStageFailureCode.TimedOut,
    WidgetAuthoringStageFailureCode.DeviceRecoveryTimedOut,
    WidgetAuthoringStageFailureCode.RuntimeUnavailable,
)

private val STAGE_TOOL_NAMES = setOf(
    SUBMIT_WIDGET_FEASIBILITY_TOOL,
    SUBMIT_WIDGET_ALGORITHM_TOOL,
    SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
    SUBMIT_WIDGET_PLAN_FUNCTION_TOOL,
    SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
)
