@file:Suppress("MaxLineLength", "ReturnCount", "TooManyFunctions", "LongMethod")

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCapability
import com.jesjobom.ararai.widget.runtime.WidgetRuntimePolicy
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import com.jesjobom.ararai.widget.runtime.requireFields
import com.jesjobom.ararai.widget.runtime.requiredArray
import com.jesjobom.ararai.widget.runtime.requiredInt
import com.jesjobom.ararai.widget.runtime.requiredObject
import com.jesjobom.ararai.widget.runtime.requiredString
import com.jesjobom.ararai.widget.runtime.utf8Size

internal const val WIDGET_AUTHORING_PROTOCOL_VERSION = 1
internal const val SUBMIT_WIDGET_FEASIBILITY_TOOL = "submit_widget_feasibility"
internal const val SUBMIT_WIDGET_ALGORITHM_TOOL = "submit_widget_algorithm"
internal const val SUBMIT_WIDGET_CALL_FUNCTION_TOOL = "submit_widget_call_function"
internal const val SUBMIT_WIDGET_PLAN_FUNCTION_TOOL = "submit_widget_plan_function"
internal const val SUBMIT_WIDGET_RENDER_FUNCTION_TOOL = "submit_widget_render_function"
internal const val SUBMIT_WIDGET_COLLECTION_CARD_TOOL = "submit_widget_collection_card"
internal const val SUBMIT_WIDGET_COLLECTION_LIST_TOOL = "submit_widget_collection_list"

internal object WidgetAuthoringPipelinePolicy {
    const val MAX_TOOLS = WidgetRuntimePolicy.MAX_TOOL_CALLS
    const val MAX_ALGORITHM_STEPS = 16
    const val MAX_TEXT_CHARS = 512
    const val MAX_QUESTION_CHARS = 240
    const val MAX_FRAGMENT_BYTES = 6 * 1024
    const val MAX_ARTIFACT_BYTES = 8 * 1024
    const val OUTPUT_RESERVE_TOKENS = 1_536
    const val ESTIMATED_INPUT_CHARS_PER_TOKEN = 4
    const val MAX_REPAIRS = 2
    const val MAX_GENERATIONS = 10
    const val PER_STAGE_TIMEOUT_MILLIS = 90_000L
    const val TOTAL_TIMEOUT_MILLIS = 8 * 60_000L
}

internal enum class WidgetFeasibilityOutcome(val wireName: String) {
    Achievable("achievable"),
    Unachievable("unachievable"),
    NeedsClarification("needs_clarification"),
}

internal data class WidgetSelectedTool(
    val capability: WidgetToolCapability,
    val purpose: String,
)

internal data class WidgetFeasibilityArtifact(
    val outcome: WidgetFeasibilityOutcome,
    val displayName: String,
    val enabled: Boolean,
    val periodicIntervalHours: Long?,
    val tools: List<WidgetSelectedTool>,
    val runtimeValues: Set<WidgetRuntimeValue>,
    val presentation: Set<WidgetPresentationCapability>,
    val reason: String?,
    val clarificationQuestion: String?,
)

internal sealed interface WidgetFeasibilityParseResult {
    data class Valid(val artifact: WidgetFeasibilityArtifact) : WidgetFeasibilityParseResult
    data class Invalid(val code: WidgetAuthoringStageFailureCode) : WidgetFeasibilityParseResult
}

