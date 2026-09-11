@file:Suppress("MaxLineLength", "ReturnCount")

package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.engine.EphemeralLocalLlmTool
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptChatMessage
import com.jesjobom.ararai.engine.PromptChatRole
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
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
        require((repairCode == null) == (rejectedArtifact == null))
    }
}

internal sealed interface WidgetAuthoringModelPreparationResult {
    data object Ready : WidgetAuthoringModelPreparationResult
    data object Ineligible : WidgetAuthoringModelPreparationResult
    data object LoadFailed : WidgetAuthoringModelPreparationResult
}

internal sealed interface WidgetAuthoringRoundResult {
    data class Captured(val rawArgumentsJson: String) : WidgetAuthoringRoundResult
    data object NoArtifact : WidgetAuthoringRoundResult
    data object TimedOut : WidgetAuthoringRoundResult
    data object InputTooLarge : WidgetAuthoringRoundResult
    data class Failed(val kind: GenerationFailureKind) : WidgetAuthoringRoundResult
}

internal class WidgetAuthoringPipelineModelController(
    private val engine: LocalLlmEngine,
    private val maxContextTokens: Int = MAX_WIDGET_AUTHORING_CONTEXT_TOKENS,
    private val generationTimeoutMillis: Long = WidgetAuthoringPipelinePolicy.PER_STAGE_TIMEOUT_MILLIS,
    private val onCapture: (toolName: String, argumentBytes: Int) -> Unit = { _, _ -> },
    private val requireDeclaredProtocol: Boolean = true,
) {
    private var preparedModelId: String? = null

    suspend fun prepare(
        model: LocalModel,
        inference: InferenceConfig,
    ): WidgetAuthoringModelPreparationResult {
        if (requireDeclaredProtocol && !model.toolCapabilities.supportsAuthoringProtocol(WIDGET_AUTHORING_PIPELINE_V1)) {
            return WidgetAuthoringModelPreparationResult.Ineligible
        }
        val authoringInference = inference.copy(
            contextTokens = maxContextTokens,
            promptReserveTokens = maxOf(
                inference.promptReserveTokens,
                WidgetAuthoringPipelinePolicy.OUTPUT_RESERVE_TOKENS,
            ),
            temperature = inference.temperature.coerceAtMost(MAX_WIDGET_AUTHORING_TEMPERATURE),
        )
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

    suspend fun capture(
        model: LocalModel,
        request: WidgetAuthoringRoundRequest,
    ): WidgetAuthoringRoundResult {
        check(preparedModelId == model.id) { "Authoring model must be prepared before a stage" }
        if (request.estimatedInputChars() > maximumStageInputChars()) {
            return WidgetAuthoringRoundResult.InputTooLarge
        }
        return try {
            withTimeoutOrNull(generationTimeoutMillis) { captureRound(request) }
                ?: WidgetAuthoringRoundResult.TimedOut
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            WidgetAuthoringRoundResult.Failed(GenerationFailureKind.Unexpected)
        }
    }

    private fun maximumStageInputChars(): Int = (maxContextTokens - WidgetAuthoringPipelinePolicy.OUTPUT_RESERVE_TOKENS)
        .coerceAtLeast(0) * WidgetAuthoringPipelinePolicy.ESTIMATED_INPUT_CHARS_PER_TOKEN

    fun clear() {
        preparedModelId = null
    }

    private suspend fun captureRound(request: WidgetAuthoringRoundRequest): WidgetAuthoringRoundResult {
        val capture = WidgetAuthoringStageCaptureTool(request.toolName, request.toolDescriptionJson, onCapture)
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
            .firstOrNull { it is GenerationEvent.Completed || it is GenerationEvent.Failed }
        return capture.capturedOrNull()?.let(WidgetAuthoringRoundResult::Captured)
            ?: if (terminal is GenerationEvent.Failed) {
                WidgetAuthoringRoundResult.Failed(terminal.kind)
            } else {
                WidgetAuthoringRoundResult.NoArtifact
            }
    }
}

internal class WidgetAuthoringStageCaptureTool(
    override val name: String,
    override val descriptionJson: String,
    private val onCapture: (toolName: String, argumentBytes: Int) -> Unit = { _, _ -> },
) : EphemeralLocalLlmTool {
    private val calls = AtomicInteger()
    private val captured = AtomicReference<String?>()

    override fun execute(argumentsJson: String): String {
        onCapture(name, argumentsJson.toByteArray(Charsets.UTF_8).size)
        return if (calls.incrementAndGet() == 1) {
            captured.set(argumentsJson)
            """{"accepted":true}"""
        } else {
            """{"accepted":false,"error":"CALL_LIMIT_REACHED"}"""
        }
    }

    fun capturedOrNull(): String? = captured.get()
}

private fun WidgetAuthoringRoundRequest.toUserText(): String = buildString {
    append("Objective: ")
    append(objective)
    append("\nValidated context: ")
    append(contextJson)
    if (repairCode != null) {
        append("\nRepair only the rejected ")
        append(stage.name)
        append(" artifact. Controlled failure: ")
        append(repairCode.name)
        append("\nRejected artifact: ")
        append(rejectedArtifact)
    }
}

private fun WidgetAuthoringRoundRequest.estimatedInputChars(): Int = toUserText().length + stageSystemInstruction(this).length + toolDescriptionJson.length

private fun stageSystemInstruction(request: WidgetAuthoringRoundRequest): String = "You are executing one isolated stage of ArarAI widget authoring protocol v1. " +
    "Use only the validated context, keep every identifier and capability fixed, and call " +
    "${request.toolName} exactly once. Do not answer in plain text. This tool captures an untrusted artifact only; " +
    "it does not execute JavaScript, invoke providers, save data, or grant authority."

private val STAGE_TOOL_NAMES = setOf(
    SUBMIT_WIDGET_FEASIBILITY_TOOL,
    SUBMIT_WIDGET_ALGORITHM_TOOL,
    SUBMIT_WIDGET_CALL_FUNCTION_TOOL,
    SUBMIT_WIDGET_PLAN_FUNCTION_TOOL,
    SUBMIT_WIDGET_RENDER_FUNCTION_TOOL,
)
