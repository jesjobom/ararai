package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ConfiguredLocalLlmEngine(
    private val llamaCppEngine: LocalLlmEngine = LlamaCppLocalLlmEngine(),
    liteRtLmEngine: LocalLlmEngine? = null,
    liteRtLmCacheDir: String? = null,
) : LocalLlmEngine {
    private val liteRtLmEngine: LocalLlmEngine = liteRtLmEngine ?: LiteRtLmLocalLlmEngine(
        bridge = AndroidLiteRtLmBridge(cacheDir = liteRtLmCacheDir),
    )
    private var activeEngine: LocalLlmEngine? = null

    override suspend fun load(model: LocalModel, config: InferenceConfig) {
        val targetEngine = when (model.runtime) {
            ModelRuntime.LlamaCpp -> llamaCppEngine
            ModelRuntime.LiteRtLm -> liteRtLmEngine
        }
        if (activeEngine !== targetEngine) {
            activeEngine?.unload()
            activeEngine = targetEngine
        }
        targetEngine.load(model, config)
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> =
        activeEngine?.generate(request)
            ?: flowOf(GenerationEvent.Failed("Model is not loaded"))

    override suspend fun unload() {
        activeEngine?.unload()
        activeEngine = null
    }
}