internal object WidgetFeasibilityParser {
    fun parse(
        raw: String,
        availableTools: Set<WidgetToolCapability>,
    ): WidgetFeasibilityParseResult {
        if (raw.utf8Size() > WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES) {
            return WidgetFeasibilityParseResult.Invalid(WidgetAuthoringStageFailureCode.ResourceLimit)
        }
        return try {
            WidgetFeasibilityParseResult.Valid(parseArtifact(raw, availableTools))
        } catch (failure: WidgetFeasibilityValidationFailure) {
            WidgetFeasibilityParseResult.Invalid(failure.code)
        } catch (_: Exception) {
            WidgetFeasibilityParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidFeasibilityShape)
        }
    }

    private fun parseArtifact(
        raw: String,
        availableTools: Set<WidgetToolCapability>,
    ): WidgetFeasibilityArtifact {
        val root = parseRoot(raw)
        validateRootFields(root)
        val outcome = parseOutcome(root)
        val message = parseMessage(root, outcome)
        val interval = if (outcome == WidgetFeasibilityOutcome.Achievable) parseSchedule(root) else null
        val tools = if (outcome == WidgetFeasibilityOutcome.Achievable) parseTools(root, availableTools) else emptyList()
        val runtime = WidgetRuntimeValue.entries.toSet().takeIf { outcome == WidgetFeasibilityOutcome.Achievable }.orEmpty()
        val presentation = WidgetPresentationCapability.entries.toSet()
            .takeIf { outcome == WidgetFeasibilityOutcome.Achievable }
            .orEmpty()
        return WidgetFeasibilityArtifact(
            outcome,
            message.toDisplayName(),
            outcome == WidgetFeasibilityOutcome.Achievable,
            interval,
            tools,
            runtime,
            presentation,
            message.takeIf { outcome == WidgetFeasibilityOutcome.Unachievable },
            message.takeIf { outcome == WidgetFeasibilityOutcome.NeedsClarification },
        )
    }

    private fun parseRoot(raw: String): JsonObject = validateFeasibility(
        WidgetAuthoringStageFailureCode.InvalidFeasibilityJsonRoot,
    ) {
        StrictJson.parse(
            raw,
            maxStringChars = WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES,
        ).requiredObject()
    }

    private fun validateRootFields(root: JsonObject) = validateFeasibility(
        WidgetAuthoringStageFailureCode.InvalidFeasibilityFields,
    ) {
        root.requireFields("outcome", "message", "periodicIntervalHours", "toolIds")
    }

    private fun parseOutcome(root: JsonObject): WidgetFeasibilityOutcome = validateFeasibility(
        WidgetAuthoringStageFailureCode.InvalidFeasibilityOutcome,
    ) {
        WidgetFeasibilityOutcome.entries.single { it.wireName == root.requiredString("outcome") }
    }

    private fun parseMessage(root: JsonObject, outcome: WidgetFeasibilityOutcome): String = validateFeasibility(
        if (outcome == WidgetFeasibilityOutcome.Achievable) {
            WidgetAuthoringStageFailureCode.InvalidFeasibilityDisplayName
        } else {
            WidgetAuthoringStageFailureCode.InvalidFeasibilityOutcome
        },
    ) {
        root.requiredString("message").also { message ->
            require(message.isNotBlank())
            val maximum = if (outcome == WidgetFeasibilityOutcome.NeedsClarification) {
                WidgetAuthoringPipelinePolicy.MAX_QUESTION_CHARS
            } else {
                WidgetAuthoringPipelinePolicy.MAX_TEXT_CHARS
            }
            require(message.length <= maximum)
        }
    }

    private fun parseSchedule(root: JsonObject): Long? = validateFeasibility(
        WidgetAuthoringStageFailureCode.InvalidFeasibilitySchedule,
    ) {
        root.strictNullableLong("periodicIntervalHours").also {
            require(it == null || it in ManagedWidgetPolicy.SUPPORTED_PERIODIC_INTERVAL_HOURS)
        }
    }

    private fun parseTools(
        root: JsonObject,
        availableTools: Set<WidgetToolCapability>,
    ): List<WidgetSelectedTool> = validateFeasibility(WidgetAuthoringStageFailureCode.InvalidFeasibilityTools) {
        val ids = root.requiredArray("toolIds").map { element ->
            require(element.isJsonPrimitive && element.asJsonPrimitive.isString)
            element.asString
        }
        require(ids.size <= WidgetAuthoringPipelinePolicy.MAX_TOOLS)
        require(ids.toSet().size == ids.size)
        ids.map { id ->
            val capability = availableTools
                .filter { it.id == id }
                .maxByOrNull(WidgetToolCapability::version)
                ?: error("Unavailable tool")
            WidgetSelectedTool(capability, "Use registered tool ${capability.id}")
        }
    }
}

private class WidgetFeasibilityValidationFailure(
    val code: WidgetAuthoringStageFailureCode,
) : RuntimeException()

private inline fun <T> validateFeasibility(
    code: WidgetAuthoringStageFailureCode,
    block: () -> T,
): T = try {
    block()
} catch (failure: WidgetFeasibilityValidationFailure) {
    throw failure
} catch (_: Exception) {
    throw WidgetFeasibilityValidationFailure(code)
}

internal enum class WidgetAlgorithmStepKind(val wireName: String) {
    RuntimeInput("runtime_input"),
    Transform("transform"),
    ToolCall("tool_call"),
}

