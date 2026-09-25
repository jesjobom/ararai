@file:Suppress("TooManyFunctions", "MaxLineLength", "ReturnCount")

package com.jesjobom.ararai.ui

import android.util.Log
import com.jesjobom.ararai.BuildConfig
import com.jesjobom.ararai.engine.ImmediateLocalLlmRecoveryGate
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.LocalLlmRecoveryGate
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.widget.managed.ConfirmedWidgetRevision
import com.jesjobom.ararai.widget.managed.ManagedWidgetApplicationServices
import com.jesjobom.ararai.widget.managed.ManagedWidgetAuthoringPipeline
import com.jesjobom.ararai.widget.managed.ManagedWidgetDefinition
import com.jesjobom.ararai.widget.managed.ManagedWidgetExecutionStatus
import com.jesjobom.ararai.widget.managed.ValidatedWidgetDraft
import com.jesjobom.ararai.widget.managed.WidgetAuthoringContextBuilder
import com.jesjobom.ararai.widget.managed.WidgetAuthoringPipelineModelController
import com.jesjobom.ararai.widget.managed.WidgetAuthoringPipelineResult
import com.jesjobom.ararai.widget.managed.WidgetAuthoringPipelineValidator
import com.jesjobom.ararai.widget.managed.WidgetAuthoringProgress
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStage
import com.jesjobom.ararai.widget.managed.WidgetAuthoringStageFailureCode
import com.jesjobom.ararai.widget.managed.WidgetConfirmationMode
import com.jesjobom.ararai.widget.managed.WidgetConfirmationResult
import com.jesjobom.ararai.widget.managed.WidgetDraftBuilder
import com.jesjobom.ararai.widget.managed.WidgetDraftConfirmationService
import com.jesjobom.ararai.widget.managed.WidgetDraftDiff
import com.jesjobom.ararai.widget.managed.WidgetDraftSummary
import com.jesjobom.ararai.widget.managed.WidgetProgramRevision
import com.jesjobom.ararai.widget.managed.WidgetRunRecord
import com.jesjobom.ararai.widget.managed.WidgetToolCallingDiagnosticEnvironment
import com.jesjobom.ararai.widget.managed.WidgetToolCallingDiagnosticMode
import com.jesjobom.ararai.widget.managed.WidgetToolCallingDiagnosticReport
import com.jesjobom.ararai.widget.managed.WidgetToolCallingDiagnosticRunner
import com.jesjobom.ararai.widget.managed.consentDigest
import com.jesjobom.ararai.widget.managed.diffWidgetDraft
import com.jesjobom.ararai.widget.runtime.QuickJsWidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCodec
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode
import com.jesjobom.ararai.widget.runtime.WidgetProgramParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramValidationResult
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ManagedWidgetListItemUiState(
    val definition: ManagedWidgetDefinition,
    val latestRun: WidgetRunRecord?,
) {
    val stale: Boolean
        get() = definition.lastAttemptAtMillis != null &&
            definition.lastAttemptAtMillis != definition.lastSuccessAtMillis
}

internal data class ManagedWidgetDetailUiState(
    val definition: ManagedWidgetDefinition,
    val revision: WidgetProgramRevision,
    val revisions: List<WidgetProgramRevision>,
    val runs: List<WidgetRunRecord>,
    val presentation: WidgetPresentationNode?,
    val presentationCompletedAtMillis: Long?,
    val presentationInvalid: Boolean,
) {
    val latestRun: WidgetRunRecord?
        get() = runs.firstOrNull()

    val stale: Boolean
        get() = definition.lastAttemptAtMillis != null &&
            definition.lastAttemptAtMillis != definition.lastSuccessAtMillis
}

internal data class ManagedWidgetDraftUiState(
    val widgetId: String?,
    val draft: ValidatedWidgetDraft,
    val diff: WidgetDraftDiff?,
)

internal sealed interface ManagedWidgetDraftGenerationResult {
    data class Ready(val value: ManagedWidgetDraftUiState) : ManagedWidgetDraftGenerationResult
    data object ModelUnavailable : ManagedWidgetDraftGenerationResult
    data object ModelLoadFailed : ManagedWidgetDraftGenerationResult
    data object MissingWidget : ManagedWidgetDraftGenerationResult
    data class Unachievable(val reason: String) : ManagedWidgetDraftGenerationResult
    data class NeedsClarification(val question: String) : ManagedWidgetDraftGenerationResult
    data class StageFailed(
        val stage: WidgetAuthoringStage,
        val code: WidgetAuthoringStageFailureCode,
    ) : ManagedWidgetDraftGenerationResult
    data object GenerationTimedOut : ManagedWidgetDraftGenerationResult
}

