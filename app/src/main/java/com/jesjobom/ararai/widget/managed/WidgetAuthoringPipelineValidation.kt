@file:Suppress(
    "MaxLineLength",
    "ReturnCount",
    "TooManyFunctions",
    "CyclomaticComplexMethod",
    "MatchingDeclarationName",
)

package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.WidgetDraftToolValidation
import com.jesjobom.ararai.widget.runtime.PlannedWidgetToolCall
import com.jesjobom.ararai.widget.runtime.StrictJson
import com.jesjobom.ararai.widget.runtime.WidgetExecutionGrant
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetPlanParser
import com.jesjobom.ararai.widget.runtime.WidgetPresentationParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramCapabilities
import com.jesjobom.ararai.widget.runtime.WidgetRequestedLimits
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeFailureCode
import com.jesjobom.ararai.widget.runtime.WidgetRuntimePolicy
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import com.jesjobom.ararai.widget.runtime.WidgetToolOutcome
import com.jesjobom.ararai.widget.runtime.requiredArray
import com.jesjobom.ararai.widget.runtime.requiredObject
import com.jesjobom.ararai.widget.runtime.requiredString
import com.jesjobom.ararai.widget.runtime.toJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class WidgetAuthoringPipelineValidator(
    private val registry: ApplicationToolRegistry,
    private val engine: WidgetJavaScriptEngine,
) {
    suspend fun validateCallFunction(
        artifact: WidgetSourceFragmentArtifact,
        step: WidgetAlgorithmStep,
        feasibility: WidgetFeasibilityArtifact,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringStageFailureCode? {
        val expectedTool = step.tool ?: return WidgetAuthoringStageFailureCode.InvalidAlgorithm
        for (fixture in runtimeFixtures(runtimeContext)) {
            currentCoroutineContext().ensureActive()
            val output = when (
                val result = call(
                    artifact.source,
                    artifact.functionName,
                    listOf(fixture.toJson(feasibility.runtimeValues)),
                )
            ) {
                is WidgetScriptResult.Success -> result.outputJson
                is WidgetScriptResult.Failure -> return result.code.validationFailure(
                    WidgetAuthoringStageFailureCode.InvalidSource,
                )
            }
            val argumentsJson = when (val normalized = normalizeCallArguments(output, step, feasibility)) {
                is CallArgumentsNormalization.Valid -> normalized.argumentsJson
                is CallArgumentsNormalization.Invalid -> return normalized.code
            }
            if (
                registry.validateWidgetDraftCall(
                    expectedTool.id,
                    expectedTool.version,
                    argumentsJson,
                ) is WidgetDraftToolValidation.Invalid
            ) {
                return WidgetAuthoringStageFailureCode.InvalidToolArguments
            }
        }
        return null
    }

    suspend fun validatePlanFunction(
        callFunctions: List<WidgetSourceFragmentArtifact>,
        planFunction: WidgetSourceFragmentArtifact,
        algorithm: WidgetAlgorithmArtifact,
        feasibility: WidgetFeasibilityArtifact,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringStageFailureCode? {
        val source = joinSource(callFunctions + planFunction)
        val expectedSteps = algorithm.toolCallSteps
        for (fixture in runtimeFixtures(runtimeContext)) {
            val output = when (
                val result = call(
                    source,
                    planFunction.functionName,
                    listOf(fixture.toJson(feasibility.runtimeValues)),
                )
            ) {
                is WidgetScriptResult.Success -> result.outputJson
                is WidgetScriptResult.Failure -> return result.code.validationFailure(
                    WidgetAuthoringStageFailureCode.InvalidSource,
                )
            }
            val calls = parsePlan(output, feasibility) ?: return WidgetAuthoringStageFailureCode.InvalidPlan
            if (calls.map { it.alias } != expectedSteps.map { it.id } || calls.map { it.tool } != expectedSteps.map { it.tool }) {
                return WidgetAuthoringStageFailureCode.CapabilityExpansion
            }
            if (calls.any { call ->
                    registry.validateWidgetDraftCall(call.tool.id, call.tool.version, call.argumentsJson) is
                        WidgetDraftToolValidation.Invalid
                }
            ) {
                return WidgetAuthoringStageFailureCode.InvalidToolArguments
            }
        }
        return null
    }

    suspend fun validateRenderFunction(
        priorFragments: List<WidgetSourceFragmentArtifact>,
        renderFunction: WidgetSourceFragmentArtifact,
        algorithm: WidgetAlgorithmArtifact,
        feasibility: WidgetFeasibilityArtifact,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringStageFailureCode? {
        val source = joinSource(priorFragments + renderFunction)
        val contracts = contracts(feasibility)
        val fixtureSets = listOf(
            RenderFixture(
                syntheticOutcomes(algorithm, contracts, emptyCollections = false),
                WidgetAuthoringStageFailureCode.InvalidRenderExecution,
            ),
            RenderFixture(
                syntheticOutcomes(algorithm, contracts, emptyCollections = true),
                WidgetAuthoringStageFailureCode.InvalidPresentationEmptyHandling,
            ),
            RenderFixture(
                failureOutcomes(algorithm),
                WidgetAuthoringStageFailureCode.InvalidPresentationFailureHandling,
            ),
        )
        for (fixture in fixtureSets) {
            val output = when (
                val result = call(
                    source,
                    renderFunction.functionName,
                    listOf(
                        runtimeContext.toJson(feasibility.runtimeValues),
                        fixture.outcomes.toOutcomeJson(),
                        "{}",
                    ),
                )
            ) {
                is WidgetScriptResult.Success -> result.outputJson
                is WidgetScriptResult.Failure -> return result.code.validationFailure(fixture.executionFailure)
            }
            presentationShapeFailure(output)?.let { return it }
            val parsed = runCatching {
                WidgetPresentationParser.parse(
                    output,
                    capabilities(feasibility),
                    grant(feasibility),
                    fixture.outcomes,
                    FIXED_PIPELINE_LIMITS,
                )
            }.getOrNull() ?: return if (output.contains("\"https_link\"")) {
                WidgetAuthoringStageFailureCode.InvalidPresentationProvenance
            } else {
                WidgetAuthoringStageFailureCode.InvalidPresentation
            }
            checkNotNull(parsed)
        }
        return null
    }

    suspend fun validateExactAssembly(
        assembly: WidgetAssembly,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringStageFailureCode? {
        val planFailure = validatePlanFunction(
            assembly.callFunctions,
            assembly.planFunction,
            assembly.algorithm,
            assembly.feasibility,
            runtimeContext,
        )
        if (planFailure != null) return planFailure
        return validateRenderFunction(
            assembly.callFunctions + assembly.planFunction,
            assembly.renderFunction,
            assembly.algorithm,
            assembly.feasibility,
            runtimeContext,
        )
    }

    fun assemble(
        feasibility: WidgetFeasibilityArtifact,
        algorithm: WidgetAlgorithmArtifact,
        callFunctions: List<WidgetSourceFragmentArtifact>,
        planFunction: WidgetSourceFragmentArtifact,
        renderFunction: WidgetSourceFragmentArtifact,
    ): WidgetAssembly {
        require(callFunctions.map { it.artifactId } == algorithm.toolCallSteps.map { it.id })
        val fragments = callFunctions + planFunction + renderFunction
        val source = joinSource(fragments)
        require(source.toByteArray(Charsets.UTF_8).size <= ManagedWidgetPolicy.MAX_PROGRAM_SOURCE_BYTES)
        return WidgetAssembly(feasibility, algorithm, callFunctions.toList(), planFunction, renderFunction, source)
    }

    private suspend fun call(source: String, entrypoint: String, arguments: List<String>): WidgetScriptResult = try {
        engine.call(source, entrypoint, arguments, FIXED_PIPELINE_LIMITS)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        WidgetScriptResult.Failure(WidgetRuntimeFailureCode.RuntimeUnavailable)
    }

    private fun parsePlan(raw: String, feasibility: WidgetFeasibilityArtifact): List<PlannedWidgetToolCall>? = runCatching {
        WidgetPlanParser.parse(raw, capabilities(feasibility), grant(feasibility), FIXED_PIPELINE_LIMITS)
    }.getOrNull()

    private fun normalizeCallArguments(
        raw: String,
        step: WidgetAlgorithmStep,
        feasibility: WidgetFeasibilityArtifact,
    ): CallArgumentsNormalization {
        val value = runCatching { StrictJson.parse(raw).requiredObject() }.getOrNull()
            ?: return CallArgumentsNormalization.Invalid(WidgetAuthoringStageFailureCode.InvalidPlan)
        if (value.keySet() != LEGACY_CALL_FIELDS) {
            return CallArgumentsNormalization.Valid(StrictJson.canonical(value))
        }
        val planned = parsePlan("[$raw]", feasibility)?.singleOrNull()
            ?: return CallArgumentsNormalization.Invalid(WidgetAuthoringStageFailureCode.InvalidPlan)
        if (planned.alias != step.id || planned.tool != step.tool) {
            return CallArgumentsNormalization.Invalid(WidgetAuthoringStageFailureCode.CapabilityExpansion)
        }
        return CallArgumentsNormalization.Valid(planned.argumentsJson)
    }

    private fun contracts(feasibility: WidgetFeasibilityArtifact): Map<WidgetToolCapability, ApplicationToolContract> {
        val descriptors = registry.descriptors()
            .filter { ApplicationToolConsumer.Widget in it.consumers }
            .associateBy { WidgetToolCapability(it.id, it.version) }
        return feasibility.tools.associate { selected ->
            selected.capability to requireNotNull(descriptors[selected.capability])
        }
    }
}

internal fun deterministicPlanArtifact(
    callFunctions: List<WidgetSourceFragmentArtifact>,
    algorithm: WidgetAlgorithmArtifact,
): WidgetSourceFragmentArtifact {
    val steps = algorithm.toolCallSteps
    require(callFunctions.map { it.artifactId } == steps.map { it.id })
    val bindings = steps.zip(callFunctions).mapIndexed { index, (step, function) ->
        val tool = requireNotNull(step.tool)
        DeterministicPlanBinding(index, step.id, tool, function.functionName)
    }
    val source = buildString {
        append("function plan(runtime) {\n")
        bindings.forEach { binding ->
            append("  const value")
            append(binding.index)
            append(" = ")
            append(binding.functionName)
            append("(runtime);\n")
        }
        append("  return [")
        append(
            bindings.joinToString(",") { binding ->
                val alias = JsonPrimitive(binding.alias).toString()
                val toolId = JsonPrimitive(binding.tool.id).toString()
                "{alias:$alias,toolId:$toolId,contractVersion:${binding.tool.version}," +
                    "arguments:value${binding.index}&&value${binding.index}.alias===$alias&&" +
                    "value${binding.index}.toolId===$toolId&&value${binding.index}.contractVersion===" +
                    "${binding.tool.version}?value${binding.index}.arguments:value${binding.index}}"
            },
        )
        append("];\n}")
    }
    return WidgetSourceFragmentArtifact(
        artifactId = "plan",
        functionName = "plan",
        inputNames = listOf("runtime"),
        source = source,
    )
}

internal fun WidgetSourceFragmentArtifact.toModelArtifactJson(): String = StrictJson.canonical(
    JsonObject().apply { addProperty("source", source) },
)

private data class DeterministicPlanBinding(
    val index: Int,
    val alias: String,
    val tool: WidgetToolCapability,
    val functionName: String,
)

private data class RenderFixture(
    val outcomes: List<WidgetToolOutcome>,
    val executionFailure: WidgetAuthoringStageFailureCode,
)

private fun WidgetRuntimeFailureCode.validationFailure(
    scriptFailure: WidgetAuthoringStageFailureCode,
): WidgetAuthoringStageFailureCode = when (this) {
    WidgetRuntimeFailureCode.InvalidProgram,
    WidgetRuntimeFailureCode.IncompatibleProgram,
    WidgetRuntimeFailureCode.IntegrityMismatch,
    -> WidgetAuthoringStageFailureCode.InvalidSource
    WidgetRuntimeFailureCode.CapabilityDenied,
    WidgetRuntimeFailureCode.PolicyViolation,
    -> WidgetAuthoringStageFailureCode.CapabilityExpansion
    WidgetRuntimeFailureCode.InvalidPlan -> WidgetAuthoringStageFailureCode.InvalidPlan
    WidgetRuntimeFailureCode.InvalidPresentation -> WidgetAuthoringStageFailureCode.InvalidPresentation
    WidgetRuntimeFailureCode.ScriptError -> scriptFailure
    WidgetRuntimeFailureCode.ResourceLimit -> WidgetAuthoringStageFailureCode.ResourceLimit
    WidgetRuntimeFailureCode.RuntimeUnavailable,
    WidgetRuntimeFailureCode.Cancelled,
    -> WidgetAuthoringStageFailureCode.RuntimeUnavailable
}

private sealed interface CallArgumentsNormalization {
    data class Valid(val argumentsJson: String) : CallArgumentsNormalization
    data class Invalid(val code: WidgetAuthoringStageFailureCode) : CallArgumentsNormalization
}

internal fun presentationShapeFailure(raw: String): WidgetAuthoringStageFailureCode? {
    val root = runCatching { StrictJson.parse(raw).requiredObject() }.getOrNull()
        ?: return WidgetAuthoringStageFailureCode.InvalidPresentationFields
    return presentationNodeFailure(root)
}

private fun presentationNodeFailure(node: JsonObject): WidgetAuthoringStageFailureCode? {
    val type = node.get("type")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        ?: return WidgetAuthoringStageFailureCode.InvalidPresentationFields
    val expectedFields = PRESENTATION_FIELDS[type]
        ?: return WidgetAuthoringStageFailureCode.InvalidPresentationNodeType
    if (node.keySet() != expectedFields) return WidgetAuthoringStageFailureCode.InvalidPresentationFields
    if (type == "text" || type == "value") {
        val tone = node.get("tone")
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
        if (tone !in PRESENTATION_TONES) return WidgetAuthoringStageFailureCode.InvalidPresentationTone
    }
    val children = when (type) {
        "card" -> listOf(node.get("child"))
        "column", "row" -> node.get("children")
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?.toList()
            ?: return WidgetAuthoringStageFailureCode.InvalidPresentationFields
        else -> emptyList()
    }
    for (child in children) {
        val childObject = child?.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: return WidgetAuthoringStageFailureCode.InvalidPresentationFields
        presentationNodeFailure(childObject)?.let { return it }
    }
    return null
}

internal fun WidgetAssembly.toProposal(): UntrustedWidgetProposal = feasibility.toUntrustedProposal(source)

internal fun callFunctionName(stepId: String): String = "buildCall" + stepId
    .split('_')
    .joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }

private fun capabilities(feasibility: WidgetFeasibilityArtifact) = WidgetProgramCapabilities(
    tools = feasibility.tools.mapTo(mutableSetOf()) { it.capability },
    runtimeValues = feasibility.runtimeValues,
    presentation = feasibility.presentation,
)

private fun grant(feasibility: WidgetFeasibilityArtifact) = WidgetExecutionGrant(
    tools = feasibility.tools.mapTo(mutableSetOf()) { it.capability },
    runtimeValues = feasibility.runtimeValues,
    presentation = feasibility.presentation,
)

private fun runtimeFixtures(base: WidgetRuntimeContext): List<WidgetRuntimeContext> = listOf(
    base,
    WidgetRuntimeContext(
        locale = "pt-BR",
        timezone = "America/Sao_Paulo",
        localTime = "2000-02-29T23:59:58-03:00",
        seed = if (base.seed == null) null else MAX_SAFE_JAVASCRIPT_INTEGER,
    ),
)

private fun syntheticOutcomes(
    algorithm: WidgetAlgorithmArtifact,
    contracts: Map<WidgetToolCapability, ApplicationToolContract>,
    emptyCollections: Boolean,
): List<WidgetToolOutcome> = algorithm.toolCallSteps.map { step ->
    val tool = requireNotNull(step.tool)
    val payload = synthesizeObject(contracts.getValue(tool).outputSchemaJson, emptyCollections)
    WidgetToolOutcome(
        alias = step.id,
        payload = payload,
        rejection = null,
    )
}

private fun failureOutcomes(algorithm: WidgetAlgorithmArtifact): List<WidgetToolOutcome> = algorithm.toolCallSteps.map { step ->
    WidgetToolOutcome(
        alias = step.id,
        payload = null,
        rejection = com.jesjobom.ararai.tools.ApplicationToolRejection.Unavailable,
    )
}

private fun List<WidgetToolOutcome>.toOutcomeJson(): String {
    val root = JsonObject()
    forEach { outcome ->
        root.add(
            outcome.alias,
            JsonObject().apply {
                if (outcome.payload != null) {
                    addProperty("status", "success")
                    add("payload", outcome.payload)
                } else {
                    addProperty("status", "failure")
                    addProperty("reason", "unavailable")
                }
            },
        )
    }
    return StrictJson.canonical(root)
}

private fun synthesizeObject(schemaJson: String, emptyCollections: Boolean): JsonObject {
    val schema = JsonParser.parseString(schemaJson).requiredObject()
    return synthesizeValue(schema, emptyCollections, propertyName = null).requiredObject()
}

private fun synthesizeValue(schema: JsonObject, emptyCollections: Boolean, propertyName: String?): JsonElement {
    schema.get("const")?.let { return it.deepCopy() }
    schema.getAsJsonArray("enum")?.firstOrNull()?.let { return it.deepCopy() }
    return when (schemaType(schema)) {
        "object" -> JsonObject().also { result ->
            schema.getAsJsonObject("properties")?.entrySet()?.forEach { (name, child) ->
                result.add(name, synthesizeValue(child.requiredObject(), emptyCollections, name))
            }
        }
        "array" -> JsonArray().also { result ->
            if (!emptyCollections) {
                schema.getAsJsonObject("items")?.let { result.add(synthesizeValue(it, false, propertyName)) }
            }
        }
        "integer", "number" -> JsonPrimitive(if (propertyName == "year") 2000 else 1)
        "boolean" -> JsonPrimitive(true)
        "string" -> JsonPrimitive(
            when (propertyName) {
                "canonicalUrl", "url" -> "https://en.wikipedia.org/wiki/Test"
                "language" -> "en"
                "kind" -> "success"
                "title" -> "Test"
                "extract", "text" -> "Synthetic fixture"
                else -> "value"
            },
        )
        else -> JsonNull.INSTANCE
    }
}

private fun schemaType(schema: JsonObject): String {
    val type = schema.get("type") ?: error("Schema type is required")
    if (type.isJsonPrimitive && type.asJsonPrimitive.isString) return type.asString
    if (type.isJsonArray) {
        return type.asJsonArray
            .asSequence()
            .filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            .map(JsonElement::getAsString)
            .firstOrNull { it != "null" }
            ?: error("Schema type array has no concrete type")
    }
    error("Schema type must be a string or string array")
}

private fun joinSource(fragments: List<WidgetSourceFragmentArtifact>): String = fragments.joinToString(separator = "\n\n", transform = WidgetSourceFragmentArtifact::source)

internal val FIXED_PIPELINE_LIMITS = WidgetRequestedLimits(
    memoryBytes = WidgetRuntimePolicy.MAX_MEMORY_BYTES,
    stackBytes = WidgetRuntimePolicy.MAX_STACK_BYTES,
    executionMillis = WidgetRuntimePolicy.MAX_EXECUTION_MILLIS,
    maxToolCalls = WidgetRuntimePolicy.MAX_TOOL_CALLS,
    maxOutputBytes = WidgetRuntimePolicy.MAX_OUTPUT_BYTES,
)

private const val MAX_SAFE_JAVASCRIPT_INTEGER = 9_007_199_254_740_991L
private val LEGACY_CALL_FIELDS = setOf("alias", "toolId", "contractVersion", "arguments")
private val PRESENTATION_TONES = setOf("neutral", "muted", "positive", "warning")
private val PRESENTATION_FIELDS = mapOf(
    "card" to setOf("type", "child"),
    "column" to setOf("type", "children"),
    "row" to setOf("type", "children"),
    "text" to setOf("type", "text", "tone"),
    "value" to setOf("type", "text", "tone"),
    "icon" to setOf("type", "name"),
    "https_link" to setOf("type", "label", "url", "sourceAlias", "sourceField"),
)
