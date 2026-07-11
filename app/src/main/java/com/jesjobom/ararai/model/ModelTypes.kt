package com.jesjobom.ararai.model

data class InferenceConfig(
    val contextTokens: Int,
    val maxTokens: Int = 128,
    val temperature: Float,
    val topP: Float,
)

enum class ModelRuntime(
    val configValue: String,
    val displayName: String,
) {
    LlamaCpp("llama_cpp", "llama.cpp"),
    LiteRtLm("litert_lm", "LiteRT-LM");

    companion object {
        fun fromConfigValue(value: String): ModelRuntime =
            entries.firstOrNull { it.configValue == value }
                ?: throw IllegalArgumentException("Unsupported model runtime: $value")
    }
}

enum class ModelArtifactFormat(
    val configValue: String,
    val displayName: String,
) {
    Gguf("gguf", "GGUF"),
    LiteRtLmBundle("litert_lm_bundle", "LiteRT-LM bundle");

    companion object {
        fun fromConfigValue(value: String): ModelArtifactFormat =
            entries.firstOrNull { it.configValue == value }
                ?: throw IllegalArgumentException("Unsupported model artifact format: $value")
    }
}

enum class ModelAccelerationPolicy(
    val configValue: String,
    val displayName: String,
) {
    GpuPreferred("gpu_preferred", "GPU preferred"),
    CpuOnly("cpu_only", "CPU only");

    companion object {
        fun fromConfigValue(value: String): ModelAccelerationPolicy =
            entries.firstOrNull { it.configValue == value }
                ?: throw IllegalArgumentException("Unsupported model acceleration policy: $value")
    }
}

data class ModelConfig(
    val id: String,
    val name: String,
    val runtime: ModelRuntime = ModelRuntime.LlamaCpp,
    val artifactFormat: ModelArtifactFormat = ModelArtifactFormat.Gguf,
    val acceleration: ModelAccelerationPolicy = ModelAccelerationPolicy.GpuPreferred,
    val url: String,
    val fileName: String,
    val relativePath: String,
    val sha256: String,
    val expectedBytes: Long?,
    val inference: InferenceConfig,
)

data class ModelCatalog(
    val defaultModelId: String,
    val models: List<ModelConfig>,
) {
    init {
        require(models.isNotEmpty()) { "Model catalog must contain at least one model" }
        require(models.map { it.id }.toSet().size == models.size) {
            "Model catalog model IDs must be unique"
        }
        require(models.any { it.id == defaultModelId }) {
            "Model catalog default model must exist"
        }
    }
}

data class LocalModel(
    val id: String,
    val name: String,
    val filePath: String,
    val runtime: ModelRuntime = ModelRuntime.LlamaCpp,
    val artifactFormat: ModelArtifactFormat = ModelArtifactFormat.Gguf,
    val acceleration: ModelAccelerationPolicy = ModelAccelerationPolicy.GpuPreferred,
)
