package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LlamaCppLocalLlmEngine(
    private val bridge: LlamaNativeBridge = JniLlamaNativeBridge,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS,
) : LocalLlmEngine {
    private val lock = Any()
    private var loadedHandle: Long = 0L
    private var loadedModelPath: String? = null

    override suspend fun load(model: LocalModel, config: InferenceConfig) {
        val alreadyLoaded = synchronized(lock) {
            loadedHandle != 0L && loadedModelPath == model.filePath
        }
        if (alreadyLoaded) return

        unload()

        val handle = withContext(dispatcher) {
            bridge.loadModel(
                modelPath = model.filePath,
                contextTokens = config.contextTokens,
                temperature = config.temperature,
                topP = config.topP,
            )
        }
        check(handle != 0L) { "Native model load returned an empty handle" }

        synchronized(lock) {
            loadedHandle = handle
            loadedModelPath = model.filePath
        }
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> = callbackFlow {
        val handle = synchronized(lock) { loadedHandle }
        if (handle == 0L) {
            trySend(GenerationEvent.Failed("Model is not loaded"))
            close()
            return@callbackFlow
        }

        val job = launch(dispatcher) {
            try {
                val prompt = bridge.formatChatPrompt(handle, request.prompt) ?: request.prompt
                val error = bridge.generate(
                    handle = handle,
                    prompt = prompt,
                    maxTokens = maxTokens,
                    callback = LlamaTokenCallback { token ->
                        trySend(GenerationEvent.Token(token)).isSuccess
                    },
                )
                if (error == null) {
                    trySend(GenerationEvent.Completed)
                } else {
                    trySend(GenerationEvent.Failed(error))
                }
            } catch (error: Throwable) {
                trySend(GenerationEvent.Failed(error.message ?: "Native generation failed"))
            } finally {
                close()
            }
        }

        awaitClose {
            if (job.isActive) {
                bridge.cancel(handle)
            }
            job.cancel()
        }
    }

    override suspend fun unload() {
        val handle = synchronized(lock) {
            val current = loadedHandle
            loadedHandle = 0L
            loadedModelPath = null
            current
        }
        if (handle != 0L) {
            withContext(dispatcher) {
                bridge.unloadModel(handle)
            }
        }
    }

    private companion object {
        const val DEFAULT_MAX_TOKENS = 128
    }
}

fun interface LlamaTokenCallback {
    fun onToken(token: String): Boolean
}

interface LlamaNativeBridge {
    fun loadModel(
        modelPath: String,
        contextTokens: Int,
        temperature: Float,
        topP: Float,
    ): Long

    fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        callback: LlamaTokenCallback,
    ): String?

    fun formatChatPrompt(handle: Long, prompt: String): String?

    fun cancel(handle: Long)

    fun unloadModel(handle: Long)
}

object JniLlamaNativeBridge : LlamaNativeBridge {
    init {
        System.loadLibrary("ararai_llama")
    }

    external override fun loadModel(
        modelPath: String,
        contextTokens: Int,
        temperature: Float,
        topP: Float,
    ): Long

    external override fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        callback: LlamaTokenCallback,
    ): String?

    external override fun formatChatPrompt(handle: Long, prompt: String): String?

    external override fun cancel(handle: Long)

    external override fun unloadModel(handle: Long)
}
