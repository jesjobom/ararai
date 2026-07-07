package com.jesjobom.ararai.model

data class InferenceConfig(
    val contextTokens: Int,
    val maxTokens: Int = 128,
    val temperature: Float,
    val topP: Float,
)

data class ModelConfig(
    val id: String,
    val name: String,
    val url: String,
    val fileName: String,
    val relativePath: String,
    val sha256: String,
    val expectedBytes: Long?,
    val inference: InferenceConfig,
)

data class LocalModel(
    val id: String,
    val name: String,
    val filePath: String,
)
