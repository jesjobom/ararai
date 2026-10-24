@file:Suppress("TooManyFunctions")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.WidgetDraftToolValidation
import com.jesjobom.ararai.widget.runtime.PlannedWidgetToolCall
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetExecutionGrant
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetPlanParser
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCapability
import com.jesjobom.ararai.widget.runtime.WidgetProgram
import com.jesjobom.ararai.widget.runtime.WidgetProgramParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramValidationResult
import com.jesjobom.ararai.widget.runtime.WidgetRequestedLimits
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeFailureCode
import com.jesjobom.ararai.widget.runtime.WidgetRuntimePolicy
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import com.jesjobom.ararai.widget.runtime.sha256
import com.jesjobom.ararai.widget.runtime.toJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ValidatedWidgetDraft(
    val proposal: UntrustedWidgetProposal,
    val program: WidgetProgram,
    val effectiveGrant: WidgetExecutionGrant,
    val plannedCalls: List<PlannedWidgetToolCall>,
    val blockedTools: Set<WidgetToolCapability>,
    val programDigest: String,
    val consentDigest: String,
    val summary: WidgetDraftSummary,
)

internal data class WidgetDraftSummary(
    val displayName: String,
    val enabledIntent: Boolean,
    val periodicIntervalHours: Long?,
    val tools: Set<WidgetToolCapability>,
    val runtimeValues: Set<WidgetRuntimeValue>,
    val presentation: Set<WidgetPresentationCapability>,
    val plannedToolIds: List<String>,
    val observationRetentionDays: Int = ManagedWidgetPolicy.MAX_OBSERVATION_AGE_DAYS,
    val observationLimit: Int = ManagedWidgetPolicy.MAX_OBSERVATIONS_PER_WIDGET,
    val actions: Set<String> = if (WidgetPresentationCapability.HttpsLink in presentation) {
        setOf("open_validated_https_link")
    } else {
        emptySet()
    },
)

internal data class WidgetSourceDiff(
    val changed: Boolean,
    val firstChangedLine: Int?,
    val removedLineCount: Int,
    val addedLineCount: Int,
)

internal data class WidgetDraftDiff(
    val displayNameChanged: Boolean,
    val enablementChanged: Boolean,
    val scheduleChanged: Boolean,
    val moreFrequentSchedule: Boolean,
    val addedTools: Set<WidgetToolCapability>,
    val removedTools: Set<WidgetToolCapability>,
    val addedRuntimeValues: Set<WidgetRuntimeValue>,
    val addedPresentation: Set<WidgetPresentationCapability>,
    val addedActions: Set<String>,
    val retentionExpanded: Boolean,
    val source: WidgetSourceDiff,
) {
    val expandsAuthority: Boolean
        get() = moreFrequentSchedule ||
            addedTools.isNotEmpty() ||
            addedRuntimeValues.isNotEmpty() ||
            addedPresentation.isNotEmpty() ||
            addedActions.isNotEmpty() ||
            retentionExpanded
}

internal enum class WidgetDraftFailureCode {
    InvalidProposal,
    InvalidProgram,
    InvalidPlan,
    InvalidToolArguments,
    RuntimeUnavailable,
}

internal sealed interface WidgetDraftBuildResult {
    data class Valid(val draft: ValidatedWidgetDraft) : WidgetDraftBuildResult
    data class Invalid(val code: WidgetDraftFailureCode) : WidgetDraftBuildResult
}

internal sealed interface WidgetDraftPlanResult {
    data class Valid(val calls: List<PlannedWidgetToolCall>) : WidgetDraftPlanResult
    data class Invalid(val code: WidgetRuntimeFailureCode) : WidgetDraftPlanResult
}

internal fun interface WidgetDraftPlanner {
    suspend fun plan(
        program: WidgetProgram,
        grant: WidgetExecutionGrant,
        context: WidgetRuntimeContext,
    ): WidgetDraftPlanResult
}

