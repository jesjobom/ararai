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
            message.take(MAX_PIPELINE_DISPLAY_NAME_CHARS),
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
            } else if (outcome == WidgetFeasibilityOutcome.Achievable) {
                MAX_PIPELINE_DISPLAY_NAME_CHARS
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

internal data class WidgetAlgorithmStep(
    val id: String,
    val kind: WidgetAlgorithmStepKind,
    val objective: String,
    val dependencies: List<String>,
    val tool: WidgetToolCapability?,
    val runtimeInputs: Set<WidgetRuntimeValue>,
)

internal data class WidgetAlgorithmArtifact(
    val steps: List<WidgetAlgorithmStep>,
    val presentationObjective: String,
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
    ): WidgetAlgorithmParseResult = try {
        require(envelope.outcome == WidgetFeasibilityOutcome.Achievable)
        require(raw.utf8Size() <= WidgetAuthoringPipelinePolicy.MAX_ARTIFACT_BYTES)
        val root = StrictJson.parse(
            raw,
            maxStringChars = WidgetAuthoringPipelinePolicy.MAX_TEXT_CHARS,
        ).requiredObject()
        root.requireFields("protocolVersion", "steps", "presentationObjective")
        require(root.requiredInt("protocolVersion") == WIDGET_AUTHORING_PROTOCOL_VERSION)
        val steps = root.requiredArray("steps").map { element -> parseStep(element.requiredObject()) }
        require(steps.isNotEmpty() && steps.size <= WidgetAuthoringPipelinePolicy.MAX_ALGORITHM_STEPS)
        require(steps.map { it.id }.toSet().size == steps.size)
        validateDependencies(steps)
        val selectedTools = envelope.tools.map { it.capability }.toSet()
        val calls = steps.filter { it.kind == WidgetAlgorithmStepKind.ToolCall }
        require(calls.size <= WidgetAuthoringPipelinePolicy.MAX_TOOLS)
        require(calls.all { it.tool in selectedTools })
        require(calls.map { it.tool }.toSet() == selectedTools)
        require(steps.flatMap { it.runtimeInputs }.all { it in envelope.runtimeValues })
        rejectLiveResultDependentCalls(steps)
        WidgetAlgorithmParseResult.Valid(
            WidgetAlgorithmArtifact(steps, root.requiredString("presentationObjective").boundedText()),
        )
    } catch (_: RuntimeException) {
        WidgetAlgorithmParseResult.Invalid(WidgetAuthoringStageFailureCode.InvalidAlgorithm)
    }

    private fun parseStep(step: JsonObject): WidgetAlgorithmStep {
        step.requireFields("id", "kind", "objective", "dependsOn", "toolId", "contractVersion", "runtimeInputs")
        val id = step.requiredString("id")
        require(ALGORITHM_STEP_ID_PATTERN.matches(id))
        val kind = WidgetAlgorithmStepKind.entries.single { it.wireName == step.requiredString("kind") }
        val dependencies = step.requiredArray("dependsOn").map { it.asString }
        require(dependencies.toSet().size == dependencies.size)
        val toolId = step.strictNullableString("toolId", 64)
        val contractVersion = step.strictNullableInt("contractVersion")
        val tool = if (kind == WidgetAlgorithmStepKind.ToolCall) {
            WidgetToolCapability(requireNotNull(toolId), requireNotNull(contractVersion))
        } else {
            require(toolId == null && contractVersion == null)
            null
        }
        return WidgetAlgorithmStep(
            id = id,
            kind = kind,
            objective = step.requiredString("objective").boundedText(),
            dependencies = dependencies,
            tool = tool,
            runtimeInputs = step.requiredArray("runtimeInputs")
                .strictEnumSet(WidgetRuntimeValue.entries) { it.wireName },
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
        root.requireFields("protocolVersion", "artifactId", "functionName", "inputNames", "source")
        require(root.requiredInt("protocolVersion") == WIDGET_AUTHORING_PROTOCOL_VERSION)
        val artifactId = root.requiredString("artifactId")
        val functionName = root.requiredString("functionName")
        val inputs = root.requiredArray("inputNames").map { it.asString }
        val source = root.requiredString("source")
        require(artifactId == expectedArtifactId)
        require(functionName == expectedFunctionName)
        require(inputs == expectedInputNames)
        require(source.utf8Size() <= WidgetAuthoringPipelinePolicy.MAX_FRAGMENT_BYTES)
        requireSafeSingleFunction(source, functionName, inputs)
        WidgetSourceFragmentParseResult.Valid(
            WidgetSourceFragmentArtifact(artifactId, functionName, inputs, source),
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
    InvalidToolArguments("invalid_tool_arguments"),
    InvalidPlan("invalid_plan"),
    InvalidPresentation("invalid_presentation"),
    CapabilityExpansion("capability_expansion"),
    ResourceLimit("resource_limit"),
    MissingArtifact("missing_artifact"),
    TimedOut("timed_out"),
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
    val feasibility: String = """{"name":"$SUBMIT_WIDGET_FEASIBILITY_TOOL","description":"Submit one small feasibility decision. The application derives protocol metadata, tool versions, runtime grants, presentation grants, enablement, and terminal normalization.","parameters":{"type":"object","additionalProperties":false,"properties":{"outcome":{"type":"string","enum":["achievable","unachievable","needs_clarification"]},"message":{"type":"string","minLength":1,"maxLength":512},"periodicIntervalHours":{"type":["integer","null"],"enum":[null,1,6,12,24]},"toolIds":{"type":"array","maxItems":4,"uniqueItems":true,"items":{"type":"string"}}},"required":["outcome","message","periodicIntervalHours","toolIds"]}}"""

    val algorithm: String = """{"name":"$SUBMIT_WIDGET_ALGORITHM_TOOL","description":"Submit one bounded typed algorithm. This captures data only.","parameters":{"type":"object","additionalProperties":false,"properties":{"protocolVersion":{"type":"integer","const":1},"steps":{"type":"array","minItems":1,"maxItems":16,"items":{"type":"object","additionalProperties":false,"properties":{"id":{"type":"string","pattern":"^[a-z][a-z0-9_]{0,31}$"},"kind":{"type":"string","enum":["runtime_input","transform","tool_call"]},"objective":{"type":"string","minLength":1,"maxLength":512},"dependsOn":{"type":"array","uniqueItems":true,"items":{"type":"string"}},"toolId":{"type":["string","null"]},"contractVersion":{"type":["integer","null"]},"runtimeInputs":{"type":"array","uniqueItems":true,"items":{"type":"string","enum":["locale","timezone","local_time","seed"]}}},"required":["id","kind","objective","dependsOn","toolId","contractVersion","runtimeInputs"]}},"presentationObjective":{"type":"string","minLength":1,"maxLength":512}},"required":["protocolVersion","steps","presentationObjective"]}}"""

    fun sourceFragment(toolName: String): String = """{"name":"$toolName","description":"Submit one bounded JavaScript function artifact. This captures source only and executes nothing.","parameters":{"type":"object","additionalProperties":false,"properties":{"protocolVersion":{"type":"integer","const":1},"artifactId":{"type":"string"},"functionName":{"type":"string"},"inputNames":{"type":"array","items":{"type":"string"}},"source":{"type":"string","minLength":1,"maxLength":6144}},"required":["protocolVersion","artifactId","functionName","inputNames","source"]}}"""
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

private fun JsonObject.strictNullableInt(name: String): Int? = strictNullableLong(name)?.also {
    require(it in Int.MIN_VALUE..Int.MAX_VALUE)
}?.toInt()

private fun JsonObject.strictNullableString(name: String, maximum: Int): String? {
    val value = get(name) ?: error("Missing nullable string")
    if (value is JsonNull || value.isJsonNull) return null
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
    return value.asString.also { require(it.isNotBlank() && it.length <= maximum) }
}

private fun String.boundedText(): String = also {
    require(isNotBlank() && length <= WidgetAuthoringPipelinePolicy.MAX_TEXT_CHARS)
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
private val FUNCTION_DECLARATION = Regex("\\bfunction\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)")
private val FORBIDDEN_SOURCE_TOKENS = Regex(
    "\\b(?:Date|eval|Function|fetch|XMLHttpRequest|WebSocket|require|import|globalThis|process|Java|Packages)\\b|Math\\s*\\.\\s*random|=>|\\b(?:async|await)\\b",
)