internal enum class WidgetRenderCapability(val wireName: String) {
    CollectionCard("collection_card"),
    CollectionList("collection_list"),
    CustomSource("custom_source"),
}

internal enum class WidgetToolSelectionMode(val wireName: String) {
    First("first"),
    SeededRandom("seeded_random"),
}

internal data class WidgetAlgorithmStep(
    val id: String,
    val kind: WidgetAlgorithmStepKind,
    val objective: String,
    val dependencies: List<String>,
    val tool: WidgetToolCapability?,
    val selectionMode: WidgetToolSelectionMode? = null,
)

internal data class WidgetAlgorithmArtifact(
    val steps: List<WidgetAlgorithmStep>,
    val presentationObjective: String,
    val renderCapability: WidgetRenderCapability? = null,
) {
    val toolCallSteps: List<WidgetAlgorithmStep>
        get() = steps.filter { it.kind == WidgetAlgorithmStepKind.ToolCall }
}

internal sealed interface WidgetAlgorithmParseResult {
    data class Valid(val artifact: WidgetAlgorithmArtifact) : WidgetAlgorithmParseResult
    data class Invalid(val code: WidgetAuthoringStageFailureCode) : WidgetAlgorithmParseResult
}

internal object WidgetAlgorithmParser {
    fun parse(
        raw: String,
        envelope: WidgetFeasibilityArtifact,
        requireRenderCapability: Boolean = false,
        requirePageSelection: Boolean = false,
    ): WidgetAlgorithmParseResult = try {
        require(envelope.outcome == WidgetFeasibilityOutcome.Achievable)
        require(raw.utf8Size() <= WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES)
        val root = StrictJson.parse(
            raw,
            maxStringChars = WidgetAuthoringPipelinePolicy.MAX_TEXT_CHARS,
        ).requiredObject()
        require(root.keySet().all { it in setOf("protocolVersion", "steps", "presentationObjective", "renderCapability") })
        require(root.has("steps") && root.has("presentationObjective"))
        root.get("protocolVersion")?.let {
            require(root.requiredInt("protocolVersion") == WIDGET_AUTHORING_PROTOCOL_VERSION)
        }
        val selectedTools = envelope.tools.map { it.capability }.toSet()
        val steps = root.requiredArray("steps").mapIndexed { index, element ->
            parseStep(element.requiredObject(), selectedTools, envelope.runtimeValues, index == 0)
        }
        require(steps.isNotEmpty() && steps.size <= WidgetAuthoringPipelinePolicy.MAX_ALGORITHM_STEPS)
        require(steps.map { it.id }.toSet().size == steps.size)
        validateDependencies(steps)
        val calls = steps.filter { it.kind == WidgetAlgorithmStepKind.ToolCall }
        require(calls.size <= WidgetAuthoringPipelinePolicy.MAX_TOOLS)
        require(calls.all { it.tool in selectedTools })
        require(calls.map { it.tool }.toSet() == selectedTools)
        if (requirePageSelection) {
            require(
                calls.filter { it.tool?.id == "wikipedia_pages" && it.tool.version == 2 }
                    .all { it.selectionMode != null },
            )
        }
        rejectLiveResultDependentCalls(steps)
        val renderCapability = root.get("renderCapability")?.let { value ->
            WidgetRenderCapability.entries.single { it.wireName == value.asString }
        }
        require(!requireRenderCapability || renderCapability != null)
        require(renderCapability !in setOf(WidgetRenderCapability.CollectionCard, WidgetRenderCapability.CollectionList) || calls.isNotEmpty())
        if (requireRenderCapability && renderCapability in setOf(
                WidgetRenderCapability.CollectionCard,
                WidgetRenderCapability.CollectionList,
            )
        ) {
            require(canUseCollectionCard(steps))
        }
        WidgetAlgorithmParseResult.Valid(
            WidgetAlgorithmArtifact(steps, root.requiredString("presentationObjective").boundedText(), renderCapability),
        )
    } catch (_: RuntimeException) {
        WidgetAlgorithmParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidAlgorithm)
    }

    private fun parseStep(
        step: JsonObject,
        selectedTools: Set<WidgetToolCapability>,
        selectedRuntimeValues: Set<WidgetRuntimeValue>,
        isFirstStep: Boolean,
    ): WidgetAlgorithmStep {
        require(
            step.keySet().all {
                it in setOf("id", "kind", "objective", "dependsOn", "toolId", "contractVersion", "runtimeInputs", "selectionMode")
            },
        )
        val id = step.requiredString("id")
        require(ALGORITHM_STEP_ID_PATTERN.matches(id))
        val kind = WidgetAlgorithmStepKind.entries.single { it.wireName == step.requiredString("kind") }
        val dependencies = if (step.has("dependsOn")) {
            step.requiredArray("dependsOn").map { it.asString }
        } else {
            require(isFirstStep) { "Only the first step may omit an empty dependency list" }
            emptyList()
        }
        require(dependencies.toSet().size == dependencies.size)
        val tool = if (kind == WidgetAlgorithmStepKind.ToolCall) {
            val toolId = requireNotNull(step.strictNullableString("toolId", 64))
            selectedTools.single { it.id == toolId }
        } else {
            null
        }
        val selectionMode = step.get("selectionMode")?.let { value ->
            WidgetToolSelectionMode.entries.single { it.wireName == value.asString }
        }
        require(selectionMode == null || (tool?.id == "wikipedia_pages" && tool.version == 2))
        require(selectionMode != WidgetToolSelectionMode.SeededRandom || WidgetRuntimeValue.Seed in selectedRuntimeValues)
        return WidgetAlgorithmStep(
            id = id,
            kind = kind,
            objective = step.requiredString("objective").boundedText(),
            dependencies = dependencies,
            tool = tool,
            selectionMode = selectionMode,
        )
    }

    private fun validateDependencies(steps: List<WidgetAlgorithmStep>) {
        val previousIds = mutableSetOf<String>()
        steps.forEach { step ->
            require(step.dependencies.all { it in previousIds })
            require(previousIds.add(step.id))
        }
    }

    private fun rejectLiveResultDependentCalls(steps: List<WidgetAlgorithmStep>) {
        val byId = steps.associateBy { it.id }
        fun dependsOnTool(step: WidgetAlgorithmStep, visited: MutableSet<String>): Boolean = step.dependencies.any { dependencyId ->
            if (!visited.add(dependencyId)) return@any false
            val dependency = requireNotNull(byId[dependencyId])
            dependency.kind == WidgetAlgorithmStepKind.ToolCall || dependsOnTool(dependency, visited)
        }
        steps.filter { it.kind == WidgetAlgorithmStepKind.ToolCall }.forEach { step ->
            require(!dependsOnTool(step, mutableSetOf()))
        }
    }

    /** A collection card cannot evaluate multiple outcomes or transform a fetched result. */
    private fun canUseCollectionCard(steps: List<WidgetAlgorithmStep>): Boolean {
        if (steps.count { it.kind == WidgetAlgorithmStepKind.ToolCall } != 1) return false
        val callIndex = steps.indexOfFirst { it.kind == WidgetAlgorithmStepKind.ToolCall }
        if (steps.drop(callIndex + 1).any { it.kind == WidgetAlgorithmStepKind.Transform }) return false
        val resultDependentIds = mutableSetOf<String>()
        steps.forEach { step ->
            if (step.kind == WidgetAlgorithmStepKind.ToolCall || step.dependencies.any(resultDependentIds::contains)) {
                resultDependentIds += step.id
                if (step.kind != WidgetAlgorithmStepKind.ToolCall) return false
            }
        }
        return true
    }
}