internal class JavaScriptWidgetDraftPlanner(
    private val engine: WidgetJavaScriptEngine,
) : WidgetDraftPlanner {
    @Suppress("ReturnCount", "SwallowedException")
    override suspend fun plan(
        program: WidgetProgram,
        grant: WidgetExecutionGrant,
        context: WidgetRuntimeContext,
    ): WidgetDraftPlanResult {
        val contextJson = runCatching { context.toJson(program.manifest.capabilities.runtimeValues) }
            .getOrElse { return WidgetDraftPlanResult.Invalid(WidgetRuntimeFailureCode.InvalidProgram) }
        val execution = try {
            engine.call(
                source = program.source,
                entrypoint = program.manifest.entrypoints.plan,
                argumentsJson = listOf(contextJson),
                limits = program.manifest.limits,
            )
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            return WidgetDraftPlanResult.Invalid(WidgetRuntimeFailureCode.Cancelled)
        } catch (_: RuntimeException) {
            return WidgetDraftPlanResult.Invalid(WidgetRuntimeFailureCode.RuntimeUnavailable)
        }
        return when (execution) {
            is WidgetScriptResult.Failure -> WidgetDraftPlanResult.Invalid(execution.code)
            is WidgetScriptResult.Success -> runCatching {
                WidgetDraftPlanResult.Valid(
                    WidgetPlanParser.parse(
                        execution.outputJson,
                        program.manifest.capabilities,
                        grant,
                        program.manifest.limits,
                    ),
                )
            }.getOrElse { WidgetDraftPlanResult.Invalid(WidgetRuntimeFailureCode.InvalidPlan) }
        }
    }
}

internal class WidgetDraftBuilder(
    private val registry: ApplicationToolRegistry,
    private val planner: WidgetDraftPlanner,
) {
    @Suppress("ReturnCount")
    suspend fun build(
        rawProposal: String,
        context: WidgetRuntimeContext,
    ): WidgetDraftBuildResult {
        val availableTools = registry.descriptors()
            .asSequence()
            .filter { ApplicationToolConsumer.Widget in it.consumers }
            .mapTo(mutableSetOf()) { WidgetToolCapability(it.id, it.version) }
        val proposal = when (val parsed = WidgetProposalParser.parse(rawProposal, availableTools)) {
            is WidgetProposalParseResult.Invalid -> return invalid(WidgetDraftFailureCode.InvalidProposal)
            is WidgetProposalParseResult.Valid -> parsed.proposal
        }
        return build(proposal, context)
    }

    @Suppress("ReturnCount")
    suspend fun build(
        proposal: UntrustedWidgetProposal,
        context: WidgetRuntimeContext,
    ): WidgetDraftBuildResult {
        val program = when (val parsed = WidgetProgramParser.parse(proposal.manifestJson(), proposal.source)) {
            is WidgetProgramValidationResult.Invalid -> return invalid(WidgetDraftFailureCode.InvalidProgram)
            is WidgetProgramValidationResult.Valid -> parsed.program
        }
        val grant = program.manifest.capabilities.let {
            WidgetExecutionGrant(it.tools, it.runtimeValues, it.presentation)
        }
        val plannedCalls = when (val result = planner.plan(program, grant, context)) {
            is WidgetDraftPlanResult.Invalid -> return invalid(result.code.toDraftFailure())
            is WidgetDraftPlanResult.Valid -> result.calls
        }
        if (plannedCalls.any { call ->
                registry.validateWidgetDraftCall(
                    call.tool.id,
                    call.tool.version,
                    call.argumentsJson,
                ) is WidgetDraftToolValidation.Invalid
            }
        ) {
            return invalid(WidgetDraftFailureCode.InvalidToolArguments)
        }
        val blockedTools = proposal.tools.filterTo(mutableSetOf()) { tool ->
            registry.widgetToolOperationalState(tool.id, tool.version)?.let { !it.enabled || !it.ready } != false
        }
        val programDigest = sha256(program.canonicalManifestJson + "\n" + proposal.source)
        val summary = proposal.summary(plannedCalls)
        return WidgetDraftBuildResult.Valid(
            ValidatedWidgetDraft(
                proposal = proposal,
                program = program,
                effectiveGrant = grant,
                plannedCalls = plannedCalls,
                blockedTools = blockedTools,
                programDigest = programDigest,
                consentDigest = consentDigest(programDigest, summary),
                summary = summary,
            ),
        )
    }

    private fun invalid(code: WidgetDraftFailureCode) = WidgetDraftBuildResult.Invalid(code)
}

