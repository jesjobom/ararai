@file:Suppress("TooManyFunctions")

package com.jesjobom.ararai.engine

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import com.jesjobom.ararai.chat.CALCULATOR_TOOL_NAME
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.chat.WEB_SEARCH_TOOL_NAME
import com.jesjobom.ararai.chat.WIKIPEDIA_SEARCH_TOOL_NAME
import com.jesjobom.ararai.knowledge.ApplicationToolExecutionEvent
import com.jesjobom.ararai.knowledge.KnowledgeTool
import com.jesjobom.ararai.knowledge.StructuredWikipediaTool
import com.jesjobom.ararai.knowledge.ToolFailureReason
import com.jesjobom.ararai.knowledge.WebSearchOpenApiTool
import com.jesjobom.ararai.knowledge.WikipediaKnowledgeTool
import com.jesjobom.ararai.knowledge.WikipediaModelToolBudget
import com.jesjobom.ararai.knowledge.WikipediaOpenApiTool
import com.jesjobom.ararai.knowledge.WikipediaStructuredOpenApiTool
import com.jesjobom.ararai.math.CalculatorExecutionEvent
import com.jesjobom.ararai.math.CalculatorOpenApiTool
import com.jesjobom.ararai.math.EvalExLocalMathEngine
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelRuntime
import com.jesjobom.ararai.tools.ApplicationToolDispatcher
import com.jesjobom.ararai.tools.WIKIPEDIA_ON_THIS_DAY_TOOL_NAME
import com.jesjobom.ararai.tools.WIKIPEDIA_PAGES_TOOL_NAME
import com.jesjobom.ararai.tools.modelApplicationToolDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class LiteRtLmLocalLlmEngine(
    private val bridge: LiteRtLmBridge = AndroidLiteRtLmBridge(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LocalLlmEngine {
    override val supportsIncrementalConversation: Boolean = true
    private val lock = Any()
    private val transitionMutex = Mutex()
    private var loadedSession: LiteRtLmSession? = null
    private var loadedModelId: String? = null
    private var loadedModelPath: String? = null
    private var loadedConfig: InferenceConfig? = null
    private var loadedInputCapabilities: ModelInputCapabilities? = null
    private var loadedToolNames: Set<String> = emptySet()
    private var loadedAuthoringToolNames: Set<String> = emptySet()
    private var loadedUseGpu: Boolean = false
    private var loadedProfile: LiteRtLmWorkloadProfile? = null
    private val runtimeTelemetry = bridge.runtimeTelemetry

    override suspend fun load(
        model: LocalModel,
        config: InferenceConfig,
    ) = loadProfile(model, config, LiteRtLmWorkloadProfile.TextOnly)

    override suspend fun loadForWorkload(
        model: LocalModel,
        config: InferenceConfig,
        workload: LocalLlmWorkload,
    ) = loadProfile(
        model,
        config,
        LiteRtLmWorkloadProfile(image = workload.image, audio = workload.audio),
    )

    private suspend fun loadProfile(
        model: LocalModel,
        config: InferenceConfig,
        profile: LiteRtLmWorkloadProfile,
    ) = withContext(dispatcher + NonCancellable) {
        transitionMutex.withLock {
            check(model.runtime == ModelRuntime.LiteRtLm) {
                "Unsupported local model runtime: ${model.runtime.displayName}"
            }

            val alreadyLoaded =
                synchronized(lock) {
                    val isLoaded =
                        loadedSession != null &&
                            loadedModelId == model.id &&
                            loadedModelPath == model.filePath &&
                            loadedConfig?.contextTokens == config.contextTokens &&
                            loadedProfile == profile
                    if (isLoaded) {
                        loadedConfig = config
                        loadedInputCapabilities = model.inputCapabilities
                        loadedToolNames = model.toolCapabilities.toolNames
                        loadedAuthoringToolNames = model.toolCapabilities.allowedAuthoringToolNames
                    }
                    isLoaded
                }
            if (alreadyLoaded) {
                runtimeTelemetry.record(
                    event = "engine_load_reused",
                    resourceId = synchronized(lock) { loadedSession?.let { System.identityHashCode(it) } },
                    outcome = "success",
                )
                return@withLock
            }

            replaceSessionLocked(model, config, profile)
        }
    }

    override suspend fun reload(
        model: LocalModel,
        config: InferenceConfig,
    ) = withContext(dispatcher + NonCancellable) {
        transitionMutex.withLock {
            check(model.runtime == ModelRuntime.LiteRtLm) {
                "Unsupported local model runtime: ${model.runtime.displayName}"
            }
            runtimeTelemetry.record(event = "engine_reload_started", elapsedMillis = 0)
            try {
                replaceSessionLocked(model, config, LiteRtLmWorkloadProfile.TextOnly)
                runtimeTelemetry.record(
                    event = "engine_reload_finished",
                    resourceId = synchronized(lock) { loadedSession?.let(System::identityHashCode) },
                    outcome = "success",
                )
            } catch (error: Throwable) {
                runtimeTelemetry.record(event = "engine_reload_finished", outcome = "failed")
                throw error
            }
        }
    }

    override suspend fun reloadWhenReady(
        model: LocalModel,
        config: InferenceConfig,
        awaitReady: suspend () -> Boolean,
    ): Boolean = withContext(dispatcher) {
        transitionMutex.withLock {
            check(model.runtime == ModelRuntime.LiteRtLm) {
                "Unsupported local model runtime: ${model.runtime.displayName}"
            }
            runtimeTelemetry.record(event = "engine_recovery_started", elapsedMillis = 0)
            try {
                withContext(NonCancellable) { unloadLocked() }
                runtimeTelemetry.record(event = "engine_recovery_unloaded", outcome = "success")
                if (!awaitReady()) {
                    runtimeTelemetry.record(event = "engine_recovery_finished", outcome = "timed_out")
                    return@withLock false
                }
                runtimeTelemetry.record(event = "engine_recovery_reload_started", elapsedMillis = 0)
                withContext(NonCancellable) {
                    replaceSessionLocked(model, config, LiteRtLmWorkloadProfile.TextOnly)
                }
                runtimeTelemetry.record(
                    event = "engine_recovery_finished",
                    resourceId = synchronized(lock) { loadedSession?.let(System::identityHashCode) },
                    outcome = "success",
                )
                true
            } catch (error: Throwable) {
                runtimeTelemetry.record(event = "engine_recovery_finished", outcome = "failed")
                throw error
            }
        }
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> = callbackFlow {
        val generationFinished = AtomicBoolean(false)
        val initialState =
            synchronized(lock) {
                val config = loadedConfig
                val capabilities = loadedInputCapabilities
                if (loadedSession == null || config == null || capabilities == null) {
                    null
                } else {
                    LoadedState(config, capabilities, loadedToolNames, loadedAuthoringToolNames)
                }
            }

        if (initialState == null) {
            trySend(expectedGenerationFailure("Model is not loaded"))
            close()
            return@callbackFlow
        }

        val job =
            launch(dispatcher) {
                try {
                    request.validateAgainst(initialState.capabilities)?.let { failure ->
                        trySend(expectedGenerationFailure(failure))
                        return@launch
                    }
                    if (!request.toolsAreSupported(initialState)) {
                        trySend(expectedGenerationFailure("Selected model does not support the requested tools"))
                        return@launch
                    }
                    val session = transitionMutex.withLock {
                        ensureProfile(LiteRtLmWorkloadProfile.from(request))
                    }
                    session.generate(request, initialState.config).collect { chunk ->
                        chunk.toGenerationEvents().forEach { trySend(it) }
                    }
                    generationFinished.set(true)
                    trySend(GenerationEvent.Completed)
                } catch (error: Throwable) {
                    trySend(error.toGenerationFailure())
                } finally {
                    generationFinished.set(true)
                    close()
                }
            }

        awaitClose {
            if (!generationFinished.get()) {
                synchronized(lock) { loadedSession }?.cancel()
            }
            job.cancel()
        }
    }

    override suspend fun prepare(workload: LocalLlmWorkload) = transitionMutex.withLock {
        withContext(dispatcher + NonCancellable) {
            ensureProfile(LiteRtLmWorkloadProfile(image = workload.image, audio = workload.audio))
            Unit
        }
    }

    override suspend fun unload() = transitionMutex.withLock {
        unloadLocked()
    }

    private suspend fun unloadLocked() {
        val session =
            synchronized(lock) {
                val current = loadedSession
                loadedSession = null
                loadedModelId = null
                loadedModelPath = null
                loadedConfig = null
                loadedInputCapabilities = null
                loadedToolNames = emptySet()
                loadedAuthoringToolNames = emptySet()
                loadedUseGpu = false
                loadedProfile = null
                current
            }

        if (session != null) {
            withContext(dispatcher) {
                session.close()
            }
        }
    }

    private suspend fun replaceSessionLocked(
        model: LocalModel,
        config: InferenceConfig,
        profile: LiteRtLmWorkloadProfile,
    ) {
        unloadLocked()

        // Native loading is not cooperatively cancellable. The ownership transfer must be
        // non-cancellable too: returning to a cancelled caller before publishing [session]
        // would orphan the native engine and allow the next screen entry to load another.
        val session = bridge.load(
            modelPath = model.filePath,
            config = config,
            useGpu = model.acceleration == ModelAccelerationPolicy.GpuPreferred,
            inputCapabilities = model.inputCapabilities,
            toolNames = model.toolCapabilities.allToolNames,
            profile = profile,
        )

        synchronized(lock) {
            loadedSession = session
            loadedModelId = model.id
            loadedModelPath = model.filePath
            loadedConfig = config
            loadedInputCapabilities = model.inputCapabilities
            loadedToolNames = model.toolCapabilities.toolNames
            loadedAuthoringToolNames = model.toolCapabilities.allowedAuthoringToolNames
            loadedUseGpu = model.acceleration == ModelAccelerationPolicy.GpuPreferred
            loadedProfile = profile
        }
        runtimeTelemetry.record(
            event = "engine_load_published",
            resourceId = System.identityHashCode(session),
            outcome = "success",
        )
    }

    private data class LoadedState(
        val config: InferenceConfig,
        val capabilities: ModelInputCapabilities,
        val toolNames: Set<String>,
        val authoringToolNames: Set<String>,
    )

    private suspend fun ensureProfile(desiredProfile: LiteRtLmWorkloadProfile): LiteRtLmSession {
        val snapshot =
            synchronized(lock) {
                ProfileState(
                    session = checkNotNull(loadedSession) { "Model is not loaded" },
                    currentProfile = checkNotNull(loadedProfile) { "Model is not loaded" },
                    modelPath = checkNotNull(loadedModelPath) { "Model is not loaded" },
                    config = checkNotNull(loadedConfig) { "Model is not loaded" },
                    capabilities = checkNotNull(loadedInputCapabilities) { "Model is not loaded" },
                    toolNames = loadedToolNames + loadedAuthoringToolNames,
                    useGpu = loadedUseGpu,
                )
            }
        if (snapshot.currentProfile == desiredProfile) return snapshot.session

        synchronized(lock) {
            loadedSession = null
            loadedProfile = null
        }
        snapshot.session.close()
        val replacement =
            bridge.load(
                modelPath = snapshot.modelPath,
                config = snapshot.config,
                useGpu = snapshot.useGpu,
                inputCapabilities = snapshot.capabilities,
                toolNames = snapshot.toolNames,
                profile = desiredProfile,
            )
        synchronized(lock) {
            loadedSession = replacement
            loadedProfile = desiredProfile
        }
        return replacement
    }

    private data class ProfileState(
        val session: LiteRtLmSession,
        val currentProfile: LiteRtLmWorkloadProfile,
        val modelPath: String,
        val config: InferenceConfig,
        val capabilities: ModelInputCapabilities,
        val toolNames: Set<String>,
        val useGpu: Boolean,
    )

    private fun PromptRequest.validateAgainst(capabilities: ModelInputCapabilities): String? = when {
        textPrompt != null && !capabilities.text -> "Selected model does not support text input"
        imageAttachments.isNotEmpty() && !capabilities.image -> "Selected model does not support image input"
        audioPrompt != null && !capabilities.audio -> "Selected model does not support audio input"
        else -> null
    }

    private fun PromptRequest.toolsAreSupported(state: LoadedState): Boolean {
        val requested = normalizedAdvertisedToolNames()
        val ephemeralNames = ephemeralTools.mapTo(mutableSetOf(), EphemeralLocalLlmTool::name)
        return if (ephemeralNames.isEmpty()) {
            state.toolNames.containsAll(requested)
        } else {
            ephemeralNames.size == ephemeralTools.size &&
                requested == ephemeralNames &&
                state.authoringToolNames.containsAll(ephemeralNames)
        }
    }

    private fun LiteRtLmChunk.toGenerationEvents(): List<GenerationEvent> = buildList {
        if (text.isNotEmpty()) add(GenerationEvent.Token(text))
        if (reasoning.isNotEmpty()) add(GenerationEvent.ReasoningToken(reasoning))
        metrics?.let { add(GenerationEvent.Metrics(it)) }
        val eventToolName = toolName ?: WIKIPEDIA_SEARCH_TOOL_NAME
        val eventToolDisplayName = toolDisplayName ?: eventToolName
        when (val event = toolEvent) {
            ApplicationToolExecutionEvent.Started ->
                add(GenerationEvent.ToolStarted(eventToolName, eventToolDisplayName))
            is ApplicationToolExecutionEvent.Succeeded ->
                add(
                    GenerationEvent.ToolFinished(
                        toolName = eventToolName,
                        sources = event.sources,
                    ),
                )
            is ApplicationToolExecutionEvent.Failed ->
                add(
                    GenerationEvent.ToolFinished(
                        toolName = eventToolName,
                        failureReason = event.reason,
                    ),
                )
            null -> Unit
        }
    }
}

private fun expectedGenerationFailure(message: String) = GenerationEvent.Failed(
    message = message,
    kind = GenerationFailureKind.Expected,
)

private fun generationOutcome(
    completed: Boolean,
    errored: Boolean,
): String = when {
    completed -> "completed"
    errored -> "failed"
    else -> "cancelled"
}

internal fun Throwable.toGenerationFailure(): GenerationEvent.Failed {
    val failureMessage = message ?: "LiteRT-LM generation failed"
    return GenerationEvent.Failed(
        message = failureMessage,
        kind = if (failureMessage.contains(TOOL_CALL_PARSE_MARKER)) {
            GenerationFailureKind.ToolCallParsing
        } else {
            GenerationFailureKind.Unexpected
        },
        cause = this,
    )
}

private const val TOOL_CALL_PARSE_MARKER = "Failed to parse tool calls"

@Suppress("LongParameterList")
interface LiteRtLmBridge {
    val runtimeTelemetry: LiteRtLmRuntimeTelemetry
        get() = LiteRtLmRuntimeTelemetry.disabled()

    suspend fun load(
        modelPath: String,
        config: InferenceConfig,
        useGpu: Boolean,
        inputCapabilities: ModelInputCapabilities,
        toolNames: Set<String>,
        profile: LiteRtLmWorkloadProfile,
    ): LiteRtLmSession
}

data class LiteRtLmWorkloadProfile(
    val image: Boolean,
    val audio: Boolean,
) {
    companion object {
        val TextOnly = LiteRtLmWorkloadProfile(image = false, audio = false)

        fun from(request: PromptRequest) = LiteRtLmWorkloadProfile(
            image = request.imageAttachments.isNotEmpty(),
            audio = request.audioPrompt != null,
        )
    }
}

interface LiteRtLmSession {
    fun generate(
        request: PromptRequest,
        config: InferenceConfig,
    ): Flow<LiteRtLmChunk>

    fun cancel()

    fun close()
}

data class LiteRtLmChunk(
    val text: String = "",
    val reasoning: String = "",
    val metrics: GenerationMetrics? = null,
    val toolEvent: ApplicationToolExecutionEvent? = null,
    val toolName: String? = null,
    val toolDisplayName: String? = null,
)

fun interface WebSearchKnowledgeToolResolver {
    fun resolve(): KnowledgeTool?
}

@OptIn(ExperimentalApi::class)
class AndroidLiteRtLmBridge(
    private val cacheDir: String? = null,
    private val wikipediaKnowledgeTool: KnowledgeTool = WikipediaKnowledgeTool(),
    private val webSearchKnowledgeToolResolver: WebSearchKnowledgeToolResolver =
        WebSearchKnowledgeToolResolver { null },
    private val calculatorEngine: com.jesjobom.ararai.math.LocalMathEngine = EvalExLocalMathEngine(),
    applicationToolDispatcher: ApplicationToolDispatcher? = null,
    private val webSearchDisplayNameProvider: () -> String = {
        webSearchKnowledgeToolResolver.resolve()?.displayName ?: "Web search"
    },
    override val runtimeTelemetry: LiteRtLmRuntimeTelemetry = LiteRtLmRuntimeTelemetry.disabled(),
) : LiteRtLmBridge {
    private val toolDispatcher = applicationToolDispatcher ?: modelApplicationToolDispatcher(
        wikipediaTool = wikipediaKnowledgeTool,
        webSearchTool = webSearchKnowledgeToolResolver::resolve,
        calculatorEngine = calculatorEngine,
    )

    override suspend fun load(
        modelPath: String,
        config: InferenceConfig,
        useGpu: Boolean,
        inputCapabilities: ModelInputCapabilities,
        toolNames: Set<String>,
        profile: LiteRtLmWorkloadProfile,
    ): LiteRtLmSession {
        val startedAt = System.nanoTime()
        runtimeTelemetry.record(event = "engine_initialize_started", elapsedMillis = 0)
        ExperimentalFlags.enableBenchmark = true
        ExperimentalFlags.enableSpeculativeDecoding = true
        val backend = if (useGpu) Backend.GPU() else Backend.CPU()
        val engineConfig =
            EngineConfig(
                modelPath = modelPath,
                backend = backend,
                visionBackend = if (profile.image && inputCapabilities.image) backend else null,
                audioBackend = if (profile.audio && inputCapabilities.audio) Backend.CPU() else null,
                maxNumTokens = config.contextTokens,
                cacheDir = cacheDir,
            )
        val engine = Engine(engineConfig)

        return try {
            engine.initialize()
            Log.d(
                LOG_TAG,
                "Engine initialized: id=${System.identityHashCode(engine)}, " +
                    "profile=$profile, elapsed=${startedAt.elapsedMillis()} ms",
            )
            runtimeTelemetry.record(
                event = "engine_initialized",
                resourceId = System.identityHashCode(engine),
                elapsedMillis = startedAt.elapsedMillis(),
                outcome = "success",
            )
            AndroidLiteRtLmSession(
                engine,
                toolDispatcher,
                webSearchDisplayNameProvider,
                toolNames,
                runtimeTelemetry,
            )
        } catch (error: Throwable) {
            runtimeTelemetry.record(
                event = "engine_initialize_finished",
                resourceId = System.identityHashCode(engine),
                elapsedMillis = startedAt.elapsedMillis(),
                outcome = "failed",
            )
            engine.close()
            throw error
        }
    }

    private fun Long.elapsedMillis(): Long = (System.nanoTime() - this) / 1_000_000

    private companion object {
        const val LOG_TAG = "ArarAI.LiteRtLm"
    }
}

@OptIn(ExperimentalApi::class)
private class AndroidLiteRtLmSession(
    private val engine: Engine,
    private val toolDispatcher: ApplicationToolDispatcher,
    private val webSearchDisplayNameProvider: () -> String,
    private val supportedToolNames: Set<String>,
    private val runtimeTelemetry: LiteRtLmRuntimeTelemetry,
) : LiteRtLmSession {
    private val conversations =
        RetainedResourceOwner<Conversation, RetainedConversationState>(
            cancelResource = { conversation ->
                runtimeTelemetry.recordResourceOperation("conversation_cancel", conversation) {
                    conversation.cancelProcess()
                }
            },
            closeResource = { conversation ->
                runtimeTelemetry.recordResourceOperation("conversation_close", conversation) {
                    conversation.close()
                }
            },
        )

    override fun generate(
        request: PromptRequest,
        config: InferenceConfig,
    ): Flow<LiteRtLmChunk> = callbackFlow {
        val generationStartedAt = System.nanoTime()
        val requestId = runtimeTelemetry.beginRequest()
        val requestFinished = AtomicBoolean(false)
        val samplerConfig =
            SamplerConfig(
                topK = DEFAULT_TOP_K,
                topP = config.topP.toDouble(),
                temperature = config.temperature.toDouble(),
                seed = DEFAULT_SEED,
            )
        val key =
            LiteRtLmConversationKey(
                sessionId = request.chatSessionId,
                temperature = config.temperature,
                topP = config.topP,
                reasoningEnabled = request.reasoningEnabled,
                systemInstruction = request.systemInstructionText(),
                advertisedToolNames = request.normalizedAdvertisedToolNames(),
            )
        val historyBeforeCurrent = request.historyBeforeCurrent()
        val selectionInput = ProductionConversationSelectionInput(
            request = request,
            historyBeforeCurrent = historyBeforeCurrent,
            samplerConfig = samplerConfig,
            key = key,
            requestId = requestId,
            generationStartedAt = generationStartedAt,
        )
        val selected = selectProductionConversationOrFinish(selectionInput, requestFinished)
        val created = selected.value
        val canReuse = selected.reused
        val conversation = created.conversation
        val conversationId = System.identityHashCode(conversation)
        created.wikipediaTool?.beginTurn { event ->
            trySend(
                LiteRtLmChunk(
                    toolEvent = event,
                    toolName = WIKIPEDIA_SEARCH_TOOL_NAME,
                    toolDisplayName = created.wikipediaTool.displayName,
                ),
            )
        }
        created.structuredWikipediaTools.forEach { wikipediaTool ->
            wikipediaTool.beginTurn { event ->
                trySend(
                    LiteRtLmChunk(
                        toolEvent = event,
                        toolName = wikipediaTool.toolId,
                        toolDisplayName = wikipediaTool.displayName,
                    ),
                )
            }
        }
        created.webSearchTool?.beginTurn { event ->
            trySend(
                LiteRtLmChunk(
                    toolEvent = event,
                    toolName = WEB_SEARCH_TOOL_NAME,
                    toolDisplayName = created.webSearchTool.displayName,
                ),
            )
        }
        created.calculatorTool?.beginTurn { event ->
            val mapped = when (event) {
                CalculatorExecutionEvent.Started -> ApplicationToolExecutionEvent.Started
                is CalculatorExecutionEvent.Finished ->
                    if (event.failure == null) {
                        ApplicationToolExecutionEvent.Succeeded(emptyList())
                    } else {
                        ApplicationToolExecutionEvent.Failed(ToolFailureReason.Unavailable)
                    }
            }
            trySend(
                LiteRtLmChunk(
                    toolEvent = mapped,
                    toolName = CALCULATOR_TOOL_NAME,
                    toolDisplayName = "Local calculator",
                ),
            )
        }
        Log.d(
            LOG_TAG,
            "Conversation ready: reused=$canReuse, elapsed=${generationStartedAt.elapsedMillis()} ms",
        )
        runtimeTelemetry.record(
            event = "conversation_ready",
            requestId = requestId,
            resourceId = conversationId,
            elapsedMillis = generationStartedAt.elapsedMillis(),
        )
        val reusable = key.sessionId != null
        val completed = AtomicBoolean(false)
        val errored = AtomicBoolean(false)
        val firstCallbackObserved = AtomicBoolean(false)

        try {
            conversations.activate(conversation)
            var previousText = ""
            var previousReasoning = ""
            val callback =
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        if (firstCallbackObserved.compareAndSet(false, true)) {
                            Log.d(LOG_TAG, "First model callback: ${generationStartedAt.elapsedMillis()} ms")
                            runtimeTelemetry.record(
                                event = "first_model_callback",
                                requestId = requestId,
                                resourceId = conversationId,
                                elapsedMillis = generationStartedAt.elapsedMillis(),
                            )
                        }
                        val currentText = message.text()
                        val currentReasoning = message.reasoning()
                        val textDelta = currentText.deltaAfter(previousText)
                        val reasoningDelta = currentReasoning.deltaAfter(previousReasoning)
                        previousText = currentText
                        previousReasoning = currentReasoning
                        if (textDelta.isNotEmpty() || reasoningDelta.isNotEmpty()) {
                            trySend(LiteRtLmChunk(text = textDelta, reasoning = reasoningDelta))
                        }
                    }

                    override fun onDone() {
                        runtimeTelemetry.record(
                            event = "model_done",
                            requestId = requestId,
                            resourceId = conversationId,
                            elapsedMillis = generationStartedAt.elapsedMillis(),
                            outcome = "success",
                        )
                        runCatching { conversation.getBenchmarkInfo() }
                            .onFailure { error ->
                                Log.w("ArarAI.LiteRtLm", "Unable to read LiteRT-LM benchmark metrics", error)
                            }.getOrNull()
                            ?.let { benchmark ->
                                Log.d(
                                    LOG_TAG,
                                    "Generation benchmark: ttft=${benchmark.timeToFirstTokenInSecond * 1_000} ms, " +
                                        "prefillTokens=${benchmark.lastPrefillTokenCount}, " +
                                        "prefillTokensPerSecond=${benchmark.lastPrefillTokensPerSecond}",
                                )
                                trySend(
                                    LiteRtLmChunk(
                                        metrics =
                                        liteRtLmGenerationMetrics(
                                            timeToFirstTokenInSecond = benchmark.timeToFirstTokenInSecond,
                                            prefillTokenCount = benchmark.lastPrefillTokenCount,
                                            prefillTokensPerSecond = benchmark.lastPrefillTokensPerSecond,
                                            decodeTokenCount = benchmark.lastDecodeTokenCount,
                                            decodeTokensPerSecond = benchmark.lastDecodeTokensPerSecond,
                                        ),
                                    ),
                                )
                            }
                        if (reusable) {
                            conversations.retain(
                                resource = conversation,
                                state =
                                RetainedConversationState(
                                    key = key,
                                    transcript = request.transcriptAfter(previousText),
                                    wikipediaTool = created.wikipediaTool,
                                    structuredWikipediaTools = created.structuredWikipediaTools,
                                    webSearchTool = created.webSearchTool,
                                    calculatorTool = created.calculatorTool,
                                ),
                            )
                        }
                        completed.set(true)
                        close(null)
                    }

                    override fun onError(throwable: Throwable) {
                        errored.set(true)
                        runtimeTelemetry.record(
                            event = "model_error",
                            requestId = requestId,
                            resourceId = conversationId,
                            elapsedMillis = generationStartedAt.elapsedMillis(),
                            outcome = "failed",
                        )
                        close(throwable)
                    }
                }

            conversation.sendMessageAsync(request.toCurrentLiteRtContents(), callback)
            Log.d(LOG_TAG, "Audio request submitted: ${generationStartedAt.elapsedMillis()} ms")
            runtimeTelemetry.record(
                event = "request_submitted",
                requestId = requestId,
                resourceId = conversationId,
                elapsedMillis = generationStartedAt.elapsedMillis(),
            )
        } catch (error: Throwable) {
            errored.set(true)
            runtimeTelemetry.record(
                event = "request_setup_failed",
                requestId = requestId,
                resourceId = conversationId,
                elapsedMillis = generationStartedAt.elapsedMillis(),
                outcome = "failed",
            )
            close(error)
        }

        awaitClose {
            val outcome = generationOutcome(completed.get(), errored.get())
            runtimeTelemetry.record(
                event = "generation_cleanup_started",
                requestId = requestId,
                resourceId = conversationId,
                elapsedMillis = generationStartedAt.elapsedMillis(),
                outcome = outcome,
            )
            try {
                if (!completed.get()) {
                    conversations.invalidate(conversation, cancelFirst = true)
                } else if (!reusable) {
                    conversations.invalidate(conversation, cancelFirst = false)
                }
            } finally {
                finishRuntimeRequestOnce(requestFinished, requestId, generationStartedAt, outcome)
            }
        }
    }

    private fun selectProductionConversationOrFinish(
        input: ProductionConversationSelectionInput,
        requestFinished: AtomicBoolean,
    ): SelectedProductionConversation = try {
        selectProductionConversation(input)
    } catch (error: Throwable) {
        finishRuntimeRequestOnce(requestFinished, input.requestId, input.generationStartedAt, "failed")
        throw error
    }

    private fun selectProductionConversation(
        input: ProductionConversationSelectionInput,
    ): SelectedProductionConversation {
        val retained = conversations.retained()
        val canReuse = retained != null && canReuseLiteRtLmConversation(
            retainedKey = retained.state.key,
            retainedTranscript = retained.state.transcript,
            requestKey = input.key,
            requestHistory = input.historyBeforeCurrent,
        )
        if (canReuse) {
            val reusableConversation = checkNotNull(retained)
            runtimeTelemetry.record(
                event = "conversation_reused",
                requestId = input.requestId,
                resourceId = System.identityHashCode(reusableConversation.resource),
                elapsedMillis = input.generationStartedAt.elapsedMillis(),
            )
            return SelectedProductionConversation(reusableConversation.toProductionConversation(), reused = true)
        }
        retained?.resource?.let { conversations.invalidate(it, cancelFirst = false) }
        return SelectedProductionConversation(
            createProductionConversation(
                input.request,
                input.historyBeforeCurrent,
                input.samplerConfig,
                input.requestId,
            ),
            reused = false,
        )
    }

    private fun finishRuntimeRequestOnce(
        requestFinished: AtomicBoolean,
        requestId: Long,
        generationStartedAt: Long,
        outcome: String,
    ) {
        if (!requestFinished.compareAndSet(false, true)) return
        runtimeTelemetry.finishRequest(
            requestId = requestId,
            elapsedMillis = generationStartedAt.elapsedMillis(),
            outcome = outcome,
        )
    }

    private fun createProductionConversation(
        request: PromptRequest,
        historyBeforeCurrent: List<PromptChatMessage>,
        samplerConfig: SamplerConfig,
        requestId: Long,
    ): ProductionConversation {
        val startedAt = System.nanoTime()
        runtimeTelemetry.record(
            event = "conversation_create_started",
            requestId = requestId,
            elapsedMillis = 0,
        )
        val tools = createProductionConversationTools(request)
        if (tools.configured.isNotEmpty()) {
            ExperimentalFlags.enableConversationConstrainedDecoding = true
        }
        return try {
            val productionConversation = ProductionConversation(
                conversation =
                engine.createConversation(
                    ConversationConfig(
                        systemInstruction = request.systemInstruction(),
                        initialMessages = historyBeforeCurrent.toLiteRtMessages(),
                        samplerConfig = samplerConfig,
                        extraContext = mapOf(ENABLE_THINKING_CONTEXT_KEY to request.reasoningEnabled),
                        tools = tools.configured,
                    ),
                ),
                wikipediaTool = tools.wikipedia,
                structuredWikipediaTools = tools.structuredWikipedia,
                webSearchTool = tools.webSearch,
                calculatorTool = tools.calculator,
            )
            runtimeTelemetry.record(
                event = "conversation_create_finished",
                requestId = requestId,
                resourceId = System.identityHashCode(productionConversation.conversation),
                elapsedMillis = startedAt.elapsedMillis(),
                outcome = "success",
            )
            productionConversation
        } catch (error: Throwable) {
            runtimeTelemetry.record(
                event = "conversation_create_finished",
                requestId = requestId,
                elapsedMillis = startedAt.elapsedMillis(),
                outcome = "failed",
            )
            throw error
        } finally {
            if (tools.configured.isNotEmpty()) {
                ExperimentalFlags.enableConversationConstrainedDecoding = false
            }
        }
    }

    private fun createProductionConversationTools(request: PromptRequest): ProductionConversationTools {
        val requested = request.normalizedAdvertisedToolNames()
        requireSupportedTools(requested)
        val wikipedia = if (WIKIPEDIA_SEARCH_TOOL_NAME in requested) {
            WikipediaOpenApiTool(toolDispatcher, supportedToolNames)
        } else {
            null
        }
        val structuredWikipedia = createStructuredWikipediaTools(requested)
        val webSearch = if (WEB_SEARCH_TOOL_NAME in requested) {
            WebSearchOpenApiTool(
                dispatcher = toolDispatcher,
                verifiedModelToolIds = supportedToolNames,
                displayName = webSearchDisplayNameProvider(),
            )
        } else {
            null
        }
        val calculator = if (CALCULATOR_TOOL_NAME in requested) {
            CalculatorOpenApiTool(toolDispatcher, supportedToolNames)
        } else {
            null
        }
        return ProductionConversationTools(
            wikipedia = wikipedia,
            structuredWikipedia = structuredWikipedia,
            webSearch = webSearch,
            calculator = calculator,
            ephemeral = request.ephemeralTools.map(::EphemeralOpenApiTool),
        )
    }

    private fun requireSupportedTools(requested: Set<String>) {
        require(supportedToolNames.containsAll(requested)) {
            "Requested tool is not supported by the loaded model"
        }
    }

    private fun RetainedResource<
        Conversation,
        RetainedConversationState,
        >.toProductionConversation() = ProductionConversation(
        conversation = resource,
        wikipediaTool = state.wikipediaTool,
        structuredWikipediaTools = state.structuredWikipediaTools,
        webSearchTool = state.webSearchTool,
        calculatorTool = state.calculatorTool,
    )

    private data class ProductionConversationSelectionInput(
        val request: PromptRequest,
        val historyBeforeCurrent: List<PromptChatMessage>,
        val samplerConfig: SamplerConfig,
        val key: LiteRtLmConversationKey,
        val requestId: Long,
        val generationStartedAt: Long,
    )

    private data class SelectedProductionConversation(
        val value: ProductionConversation,
        val reused: Boolean,
    )

    private data class ProductionConversation(
        val conversation: Conversation,
        val wikipediaTool: WikipediaOpenApiTool?,
        val structuredWikipediaTools: List<WikipediaStructuredOpenApiTool>,
        val webSearchTool: WebSearchOpenApiTool?,
        val calculatorTool: CalculatorOpenApiTool?,
    )

    private data class ProductionConversationTools(
        val wikipedia: WikipediaOpenApiTool?,
        val structuredWikipedia: List<WikipediaStructuredOpenApiTool>,
        val webSearch: WebSearchOpenApiTool?,
        val calculator: CalculatorOpenApiTool?,
        val ephemeral: List<EphemeralOpenApiTool>,
    ) {
        val configured = listOfNotNull(
            wikipedia?.let(::tool),
            webSearch?.let(::tool),
            calculator?.let(::tool),
        ) + structuredWikipedia.map(::tool) + ephemeral.map(::tool)
    }

    private fun createStructuredWikipediaTools(requested: Set<String>): List<WikipediaStructuredOpenApiTool> {
        val budget = WikipediaModelToolBudget()
        return buildList {
            if (WIKIPEDIA_PAGES_TOOL_NAME in requested) {
                add(
                    WikipediaStructuredOpenApiTool(
                        StructuredWikipediaTool.Pages,
                        toolDispatcher,
                        supportedToolNames,
                        budget,
                    ),
                )
            }
            if (WIKIPEDIA_ON_THIS_DAY_TOOL_NAME in requested) {
                add(
                    WikipediaStructuredOpenApiTool(
                        StructuredWikipediaTool.OnThisDay,
                        toolDispatcher,
                        supportedToolNames,
                        budget,
                    ),
                )
            }
        }
    }

    override fun cancel() {
        runtimeTelemetry.record(event = "session_cancel_requested")
        conversations.cancelActive()
    }

    override fun close() {
        Log.d(LOG_TAG, "Engine closing: id=${System.identityHashCode(engine)}")
        runtimeTelemetry.recordResourceOperation("engine_close", engine) {
            conversations.closeAll()
            engine.close()
        }
    }

    private fun Message.text(): String = contents.contents
        .filterIsInstance<Content.Text>()
        .joinToString(separator = "") { it.text }

    private fun Message.reasoning(): String = channels.values.joinToString(separator = "")

    private fun String.deltaAfter(previous: String): String = if (startsWith(previous)) removePrefix(previous) else this

    private companion object {
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_SEED = 0
        const val ENABLE_THINKING_CONTEXT_KEY = "enable_thinking"
        const val LOG_TAG = "ArarAI.LiteRtLm"
    }

    private fun Long.elapsedMillis(): Long = (System.nanoTime() - this) / 1_000_000

    private data class RetainedConversationState(
        val key: LiteRtLmConversationKey,
        val transcript: List<PromptChatMessage>,
        val wikipediaTool: WikipediaOpenApiTool? = null,
        val structuredWikipediaTools: List<WikipediaStructuredOpenApiTool> = emptyList(),
        val webSearchTool: WebSearchOpenApiTool? = null,
        val calculatorTool: CalculatorOpenApiTool? = null,
    )
}

private class EphemeralOpenApiTool(
    private val delegate: EphemeralLocalLlmTool,
) : com.google.ai.edge.litertlm.OpenApiTool {
    override fun getToolDescriptionJsonString(): String = delegate.descriptionJson

    override fun execute(paramsJsonString: String): String = delegate.execute(paramsJsonString)
}

internal fun liteRtLmGenerationMetrics(
    timeToFirstTokenInSecond: Double,
    prefillTokenCount: Int,
    prefillTokensPerSecond: Double,
    decodeTokenCount: Int,
    decodeTokensPerSecond: Double,
) = GenerationMetrics(
    timeToFirstTokenMillis = (timeToFirstTokenInSecond * 1_000).toLong(),
    prefillTokenCount = prefillTokenCount,
    prefillTokensPerSecond = prefillTokensPerSecond,
    decodeTokenCount = decodeTokenCount,
    decodeTokensPerSecond = decodeTokensPerSecond,
)

private fun PromptRequest.systemInstruction(): Contents? = chatMessages
    .firstOrNull { it.role == PromptChatRole.System }
    ?.text
    ?.takeIf { it.isNotBlank() }
    ?.let { Contents.of(listOf(Content.Text(it))) }

private fun PromptRequest.systemInstructionText(): String? = chatMessages
    .firstOrNull { it.role == PromptChatRole.System }
    ?.text
    ?.trim()
    ?.takeIf(String::isNotBlank)

private fun PromptRequest.normalizedAdvertisedToolNames(): Set<String> = advertisedToolNames
    .asSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toSortedSet()

private fun PromptRequest.historyBeforeCurrent(): List<PromptChatMessage> {
    val withoutSystem = chatMessages.filter { it.role != PromptChatRole.System }
    return if (audioPrompt == null && withoutSystem.lastOrNull()?.role == PromptChatRole.User) {
        withoutSystem.dropLast(1)
    } else {
        withoutSystem
    }
}

internal fun PromptRequest.transcriptAfter(assistantText: String): List<PromptChatMessage> = buildList {
    addAll(chatMessages.filter { it.role != PromptChatRole.System })
    (content as? MessageContent.AudioPromptContent)
        ?.transcript
        ?.takeIf(String::isNotBlank)
        ?.let { add(PromptChatMessage(PromptChatRole.User, it)) }
    add(PromptChatMessage(PromptChatRole.Assistant, assistantText))
}

private fun List<PromptChatMessage>.toLiteRtMessages(): List<Message> = map { message ->
    when (message.role) {
        PromptChatRole.System -> Message.system(message.text)
        PromptChatRole.User -> Message.user(message.text)
        PromptChatRole.Assistant -> Message.model(Contents.of(listOf(Content.Text(message.text))))
    }
}

private fun PromptRequest.toCurrentLiteRtContents(): Contents = Contents.of(
    buildList {
        imageAttachments.forEach { add(Content.ImageFile(it.uri.toLiteRtFilePath())) }
        audioPrompt?.let { add(Content.AudioFile(it.uri.toLiteRtFilePath())) }
        if (audioPrompt == null) {
            chatMessages
                .lastOrNull { it.role == PromptChatRole.User }
                ?.text
                ?.takeIf { it.isNotBlank() }
                ?.let { add(Content.Text(it)) }
        }
    },
)

internal fun PromptRequest.toLiteRtContents(): Contents = Contents.of(
    toLiteRtInputParts().map { part ->
        when (part) {
            is LiteRtInputPart.AudioFile -> Content.AudioFile(part.path)
            is LiteRtInputPart.ImageFile -> Content.ImageFile(part.path)
            is LiteRtInputPart.Text -> Content.Text(part.text)
        }
    },
)

internal sealed interface LiteRtInputPart {
    data class AudioFile(
        val path: String,
    ) : LiteRtInputPart

    data class ImageFile(
        val path: String,
    ) : LiteRtInputPart

    data class Text(
        val text: String,
    ) : LiteRtInputPart
}

internal fun PromptRequest.toLiteRtInputParts(): List<LiteRtInputPart> = buildList {
    imageAttachments.forEach { image ->
        add(LiteRtInputPart.ImageFile(image.uri.toLiteRtFilePath()))
    }
    audioPrompt?.let { audio -> add(LiteRtInputPart.AudioFile(audio.uri.toLiteRtFilePath())) }

    val contextText =
        if (audioPrompt != null) {
            chatMessages.takeIf { it.isNotEmpty() }?.toPlainChatPrompt()
        } else {
            textPrompt
        }
    contextText?.takeIf { it.isNotBlank() }?.let { add(LiteRtInputPart.Text(it)) }
}

internal fun String.toLiteRtFilePath(): String = when {
    startsWith("file://") -> removePrefix("file://")
    startsWith("file:") -> removePrefix("file:")
    else -> this
}
