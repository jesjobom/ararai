package com.jesjobom.ararai.engine

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
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
            )
        }

        synchronized(lock) {
            loadedSession = session
            loadedModelId = model.id
            loadedModelPath = model.filePath
            loadedConfig = config
        }
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> = callbackFlow {
        val state = synchronized(lock) {
            val session = loadedSession
            val config = loadedConfig
            if (session == null || config == null) null else LoadedState(session, config)
        }

        if (state == null) {
            trySend(GenerationEvent.Failed("Model is not loaded"))
            close()
            return@callbackFlow
        }

        val job = launch(dispatcher) {
            try {
                var emittedChunks = 0
                state.session.generate(request.prompt, state.config).collect { chunk ->
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
    )
}

interface LiteRtLmBridge {
    suspend fun load(
        modelPath: String,
        config: InferenceConfig,
        useGpu: Boolean,
    ): LiteRtLmSession
}

interface LiteRtLmSession {
    fun generate(prompt: String, config: InferenceConfig): Flow<String>
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
    ): LiteRtLmSession {
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = if (useGpu) Backend.GPU() else Backend.CPU(),
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

    override fun generate(prompt: String, config: InferenceConfig): Flow<String> = callbackFlow {
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

            conversation.sendMessageAsync(prompt, callback)
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

    private companion object {
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_SEED = 0
    }
}