internal enum class WidgetConfirmationMode { CreateAndEnable, SaveDisabled }

internal sealed interface WidgetConfirmationResult {
    data class Confirmed(val definition: ManagedWidgetDefinition) : WidgetConfirmationResult
    data class Blocked(val tools: Set<WidgetToolCapability>) : WidgetConfirmationResult
    data object Failed : WidgetConfirmationResult
}

internal class WidgetDraftConfirmationService(
    private val schedules: ManagedWidgetScheduleController,
) {
    suspend fun confirmCreate(
        draft: ValidatedWidgetDraft,
        mode: WidgetConfirmationMode,
    ): WidgetConfirmationResult = confirm(draft, mode) { candidate ->
        schedules.createConfirmed(candidate)
    }

    suspend fun confirmEdit(
        widgetId: String,
        draft: ValidatedWidgetDraft,
        mode: WidgetConfirmationMode,
    ): WidgetConfirmationResult = confirm(draft, mode) { candidate ->
        schedules.replaceActiveRevision(widgetId, candidate)
    }

    private suspend fun confirm(
        draft: ValidatedWidgetDraft,
        mode: WidgetConfirmationMode,
        persist: suspend (ConfirmedWidgetRevision) -> ManagedWidgetDefinition,
    ): WidgetConfirmationResult {
        if (mode == WidgetConfirmationMode.CreateAndEnable && draft.blockedTools.isNotEmpty()) {
            return WidgetConfirmationResult.Blocked(draft.blockedTools)
        }
        val enabled = mode == WidgetConfirmationMode.CreateAndEnable
        val confirmedSummary = draft.summary.copy(enabledIntent = enabled)
        val candidate = ConfirmedWidgetRevision(
            displayName = draft.proposal.displayName,
            enabled = enabled,
            periodicIntervalHours = draft.proposal.periodicIntervalHours,
            manifestJson = draft.program.canonicalManifestJson,
            source = draft.program.source,
            programDigest = draft.programDigest,
            consentDigest = consentDigest(draft.programDigest, confirmedSummary),
        )
        return try {
            WidgetConfirmationResult.Confirmed(persist(candidate))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            WidgetConfirmationResult.Failed
        }
    }
}

internal sealed interface ManagedWidgetAuthoringResult {
    data class DraftReady(val draft: ValidatedWidgetDraft) : ManagedWidgetAuthoringResult
    data object ModelUnavailable : ManagedWidgetAuthoringResult
    data object ModelLoadFailed : ManagedWidgetAuthoringResult
    data object ProposalMissing : ManagedWidgetAuthoringResult
    data class ProposalInvalid(val code: WidgetDraftFailureCode) : ManagedWidgetAuthoringResult
    data object GenerationTimedOut : ManagedWidgetAuthoringResult
    data class GenerationFailed(
        val kind: com.jesjobom.ararai.engine.GenerationFailureKind,
    ) : ManagedWidgetAuthoringResult
}

