package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.flow.Flow

interface LocalLlmEngine {
    suspend fun load(model: LocalModel, config: InferenceConfig)
    fun generate(request: PromptRequest): Flow<GenerationEvent>
    suspend fun unload()
}

data class PromptRequest(
    val prompt: String,
)

sealed interface GenerationEvent {
    data class Token(val text: String) : GenerationEvent
    data class Failed(val message: String) : GenerationEvent
    data object Completed : GenerationEvent
}
