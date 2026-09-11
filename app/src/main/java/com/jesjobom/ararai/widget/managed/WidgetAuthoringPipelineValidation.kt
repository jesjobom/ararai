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
            val output = call(
                artifact.source,
                artifact.functionName,
                listOf(fixture.toJson(feasibility.runtimeValues)),
            ) ?: return WidgetAuthoringStageFailureCode.InvalidSource
            val calls = parsePlan("[$output]", feasibility) ?: return WidgetAuthoringStageFailureCode.InvalidPlan
            val planned = calls.singleOrNull() ?: return WidgetAuthoringStageFailureCode.InvalidPlan
            if (planned.alias != step.id || planned.tool != expectedTool) {
                return WidgetAuthoringStageFailureCode.CapabilityExpansion
            }
            if (
                registry.validateWidgetDraftCall(
                    planned.tool.id,
                    planned.tool.version,
                    planned.argumentsJson,
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
            val output = call(
                source,
                planFunction.functionName,
                listOf(fixture.toJson(feasibility.runtimeValues)),
            ) ?: return WidgetAuthoringStageFailureCode.InvalidSource
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
            syntheticOutcomes(algorithm, contracts, emptyCollections = false),
            syntheticOutcomes(algorithm, contracts, emptyCollections = true),
            failureOutcomes(algorithm),
        )
        for (outcomes in fixtureSets) {
            val output = call(
                source,
                renderFunction.functionName,
                listOf(
                    runtimeContext.toJson(feasibility.runtimeValues),
                    outcomes.toOutcomeJson(),
                    "{}",
                ),
            ) ?: return WidgetAuthoringStageFailureCode.InvalidSource
            val parsed = runCatching {
                WidgetPresentationParser.parse(
                    output,
                    capabilities(feasibility),
                    grant(feasibility),
                    outcomes,
                    FIXED_PIPELINE_LIMITS,
                )
            }.getOrNull() ?: return WidgetAuthoringStageFailureCode.InvalidPresentation
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

    private suspend fun call(source: String, entrypoint: String, arguments: List<String>): String? = try {
        when (val result = engine.call(source, entrypoint, arguments, FIXED_PIPELINE_LIMITS)) {
            is WidgetScriptResult.Success -> result.outputJson
            is WidgetScriptResult.Failure -> null
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: RuntimeException) {
        null
    }

    private fun parsePlan(raw: String, feasibility: WidgetFeasibilityArtifact): List<PlannedWidgetToolCall>? = runCatching {
        WidgetPlanParser.parse(raw, capabilities(feasibility), grant(feasibility), FIXED_PIPELINE_LIMITS)
    }.getOrNull()

    private fun contracts(feasibility: WidgetFeasibilityArtifact): Map<WidgetToolCapability, ApplicationToolContract> {
        val descriptors = registry.descriptors()
            .filter { ApplicationToolConsumer.Widget in it.consumers }
            .associateBy { WidgetToolCapability(it.id, it.version) }
        return feasibility.tools.associate { selected ->
            selected.capability to requireNotNull(descriptors[selected.capability])
        }
    }
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