internal class ManagedWidgetAuthoringController(
    private val modelAuthoring: WidgetAuthoringModelController,
    private val draftBuilder: WidgetDraftBuilder,
) {
    var currentDraft: ValidatedWidgetDraft? = null
        private set

    suspend fun generate(
        model: com.jesjobom.ararai.model.LocalModel?,
        inference: com.jesjobom.ararai.model.InferenceConfig?,
        prompt: WidgetAuthoringPrompt,
        runtimeContext: WidgetRuntimeContext,
    ): ManagedWidgetAuthoringResult {
        currentDraft = null
        if (model == null || inference == null) return ManagedWidgetAuthoringResult.ModelUnavailable
        return when (val proposal = modelAuthoring.propose(model, inference, prompt)) {
            WidgetAuthorshipResult.IneligibleModel -> ManagedWidgetAuthoringResult.ModelUnavailable
            WidgetAuthorshipResult.ModelLoadFailed -> ManagedWidgetAuthoringResult.ModelLoadFailed
            WidgetAuthorshipResult.NoProposal -> ManagedWidgetAuthoringResult.ProposalMissing
            WidgetAuthorshipResult.GenerationTimedOut -> ManagedWidgetAuthoringResult.GenerationTimedOut
            is WidgetAuthorshipResult.GenerationFailed -> ManagedWidgetAuthoringResult.GenerationFailed(proposal.kind)
            is WidgetAuthorshipResult.Proposed -> when (
                val built = draftBuilder.build(proposal.rawArgumentsJson, runtimeContext)
            ) {
                is WidgetDraftBuildResult.Invalid -> ManagedWidgetAuthoringResult.ProposalInvalid(built.code)
                is WidgetDraftBuildResult.Valid -> {
                    currentDraft = built.draft
                    ManagedWidgetAuthoringResult.DraftReady(built.draft)
                }
            }
        }
    }

    fun decline() {
        currentDraft = null
    }
}

internal fun diffWidgetDraft(
    currentDefinition: ManagedWidgetDefinition,
    currentRevision: WidgetProgramRevision,
    draft: ValidatedWidgetDraft,
): WidgetDraftDiff {
    require(currentDefinition.id == currentRevision.widgetId)
    val currentProgram = WidgetProgramParser.parse(currentRevision.manifestJson, currentRevision.source)
    require(currentProgram is WidgetProgramValidationResult.Valid)
    val capabilities = currentProgram.program.manifest.capabilities
    val currentActions = if (WidgetPresentationCapability.HttpsLink in capabilities.presentation) {
        setOf("open_validated_https_link")
    } else {
        emptySet()
    }
    return WidgetDraftDiff(
        displayNameChanged = currentDefinition.displayName != draft.summary.displayName,
        enablementChanged = currentDefinition.enabled != draft.summary.enabledIntent,
        scheduleChanged = currentDefinition.periodicIntervalHours != draft.summary.periodicIntervalHours,
        moreFrequentSchedule = isMoreFrequent(
            currentDefinition.periodicIntervalHours,
            draft.summary.periodicIntervalHours,
        ),
        addedTools = draft.summary.tools - capabilities.tools,
        removedTools = capabilities.tools - draft.summary.tools,
        addedRuntimeValues = draft.summary.runtimeValues - capabilities.runtimeValues,
        addedPresentation = draft.summary.presentation - capabilities.presentation,
        addedActions = draft.summary.actions - currentActions,
        retentionExpanded = false,
        source = sourceDiff(currentRevision.source, draft.proposal.source),
    )
}

