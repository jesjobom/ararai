package com.jesjobom.ararai.engine

import app.cash.turbine.test
import com.jesjobom.ararai.chat.AudioPrompt
import com.jesjobom.ararai.chat.ImageAttachment
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.knowledge.ApplicationToolExecutionEvent
import com.jesjobom.ararai.knowledge.KnowledgeSource
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelRuntime
import com.jesjobom.ararai.model.ModelToolCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class LiteRtLmLocalLlmEngineTest {
    private val config =
        InferenceConfig(
            contextTokens = 128,
            promptReserveTokens = 8,
            temperature = 0.7f,
            topP = 0.9f,
        )
    private val model =
        LocalModel(
            id = "gemma-litert",
            name = "Gemma LiteRT",
            filePath = "/tmp/gemma.litertlm",
            runtime = ModelRuntime.LiteRtLm,
            acceleration = ModelAccelerationPolicy.GpuPreferred,
        )

    @Test
    fun `declares incremental conversation capability`() {
        assertTrue(LiteRtLmLocalLlmEngine(bridge = RecordingBridge()).supportsIncrementalConversation)
    }

    @Test
    fun `retained audio transcript includes reconstructible user turn`() {
        val request =
            PromptRequest(
                content =
                MessageContent.AudioPromptContent(
                    audio = AudioPrompt("/tmp/voice.wav", "audio/wav"),
                    transcript = "Pergunta transcrita",
                    transcriptionStatus = com.jesjobom.ararai.chat.AudioTranscriptionStatus.Completed,
                ),
                chatMessages =
                listOf(
                    PromptChatMessage(PromptChatRole.System, "System"),
                    PromptChatMessage(PromptChatRole.User, "Earlier"),
                    PromptChatMessage(PromptChatRole.Assistant, "Previous answer"),
                ),
                chatSessionId = "shared-session",
            )

        assertEquals(
            listOf(
                PromptChatMessage(PromptChatRole.User, "Earlier"),
                PromptChatMessage(PromptChatRole.Assistant, "Previous answer"),
                PromptChatMessage(PromptChatRole.User, "Pergunta transcrita"),
                PromptChatMessage(PromptChatRole.Assistant, "Nova resposta"),
            ),
            request.transcriptAfter("Nova resposta"),
        )
    }

    @Test
    fun `loads litert lm model with gpu preference and streams chunks`() = runTest {
        val bridge = RecordingBridge(chunks = listOf(LiteRtLmChunk(text = "ola"), LiteRtLmChunk(text = " mundo")))
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        engine.load(model, config)

        assertEquals("/tmp/gemma.litertlm", bridge.loadedModelPath)
        assertEquals(config, bridge.loadedConfig)
        assertEquals(true, bridge.loadedUseGpu)
        assertEquals(ModelInputCapabilities(), bridge.loadedInputCapabilities)
        assertEquals(LiteRtLmWorkloadProfile.TextOnly, bridge.loadedProfile)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Token("ola"), awaitItem())
            assertEquals(GenerationEvent.Token(" mundo"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `reloads engine when total context capacity changes`() = runTest {
        val bridge = RecordingBridge()
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        engine.load(model, config)
        engine.load(model, config.copy(temperature = 0.2f))
        engine.load(model, config.copy(contextTokens = 256))

        assertEquals(2, bridge.loadCalls)
        assertEquals(256, bridge.loadedConfig?.contextTokens)
    }

    @Test
    fun `forwards model tool capabilities and rejects unsupported tools`() = runTest {
        val bridge = RecordingBridge()
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        val capableModel =
            model.copy(
                toolCapabilities =
                ModelToolCapabilities(setOf("wikipedia_search")),
            )

        engine.load(capableModel, config)

        assertEquals(setOf("wikipedia_search"), bridge.loadedToolNames)
        engine
            .generate(
                PromptRequest(
                    content = MessageContent.TextPrompt("hello"),
                    advertisedToolNames = setOf("calendar_lookup"),
                ),
            ).test {
                assertEquals(
                    GenerationEvent.Failed("Selected model does not support the requested tools"),
                    awaitItem(),
                )
                awaitComplete()
            }
        assertEquals(null, bridge.session.lastRequest)
    }

    @Test
    fun `translates knowledge tool progress and sources across engine boundary`() = runTest {
        val source =
            KnowledgeSource(
                provider = "Wikipedia",
                title = "Alan Turing",
                canonicalUrl = "https://en.wikipedia.org/wiki/Alan_Turing",
                language = "en",
                retrievedAtMillis = 42L,
            )
        val bridge =
            RecordingBridge(
                chunks =
                listOf(
                    LiteRtLmChunk(
                        toolEvent = ApplicationToolExecutionEvent.Started,
                        toolDisplayName = "Wikipedia",
                    ),
                    LiteRtLmChunk(
                        toolEvent =
                        ApplicationToolExecutionEvent.Succeeded(listOf(source)),
                    ),
                    LiteRtLmChunk(text = "answer"),
                ),
            )
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        engine.load(
            model.copy(
                toolCapabilities =
                ModelToolCapabilities(setOf("wikipedia_search")),
            ),
            config,
        )

        engine
            .generate(
                PromptRequest(
                    content = MessageContent.TextPrompt("Who was Alan Turing?"),
                    advertisedToolNames = setOf("wikipedia_search"),
                ),
            ).test {
                assertEquals(
                    GenerationEvent.ToolStarted("wikipedia_search", "Wikipedia"),
                    awaitItem(),
                )
                assertEquals(
                    GenerationEvent.ToolFinished(
                        toolName = "wikipedia_search",
                        sources = listOf(source),
                    ),
                    awaitItem(),
                )
                assertEquals(GenerationEvent.Token("answer"), awaitItem())
                assertEquals(GenerationEvent.Completed, awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun `preserves web search tool identity across engine boundary`() = runTest {
        val source =
            KnowledgeSource(
                provider = "Exa Web Search",
                title = "Release",
                canonicalUrl = "https://example.com/release",
                language = "en",
                retrievedAtMillis = 42L,
            )
        val bridge =
            RecordingBridge(
                chunks =
                listOf(
                    LiteRtLmChunk(
                        toolEvent = ApplicationToolExecutionEvent.Started,
                        toolName = "web_search",
                        toolDisplayName = "Exa",
                    ),
                    LiteRtLmChunk(
                        toolEvent =
                        ApplicationToolExecutionEvent.Succeeded(listOf(source)),
                        toolName = "web_search",
                    ),
                ),
            )
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        engine.load(
            model.copy(
                toolCapabilities =
                ModelToolCapabilities(setOf("web_search")),
            ),
            config,
        )

        engine
            .generate(
                PromptRequest(
                    content = MessageContent.TextPrompt("What shipped?"),
                    advertisedToolNames = setOf("web_search"),
                ),
            ).test {
                assertEquals(
                    GenerationEvent.ToolStarted("web_search", "Exa"),
                    awaitItem(),
                )
                assertEquals(
                    GenerationEvent.ToolFinished(
                        toolName = "web_search",
                        sources = listOf(source),
                    ),
                    awaitItem(),
                )
                assertEquals(GenerationEvent.Completed, awaitItem())
                awaitComplete()
            }
    }

    @Test
    fun `streams LiteRT reasoning separately and forwards reasoning preference`() = runTest {
        val bridge =
            RecordingBridge(
                chunks =
                listOf(
                    LiteRtLmChunk(reasoning = "porque "),
                    LiteRtLmChunk(text = "resposta"),
                ),
            )
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        engine.load(model, config)
        engine
            .generate(
                PromptRequest(
                    content = MessageContent.TextPrompt("pense"),
                    reasoningEnabled = true,
                ),
            ).test {
                assertEquals(GenerationEvent.ReasoningToken("porque "), awaitItem())
                assertEquals(GenerationEvent.Token("resposta"), awaitItem())
                assertEquals(GenerationEvent.Completed, awaitItem())
                awaitComplete()
            }

        assertEquals(true, bridge.session.lastRequest?.reasoningEnabled)
    }

    @Test
    fun `does not cancel LiteRT generation based on callback chunk count`() = runTest {
        val chunks = List(config.promptReserveTokens + 3) { LiteRtLmChunk(text = "chunk-$it") }
        val bridge = RecordingBridge(chunks = chunks)
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        engine.load(model, config)
        engine.generate(PromptRequest("continue")).test {
            chunks.forEach { chunk ->
                assertEquals(GenerationEvent.Token(chunk.text), awaitItem())
            }
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals(false, bridge.session.cancelled)
    }

    @Test
    fun `loads cpu backend when acceleration is cpu only`() = runTest {
        val bridge = RecordingBridge()
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        engine.load(model.copy(acceleration = ModelAccelerationPolicy.CpuOnly), config)

        assertEquals(false, bridge.loadedUseGpu)
    }

    @Test
    fun `reports failure when generation throws`() = runTest {
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = RecordingBridge(failure = IllegalStateException("boom")),
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        engine.load(model, config)

        engine.generate(PromptRequest("oi")).test {
            assertEquals(GenerationEvent.Failed("boom"), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `passes multimodal request to litert session when capabilities allow it`() = runTest {
        val bridge = RecordingBridge(chunks = listOf(LiteRtLmChunk(text = "ok")))
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        val multimodalModel =
            model.copy(
                inputCapabilities = ModelInputCapabilities(image = true, audio = true),
            )

        engine.load(multimodalModel, config)
        assertEquals(LiteRtLmWorkloadProfile.TextOnly, bridge.loadedProfile)
        val request =
            PromptRequest(
                MessageContent.TextPrompt(
                    text = "describe",
                    imageAttachments = listOf(ImageAttachment("file:///tmp/image.png", "image/png")),
                ),
            )

        engine.generate(request).test {
            assertEquals(GenerationEvent.Token("ok"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }

        assertEquals(request, bridge.session.lastRequest)
        assertEquals(LiteRtLmWorkloadProfile(image = true, audio = false), bridge.loadedProfile)
        assertEquals(
            listOf(
                LiteRtLmWorkloadProfile.TextOnly,
                LiteRtLmWorkloadProfile(image = true, audio = false),
            ),
            bridge.loadedProfiles,
        )
    }

    @Test
    fun `prepares audio workload before the first audio request`() = runTest {
        val bridge = RecordingBridge()
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        val audioModel = model.copy(inputCapabilities = ModelInputCapabilities(audio = true))

        engine.load(audioModel, config)
        engine.prepare(LocalLlmWorkload.Audio)

        assertEquals(
            listOf(
                LiteRtLmWorkloadProfile.TextOnly,
                LiteRtLmWorkloadProfile(image = false, audio = true),
            ),
            bridge.loadedProfiles,
        )
    }

    @Test
    fun `normalizes file uris to litert file paths`() {
        assertEquals("/tmp/image.png", "file:///tmp/image.png".toLiteRtFilePath())
        assertEquals(
            "/data/user/0/com.jesjobom.ararai/files/image.jpg",
            "file:/data/user/0/com.jesjobom.ararai/files/image.jpg".toLiteRtFilePath(),
        )
        assertEquals("/tmp/image.png", "/tmp/image.png".toLiteRtFilePath())
    }

    @Test
    fun `reuses conversation only for matching session settings and transcript`() {
        val key =
            LiteRtLmConversationKey(
                sessionId = "session-1",
                temperature = 0.7f,
                topP = 0.9f,
                reasoningEnabled = false,
            )
        val transcript =
            listOf(
                PromptChatMessage(PromptChatRole.User, "question"),
                PromptChatMessage(PromptChatRole.Assistant, "answer"),
            )

        assertTrue(canReuseLiteRtLmConversation(key, transcript, key, transcript))
        assertEquals(
            false,
            canReuseLiteRtLmConversation(key, transcript, key.copy(sessionId = "session-2"), transcript),
        )
        assertEquals(
            false,
            canReuseLiteRtLmConversation(key, transcript, key.copy(reasoningEnabled = true), transcript),
        )
        assertEquals(
            false,
            canReuseLiteRtLmConversation(key, transcript, key.copy(systemInstruction = "Changed"), transcript),
        )
        assertEquals(
            false,
            canReuseLiteRtLmConversation(
                key,
                transcript,
                key.copy(advertisedToolNames = setOf("wikipedia_search")),
                transcript,
            ),
        )
        assertTrue(
            canReuseLiteRtLmConversation(
                key.copy(advertisedToolNames = setOf("calendar_lookup", "wikipedia_search")),
                transcript,
                key.copy(advertisedToolNames = setOf("wikipedia_search", "calendar_lookup")),
                transcript,
            ),
        )
        assertEquals(
            false,
            canReuseLiteRtLmConversation(key, transcript, key, transcript.dropLast(1)),
        )
    }

    @Test
    fun `maps native LiteRT benchmark metrics without using callback count`() {
        val metrics =
            liteRtLmGenerationMetrics(
                timeToFirstTokenInSecond = 0.125,
                prefillTokenCount = 20,
                decodeTokenCount = 8,
                prefillTokensPerSecond = 100.0,
                decodeTokensPerSecond = 16.0,
            )

        assertEquals(125L, metrics.timeToFirstTokenMillis)
        assertEquals(20, metrics.prefillTokenCount)
        assertEquals(8, metrics.decodeTokenCount)
        assertEquals(100.0, metrics.prefillTokensPerSecond, 0.001)
        assertEquals(16.0, metrics.decodeTokensPerSecond, 0.001)
    }

    @Test
    fun `prepares dedicated cache and falls back when cache root is unusable`() {
        val root = Files.createTempDirectory("litert-cache-test").toFile()
        val cachePath = prepareLiteRtLmCacheDir(root)
        assertEquals(root.resolve("litert_lm").absolutePath, cachePath)
        assertTrue(root.resolve("litert_lm").isDirectory)

        val fileRoot = root.resolve("not-a-directory").apply { writeText("x") }
        var failure: Throwable? = null
        assertEquals(null, prepareLiteRtLmCacheDir(fileRoot) { failure = it })
        assertTrue(failure != null)
    }

    @Test
    fun `builds LiteRT contents with current audio and textual chat context`() {
        val audio = AudioPrompt("file:///tmp/current.wav", "audio/wav")
        val request =
            PromptRequest(
                content = MessageContent.AudioPromptContent(audio),
                chatMessages =
                listOf(
                    PromptChatMessage(PromptChatRole.System, "Be useful."),
                    PromptChatMessage(PromptChatRole.User, "Earlier question"),
                    PromptChatMessage(PromptChatRole.Assistant, "Earlier answer"),
                ),
            )

        val contents = request.toLiteRtInputParts()

        assertEquals(listOf("/tmp/current.wav"), contents.filterIsInstance<LiteRtInputPart.AudioFile>().map { it.path })
        val context = contents.filterIsInstance<LiteRtInputPart.Text>().single().text
        assertTrue(context.contains("System: Be useful."))
        assertTrue(context.contains("User: Earlier question"))
        assertTrue(context.contains("Assistant: Earlier answer"))
    }

    @Test
    fun `rejects unsupported audio request before session generation`() = runTest {
        val bridge = RecordingBridge()
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        engine.load(model, config)

        engine
            .generate(
                PromptRequest(
                    MessageContent.AudioPromptContent(
                        AudioPrompt("file:///tmp/audio.wav", "audio/wav"),
                    ),
                ),
            ).test {
                assertEquals(GenerationEvent.Failed("Selected model does not support audio input"), awaitItem())
                awaitComplete()
            }

        assertEquals(null, bridge.session.lastRequest)
    }

    @Test
    fun `unload closes the active session`() = runTest {
        val bridge = RecordingBridge()
        val engine =
            LiteRtLmLocalLlmEngine(
                bridge = bridge,
                dispatcher = StandardTestDispatcher(testScheduler),
            )

        engine.load(model, config)
        engine.unload()

        assertTrue(bridge.session.closed)
    }

    @Test
    fun `cancelling an active retained resource cancels and closes it exactly once`() {
        val resource = RecordingResource()
        val owner = resourceOwner()
        owner.activate(resource)

        owner.cancelActive()
        owner.invalidate(resource, cancelFirst = true)

        assertEquals(1, resource.cancelCalls)
        assertEquals(1, resource.closeCalls)
        assertEquals(null, owner.retained())
    }

    @Test
    fun `cancelling a retained resource clears reusable state`() {
        val resource = RecordingResource()
        val owner = resourceOwner()
        owner.activate(resource)
        owner.retain(resource, "session-1")

        owner.cancelActive()

        assertEquals(1, resource.cancelCalls)
        assertEquals(1, resource.closeCalls)
        assertEquals(null, owner.retained())
        assertEquals(false, owner.retain(resource, "session-1"))
    }

    @Test
    fun `generation after cancellation must activate a new resource`() {
        val cancelled = RecordingResource()
        val replacement = RecordingResource()
        val owner = resourceOwner()
        owner.activate(cancelled)
        owner.retain(cancelled, "session-1")
        owner.cancelActive()

        assertEquals(false, owner.activate(cancelled))
        assertTrue(owner.activate(replacement))
        assertTrue(owner.retain(replacement, "session-1"))

        assertTrue(owner.retained()?.resource === replacement)
        assertEquals(0, replacement.closeCalls)
    }

    @Test
    fun `unload after cancellation does not close a resource twice`() {
        val resource = RecordingResource()
        val owner = resourceOwner()
        owner.activate(resource)
        owner.retain(resource, "session-1")

        owner.cancelActive()
        owner.closeAll()

        assertEquals(1, resource.cancelCalls)
        assertEquals(1, resource.closeCalls)
    }

    @Test
    fun `replacing incompatible retained state closes the old resource without cancelling it`() {
        val old = RecordingResource()
        val replacement = RecordingResource()
        val owner = resourceOwner()
        owner.activate(old)
        owner.retain(old, "session-1")

        owner.invalidate(old, cancelFirst = false)
        owner.activate(replacement)
        owner.retain(replacement, "session-2")

        assertEquals(0, old.cancelCalls)
        assertEquals(1, old.closeCalls)
        assertTrue(owner.retained()?.resource === replacement)
    }

    private fun resourceOwner(): RetainedResourceOwner<RecordingResource, String> = RetainedResourceOwner(
        cancelResource = { it.cancelCalls += 1 },
        closeResource = { it.closeCalls += 1 },
    )

    private class RecordingResource {
        var cancelCalls: Int = 0
        var closeCalls: Int = 0
    }

    private class RecordingBridge(
        chunks: List<LiteRtLmChunk> = emptyList(),
        failure: Throwable? = null,
    ) : LiteRtLmBridge {
        val session = RecordingSession(chunks, failure)
        var loadedModelPath: String? = null
            private set
        var loadedConfig: InferenceConfig? = null
            private set
        var loadedUseGpu: Boolean? = null
            private set
        var loadedInputCapabilities: ModelInputCapabilities? = null
            private set
        var loadedToolNames: Set<String> = emptySet()
            private set
        var loadedProfile: LiteRtLmWorkloadProfile? = null
            private set
        val loadedProfiles = mutableListOf<LiteRtLmWorkloadProfile>()
        var loadCalls = 0
            private set

        override suspend fun load(
            modelPath: String,
            config: InferenceConfig,
            useGpu: Boolean,
            inputCapabilities: ModelInputCapabilities,
            toolNames: Set<String>,
            profile: LiteRtLmWorkloadProfile,
        ): LiteRtLmSession {
            loadCalls += 1
            loadedModelPath = modelPath
            loadedConfig = config
            loadedUseGpu = useGpu
            loadedInputCapabilities = inputCapabilities
            loadedToolNames = toolNames
            loadedProfile = profile
            loadedProfiles += profile
            return session
        }
    }

    private class RecordingSession(
        private val chunks: List<LiteRtLmChunk>,
        private val failure: Throwable?,
    ) : LiteRtLmSession {
        var closed = false
            private set
        var cancelled = false
            private set
        var lastRequest: PromptRequest? = null
            private set

        override fun generate(
            request: PromptRequest,
            config: InferenceConfig,
        ): Flow<LiteRtLmChunk> {
            lastRequest = request
            return if (failure != null) {
                flow { throw failure }
            } else {
                flowOf(*chunks.toTypedArray())
            }
        }

        override fun cancel() {
            cancelled = true
        }

        override fun close() {
            closed = true
        }
    }
}
