package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability

internal sealed interface WidgetAuthoringArtifactValidation<out T> {
    data class Valid<T>(val artifact: T) : WidgetAuthoringArtifactValidation<T>
    data class Invalid(val code: WidgetAuthoringStageFailureCode) : WidgetAuthoringArtifactValidation<Nothing>
}

internal class WidgetAuthoringStageValidators(
    private val validator: WidgetAuthoringPipelineValidator,
) {
    fun feasibility(
        raw: String,
        availableTools: Set<WidgetToolCapability>,
    ): WidgetAuthoringArtifactValidation<WidgetFeasibilityArtifact> = when (
        val parsed = WidgetFeasibilityParser.parse(raw, availableTools)
    ) {
        is WidgetFeasibilityParseResult.Valid -> WidgetAuthoringArtifactValidation.Valid(parsed.artifact)
        is WidgetFeasibilityParseResult.Invalid -> WidgetAuthoringArtifactValidation.Invalid(parsed.code)
    }

    fun algorithm(
        raw: String,
        feasibility: WidgetFeasibilityArtifact,
    ): WidgetAuthoringArtifactValidation<WidgetAlgorithmArtifact> = when (
        val parsed = WidgetAlgorithmParser.parse(raw, feasibility)
    ) {
        is WidgetAlgorithmParseResult.Valid -> WidgetAuthoringArtifactValidation.Valid(parsed.artifact)
        is WidgetAlgorithmParseResult.Invalid -> WidgetAuthoringArtifactValidation.Invalid(parsed.code)
    }

    suspend fun callFunction(
        raw: String,
        step: WidgetAlgorithmStep,
        feasibility: WidgetFeasibilityArtifact,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringArtifactValidation<WidgetSourceFragmentArtifact> {
        val parsed = WidgetSourceFragmentParser.parse(
            raw,
            expectedArtifactId = step.id,
            expectedFunctionName = callFunctionName(step.id),
            expectedInputNames = listOf("runtime"),
        )
        return when (parsed) {
            is WidgetSourceFragmentParseResult.Invalid -> WidgetAuthoringArtifactValidation.Invalid(parsed.code)
            is WidgetSourceFragmentParseResult.Valid -> validator.validateCallFunction(
                parsed.artifact,
                step,
                feasibility,
                runtimeContext,
            ).toValidation(parsed.artifact)
        }
    }

    suspend fun plan(
        raw: String,
        callFunctions: List<WidgetSourceFragmentArtifact>,
        algorithm: WidgetAlgorithmArtifact,
        feasibility: WidgetFeasibilityArtifact,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringArtifactValidation<WidgetSourceFragmentArtifact> {
        val parsed = WidgetSourceFragmentParser.parse(
            raw,
            expectedArtifactId = "plan",
            expectedFunctionName = "plan",
            expectedInputNames = listOf("runtime"),
        )
        return when (parsed) {
            is WidgetSourceFragmentParseResult.Invalid -> WidgetAuthoringArtifactValidation.Invalid(parsed.code)
            is WidgetSourceFragmentParseResult.Valid -> validator.validatePlanFunction(
                callFunctions,
                parsed.artifact,
                algorithm,
                feasibility,
                runtimeContext,
            ).toValidation(parsed.artifact)
        }
    }

    suspend fun render(
        raw: String,
        callFunctionsAndPlan: List<WidgetSourceFragmentArtifact>,
        algorithm: WidgetAlgorithmArtifact,
        feasibility: WidgetFeasibilityArtifact,
        runtimeContext: WidgetRuntimeContext,
    ): WidgetAuthoringArtifactValidation<WidgetSourceFragmentArtifact> {
        val parsed = WidgetSourceFragmentParser.parse(
            raw,
            expectedArtifactId = "render",
            expectedFunctionName = "render",
            expectedInputNames = listOf("runtime", "outcomes", "state"),
        )
        return when (parsed) {
            is WidgetSourceFragmentParseResult.Invalid -> WidgetAuthoringArtifactValidation.Invalid(parsed.code)
            is WidgetSourceFragmentParseResult.Valid -> validator.validateRenderFunction(
                callFunctionsAndPlan,
                parsed.artifact,
                algorithm,
                feasibility,
                runtimeContext,
            ).toValidation(parsed.artifact)
        }
    }
}

private fun <T> WidgetAuthoringStageFailureCode?.toValidation(
    artifact: T,
): WidgetAuthoringArtifactValidation<T> = if (this == null) {
    WidgetAuthoringArtifactValidation.Valid(artifact)
} else {
    WidgetAuthoringArtifactValidation.Invalid(this)
}