internal fun consentDigest(
    programDigest: String,
    summary: WidgetDraftSummary,
): String = sha256(
    StrictJson.canonical(
        JsonObject().apply {
            addProperty("programDigest", programDigest)
            addProperty("enabled", summary.enabledIntent)
            add("periodicIntervalHours", summary.periodicIntervalHours?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
            add(
                "tools",
                summary.tools
                    .sortedWith(compareBy(WidgetToolCapability::id, WidgetToolCapability::version))
                    .toToolJson(),
            )
            add("runtime", summary.runtimeValues.map(WidgetRuntimeValue::wireName).sorted().toStringJson())
            add(
                "presentation",
                summary.presentation.map(WidgetPresentationCapability::wireName).sorted().toStringJson(),
            )
            addProperty("observationRetentionDays", summary.observationRetentionDays)
            addProperty("observationLimit", summary.observationLimit)
            add("actions", summary.actions.sorted().toStringJson())
        },
    ),
)

internal fun UntrustedWidgetProposal.manifestJson(): String = StrictJson.canonical(
    JsonObject().apply {
        addProperty("schemaVersion", WidgetRuntimePolicy.MANIFEST_SCHEMA_VERSION)
        addProperty("language", WidgetRuntimePolicy.LANGUAGE)
        addProperty("apiVersion", WidgetRuntimePolicy.WIDGET_API_VERSION)
        add(
            "entrypoints",
            JsonObject().apply {
                addProperty("plan", "plan")
                addProperty("render", "render")
            },
        )
        add(
            "capabilities",
            JsonObject().apply {
                add(
                    "tools",
                    tools.sortedWith(compareBy(WidgetToolCapability::id, WidgetToolCapability::version)).toToolJson(),
                )
                add("runtime", runtimeValues.map(WidgetRuntimeValue::wireName).sorted().toStringJson())
                add("presentation", presentation.map(WidgetPresentationCapability::wireName).sorted().toStringJson())
            },
        )
        add("limits", FIXED_DRAFT_LIMITS.toJson())
        addProperty("sourceSha256", sha256(source))
    },
)

private fun UntrustedWidgetProposal.summary(calls: List<PlannedWidgetToolCall>) = WidgetDraftSummary(
    displayName = displayName,
    enabledIntent = enabled,
    periodicIntervalHours = periodicIntervalHours,
    tools = tools,
    runtimeValues = runtimeValues,
    presentation = presentation,
    plannedToolIds = calls.map { it.tool.id },
)

private fun WidgetRuntimeFailureCode.toDraftFailure(): WidgetDraftFailureCode = when (this) {
    WidgetRuntimeFailureCode.InvalidProgram,
    WidgetRuntimeFailureCode.IncompatibleProgram,
    WidgetRuntimeFailureCode.IntegrityMismatch,
    WidgetRuntimeFailureCode.CapabilityDenied,
    WidgetRuntimeFailureCode.PolicyViolation,
    -> WidgetDraftFailureCode.InvalidProgram
    WidgetRuntimeFailureCode.InvalidPlan,
    WidgetRuntimeFailureCode.InvalidPresentation,
    WidgetRuntimeFailureCode.ScriptError,
    WidgetRuntimeFailureCode.ResourceLimit,
    WidgetRuntimeFailureCode.Cancelled,
    -> WidgetDraftFailureCode.InvalidPlan
    WidgetRuntimeFailureCode.RuntimeUnavailable -> WidgetDraftFailureCode.RuntimeUnavailable
}

private fun isMoreFrequent(old: Long?, new: Long?): Boolean = new != null && (old == null || new < old)

private fun sourceDiff(old: String, new: String): WidgetSourceDiff {
    if (old == new) return WidgetSourceDiff(false, null, 0, 0)
    val oldLines = old.lines()
    val newLines = new.lines()
    val firstChanged = (0 until minOf(oldLines.size, newLines.size))
        .firstOrNull { oldLines[it] != newLines[it] }
        ?: minOf(oldLines.size, newLines.size)
    return WidgetSourceDiff(
        changed = true,
        firstChangedLine = firstChanged + 1,
        removedLineCount = oldLines.size - firstChanged,
        addedLineCount = newLines.size - firstChanged,
    )
}

private fun WidgetRequestedLimits.toJson() = JsonObject().apply {
    addProperty("memoryBytes", memoryBytes)
    addProperty("stackBytes", stackBytes)
    addProperty("executionMillis", executionMillis)
    addProperty("maxToolCalls", maxToolCalls)
    addProperty("maxOutputBytes", maxOutputBytes)
}

private fun List<WidgetToolCapability>.toToolJson() = JsonArray().also { array ->
    forEach { tool ->
        array.add(
            JsonObject().apply {
                addProperty("id", tool.id)
                addProperty("version", tool.version)
            },
        )
    }
}

private fun List<String>.toStringJson() = JsonArray().also { array -> forEach(array::add) }

internal val FIXED_DRAFT_LIMITS = WidgetRequestedLimits(
    memoryBytes = WidgetRuntimePolicy.MAX_MEMORY_BYTES,
    stackBytes = WidgetRuntimePolicy.MAX_STACK_BYTES,
    executionMillis = WidgetRuntimePolicy.MAX_EXECUTION_MILLIS,
    maxToolCalls = WidgetRuntimePolicy.MAX_TOOL_CALLS,
    maxOutputBytes = WidgetRuntimePolicy.MAX_OUTPUT_BYTES,
)
