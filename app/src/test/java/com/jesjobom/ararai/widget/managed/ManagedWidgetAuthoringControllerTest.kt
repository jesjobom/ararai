package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelToolCapabilities
import com.jesjobom.ararai.model.PROPOSE_WIDGET_TOOL_NAME
import com.jesjobom.ararai.tools.ApplicationTool
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.tools.ApplicationToolOperationalState
import com.jesjobom.ararai.tools.ApplicationToolRegistry
import com.jesjobom.ararai.tools.applicationToolBinding
import com.jesjobom.ararai.widget.runtime.PlannedWidgetToolCall
import com.jesjobom.ararai.widget.runtime.WidgetProgramParserTest
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedWidgetAuthoringControllerTest {
    @Test
    fun `invalid output can be retried explicitly without retaining the rejected draft`() = runTest {
        val engine = ProposalQueueEngine(mutableListOf("{}", validProposal()))
        val controller = controller(engine)

        assertEquals(
            ManagedWidgetAuthoringResult.ProposalInvalid(WidgetDraftFailureCode.InvalidProposal),
            controller.generate(model("first"), INFERENCE, PROMPT, RUNTIME_CONTEXT),
        )
        assertNull(controller.currentDraft)

        val retried = controller.generate(model("first"), INFERENCE, PROMPT, RUNTIME_CONTEXT)
        assertTrue(retried is ManagedWidgetAuthoringResult.DraftReady)
        assertTrue(controller.currentDraft != null)
        controller.decline()
        assertNull(controller.currentDraft)
    }

    @Test
    fun `missing and ineligible models keep authorship unavailable`() = runTest {
        val engine = ProposalQueueEngine(mutableListOf(validProposal()))
        val controller = controller(engine)

        assertEquals(
            ManagedWidgetAuthoringResult.ModelUnavailable,
            controller.generate(null, null, PROMPT, RUNTIME_CONTEXT),
        )
        assertEquals(
            ManagedWidgetAuthoringResult.ModelUnavailable,
            controller.generate(
                model("ineligible").copy(toolCapabilities = ModelToolCapabilities()),
                INFERENCE,
                PROMPT,
                RUNTIME_CONTEXT,
            ),
        )
        assertTrue(engine.loadedModelIds.isEmpty())
    }

    @Test
    fun `each explicit retry uses the currently selected eligible model`() = runTest {
        val engine = ProposalQueueEngine(mutableListOf(validProposal(), validProposal()))
        val controller = controller(engine)

        controller.generate(model("first"), INFERENCE, PROMPT, RUNTIME_CONTEXT)
        controller.generate(model("replacement"), INFERENCE, PROMPT, RUNTIME_CONTEXT)

        assertEquals(listOf("first", "replacement"), engine.loadedModelIds)
    }

    private fun controller(engine: LocalLlmEngine): ManagedWidgetAuthoringController {
        val registry = ApplicationToolRegistry(
            listOf(
                applicationToolBinding(
                    contract = CONTRACT,
                    state = { ApplicationToolOperationalState(enabled = true, ready = true) },
                    executor = ApplicationTool(
                        "Wikipedia pages",
                        ApplicationToolCategory.ExternalKnowledge,
                    ) { _: Unit -> Unit },
                    decodeArguments = { Unit },
                    encodeResult = { "{}" },
                ),
            ),
        )
        val planner = WidgetDraftPlanner { _, _, _ ->
            WidgetDraftPlanResult.Valid(
                listOf(
                    PlannedWidgetToolCall(
                        "today",
                        WidgetToolCapability("wikipedia_pages", 1),
                        """{"query":"history","language":"en"}""",
                    ),
                ),
            )
        }
        return ManagedWidgetAuthoringController(
            WidgetAuthoringModelController(engine),
            WidgetDraftBuilder(registry, planner),
        )
    }

    private fun model(id: String) = LocalModel(
        id = id,
        name = id,
        filePath = "/models/$id.litertlm",
        toolCapabilities = ModelToolCapabilities(authoringToolNames = setOf(PROPOSE_WIDGET_TOOL_NAME)),
    )

    private class ProposalQueueEngine(
        private val proposals: MutableList<String>,
    ) : LocalLlmEngine {
        val loadedModelIds = mutableListOf<String>()

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            loadedModelIds += model.id
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            request.ephemeralTools.single().execute(proposals.removeAt(0))
            emit(GenerationEvent.Completed)
        }

        override suspend fun unload() = Unit
    }

    companion object {
        private val INFERENCE = InferenceConfig(2_048, 512, 0.7f, 0.9f)
        private val PROMPT = WidgetAuthoringPrompt("Create it", "{}")
        private val RUNTIME_CONTEXT = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-06T12:00:00", 42)
        private val CONTRACT = ApplicationToolContract(
            id = "wikipedia_pages",
            version = 1,
            displayName = "Wikipedia pages",
            category = ApplicationToolCategory.ExternalKnowledge,
            consumers = setOf(ApplicationToolConsumer.Widget),
            inputSchemaJson = """{"type":"object"}""",
            outputSchemaJson = """{"type":"object"}""",
        )

        private fun validProposal() = JsonObject().apply {
            addProperty("proposalVersion", 1)
            addProperty("displayName", "On this day")
            addProperty("enabled", true)
            addProperty("periodicIntervalHours", 24)
            add(
                "tools",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            addProperty("id", "wikipedia_pages")
                            addProperty("version", 1)
                        },
                    )
                },
            )
            add("runtime", JsonArray().apply { add("local_time") })
            add(
                "presentation",
                JsonArray().apply {
                    add("card")
                    add("text")
                },
            )
            addProperty("source", WidgetProgramParserTest.SOURCE)
        }.toString()
    }
}
