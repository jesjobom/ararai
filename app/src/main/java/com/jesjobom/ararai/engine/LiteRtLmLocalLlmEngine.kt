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
                var emittedChunks = 0
                state.session.generate(request, state.config).collect { chunk ->
                    if (chunk.isNotEmpty()) {
                        emittedChunks += 1
                        trySend(GenerationEvent.Token(chunk))
                    }
                    if (emittedChunks >= state.config.maxTokens) {
                        state.session.cancel()
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
    fun generate(request: PromptRequest, config: InferenceConfig): Flow<String>
    fun cancel()
    fun close()
}

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

    override fun generate(request: PromptRequest, config: InferenceConfig): Flow<String> = callbackFlow {
        val samplerConfig = SamplerConfig(
            topK = DEFAULT_TOP_K,
            topP = config.topP.toDouble(),
            temperature = config.temperature.toDouble(),
            seed = DEFAULT_SEED,
        )
        val conversation = engine.createConversation(
            ConversationConfig(samplerConfig = samplerConfig),
        )

        try {
            activeConversation = conversation
            var previousText = ""
            val callback = object : MessageCallback {
                override fun onMessage(message: Message) {
                    val currentText = message.text()
                    val delta = if (currentText.startsWith(previousText)) {
                        currentText.removePrefix(previousText)
                    } else {
                        currentText
                    }
                    previousText = currentText
                    if (delta.isNotEmpty()) {
                        trySend(delta)
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

    private fun PromptRequest.toLiteRtContents(): Contents =
        audioPrompt?.let { audio ->
            Contents.of(Content.AudioFile(audio.uri.asFilePath()))
        } ?: Contents.of(
            buildList {
                imageAttachments.forEach { image ->
                    add(Content.ImageFile(image.uri.asFilePath()))
                }
                textPrompt?.takeIf { it.isNotBlank() }?.let { text ->
                    add(Content.Text(text))
                }
            },
        )

    private fun String.asFilePath(): String =
        toLiteRtFilePath()

    private companion object {
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_SEED = 0
    }
}

internal fun String.toLiteRtFilePath(): String =
    when {
        startsWith("file://") -> removePrefix("file://")
        startsWith("file:") -> removePrefix("file:")
        else -> this
    }
