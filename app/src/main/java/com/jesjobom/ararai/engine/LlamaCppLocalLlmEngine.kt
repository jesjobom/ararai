package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelRuntime
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
) : LocalLlmEngine {
    private val lock = Any()
    private var loadedHandle: Long = 0L
    private var loadedModelId: String? = null
    private var loadedModelPath: String? = null
    private var loadedContextTokens: Int = 0
    private var loadedTemperature: Float = 0f
    private var loadedTopP: Float = 0f
    private var loadedTopK: Int = 0
    private var loadedMinP: Float = 0f
    private var loadedRepeatPenalty: Float = 0f
    private var loadedMaxTokens: Int = DEFAULT_MAX_TOKENS
    private var loadedGpuLayerCount: Int = 0

    override suspend fun load(
        model: LocalModel,
        config: InferenceConfig,
    ) {
        check(model.runtime == ModelRuntime.LlamaCpp) {
            "Unsupported local model runtime: ${model.runtime.displayName}"
        }

        val requestedGpuLayerCount = gpuLayerCountFor(model.acceleration)
        val alreadyLoaded =
            synchronized(lock) {
                val isCompatible =
                    loadedState()?.isCompatibleWith(
                        model = model,
                        config = config,
                        gpuLayerCount = requestedGpuLayerCount,
                    ) == true
                if (isCompatible) {
                    loadedMaxTokens = config.maxTokens
                }
                isCompatible
            }
        if (alreadyLoaded) return

        unload()

        loadModel(
            modelId = model.id,
            modelPath = model.filePath,
            config = config,
            gpuLayerCount = requestedGpuLayerCount,
        )
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> = callbackFlow {
        val state = synchronized(lock) { loadedState() }
        val handle = state?.handle ?: 0L
        if (handle == 0L) {
            trySend(GenerationEvent.Failed("Model is not loaded"))
            close()
            return@callbackFlow
        }

        val job =
            launch(dispatcher) {
                try {
                    val result =
                        generateWithState(
                            state = state ?: return@launch,
                            request = request,
                            emitToken = { token -> trySend(GenerationEvent.Token(token)).isSuccess },
                        )
                    if (result.error == null) {
                        trySend(GenerationEvent.Completed)
                    } else if (result.shouldRetryCpu) {
                        val retryState = reloadCpuOnly(result.state)
                        val retryResult =
                            generateWithState(
                                state = retryState,
                                request = request,
                                emitToken = { token -> trySend(GenerationEvent.Token(token)).isSuccess },
                            )
                        if (retryResult.error == null) {
                            trySend(GenerationEvent.Completed)
                        } else {
                            trySend(GenerationEvent.Failed("CPU fallback failed: ${retryResult.error}"))
                        }
                    } else {
                        trySend(GenerationEvent.Failed(result.error))
                    }
                } catch (error: Throwable) {
                    trySend(GenerationEvent.Failed(error.message ?: "Native generation failed"))
                } finally {
                    close()
                }
            }

        awaitClose {
            if (job.isActive) {
                val currentHandle =
                    synchronized(lock) {
                        if (loadedHandle != 0L) loadedHandle else handle
                    }
                bridge.cancel(currentHandle)
            }
            job.cancel()
        }
    }

    override suspend fun unload() {
        val handle =
            synchronized(lock) {
                val current = loadedHandle
                loadedHandle = 0L
                loadedModelId = null
                loadedModelPath = null
                loadedContextTokens = 0
                loadedTemperature = 0f
                loadedTopP = 0f
                loadedTopK = 0
                loadedMinP = 0f
                loadedRepeatPenalty = 0f
                loadedMaxTokens = DEFAULT_MAX_TOKENS
                loadedGpuLayerCount = 0
                current
            }
        if (handle != 0L) {
            withContext(dispatcher) {
                bridge.unloadModel(handle)
            }
        }
    }

    private suspend fun loadModel(
        modelId: String,
        modelPath: String,
        config: InferenceConfig,
        gpuLayerCount: Int,
    ): LoadedState {
        val handle =
            withContext(dispatcher) {
                bridge.loadModel(
                    modelPath = modelPath,
                    contextTokens = config.contextTokens,
                    temperature = config.temperature,
                    topP = config.topP,
                    topK = config.topK,
                    minP = config.minP,
                    repeatPenalty = config.repeatPenalty,
                    gpuLayerCount = gpuLayerCount,
                )
            }
        check(handle != 0L) { "Native model load returned an empty handle" }

        val state =
            LoadedState(
                handle = handle,
                modelId = modelId,
                modelPath = modelPath,
                contextTokens = config.contextTokens,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK,
                minP = config.minP,
                repeatPenalty = config.repeatPenalty,
                maxTokens = config.maxTokens,
                gpuLayerCount = gpuLayerCount,
            )
        synchronized(lock) {
            loadedHandle = state.handle
            loadedModelId = state.modelId
            loadedModelPath = state.modelPath
            loadedContextTokens = state.contextTokens
            loadedTemperature = state.temperature
            loadedTopP = state.topP
            loadedTopK = state.topK
            loadedMinP = state.minP
            loadedRepeatPenalty = state.repeatPenalty
            loadedMaxTokens = state.maxTokens
            loadedGpuLayerCount = state.gpuLayerCount
        }
        return state
    }

    private suspend fun reloadCpuOnly(state: LoadedState): LoadedState {
        val currentHandle =
            synchronized(lock) {
                if (loadedHandle == state.handle) {
                    loadedHandle = 0L
                    loadedModelId = null
                    loadedModelPath = null
                    loadedContextTokens = 0
                    loadedTemperature = 0f
                    loadedTopP = 0f
                    loadedGpuLayerCount = 0
                    state.handle
                } else {
                    0L
                }
            }
        if (currentHandle != 0L) {
            bridge.unloadModel(currentHandle)
        }

        return loadModel(
            modelId = state.modelId,
            modelPath = state.modelPath,
            config =
            InferenceConfig(
                contextTokens = state.contextTokens,
                maxTokens = state.maxTokens,
                temperature = state.temperature,
                topP = state.topP,
                topK = state.topK,
                minP = state.minP,
                repeatPenalty = state.repeatPenalty,
            ),
            gpuLayerCount = CPU_ONLY_LAYER_COUNT,
        )
    }

    private fun generateWithState(
        state: LoadedState,
        request: PromptRequest,
        emitToken: (String) -> Boolean,
    ): GenerationResult {
        if (request.imageAttachments.isNotEmpty() || request.audioPrompt != null) {
            return GenerationResult(
                state = state,
                error = "Selected llama.cpp model does not support image or audio input",
                emittedTokens = 0,
            )
        }
        val textPrompt = request.textPrompt ?: request.prompt
        val chatMessages =
            request.chatMessages.ifEmpty {
                listOf(PromptChatMessage(PromptChatRole.User, textPrompt))
            }
        val prompt = bridge.formatChatPrompt(state.handle, chatMessages) ?: chatMessages.toPlainChatPrompt()
        var emittedTokens = 0
        val error =
            bridge.generate(
                handle = state.handle,
                prompt = prompt,
                maxTokens = state.maxTokens,
                callback =
                LlamaTokenCallback { token ->
                    emittedTokens += 1
                    emitToken(token)
                },
            )
        return GenerationResult(
            state = state,
            error = error,
            emittedTokens = emittedTokens,
        )
    }

    private fun loadedState(): LoadedState? {
        val id = loadedModelId ?: return null
        val path = loadedModelPath ?: return null
        if (loadedHandle == 0L) return null
        return LoadedState(
            handle = loadedHandle,
            modelId = id,
            modelPath = path,
            contextTokens = loadedContextTokens,
            temperature = loadedTemperature,
            topP = loadedTopP,
            topK = loadedTopK,
            minP = loadedMinP,
            repeatPenalty = loadedRepeatPenalty,
            maxTokens = loadedMaxTokens,
            gpuLayerCount = loadedGpuLayerCount,
        )
    }

    private data class LoadedState(
        val handle: Long,
        val modelId: String,
        val modelPath: String,
        val contextTokens: Int,
        val temperature: Float,
        val topP: Float,
        val topK: Int,
        val minP: Float,
        val repeatPenalty: Float,
        val maxTokens: Int,
        val gpuLayerCount: Int,
    ) {
        fun isCompatibleWith(
            model: LocalModel,
            config: InferenceConfig,
            gpuLayerCount: Int,
        ): Boolean = modelId == model.id &&
            modelPath == model.filePath &&
            contextTokens == config.contextTokens &&
            temperature == config.temperature &&
            topP == config.topP &&
            topK == config.topK &&
            minP == config.minP &&
            repeatPenalty == config.repeatPenalty &&
            this.gpuLayerCount == gpuLayerCount
    }

    private data class GenerationResult(
        val state: LoadedState,
        val error: String?,
        val emittedTokens: Int,
    ) {
        val shouldRetryCpu: Boolean =
            error?.contains(INVALID_LOGITS_ERROR) == true &&
                emittedTokens == 0 &&
                state.gpuLayerCount > CPU_ONLY_LAYER_COUNT
    }

    private fun gpuLayerCountFor(acceleration: ModelAccelerationPolicy): Int = when (acceleration) {
        ModelAccelerationPolicy.CpuOnly -> CPU_ONLY_LAYER_COUNT
        ModelAccelerationPolicy.GpuPreferred -> DEFAULT_GPU_LAYER_COUNT
    }

    private companion object {
        const val DEFAULT_MAX_TOKENS = 128
        const val DEFAULT_GPU_LAYER_COUNT = 999
        const val CPU_ONLY_LAYER_COUNT = 0
        const val INVALID_LOGITS_ERROR = "Native sampler received invalid logits"
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
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
        gpuLayerCount: Int,
    ): Long

    fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        callback: LlamaTokenCallback,
    ): String?

    fun formatChatPrompt(
        handle: Long,
        messages: List<PromptChatMessage>,
    ): String?

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
        topK: Int,
        minP: Float,
        repeatPenalty: Float,
        gpuLayerCount: Int,
    ): Long

    external override fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        callback: LlamaTokenCallback,
    ): String?

    override fun formatChatPrompt(
        handle: Long,
        messages: List<PromptChatMessage>,
    ): String? = formatStructuredChatPrompt(
        handle = handle,
        roles = messages.map { it.role.templateRole }.toTypedArray(),
        contents = messages.map { it.text }.toTypedArray(),
    )

    private external fun formatStructuredChatPrompt(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
    ): String?

    external override fun cancel(handle: Long)

    external override fun unloadModel(handle: Long)
}
