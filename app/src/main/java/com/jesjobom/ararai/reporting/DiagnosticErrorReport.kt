package com.jesjobom.ararai.reporting

import android.os.Build
import com.jesjobom.ararai.BuildConfig
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.Locale
import java.util.UUID

enum class DiagnosticErrorCategory(val wireValue: String) {
    ToolCallParsing("tool_call_parsing"),
    UnexpectedGeneration("unexpected_generation"),
}

data class DiagnosticOperationContext(
    val stage: String,
    val modelId: String?,
    val runtime: String?,
    val contextTokens: Int?,
    val reasoningEnabled: Boolean,
    val enabledToolNames: Set<String>,
)

data class DiagnosticErrorReportPayload(
    val reportId: String,
    val category: DiagnosticErrorCategory,
    val stage: String,
    val exceptionType: String,
    val exceptionSummary: String,
    val stackSummary: List<String>,
    val appVersion: String,
    val androidApiLevel: Int,
    val localeTag: String,
    val modelId: String?,
    val runtime: String?,
    val contextTokens: Int?,
    val reasoningEnabled: Boolean,
    val enabledToolNames: List<String>,
    val reportedAtEpochMillis: Long,
) {
    fun toCallableData(): Map<String, Any?> = mapOf(
        "schemaVersion" to SCHEMA_VERSION,
        "reportId" to reportId,
        "category" to category.wireValue,
        "stage" to stage,
        "exceptionType" to exceptionType,
        "exceptionSummary" to exceptionSummary,
        "stackSummary" to stackSummary,
        "metadata" to mapOf(
            "appVersion" to appVersion,
            "androidApiLevel" to androidApiLevel,
            "localeTag" to localeTag,
            "modelId" to modelId,
            "runtime" to runtime,
            "contextTokens" to contextTokens,
            "reasoningEnabled" to reasoningEnabled,
            "enabledToolNames" to enabledToolNames,
        ),
        "reportedAtEpochMillis" to reportedAtEpochMillis,
    )

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

fun interface DiagnosticErrorReportTransport {
    suspend fun submit(payload: DiagnosticErrorReportPayload): Boolean
}

sealed interface DiagnosticErrorReportState {
    val payload: DiagnosticErrorReportPayload

    data class AwaitingConsent(override val payload: DiagnosticErrorReportPayload) : DiagnosticErrorReportState
    data class Sending(override val payload: DiagnosticErrorReportPayload) : DiagnosticErrorReportState
    data class Sent(override val payload: DiagnosticErrorReportPayload) : DiagnosticErrorReportState
    data class Failed(override val payload: DiagnosticErrorReportPayload) : DiagnosticErrorReportState
}

class DiagnosticErrorReportCoordinator(
    private val transport: DiagnosticErrorReportTransport,
    private val reportIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val appVersionProvider: () -> String = { BuildConfig.VERSION_NAME },
    private val androidApiLevelProvider: () -> Int = { Build.VERSION.SDK_INT },
    private val localeProvider: () -> String = { Locale.getDefault().toLanguageTag() },
) {
    private val mutableState = MutableStateFlow<DiagnosticErrorReportState?>(null)
    val state: StateFlow<DiagnosticErrorReportState?> = mutableState.asStateFlow()

    fun offer(error: Throwable, context: DiagnosticOperationContext) {
        if (mutableState.value != null || !error.isReportableRecoverableError()) return
        mutableState.value = DiagnosticErrorReportState.AwaitingConsent(
            DiagnosticErrorReportPayload.from(
                category = error.diagnosticCategory(),
                error = error,
                context = context,
            ),
        )
    }

    fun offerGenerationFailure(failure: GenerationEvent.Failed, context: DiagnosticOperationContext) {
        if (mutableState.value != null || failure.kind == GenerationFailureKind.Expected) return
        val category = when (failure.kind) {
            GenerationFailureKind.ToolCallParsing -> DiagnosticErrorCategory.ToolCallParsing
            GenerationFailureKind.Unexpected -> DiagnosticErrorCategory.UnexpectedGeneration
            GenerationFailureKind.Expected -> return
        }
        mutableState.value = DiagnosticErrorReportState.AwaitingConsent(
            DiagnosticErrorReportPayload.from(
                category = category,
                error = failure.cause,
                context = context,
            ),
        )
    }

    fun dismiss() {
        mutableState.value = null
    }

    suspend fun submit() {
        val awaiting = mutableState.value as? DiagnosticErrorReportState.AwaitingConsent ?: return
        mutableState.value = DiagnosticErrorReportState.Sending(awaiting.payload)
        mutableState.value =
            if (runCatching { transport.submit(awaiting.payload) }.getOrDefault(false)) {
                DiagnosticErrorReportState.Sent(awaiting.payload)
            } else {
                DiagnosticErrorReportState.Failed(awaiting.payload)
            }
    }

    private fun DiagnosticErrorReportPayload.Companion.from(
        category: DiagnosticErrorCategory,
        error: Throwable?,
        context: DiagnosticOperationContext,
    ): DiagnosticErrorReportPayload = DiagnosticErrorReportPayload(
        reportId = reportIdProvider().take(MAX_REPORT_ID_LENGTH),
        category = category,
        stage = context.stage.sanitized(MAX_STAGE_LENGTH),
        exceptionType = (error?.javaClass?.simpleName ?: GENERATION_FAILURE_TYPE)
            .sanitized(MAX_EXCEPTION_TYPE_LENGTH),
        exceptionSummary = category.safeSummary,
        stackSummary = error?.stackTrace
            .orEmpty()
            .asSequence()
            .filter { it.className.startsWith(APP_PACKAGE) || it.className.startsWith(LITERT_PACKAGE) }
            .take(MAX_STACK_FRAMES)
            .map { frame ->
                "${frame.className}.${frame.methodName}:${frame.lineNumber}"
                    .sanitized(MAX_STACK_FRAME_LENGTH)
            }.toList(),
        appVersion = appVersionProvider().sanitized(MAX_METADATA_STRING_LENGTH),
        androidApiLevel = androidApiLevelProvider(),
        localeTag = localeProvider().sanitized(MAX_METADATA_STRING_LENGTH),
        modelId = context.modelId?.sanitized(MAX_METADATA_STRING_LENGTH),
        runtime = context.runtime?.sanitized(MAX_METADATA_STRING_LENGTH),
        contextTokens = context.contextTokens?.takeIf { it in 1..MAX_CONTEXT_TOKENS },
        reasoningEnabled = context.reasoningEnabled,
        enabledToolNames = context.enabledToolNames
            .asSequence()
            .map { it.sanitized(MAX_TOOL_NAME_LENGTH) }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .take(MAX_TOOL_NAMES)
            .toList(),
        reportedAtEpochMillis = clock(),
    )

    private companion object {
        const val APP_PACKAGE = "com.jesjobom.ararai."
        const val LITERT_PACKAGE = "com.google.ai.edge.litertlm."
        const val MAX_REPORT_ID_LENGTH = 64
        const val MAX_STAGE_LENGTH = 48
        const val MAX_EXCEPTION_TYPE_LENGTH = 80
        const val MAX_STACK_FRAMES = 12
        const val MAX_STACK_FRAME_LENGTH = 180
        const val MAX_METADATA_STRING_LENGTH = 120
        const val MAX_TOOL_NAME_LENGTH = 48
        const val MAX_TOOL_NAMES = 8
        const val MAX_CONTEXT_TOKENS = 1_000_000
        const val GENERATION_FAILURE_TYPE = "GenerationFailure"
    }
}

private fun Throwable.isReportableRecoverableError(): Boolean {
    if (this is kotlinx.coroutines.CancellationException || this is IOException) return false
    return diagnosticCategory() == DiagnosticErrorCategory.ToolCallParsing || this is RuntimeException
}

private fun Throwable.diagnosticCategory(): DiagnosticErrorCategory = if (
    generateSequence(this) { it.cause }
        .any { it.message.orEmpty().contains(TOOL_PARSE_MARKER) }
) {
    DiagnosticErrorCategory.ToolCallParsing
} else {
    DiagnosticErrorCategory.UnexpectedGeneration
}

private val DiagnosticErrorCategory.safeSummary: String
    get() = when (this) {
        DiagnosticErrorCategory.ToolCallParsing -> "The local runtime could not parse a model tool call."
        DiagnosticErrorCategory.UnexpectedGeneration -> "The local generation operation failed unexpectedly."
    }

private fun String.sanitized(maxLength: Int): String = asSequence()
    .filter { character -> character.isLetterOrDigit() || character in SAFE_CHARACTERS }
    .joinToString(separator = "")
    .trim()
    .take(maxLength)

private const val TOOL_PARSE_MARKER = "Failed to parse tool calls"
private val SAFE_CHARACTERS = setOf('.', '_', '-', ':', ' ', '(', ')')
