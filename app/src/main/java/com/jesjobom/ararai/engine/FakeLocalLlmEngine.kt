package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLocalLlmEngine(
    private val chunks: List<String> =
        listOf(
            "ArarAI phase 1 is running. ",
            "Native llama.cpp inference comes next.",
        ),
    private val delayMillis: Long = 0L,
) : LocalLlmEngine {
    private var loadedModel: LocalModel? = null

    override suspend fun load(
        model: LocalModel,
        config: InferenceConfig,
    ) {
        loadedModel = model
    }

    override fun generate(request: PromptRequest): Flow<GenerationEvent> = flow {
        if (loadedModel == null) {
            emit(
                GenerationEvent.Failed(
                    message = "Model is not loaded",
                    kind = GenerationFailureKind.Expected,
                ),
            )
            return@flow
        }

        chunks.forEach { chunk ->
            if (delayMillis > 0L) delay(delayMillis)
            emit(GenerationEvent.Token(chunk))
        }
        emit(GenerationEvent.Completed)
    }

    override suspend fun unload() {
        loadedModel = null
    }
}
