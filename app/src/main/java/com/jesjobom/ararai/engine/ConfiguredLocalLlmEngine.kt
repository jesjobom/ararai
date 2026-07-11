package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ConfiguredLocalLlmEngine(
    private val llamaCppEngine: LocalLlmEngine = LlamaCppLocalLlmEngine(),
) : LocalLlmEngine {
    private var activeEngine: LocalLlmEngine? = null

    override suspend fun load(model: LocalModel, config: InferenceConfig) {
        activeEngine = when (model.runtime) {
            ModelRuntime.LlamaCpp -> llamaCppEngine
            ModelRuntime.LiteRtLm -> {
                error("LiteRT-LM runtime is not implemented yet")
            }
        }
        activeEngine?.load(model, config)
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> =
        activeEngine?.generate(request)
            ?: flowOf(GenerationEvent.Failed("Model is not loaded"))

    override suspend fun unload() {
        activeEngine?.unload()
        activeEngine = null
    }
}