internal data class WidgetSourceFragmentArtifact(
    val artifactId: String,
    val functionName: String,
    val inputNames: List<String>,
    val source: String,
)

internal sealed interface WidgetSourceFragmentParseResult {
    data class Valid(val artifact: WidgetSourceFragmentArtifact) : WidgetSourceFragmentParseResult
    data class Invalid(val code: WidgetAuthoringStageFailureCode) : WidgetSourceFragmentParseResult
}

internal object WidgetSourceFragmentParser {
    fun parse(
        raw: String,
        expectedArtifactId: String,
        expectedFunctionName: String,
        expectedInputNames: List<String>,
    ): WidgetSourceFragmentParseResult = try {
        require(raw.utf8Size() <= WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES)
        val root = StrictJson.parse(
            raw,
            maxStringChars = WidgetAuthoringPipelinePolicy.MAX_FRAGMENT_BYTES,
        ).requiredObject()
        val legacyFields = setOf("protocolVersion", "artifactId", "functionName", "inputNames", "source")
        require(root.keySet() == setOf("source") || root.keySet() == legacyFields)
        if (root.keySet() == legacyFields) {
            require(root.requiredInt("protocolVersion") == WIDGET_AUTHORING_PROTOCOL_VERSION)
            require(root.requiredString("artifactId") == expectedArtifactId)
            require(root.requiredString("functionName") == expectedFunctionName)
            require(root.requiredArray("inputNames").map { it.asString } == expectedInputNames)
        }
        val source = root.requiredString("source")
        require(source.utf8Size() <= WidgetAuthoringPipelinePolicy.MAX_FRAGMENT_BYTES)
        requireSafeSingleFunction(source, expectedFunctionName, expectedInputNames)
        WidgetSourceFragmentParseResult.Valid(
            WidgetSourceFragmentArtifact(expectedArtifactId, expectedFunctionName, expectedInputNames, source),
        )
    } catch (_: RuntimeException) {
        WidgetSourceFragmentParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidSource)
    }

    private fun requireSafeSingleFunction(
        source: String,
        functionName: String,
        inputNames: List<String>,
    ) {
        require(source.isNotBlank())
        require(!FORBIDDEN_SOURCE_TOKENS.containsMatchIn(source))
        val declarations = FUNCTION_DECLARATION.findAll(source).toList()
        require(declarations.size == 1)
        val declaration = declarations.single()
        require(declaration.groupValues[1] == functionName)
        val declaredInputs = declaration.groupValues[2]
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
        require(declaredInputs == inputNames)
    }
}

