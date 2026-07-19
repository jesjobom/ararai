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
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelRuntime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

class LiteRtLmLocalLlmEngine(
    private val bridge: LiteRtLmBridge = AndroidLiteRtLmBridge(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LocalLlmEngine {
    private val lock = Any()
    private var loadedSession: LiteRtLmSession? = null
    private var loadedModelId: String? = null
    private var loadedModelPath: String? = null
    private var loadedConfig: InferenceConfig? = null
    private var loadedInputCapabilities: ModelInputCapabilities? = null
    private var loadedUseGpu: Boolean = false
    private var loadedProfile: LiteRtLmWorkloadProfile? = null

    override suspend fun load(
        model: LocalModel,
        config: InferenceConfig,
    ) {
        check(model.runtime == ModelRuntime.LiteRtLm) {
            "Unsupported local model runtime: ${model.runtime.displayName}"
        }

        val alreadyLoaded =
            synchronized(lock) {
                val isLoaded =
                    loadedSession != null &&
                        loadedModelId == model.id &&
                        loadedModelPath == model.filePath
                if (isLoaded) {
                    loadedConfig = config
                    loadedInputCapabilities = model.inputCapabilities
                }
                isLoaded
            }
        if (alreadyLoaded) return

        unload()

        val session =
            withContext(dispatcher) {
                bridge.load(
                    modelPath = model.filePath,
                    config = config,
                    useGpu = model.acceleration == ModelAccelerationPolicy.GpuPreferred,
                    inputCapabilities = model.inputCapabilities,
                    profile = LiteRtLmWorkloadProfile.TextOnly,
                )
            }

        synchronized(lock) {
            loadedSession = session
            loadedModelId = model.id
            loadedModelPath = model.filePath
            loadedConfig = config
            loadedInputCapabilities = model.inputCapabilities
            loadedUseGpu = model.acceleration == ModelAccelerationPolicy.GpuPreferred
            loadedProfile = LiteRtLmWorkloadProfile.TextOnly
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
                    LoadedState(config, capabilities)
                }
            }

        if (initialState == null) {
            trySend(GenerationEvent.Failed("Model is not loaded"))
            close()
            return@callbackFlow
        }

        val job =
            launch(dispatcher) {
                try {
                    request.validateAgainst(initialState.capabilities)?.let { failure ->
                        trySend(GenerationEvent.Failed(failure))
                        return@launch
                    }
                    val session = ensureProfile(request)
                    session.generate(request, initialState.config).collect { chunk ->
                        if (chunk.text.isNotEmpty()) {
                            trySend(GenerationEvent.Token(chunk.text))
                        }
                        if (chunk.reasoning.isNotEmpty()) {
                            trySend(GenerationEvent.ReasoningToken(chunk.reasoning))
                        }
                        chunk.metrics?.let { trySend(GenerationEvent.Metrics(it)) }
                    }
                    generationFinished.set(true)
                    trySend(GenerationEvent.Completed)
                } catch (error: Throwable) {
                    trySend(GenerationEvent.Failed(error.message ?: "LiteRT-LM generation failed"))
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

    override suspend fun unload() {
        val session =
            synchronized(lock) {
                val current = loadedSession
                loadedSession = null
                loadedModelId = null
                loadedModelPath = null
                loadedConfig = null
                loadedInputCapabilities = null
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

    private data class LoadedState(
        val config: InferenceConfig,
        val capabilities: ModelInputCapabilities,
    )

    private suspend fun ensureProfile(request: PromptRequest): LiteRtLmSession {
        val desiredProfile = LiteRtLmWorkloadProfile.from(request)
        val snapshot =
            synchronized(lock) {
                ProfileState(
                    session = checkNotNull(loadedSession) { "Model is not loaded" },
                    currentProfile = checkNotNull(loadedProfile) { "Model is not loaded" },
                    modelPath = checkNotNull(loadedModelPath) { "Model is not loaded" },
                    config = checkNotNull(loadedConfig) { "Model is not loaded" },
                    capabilities = checkNotNull(loadedInputCapabilities) { "Model is not loaded" },
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
        val useGpu: Boolean,
    )

    private fun PromptRequest.validateAgainst(capabilities: ModelInputCapabilities): String? = when {
        textPrompt != null && !capabilities.text -> "Selected model does not support text input"
        imageAttachments.isNotEmpty() && !capabilities.image -> "Selected model does not support image input"
        audioPrompt != null && !capabilities.audio -> "Selected model does not support audio input"
        else -> null
    }
}

interface LiteRtLmBridge {
    suspend fun load(
        modelPath: String,
        config: InferenceConfig,
        useGpu: Boolean,
        inputCapabilities: ModelInputCapabilities,
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
)

@OptIn(ExperimentalApi::class)
class AndroidLiteRtLmBridge(
    private val cacheDir: String? = null,
) : LiteRtLmBridge {
    override suspend fun load(
        modelPath: String,
        config: InferenceConfig,
        useGpu: Boolean,
        inputCapabilities: ModelInputCapabilities,
        profile: LiteRtLmWorkloadProfile,
    ): LiteRtLmSession {
        ExperimentalFlags.enableBenchmark = true
        ExperimentalFlags.enableSpeculativeDecoding = true
        val backend = if (useGpu) Backend.GPU() else Backend.CPU()
        val engineConfig =
            EngineConfig(
                modelPath = modelPath,
                backend = backend,
                visionBackend = if (profile.image && inputCapabilities.image) backend else null,
                audioBackend = if (profile.audio && inputCapabilities.audio) Backend.CPU() else null,
                maxNumTokens = null,
                cacheDir = cacheDir,
            )
        val engine = Engine(engineConfig)

        return try {
            engine.initialize()
            AndroidLiteRtLmSession(engine)
        } catch (error: Throwable) {
            engine.close()
            throw error
        }
    }
}

@OptIn(ExperimentalApi::class)
private class AndroidLiteRtLmSession(
    private val engine: Engine,
) : LiteRtLmSession {
    private val conversations =
        RetainedResourceOwner<Conversation, RetainedConversationState>(
            cancelResource = Conversation::cancelProcess,
            closeResource = Conversation::close,
        )

    override fun generate(
        request: PromptRequest,
        config: InferenceConfig,
    ): Flow<LiteRtLmChunk> = callbackFlow {
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
            )
        val historyBeforeCurrent = request.historyBeforeCurrent()
        val retained = conversations.retained()
        val canReuse =
            retained != null &&
                canReuseLiteRtLmConversation(
                    retainedKey = retained.state.key,
                    retainedTranscript = retained.state.transcript,
                    requestKey = key,
                    requestHistory = historyBeforeCurrent,
                )
        val conversation =
            if (canReuse) {
                retained.resource
            } else {
                retained?.resource?.let { conversations.invalidate(it, cancelFirst = false) }
                engine.createConversation(
                    ConversationConfig(
                        systemInstruction = request.systemInstruction(),
                        initialMessages = historyBeforeCurrent.toLiteRtMessages(),
                        samplerConfig = samplerConfig,
                        extraContext = mapOf(ENABLE_THINKING_CONTEXT_KEY to request.reasoningEnabled),
                    ),
                )
            }
        val reusable = key.sessionId != null
        val completed = AtomicBoolean(false)

        try {
            conversations.activate(conversation)
            var previousText = ""
            var previousReasoning = ""
            val callback =
                object : MessageCallback {
                    override fun onMessage(message: Message) {
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
                        runCatching { conversation.getBenchmarkInfo() }
                            .onFailure { error ->
                                Log.w("ArarAI.LiteRtLm", "Unable to read LiteRT-LM benchmark metrics", error)
                            }.getOrNull()
                            ?.let { benchmark ->
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
                                ),
                            )
                        }
                        completed.set(true)
                        close(null)
                    }

                    override fun onError(throwable: Throwable) {
                        close(throwable)
                    }
                }

            conversation.sendMessageAsync(request.toCurrentLiteRtContents(), callback)
        } catch (error: Throwable) {
            close(error)
        }

        awaitClose {
            if (!completed.get()) {
                conversations.invalidate(conversation, cancelFirst = true)
            } else if (!reusable) {
                conversations.invalidate(conversation, cancelFirst = false)
            }
        }
    }

    override fun cancel() {
        conversations.cancelActive()
    }

    override fun close() {
        conversations.closeAll()
        engine.close()
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
    }

    private data class RetainedConversationState(
        val key: LiteRtLmConversationKey,
        val transcript: List<PromptChatMessage>,
    )
}

internal data class RetainedResource<R : Any, S>(
    val resource: R,
    val state: S,
)

internal class RetainedResourceOwner<R : Any, S>(
    private val cancelResource: (R) -> Unit,
    private val closeResource: (R) -> Unit,
) {
    private val lock = Any()
    private val disposed = IdentityHashMap<R, Unit>()
    private var active: R? = null
    private var retained: RetainedResource<R, S>? = null

    fun retained(): RetainedResource<R, S>? = synchronized(lock) { retained }

    fun activate(resource: R): Boolean = synchronized(lock) {
        if (disposed.containsKey(resource)) return@synchronized false
        active = resource
        true
    }

    fun retain(
        resource: R,
        state: S,
    ): Boolean = synchronized(lock) {
        if (disposed.containsKey(resource)) return@synchronized false
        active = resource
        retained = RetainedResource(resource, state)
        true
    }

    fun invalidate(
        resource: R,
        cancelFirst: Boolean,
    ) {
        disposeClaimed(claimForDisposal(listOf(resource)), cancelFirst)
    }

    fun cancelActive() {
        val owned = synchronized(lock) { listOfNotNull(active, retained?.resource) }
        disposeClaimed(claimForDisposal(owned), cancelFirst = true)
    }

    fun closeAll() {
        val owned = synchronized(lock) { listOfNotNull(active, retained?.resource) }
        disposeClaimed(claimForDisposal(owned), cancelFirst = false)
    }

    private fun claimForDisposal(resources: List<R>): List<R> = synchronized(lock) {
        resources.filter { resource ->
            if (disposed.containsKey(resource)) {
                false
            } else {
                disposed[resource] = Unit
                if (active === resource) active = null
                if (retained?.resource === resource) retained = null
                true
            }
        }
    }

    private fun disposeClaimed(
        resources: List<R>,
        cancelFirst: Boolean,
    ) {
        resources.forEach { resource ->
            try {
                if (cancelFirst) cancelResource(resource)
            } finally {
                closeResource(resource)
            }
        }
    }
}

internal data class LiteRtLmConversationKey(
    val sessionId: String?,
    val temperature: Float,
    val topP: Float,
    val reasoningEnabled: Boolean,
)

internal fun canReuseLiteRtLmConversation(
    retainedKey: LiteRtLmConversationKey,
    retainedTranscript: List<PromptChatMessage>,
    requestKey: LiteRtLmConversationKey,
    requestHistory: List<PromptChatMessage>,
): Boolean = requestKey.sessionId != null &&
    retainedKey == requestKey &&
    retainedTranscript == requestHistory

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

private fun PromptRequest.historyBeforeCurrent(): List<PromptChatMessage> {
    val withoutSystem = chatMessages.filter { it.role != PromptChatRole.System }
    return if (audioPrompt == null && withoutSystem.lastOrNull()?.role == PromptChatRole.User) {
        withoutSystem.dropLast(1)
    } else {
        withoutSystem
    }
}

private fun PromptRequest.transcriptAfter(assistantText: String): List<PromptChatMessage> = chatMessages.filter { it.role != PromptChatRole.System } +
    PromptChatMessage(PromptChatRole.Assistant, assistantText)

private fun List<PromptChatMessage>.toLiteRtMessages(): List<Message> = map { message ->
    when (message.role) {
        PromptChatRole.System -> Message.system(message.text)
        PromptChatRole.User -> Message.user(message.text)
        PromptChatRole.Assistant -> Message.model(Contents.of(listOf(Content.Text(message.text))))
    }
}

private fun PromptRequest.toCurrentLiteRtContents(): Contents = Contents.of(
    buildList {
        audioPrompt?.let { add(Content.AudioFile(it.uri.toLiteRtFilePath())) }
            ?: imageAttachments.forEach { add(Content.ImageFile(it.uri.toLiteRtFilePath())) }
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
    audioPrompt?.let { audio ->
        add(LiteRtInputPart.AudioFile(audio.uri.toLiteRtFilePath()))
    } ?: imageAttachments.forEach { image ->
        add(LiteRtInputPart.ImageFile(image.uri.toLiteRtFilePath()))
    }

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
