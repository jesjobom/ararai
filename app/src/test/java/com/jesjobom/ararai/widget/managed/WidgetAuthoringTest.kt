package com.jesjobom.ararai.widget.managed

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.engine.GenerationEvent
import com.jesjobom.ararai.engine.GenerationFailureKind
import com.jesjobom.ararai.engine.LocalLlmEngine
import com.jesjobom.ararai.engine.PromptRequest
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelToolCapabilities
import com.jesjobom.ararai.model.PROPOSE_WIDGET_TOOL_NAME
import com.jesjobom.ararai.tools.ApplicationToolCategory
import com.jesjobom.ararai.tools.ApplicationToolConsumer
import com.jesjobom.ararai.tools.ApplicationToolContract
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCapability
import com.jesjobom.ararai.widget.runtime.WidgetProgramParserTest
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetAuthoringTest {
    @Test
    fun `proposal parser accepts one bounded complete draft`() {
        val result = WidgetProposalParser.parse(validProposal(), AVAILABLE_TOOLS)

        assertTrue(result is WidgetProposalParseResult.Valid)
        val proposal = (result as WidgetProposalParseResult.Valid).proposal
        assertEquals("On this day", proposal.displayName)
        assertEquals(24L, proposal.periodicIntervalHours)
        assertEquals(AVAILABLE_TOOLS, proposal.tools)
        assertEquals(setOf(WidgetRuntimeValue.Locale, WidgetRuntimeValue.LocalTime), proposal.runtimeValues)
        assertEquals(
            setOf(WidgetPresentationCapability.Card, WidgetPresentationCapability.Text),
            proposal.presentation,
        )
    }

    @Test
    fun `proposal schema rejects model owned control fields and arbitrary endpoints`() {
        listOf("widgetId", "revision", "programDigest", "consentDigest", "limits", "endpoint").forEach { field ->
            val proposal = validProposalObject().apply { addProperty(field, "invented") }.toString()

            assertEquals(
                WidgetProposalParseResult.Invalid(WidgetProposalFailureCode.Malformed),
                WidgetProposalParser.parse(proposal, AVAILABLE_TOOLS),
            )
        }
    }

    @Test
    fun `proposal parser rejects unsupported schedule tool version and capabilities`() {
        assertInvalid(
            WidgetProposalFailureCode.UnsupportedSchedule,
            validProposalObject().apply { addProperty("periodicIntervalHours", 2) },
        )
        assertInvalid(
            WidgetProposalFailureCode.UnsupportedTool,
            validProposalObject().apply {
                getAsJsonArray("tools").single().asJsonObject.addProperty("version", 99)
            },
        )
        assertInvalid(
            WidgetProposalFailureCode.UnsupportedTool,
            validProposalObject().apply {
                getAsJsonArray("tools").single().asJsonObject.addProperty("id", "invented_tool")
            },
        )
        assertInvalid(
            WidgetProposalFailureCode.UnsupportedCapability,
            validProposalObject().apply { getAsJsonArray("runtime").add("credentials") },
        )
        assertInvalid(
            WidgetProposalFailureCode.UnsupportedCapability,
            validProposalObject().apply { getAsJsonArray("presentation").add("webview") },
        )
    }

    @Test
    fun `proposal parser rejects malformed duplicate and oversized content`() {
        assertEquals(
            WidgetProposalParseResult.Invalid(WidgetProposalFailureCode.Malformed),
            WidgetProposalParser.parse("{}", AVAILABLE_TOOLS),
        )
        assertEquals(
            WidgetProposalParseResult.Invalid(WidgetProposalFailureCode.Malformed),
            WidgetProposalParser.parse(
                validProposal().replaceFirst("{", "{\"displayName\":\"duplicate\","),
                AVAILABLE_TOOLS,
            ),
        )
        assertEquals(
            WidgetProposalParseResult.Invalid(WidgetProposalFailureCode.TooLarge),
            WidgetProposalParser.parse(" ".repeat(ManagedWidgetPolicy.MAX_PROPOSAL_BYTES + 1), AVAILABLE_TOOLS),
        )
    }

    @Test
    fun `capture tool accepts at most one raw proposal without executing it`() {
        val tool = WidgetProposalCaptureTool()

        assertEquals("""{"accepted":true}""", tool.execute(validProposal()))
        assertEquals("""{"accepted":false,"error":"CALL_LIMIT_REACHED"}""", tool.execute("{}"))
        assertEquals(validProposal(), tool.capturedOrNull())
    }

    @Test
    fun `authoring context includes only widget descriptors and relevant normalized edit state`() {
        val definition = definition()
        val revision = revision()
        val prompt = WidgetAuthoringContextBuilder.build(
            userInstruction = "Change the title",
            toolContracts = listOf(widgetContract(), onThisDayContract(), modelOnlyContract()),
            currentDefinition = definition,
            currentRevision = revision,
        )

        assertTrue(prompt.apiContextJson.contains("wikipedia_pages"))
        assertTrue(prompt.apiContextJson.contains("wikipedia_on_this_day"))
        assertTrue(prompt.apiContextJson.contains("events.0.canonicalUrl"))
        assertTrue(prompt.apiContextJson.contains("runtime.currentLocalDateTime()"))
        assertTrue(prompt.apiContextJson.contains("runtime.language"))
        assertTrue(prompt.apiContextJson.contains("runtime.seededIndex"))
        assertTrue(prompt.apiContextJson.contains("wikipediaBehaviorGuidance"))
        assertTrue(prompt.apiContextJson.contains("\"mode\":\"edit\""))
        assertTrue(prompt.apiContextJson.contains("function plan(runtime)"))
        assertTrue(prompt.apiContextJson.contains("function render(runtime, outcomes, state)"))
        assertFalse(prompt.apiContextJson.contains("private_model_tool"))
        assertTrue(prompt.apiContextJson.contains(WidgetProgramParserTest.SOURCE))
        assertFalse(prompt.apiContextJson.contains(definition.id))
        assertFalse(prompt.apiContextJson.contains(definition.consentDigest))
        assertFalse(prompt.apiContextJson.contains(revision.programDigest))
        assertFalse(prompt.apiContextJson.contains("sourceSha256"))
        assertFalse(prompt.apiContextJson.contains("memoryBytes"))
    }

    @Test
    fun `authoring context owns creation semantics and maps natural variation to seeded selection`() {
        val prompt = WidgetAuthoringContextBuilder.build(
            userInstruction = "Mostre um evento aleatório da Wikipédia para hoje",
            toolContracts = listOf(onThisDayContract()),
        )

        val root = JsonParser.parseString(prompt.apiContextJson).asJsonObject
        val task = root.getAsJsonObject("authoringTask")
        val programApi = root.getAsJsonObject("programApi")

        assertEquals("create", task.get("mode").asString)
        assertTrue(task.get("instructionSemantics").asString.contains("desired widget behavior"))
        assertTrue(task.get("instructionSemantics").asString.contains("do not require"))
        assertTrue(programApi.get("randomSelection").asString.contains("aleatório"))
        assertTrue(programApi.get("randomSelection").asString.contains("runtime.seededIndex(length)"))
        assertTrue(programApi.get("randomSelection").asString.contains("Math.random is unavailable"))
        assertTrue(programApi.get("randomSelection").asString.contains("same runtime snapshot"))
    }

    @Test
    fun `eligible authoring captures one proposal after the ephemeral conversation completes`() = runTest {
        val engine = RecordingAuthoringEngine(AuthoringMode.Propose)
        val controller = WidgetAuthoringModelController(engine)

        val result = controller.propose(eligibleModel(), INFERENCE, prompt())

        assertEquals(WidgetAuthorshipResult.Proposed(validProposal()), result)
        assertEquals(setOf(PROPOSE_WIDGET_TOOL_NAME), engine.lastRequest?.advertisedToolNames)
        assertEquals(listOf(PROPOSE_WIDGET_TOOL_NAME), engine.lastRequest?.ephemeralTools?.map { it.name })
        assertEquals(null, engine.lastRequest?.chatSessionId)
        assertTrue(engine.generationReleased)
    }

    @Test
    fun `captured proposal is discarded when native generation never reaches a terminal callback`() = runTest {
        val engine = RecordingAuthoringEngine(AuthoringMode.ProposeThenWait)

        val result = WidgetAuthoringModelController(
            engine = engine,
            generationTimeoutMillis = 1_000,
        ).propose(eligibleModel(), INFERENCE, prompt())

        assertEquals(WidgetAuthorshipResult.GenerationTimedOut, result)
        assertTrue(engine.generationReleased)
    }

    @Test
    fun `authoring uses bounded deterministic inference without changing the saved preference`() = runTest {
        val engine = RecordingAuthoringEngine(AuthoringMode.Propose)
        val chatInference = INFERENCE.copy(contextTokens = 2_048, temperature = 0.3f)

        val result = WidgetAuthoringModelController(engine).propose(eligibleModel(), chatInference, prompt())

        assertTrue(result is WidgetAuthorshipResult.Proposed)
        assertEquals(MAX_WIDGET_AUTHORING_CONTEXT_TOKENS, engine.loadedConfig?.contextTokens)
        assertEquals(chatInference.promptReserveTokens, engine.loadedConfig?.promptReserveTokens)
        assertEquals(MAX_WIDGET_AUTHORING_TEMPERATURE, engine.loadedConfig?.temperature)
        assertEquals(2_048, chatInference.contextTokens)
        assertEquals(0.3f, chatInference.temperature)
    }

    @Test
    fun `authoring timeout cancels generation and returns a controlled result`() = runTest {
        val engine = RecordingAuthoringEngine(AuthoringMode.Wait)

        val result = WidgetAuthoringModelController(
            engine = engine,
            generationTimeoutMillis = 1_000,
        ).propose(eligibleModel(), INFERENCE, prompt())

        assertEquals(WidgetAuthorshipResult.GenerationTimedOut, result)
        assertTrue(engine.generationReleased)
    }

    @Test
    fun `ineligible no-call and generation failure attempts produce no proposal`() = runTest {
        val ineligibleEngine = RecordingAuthoringEngine(AuthoringMode.Propose)
        assertEquals(
            WidgetAuthorshipResult.IneligibleModel,
            WidgetAuthoringModelController(ineligibleEngine).propose(ineligibleModel(), INFERENCE, prompt()),
        )
        assertEquals(null, ineligibleEngine.lastRequest)

        val noCallEngine = RecordingAuthoringEngine(AuthoringMode.NoCall)
        assertEquals(
            WidgetAuthorshipResult.NoProposal,
            WidgetAuthoringModelController(noCallEngine)
                .propose(eligibleModel(), INFERENCE, prompt()),
        )
        assertTrue(noCallEngine.generationReleased)
        val failedEngine = RecordingAuthoringEngine(AuthoringMode.Fail)
        assertEquals(
            WidgetAuthorshipResult.GenerationFailed(GenerationFailureKind.Unexpected),
            WidgetAuthoringModelController(failedEngine)
                .propose(eligibleModel(), INFERENCE, prompt()),
        )
        assertTrue(failedEngine.generationReleased)
    }

    @Test
    fun `model load failures are distinct from generation failures`() = runTest {
        val engine = RecordingAuthoringEngine(AuthoringMode.LoadFail)

        val result = WidgetAuthoringModelController(engine).propose(eligibleModel(), INFERENCE, prompt())

        assertEquals(WidgetAuthorshipResult.ModelLoadFailed, result)
        assertEquals(null, engine.lastRequest)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `caller cancellation releases an active authoring conversation`() = runTest {
        val engine = RecordingAuthoringEngine(AuthoringMode.Wait)
        val attempt = launch {
            WidgetAuthoringModelController(engine).propose(eligibleModel(), INFERENCE, prompt())
        }
        runCurrent()

        attempt.cancel(CancellationException("navigation"))
        advanceUntilIdle()

        assertTrue(attempt.isCancelled)
        assertTrue(engine.generationReleased)
    }

    private fun assertInvalid(
        code: WidgetProposalFailureCode,
        proposal: JsonObject,
    ) {
        assertEquals(
            WidgetProposalParseResult.Invalid(code),
            WidgetProposalParser.parse(proposal.toString(), AVAILABLE_TOOLS),
        )
    }

    private fun prompt() = WidgetAuthoringPrompt("Create it", "{}")

    private fun eligibleModel() = LocalModel(
        id = "eligible",
        name = "Eligible",
        filePath = "/models/eligible.litertlm",
        toolCapabilities = ModelToolCapabilities(authoringToolNames = setOf(PROPOSE_WIDGET_TOOL_NAME)),
    )

    private fun ineligibleModel() = eligibleModel().copy(toolCapabilities = ModelToolCapabilities())

    private fun definition() = ManagedWidgetDefinition(
        id = "private-widget-id",
        displayName = "Existing",
        enabled = true,
        periodicIntervalHours = 24,
        activeRevision = 1,
        consentDigest = "b".repeat(64),
        status = ManagedWidgetStatus.Ready,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun revision() = WidgetProgramRevision(
        widgetId = definition().id,
        revision = 1,
        manifestJson = WidgetProgramParserTest.validManifest().replace("weather_lookup", "wikipedia_pages"),
        source = WidgetProgramParserTest.SOURCE,
        programDigest = "a".repeat(64),
        createdAtMillis = 1,
    )

    private fun widgetContract() = ApplicationToolContract(
        id = "wikipedia_pages",
        version = 1,
        displayName = "Wikipedia pages",
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(ApplicationToolConsumer.Widget),
        inputSchemaJson = """{"type":"object"}""",
        outputSchemaJson = """{"type":"object"}""",
    )

    private fun onThisDayContract() = ApplicationToolContract(
        id = "wikipedia_on_this_day",
        version = 1,
        displayName = "Wikipedia events by date",
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(ApplicationToolConsumer.Model, ApplicationToolConsumer.Widget),
        inputSchemaJson = """{"type":"object"}""",
        outputSchemaJson = """{"type":"object"}""",
    )

    private fun modelOnlyContract() = ApplicationToolContract(
        id = "private_model_tool",
        version = 1,
        displayName = "credential-secret-header",
        category = ApplicationToolCategory.ExternalKnowledge,
        consumers = setOf(ApplicationToolConsumer.Model),
        inputSchemaJson = """{"type":"object"}""",
        outputSchemaJson = """{"type":"object"}""",
    )

    private enum class AuthoringMode { Propose, ProposeThenWait, NoCall, Fail, Wait, LoadFail }

    private class RecordingAuthoringEngine(
        private val mode: AuthoringMode,
    ) : LocalLlmEngine {
        var lastRequest: PromptRequest? = null
        var loadedConfig: InferenceConfig? = null
        var generationReleased = false

        override suspend fun load(model: LocalModel, config: InferenceConfig) {
            if (mode == AuthoringMode.LoadFail) error("load failed")
            loadedConfig = config
        }

        override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
            lastRequest = request
            try {
                when (mode) {
                    AuthoringMode.Propose -> {
                        request.ephemeralTools.single().execute(validProposal())
                        emit(GenerationEvent.Completed)
                    }
                    AuthoringMode.ProposeThenWait -> {
                        request.ephemeralTools.single().execute(validProposal())
                        awaitCancellation()
                    }
                    AuthoringMode.NoCall -> emit(GenerationEvent.Completed)
                    AuthoringMode.Fail -> emit(GenerationEvent.Failed("controlled"))
                    AuthoringMode.Wait -> awaitCancellation()
                    AuthoringMode.LoadFail -> error("generation must not start")
                }
            } finally {
                generationReleased = true
            }
        }

        override suspend fun unload() = Unit
    }

    companion object {
        private val AVAILABLE_TOOLS = setOf(WidgetToolCapability("wikipedia_pages", 1))
        private val INFERENCE = InferenceConfig(2_048, 512, 0.7f, 0.9f)

        private fun validProposal(): String = validProposalObject().toString()

        private fun validProposalObject() = JsonObject().apply {
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
            add(
                "runtime",
                JsonArray().apply {
                    add("locale")
                    add("local_time")
                },
            )
            add(
                "presentation",
                JsonArray().apply {
                    add("card")
                    add("text")
                },
            )
            addProperty("source", WidgetProgramParserTest.SOURCE)
        }
    }
}