internal data class WidgetAssembly(
    val feasibility: WidgetFeasibilityArtifact,
    val algorithm: WidgetAlgorithmArtifact,
    val callFunctions: List<WidgetSourceFragmentArtifact>,
    val planFunction: WidgetSourceFragmentArtifact,
    val renderFunction: WidgetSourceFragmentArtifact,
    val source: String,
)

internal enum class WidgetAuthoringStage {
    Feasibility,
    Algorithm,
    CallFunction,
    PlanFunction,
    RenderFunction,
    AssemblyValidation,
}

internal enum class WidgetAuthoringStageFailureCode(val diagnosticWireName: String) {
    InvalidSchema("invalid_schema"),
    InvalidFeasibilityShape("invalid_feasibility_shape"),
    InvalidFeasibilityJsonRoot("invalid_feasibility_json_root"),
    InvalidFeasibilityFields("invalid_feasibility_fields"),
    InvalidFeasibilityProtocol("invalid_feasibility_protocol"),
    InvalidFeasibilityDisplayName("invalid_feasibility_display_name"),
    InvalidFeasibilityEnabled("invalid_feasibility_enabled"),
    InvalidFeasibilitySchedule("invalid_feasibility_schedule"),
    InvalidFeasibilityTools("invalid_feasibility_tools"),
    InvalidFeasibilityRuntime("invalid_feasibility_runtime"),
    InvalidFeasibilityPresentation("invalid_feasibility_presentation"),
    InvalidFeasibilityOutcome("invalid_feasibility_outcome"),
    InvalidAlgorithm("invalid_algorithm"),
    InvalidSource("invalid_source"),
    InvalidRenderExecution("invalid_render_execution"),
    InvalidToolArguments("invalid_tool_arguments"),
    InvalidToolSelection("invalid_tool_selection"),
    InvalidPlan("invalid_plan"),
    InvalidPresentation("invalid_presentation"),
    InvalidPresentationNodeType("invalid_presentation_node_type"),
    InvalidPresentationFields("invalid_presentation_fields"),
    InvalidPresentationTone("invalid_presentation_tone"),
    InvalidPresentationProvenance("invalid_presentation_provenance"),
    InvalidPresentationSuccessHandling("invalid_presentation_success_handling"),
    InvalidPresentationEmptyHandling("invalid_presentation_empty_handling"),
    InvalidPresentationFailureHandling("invalid_presentation_failure_handling"),
    CapabilityExpansion("capability_expansion"),
    ResourceLimit("resource_limit"),
    MissingArtifact("missing_artifact"),
    TimedOut("timed_out"),
    DeviceRecoveryTimedOut("device_recovery_timed_out"),
    RuntimeUnavailable("runtime_unavailable"),
}

internal data class WidgetAuthoringAttemptFailure(
    val stage: WidgetAuthoringStage,
    val attempt: Int,
    val code: WidgetAuthoringStageFailureCode,
    val argumentBytes: Long?,
) {
    init {
        require(attempt in 1..WidgetAuthoringPipelinePolicy.MAX_REPAIRS + 1)
        require(argumentBytes == null || argumentBytes >= 0)
    }
}

