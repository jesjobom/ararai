package com.jesjobom.ararai.widget.managed

import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.widget.runtime.WidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetRequestedLimits
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetScriptResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAuthoringAssemblyBuilderTest {
    @Test
    fun `builds preview only from complete accepted checkpoint graph`() = runTest {
        val registry = ApplicationToolRegistry(emptyList())
        val javascript = AssemblyJavaScriptEngine()
        val builder = WidgetAuthoringAssemblyBuilder(
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
        )

        val result = builder.build(snapshot(), RUNTIME)

        assertTrue(result is WidgetAuthoringAssemblyResult.Ready)
        val draft = (result as WidgetAuthoringAssemblyResult.Ready).draft
        assertEquals("Status", draft.summary.displayName)
        assertEquals(PLAN_SOURCE + "\n\n" + RENDER_SOURCE, draft.program.source)
    }

    @Test
    fun `stale checkpoint prevents preview without executing source`() = runTest {
        val registry = ApplicationToolRegistry(emptyList())
        val javascript = AssemblyJavaScriptEngine()
        val builder = WidgetAuthoringAssemblyBuilder(
            WidgetAuthoringPipelineValidator(registry, javascript),
            WidgetDraftBuilder(registry, JavaScriptWidgetDraftPlanner(javascript)),
            registry,
        )
        val stale = snapshot().let { value ->
            value.copy(
                checkpoints = value.checkpoints.map { checkpoint ->
                    if (checkpoint.stage == WidgetAuthoringStageKey.Plan) {
                        checkpoint.copy(status = WidgetAuthoringCheckpointStatus.Stale)
                    } else {
                        checkpoint
                    }
                },
            )
        }

        val result = builder.build(stale, RUNTIME)

        assertEquals(
            WidgetAuthoringAssemblyResult.Invalid(WidgetAuthoringStageFailureCode.InvalidSchema),
            result,
        )
        assertEquals(0, javascript.calls)
    }

    private fun snapshot(): WidgetAuthoringSessionSnapshot {
        val session = WidgetAuthoringSession(
            id = "session",
            revision = 10,
            status = WidgetAuthoringSessionStatus.AwaitingStage,
            instruction = "Create a status widget",
            targetWidgetId = null,
            modelId = "e4b",
            modelArtifactDigest = DIGEST,
            inferenceConfigJson = "{}",
            protocolVersion = 1,
            schemaVersion = 1,
            toolContractDigest = DIGEST,
            currentStage = WidgetAuthoringStageKey.Assembly,
            activeAttemptId = null,
            createdAtMillis = 1,
            updatedAtMillis = 2,
        )
        return WidgetAuthoringSessionSnapshot(
            session,
            attempts = emptyList(),
            checkpoints = listOf(
                checkpoint(WidgetAuthoringStageKey.Feasibility, FEASIBILITY),
                checkpoint(WidgetAuthoringStageKey.Algorithm, ALGORITHM),
                checkpoint(WidgetAuthoringStageKey.Plan, planArtifact()),
                checkpoint(WidgetAuthoringStageKey.Render, renderArtifact()),
            ),
        )
    }

    private fun checkpoint(
        stage: WidgetAuthoringStageKey,
        artifact: String,
    ) = WidgetAuthoringCheckpoint(
        sessionId = "session",
        stage = stage,
        stageRevision = 1,
        acceptedAttemptId = "attempt-${stage.wireValue}",
        artifact = artifact,
        artifactDigest = authoringDigest(artifact),
        upstreamDigest = DIGEST,
        status = WidgetAuthoringCheckpointStatus.Accepted,
        acceptedAtMillis = 1,
    )

    private class AssemblyJavaScriptEngine : WidgetJavaScriptEngine {
        var calls = 0

        override suspend fun call(
            source: String,
            entrypoint: String,
            argumentsJson: List<String>,
            limits: WidgetRequestedLimits,
        ): WidgetScriptResult {
            calls++
            return when (entrypoint) {
                "plan" -> WidgetScriptResult.Success("[]")
                "render" -> WidgetScriptResult.Success(
                    "{\"type\":\"text\",\"text\":\"Status\",\"tone\":\"neutral\"}",
                )
                else -> error("Unexpected entrypoint")
            }
        }
    }

    private companion object {
        val DIGEST = "a".repeat(64)
        val RUNTIME = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-27T12:00:00-04:00", 7)
        const val FEASIBILITY =
            "{\"outcome\":\"achievable\",\"message\":\"Status\",\"periodicIntervalHours\":null,\"toolIds\":[]}"
        const val ALGORITHM =
            "{\"protocolVersion\":1,\"steps\":[{\"id\":\"input\",\"kind\":\"runtime_input\"," +
                "\"objective\":\"Read status\",\"dependsOn\":[]}],\"presentationObjective\":\"Show status\"}"
        const val PLAN_SOURCE = "function plan(runtime) { return []; }"
        const val RENDER_SOURCE =
            "function render(runtime, outcomes, state) { return {type:'text',text:'Status',tone:'neutral'}; }"

        fun planArtifact() = fragment("plan", "plan", "[\"runtime\"]", PLAN_SOURCE)

        fun renderArtifact() = fragment(
            "render",
            "render",
            "[\"runtime\",\"outcomes\",\"state\"]",
            RENDER_SOURCE,
        )

        fun fragment(
            id: String,
            functionName: String,
            inputs: String,
            source: String,
        ) = "{\"protocolVersion\":1,\"artifactId\":\"$id\",\"functionName\":\"$functionName\"," +
            "\"inputNames\":$inputs,\"source\":${jsonString(source)}}"

        fun jsonString(value: String): String = buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    else -> append(character)
                }
            }
            append('"')
        }
    }
}
