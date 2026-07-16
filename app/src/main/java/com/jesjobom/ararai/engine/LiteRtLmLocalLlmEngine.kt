package com.jesjobom.ararai.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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

    override suspend fun load(model: LocalModel, config: InferenceConfig) {
        check(model.runtime == ModelRuntime.LiteRtLm) {
            "Unsupported local model runtime: ${model.runtime.displayName}"
        }

        val alreadyLoaded = synchronized(lock) {
            val isLoaded = loadedSession != null &&
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

        val session = withContext(dispatcher) {
            bridge.load(
                modelPath = model.filePath,
                config = config,
                useGpu = model.acceleration == ModelAccelerationPolicy.GpuPreferred,
                inputCapabilities = model.inputCapabilities,
            )
        }

        synchronized(lock) {
            loadedSession = session
            loadedModelId = model.id
            loadedModelPath = model.filePath
            loadedConfig = config
            loadedInputCapabilities = model.inputCapabilities
        }
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> = callbackFlow {
        val state = synchronized(lock) {
            val session = loadedSession
            val config = loadedConfig
            val capabilities = loadedInputCapabilities
            if (session == null || config == null || capabilities == null) {
                null
            } else {
                LoadedState(session, config, capabilities)
            }
        }

        if (state == null) {
            trySend(GenerationEvent.Failed("Model is not loaded"))
            close()
            return@callbackFlow
        }

        val job = launch(dispatcher) {
            try {
                request.validateAgainst(state.capabilities)?.let { failure ->
                    trySend(GenerationEvent.Failed(failure))
                    return@launch
                }
                state.session.generate(request, state.config).collect { chunk ->
                    if (chunk.text.isNotEmpty()) {
                        trySend(GenerationEvent.Token(chunk.text))
                    }
                    if (chunk.reasoning.isNotEmpty()) {
                        trySend(GenerationEvent.ReasoningToken(chunk.reasoning))
                    }
                }
                trySend(GenerationEvent.Completed)
            } catch (error: Throwable) {
                trySend(GenerationEvent.Failed(error.message ?: "LiteRT-LM generation failed"))
            } finally {
                close()
            }
        }

        awaitClose {
            if (job.isActive) {
                state.session.cancel()
            }
            job.cancel()
        }
    }

    override suspend fun unload() {
        val session = synchronized(lock) {
            val current = loadedSession
            loadedSession = null
            loadedModelId = null
            loadedModelPath = null
            loadedConfig = null
            loadedInputCapabilities = null
            current
        }

        if (session != null) {
            withContext(dispatcher) {
                session.close()
            }
        }
    }

    private data class LoadedState(
        val session: LiteRtLmSession,
        val config: InferenceConfig,
        val capabilities: ModelInputCapabilities,
    )

    private fun PromptRequest.validateAgainst(capabilities: ModelInputCapabilities): String? =
        when {
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
    ): LiteRtLmSession
}

interface LiteRtLmSession {
    fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk>
    fun cancel()
    fun close()
}

data class LiteRtLmChunk(
    val text: String = "",
    val reasoning: String = "",
)

class AndroidLiteRtLmBridge(
    private val cacheDir: String? = null,
) : LiteRtLmBridge {
    override suspend fun load(
        modelPath: String,
        config: InferenceConfig,
        useGpu: Boolean,
        inputCapabilities: ModelInputCapabilities,
    ): LiteRtLmSession {
        val backend = if (useGpu) Backend.GPU() else Backend.CPU()
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            visionBackend = if (inputCapabilities.image) backend else null,
            audioBackend = if (inputCapabilities.audio) Backend.CPU() else null,
            maxNumTokens = config.contextTokens,
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

private class AndroidLiteRtLmSession(
    private val engine: Engine,
) : LiteRtLmSession {
    @Volatile
    private var activeConversation: Conversation? = null

    override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = callbackFlow {
        val samplerConfig = SamplerConfig(
            topK = DEFAULT_TOP_K,
            topP = config.topP.toDouble(),
            temperature = config.temperature.toDouble(),
            seed = DEFAULT_SEED,
        )
        val conversation = engine.createConversation(
            ConversationConfig(
                samplerConfig = samplerConfig,
                extraContext = mapOf(ENABLE_THINKING_CONTEXT_KEY to request.reasoningEnabled),
            ),
        )

        try {
            activeConversation = conversation
            var previousText = ""
            var previousReasoning = ""
            val callback = object : MessageCallback {
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
                    close(null)
                }

                override fun onError(throwable: Throwable) {
                    close(throwable)
                }
            }

            conversation.sendMessageAsync(request.toLiteRtContents(), callback)
        } catch (error: Throwable) {
            close(error)
        }

        awaitClose {
            activeConversation = null
            conversation.cancelProcess()
            conversation.close()
        }
    }

    override fun cancel() {
        activeConversation?.cancelProcess()
    }

    override fun close() {
        activeConversation?.close()
        activeConversation = null
        engine.close()
    }

    private fun Message.text(): String =
        contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString(separator = "") { it.text }

    private fun Message.reasoning(): String =
        channels.values.joinToString(separator = "")

    private fun String.deltaAfter(previous: String): String =
        if (startsWith(previous)) removePrefix(previous) else this

    private companion object {
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_SEED = 0
        const val ENABLE_THINKING_CONTEXT_KEY = "enable_thinking"
    }
}

internal fun PromptRequest.toLiteRtContents(): Contents =
    Contents.of(
        toLiteRtInputParts().map { part ->
            when (part) {
                is LiteRtInputPart.AudioFile -> Content.AudioFile(part.path)
                is LiteRtInputPart.ImageFile -> Content.ImageFile(part.path)
                is LiteRtInputPart.Text -> Content.Text(part.text)
            }
        },
    )

internal sealed interface LiteRtInputPart {
    data class AudioFile(val path: String) : LiteRtInputPart
    data class ImageFile(val path: String) : LiteRtInputPart
    data class Text(val text: String) : LiteRtInputPart
}

internal fun PromptRequest.toLiteRtInputParts(): List<LiteRtInputPart> =
    buildList {
            audioPrompt?.let { audio ->
                add(LiteRtInputPart.AudioFile(audio.uri.toLiteRtFilePath()))
            } ?: imageAttachments.forEach { image ->
                add(LiteRtInputPart.ImageFile(image.uri.toLiteRtFilePath()))
            }

            val contextText = if (audioPrompt != null) {
                chatMessages.takeIf { it.isNotEmpty() }?.toPlainChatPrompt()
            } else {
                textPrompt
            }
            contextText?.takeIf { it.isNotBlank() }?.let { add(LiteRtInputPart.Text(it)) }
        }

internal fun String.toLiteRtFilePath(): String =
    when {
        startsWith("file://") -> removePrefix("file://")
        startsWith("file:") -> removePrefix("file:")
        else -> this
    }