internal sealed interface WidgetAuthoringProgress {
    data object AnalyzingFeasibility : WidgetAuthoringProgress
    data object WaitingForDeviceRecovery : WidgetAuthoringProgress
    data object ReloadingModel : WidgetAuthoringProgress
    data object DesigningAlgorithm : WidgetAuthoringProgress
    data class GeneratingCall(val index: Int, val count: Int) : WidgetAuthoringProgress
    data object GeneratingPlan : WidgetAuthoringProgress
    data object GeneratingPresentation : WidgetAuthoringProgress
    data object ValidatingAssembly : WidgetAuthoringProgress
    data class Repairing(
        val stage: WidgetAuthoringStage,
        val repair: Int,
        val maximum: Int = WidgetAuthoringPipelinePolicy.MAX_REPAIRS,
    ) : WidgetAuthoringProgress
    data object DraftReady : WidgetAuthoringProgress
}

internal data class WidgetAuthoringAttemptBudget(
    val maximumGenerations: Int = WidgetAuthoringPipelinePolicy.MAX_GENERATIONS,
    val maximumRepairs: Int = WidgetAuthoringPipelinePolicy.MAX_REPAIRS,
) {
    init {
        require(maximumGenerations in 1..WidgetAuthoringPipelinePolicy.MAX_GENERATIONS)
        require(maximumRepairs in 0..WidgetAuthoringPipelinePolicy.MAX_REPAIRS)
    }

    private var generations = 0
    private var repairs = 0

    fun consumeGeneration(isRepair: Boolean): Boolean {
        if (generations >= maximumGenerations) return false
        if (isRepair && repairs >= maximumRepairs) return false
        generations++
        if (isRepair) repairs++
        return true
    }

    fun repairCount(): Int = repairs
}

internal object WidgetAuthoringStageSchemas {
    val feasibility: String = """{"name":"$SUBMIT_WIDGET_FEASIBILITY_TOOL","description":"Submit one small feasibility decision. For achievable, message is a concise display title of at most 80 characters, not a copy of the instruction. The application derives protocol metadata, tool versions, runtime grants, presentation grants, enablement, and terminal normalization.","parameters":{"type":"object","additionalProperties":false,"properties":{"outcome":{"type":"string","enum":["achievable","unachievable","needs_clarification"]},"message":{"type":"string","minLength":1,"maxLength":512},"periodicIntervalHours":{"type":["integer","null"],"enum":[null,1,6,12,24]},"toolIds":{"type":"array","maxItems":4,"uniqueItems":true,"items":{"type":"string"}}},"required":["outcome","message","periodicIntervalHours","toolIds"]}}"""

    val algorithm: String = """{"name":"$SUBMIT_WIDGET_ALGORITHM_TOOL","description":"Submit one bounded typed algorithm. Protocol metadata, runtime authority, and tool versions are application-derived. This captures data only.","parameters":{"type":"object","additionalProperties":false,"properties":{"steps":{"type":"array","minItems":1,"maxItems":16,"items":{"type":"object","additionalProperties":false,"properties":{"id":{"type":"string","pattern":"^[a-z][a-z0-9_]{0,31}$"},"kind":{"type":"string","enum":["runtime_input","transform","tool_call"]},"objective":{"type":"string","minLength":1,"maxLength":512},"dependsOn":{"type":"array","uniqueItems":true,"items":{"type":"string"}},"selectionMode":{"type":"string","enum":["first","seeded_random"],"description":"Required for wikipedia_pages version 2 tool calls; omit for other steps."},"toolId":{"type":["string","null"],"description":"Required only when kind is tool_call; otherwise omit."}},"required":["id","kind","objective","dependsOn"]}},"presentationObjective":{"type":"string","minLength":1,"maxLength":512},"renderCapability":{"type":"string","enum":["collection_card","collection_list","custom_source"]}},"required":["steps","presentationObjective","renderCapability"]}}"""

    fun sourceFragment(toolName: String): String = """{"name":"$toolName","description":"Submit one bounded JavaScript function source. Artifact identity and signature are fixed by the application. This captures source only and executes nothing.","parameters":{"type":"object","additionalProperties":false,"properties":{"source":{"type":"string","minLength":1,"maxLength":6144}},"required":["source"]}}"""