internal class ManagedWidgetsController(
    private val services: ManagedWidgetApplicationServices,
    localLlmEngine: LocalLlmEngine,
    widgetJavaScriptEngine: WidgetJavaScriptEngine = QuickJsWidgetJavaScriptEngine(),
    private val runtimeContextProvider: () -> WidgetRuntimeContext = ::currentManagedWidgetRuntimeContext,
    recoveryGate: LocalLlmRecoveryGate = ImmediateLocalLlmRecoveryGate,
) {
    private val toolCallingDiagnostic = WidgetToolCallingDiagnosticRunner(
        engine = localLlmEngine,
        registry = services.toolRegistry,
        javascriptEngine = widgetJavaScriptEngine,
        runtimeContextProvider = runtimeContextProvider,
        recoveryGate = recoveryGate,
    )
    private val draftBuilder = WidgetDraftBuilder(
        registry = services.toolRegistry,
        planner = com.jesjobom.ararai.widget.managed.JavaScriptWidgetDraftPlanner(widgetJavaScriptEngine),
    )
    private val authoring = ManagedWidgetAuthoringPipeline(
        modelController = WidgetAuthoringPipelineModelController(localLlmEngine, recoveryGate = recoveryGate),
        validator = WidgetAuthoringPipelineValidator(services.toolRegistry, widgetJavaScriptEngine),
        draftBuilder = draftBuilder,
        registry = services.toolRegistry,
    )
    private val confirmations = WidgetDraftConfirmationService(services.schedules)

    suspend fun loadList(): List<ManagedWidgetListItemUiState> = services.repository.listDefinitions().map { definition ->
        ManagedWidgetListItemUiState(definition, services.repository.listRuns(definition.id).firstOrNull())
    }

    suspend fun loadDetail(widgetId: String): ManagedWidgetDetailUiState? {
        val definition = services.repository.listDefinitions().firstOrNull { it.id == widgetId } ?: return null
        val revision = services.repository.activeRevision(widgetId) ?: return null
        val revisions = services.repository.listRevisions(widgetId)
        val runs = services.repository.listRuns(widgetId)
        val cache = services.repository.cachedPresentation(widgetId)
        val presentation = cache?.let { runCatching { WidgetPresentationCodec.decodeCached(it.presentationJson) }.getOrNull() }
        return ManagedWidgetDetailUiState(
            definition = definition,
            revision = revision,
            revisions = revisions,
            runs = runs,
            presentation = presentation,
            presentationCompletedAtMillis = cache?.completedAtMillis,
            presentationInvalid = cache != null && presentation == null,
        )
    }

    suspend fun refresh(widgetId: String): ManagedWidgetExecutionStatus = services.manualRefresh.refresh(widgetId)

    suspend fun setEnabled(widgetId: String, enabled: Boolean): ManagedWidgetDefinition = services.schedules.setEnabled(widgetId, enabled)

    suspend fun delete(widgetId: String) = services.schedules.delete(widgetId)

    suspend fun duplicate(widgetId: String): ManagedWidgetDefinition? {
        val definition = services.repository.listDefinitions().firstOrNull { it.id == widgetId } ?: return null
        val revision = services.repository.activeRevision(widgetId) ?: return null
        val parsed = WidgetProgramParser.parse(revision.manifestJson, revision.source)
        if (parsed !is WidgetProgramValidationResult.Valid) return null
        val capabilities = parsed.program.manifest.capabilities
        val summary = WidgetDraftSummary(
            displayName = duplicateName(definition.displayName),
            enabledIntent = false,
            periodicIntervalHours = definition.periodicIntervalHours,
            tools = capabilities.tools,
            runtimeValues = capabilities.runtimeValues,
            presentation = capabilities.presentation,
            plannedToolIds = emptyList(),
        )
        return services.schedules.createConfirmed(
            ConfirmedWidgetRevision(
                displayName = summary.displayName,
                enabled = false,
                periodicIntervalHours = summary.periodicIntervalHours,
                manifestJson = parsed.program.canonicalManifestJson,
                source = parsed.program.source,
                programDigest = revision.programDigest,
                consentDigest = consentDigest(revision.programDigest, summary),
            ),
        )
    }

    suspend fun generateDraft(
        model: LocalModel?,
        inference: InferenceConfig?,
        instruction: String,
        widgetId: String?,
        onProgress: (WidgetAuthoringProgress) -> Unit = {},
    ): ManagedWidgetDraftGenerationResult {
        val existing = widgetId?.let { loadExisting(it) }
        if (widgetId != null && existing == null) return ManagedWidgetDraftGenerationResult.MissingWidget
        val prompt = WidgetAuthoringContextBuilder.build(
            userInstruction = instruction,
            toolContracts = services.toolRegistry.descriptors(),
            currentDefinition = existing?.first,
            currentRevision = existing?.second,
        )
        return when (
            val generated = authoring.generate(
                model,
                inference,
                prompt,
                runtimeContextProvider(),
                onProgress,
            )
        ) {
            is WidgetAuthoringPipelineResult.DraftReady -> ManagedWidgetDraftGenerationResult.Ready(
                ManagedWidgetDraftUiState(
                    widgetId = widgetId,
                    draft = generated.draft,
                    diff = existing?.let { diffWidgetDraft(it.first, it.second, generated.draft) },
                ),
            )
            WidgetAuthoringPipelineResult.ModelUnavailable -> ManagedWidgetDraftGenerationResult.ModelUnavailable
            WidgetAuthoringPipelineResult.ModelLoadFailed -> ManagedWidgetDraftGenerationResult.ModelLoadFailed
            is WidgetAuthoringPipelineResult.Unachievable -> ManagedWidgetDraftGenerationResult.Unachievable(
                generated.reason,
            )
            is WidgetAuthoringPipelineResult.NeedsClarification -> {
                ManagedWidgetDraftGenerationResult.NeedsClarification(generated.question)
            }
            is WidgetAuthoringPipelineResult.StageFailed -> ManagedWidgetDraftGenerationResult.StageFailed(
                generated.stage,
                generated.code,
            )
            WidgetAuthoringPipelineResult.TimedOut -> ManagedWidgetDraftGenerationResult.GenerationTimedOut
        }
    }

    suspend fun runToolCallingDiagnostic(
        model: LocalModel,
        inference: InferenceConfig,
        environment: WidgetToolCallingDiagnosticEnvironment,
        mode: WidgetToolCallingDiagnosticMode = WidgetToolCallingDiagnosticMode.FullMatrix,
    ): WidgetToolCallingDiagnosticReport {
        val productionPrompt = WidgetAuthoringContextBuilder.build(
            userInstruction = if (mode == WidgetToolCallingDiagnosticMode.FeasibilityCompactExplicit) {
                TOOL_CALLING_DIAGNOSTIC_EXPLICIT_PROMPT
            } else {
                TOOL_CALLING_DIAGNOSTIC_PROMPT
            },
            toolContracts = services.toolRegistry.descriptors(),
        )
        return toolCallingDiagnostic.run(
            model,
            inference,
            productionPrompt,
            environment,
            mode,
            captureRawArtifacts = BuildConfig.DEBUG,
        ).also { report ->
            if (BuildConfig.DEBUG) report.logControlledTrace()
        }
    }

    suspend fun confirm(
        draft: ManagedWidgetDraftUiState,
        mode: WidgetConfirmationMode,
    ): WidgetConfirmationResult = if (draft.widgetId == null) {
        confirmations.confirmCreate(draft.draft, mode)
    } else {
        confirmations.confirmEdit(draft.widgetId, draft.draft, mode)
    }

    fun declineDraft() = authoring.clear()

    private suspend fun loadExisting(widgetId: String): Pair<ManagedWidgetDefinition, WidgetProgramRevision>? {
        val definition = services.repository.listDefinitions().firstOrNull { it.id == widgetId } ?: return null
        val revision = services.repository.activeRevision(widgetId) ?: return null
        return definition to revision
    }

    private fun duplicateName(name: String): String {
        val suffix = " copy"
        return name.take(80 - suffix.length).trimEnd() + suffix
    }
}

internal const val TOOL_CALLING_DIAGNOSTIC_PROMPT =
    "Mostre um evento aleatório da Wikipédia para o dia e o mês de hoje. " +
        "Atualize o evento a cada hora."
internal const val TOOL_CALLING_DIAGNOSTIC_EXPLICIT_PROMPT =
    "Mostre um evento da Wikipédia para o dia e o mês de hoje e atualize a cada hora. " +
        "Use wikipedia_on_this_day@1 com a data de runtime.currentLocalDateTime() e escolha o evento com " +
        "runtime.seededIndex(events.length)."
private const val WIDGET_DIAGNOSTIC_LOG_TAG = "ArarAI.WidgetDiagnostic"

private fun WidgetToolCallingDiagnosticReport.logControlledTrace() {
    controlledLogLines().forEach { line -> Log.i(WIDGET_DIAGNOSTIC_LOG_TAG, line) }
}

private fun currentManagedWidgetRuntimeContext(): WidgetRuntimeContext {
    val now = ZonedDateTime.now()
    return WidgetRuntimeContext(
        locale = Locale.getDefault().toLanguageTag(),
        timezone = now.zone.id,
        localTime = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        seed = System.currentTimeMillis(),
    )
}
