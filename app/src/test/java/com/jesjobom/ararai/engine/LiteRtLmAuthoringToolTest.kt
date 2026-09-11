package com.jesjobom.ararai.engine

import app.cash.turbine.test
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelToolCapabilities
import com.jesjobom.ararai.model.PROPOSE_WIDGET_TOOL_NAME
import com.jesjobom.ararai.model.WIDGET_AUTHORING_PIPELINE_V1
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtLmAuthoringToolTest {
    @Test
    fun `authoring capability requires its matching ephemeral tool and stays unavailable to normal chat`() = runTest {
        val bridge = RecordingAuthoringBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        val model = LocalModel(
            id = "gemma",
            name = "Gemma",
            filePath = "/models/gemma.litertlm",
            toolCapabilities = ModelToolCapabilities(
                toolNames = setOf("wikipedia_search"),
                authoringToolNames = setOf(PROPOSE_WIDGET_TOOL_NAME),
            ),
        )
        val captureTool = object : EphemeralLocalLlmTool {
            override val name = PROPOSE_WIDGET_TOOL_NAME
            override val descriptionJson = """{"name":"propose_widget","parameters":{"type":"object"}}"""
            override fun execute(argumentsJson: String) = "{}"
        }
        engine.load(model, InferenceConfig(128, 8, 0.7f, 0.9f))

        assertEquals(setOf("wikipedia_search", PROPOSE_WIDGET_TOOL_NAME), bridge.loadedToolNames)
        engine.generate(
            PromptRequest(
                content = MessageContent.TextPrompt("normal chat"),
                advertisedToolNames = setOf(PROPOSE_WIDGET_TOOL_NAME),
            ),
        ).test {
            assertUnsupported(awaitItem())
            awaitComplete()
        }
        engine.generate(
            PromptRequest(
                content = MessageContent.TextPrompt("author"),
                advertisedToolNames = setOf(PROPOSE_WIDGET_TOOL_NAME),
                ephemeralTools = listOf(captureTool),
            ),
        ).test {
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }
        engine.generate(
            PromptRequest(
                content = MessageContent.TextPrompt("mixed"),
                advertisedToolNames = setOf(PROPOSE_WIDGET_TOOL_NAME, "wikipedia_search"),
                ephemeralTools = listOf(captureTool),
            ),
        ).test {
            assertUnsupported(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `versioned pipeline capability allows only its closed ephemeral stage tools`() = runTest {
        val bridge = RecordingAuthoringBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        val model = LocalModel(
            id = "gemma-pipeline",
            name = "Gemma pipeline",
            filePath = "/models/gemma.litertlm",
            toolCapabilities = ModelToolCapabilities(
                authoringProtocolNames = setOf(WIDGET_AUTHORING_PIPELINE_V1),
            ),
        )
        val stageTool = object : EphemeralLocalLlmTool {
            override val name = "submit_widget_algorithm"
            override val descriptionJson = """{"name":"submit_widget_algorithm","parameters":{"type":"object"}}"""
            override fun execute(argumentsJson: String) = "{}"
        }
        val legacyTool = object : EphemeralLocalLlmTool {
            override val name = PROPOSE_WIDGET_TOOL_NAME
            override val descriptionJson = """{"name":"propose_widget","parameters":{"type":"object"}}"""
            override fun execute(argumentsJson: String) = "{}"
        }
        engine.load(model, InferenceConfig(128, 8, 0.2f, 0.9f))

        assertEquals(
            setOf(
                "submit_widget_feasibility",
                "submit_widget_algorithm",
                "submit_widget_call_function",
                "submit_widget_plan_function",
                "submit_widget_render_function",
            ),
            bridge.loadedToolNames,
        )
        engine.generate(
            PromptRequest(
                content = MessageContent.TextPrompt("stage"),
                advertisedToolNames = setOf(stageTool.name),
                ephemeralTools = listOf(stageTool),
            ),
        ).test {
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }
        engine.generate(
            PromptRequest(
                content = MessageContent.TextPrompt("legacy"),
                advertisedToolNames = setOf(legacyTool.name),
                ephemeralTools = listOf(legacyTool),
            ),
        ).test {
            assertUnsupported(awaitItem())
            awaitComplete()
        }
    }

    private fun assertUnsupported(event: GenerationEvent) {
        event as GenerationEvent.Failed
        assertEquals("Selected model does not support the requested tools", event.message)
        assertEquals(GenerationFailureKind.Expected, event.kind)
    }

    private class RecordingAuthoringBridge : LiteRtLmBridge {
        var loadedToolNames: Set<String> = emptySet()

        override suspend fun load(
            modelPath: String,
            config: InferenceConfig,
            useGpu: Boolean,
            inputCapabilities: ModelInputCapabilities,
            toolNames: Set<String>,
            profile: LiteRtLmWorkloadProfile,
        ): LiteRtLmSession {
            loadedToolNames = toolNames
            return object : LiteRtLmSession {
                override fun generate(
                    request: PromptRequest,
                    config: InferenceConfig,
                ): Flow<LiteRtLmChunk> = emptyFlow()
                override fun cancel() = Unit
                override fun close() = Unit
            }
        }
    }
}