    val collectionCard: String = """{"name":"$SUBMIT_WIDGET_COLLECTION_CARD_TOOL","description":"Submit one declarative collection card. Copy alias, array path and item property names from the supplied choices; only fallback is literal display text. Do not write JavaScript.","parameters":{"type":"object","additionalProperties":false,"properties":{"alias":{"type":"string","description":"Exact tool-result alias, not tool id"},"items":{"type":"string","description":"Exact array path relative to payload"},"title":{"type":"string","description":"Exact text property name on an array item, not display text"},"titleSuffixField":{"type":"string","description":"Optional item string or integer property to append in parentheses to the title"},"body":{"type":"string","description":"Optional text property name on an array item, not display text"},"url":{"type":"string","description":"Optional HTTPS URL property name on an array item"},"fallback":{"type":"string","minLength":1,"maxLength":256,"description":"Literal text for failure or empty array"},"selection":{"type":"string","enum":["first","seeded_random"]}},"required":["alias","items","title","fallback","selection"]}}"""

    val collectionList: String = """{"name":"$SUBMIT_WIDGET_COLLECTION_LIST_TOOL","description":"Submit one declarative list of up to ten unmodified results from one tool array. Copy alias, array path and item property names; only fallback is literal display text. Do not write JavaScript.","parameters":{"type":"object","additionalProperties":false,"properties":{"alias":{"type":"string","description":"Exact tool-result alias, not tool id"},"items":{"type":"string","description":"Exact array path relative to payload"},"title":{"type":"string","description":"Exact text property name on an array item, not display text"},"body":{"type":"string","description":"Optional text property name on an array item, not display text"},"url":{"type":"string","description":"Optional HTTPS URL property name on an array item"},"fallback":{"type":"string","minLength":1,"maxLength":256,"description":"Literal text for failure or empty array"}},"required":["alias","items","title","fallback"]}}"""
}

internal fun WidgetFeasibilityArtifact.toUntrustedProposal(source: String): UntrustedWidgetProposal = UntrustedWidgetProposal(
    displayName = displayName,
    enabled = enabled,
    periodicIntervalHours = periodicIntervalHours,
    tools = tools.mapTo(mutableSetOf()) { it.capability },
    runtimeValues = runtimeValues,
    presentation = presentation,
    source = source,
)

private fun JsonObject.strictNullableLong(name: String): Long? {
    val value = get(name) ?: error("Missing nullable number")
    if (value is JsonNull || value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
    return value.asBigDecimal.longValueExact()
}

private fun JsonObject.strictNullableString(name: String, maximum: Int): String? {
    val value = get(name) ?: error("Missing nullable string")
    if (value is JsonNull || value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
    return value.asString.also { require(it.isNotBlank() && it.length <= maximum) }
}

private fun String.boundedText(): String = also {
    require(isNotBlank() && length <= WidgetAuthoringPipelinePolicy.MAX_TEXT_CHARS)
}

private fun String.toDisplayName(): String {
    val normalized = trim().replace(WHITESPACE, " ")
    if (normalized.length <= MAX_PIPELINE_DISPLAY_NAME_CHARS) return normalized
    return normalized.take(MAX_PIPELINE_DISPLAY_NAME_CHARS - 1).trimEnd() + DISPLAY_NAME_ELLIPSIS
}

private fun <T> JsonArray.strictEnumSet(values: Iterable<T>, wireName: (T) -> String): Set<T> {
    val byName = values.associateBy(wireName)
    val result = map { element ->
        require(element.isJsonPrimitive && element.asJsonPrimitive.isString)
        requireNotNull(byName[element.asString])
    }.toSet()
    require(result.size == size())
    return result
}

private val ALGORITHM_STEP_ID_PATTERN = Regex("[a-z][a-z0-9_]{0,31}")
private const val MAX_PIPELINE_DISPLAY_NAME_CHARS = 80
private const val DISPLAY_NAME_ELLIPSIS = '…'
private val WHITESPACE = Regex("\\s+")
private val FUNCTION_DECLARATION = Regex("\\bfunction\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)")
private val FORBIDDEN_SOURCE_TOKENS = Regex(
    "\\b(?:Date|eval|Function|fetch|XMLHttpRequest|WebSocket|require|import|globalThis|process|Java|Packages)\\b|Math\\s*\\.\\s*random|=>|\\b(?:async|await)\\b",
)
