package com.jesjobom.ararai.model

data class InferenceConfig(
    val contextTokens: Int,
    val maxTokens: Int = 128,
    val temperature: Float,
    val topP: Float,
    val topK: Int = 40,
    val minP: Float = 0.05f,
    val repeatPenalty: Float = 1.10f,
)

data class ChatConfig(
    val systemPrompt: String,
)

data class ModelInputCapabilities(
    val text: Boolean = true,
    val image: Boolean = false,
    val audio: Boolean = false,
)

data class ModelReasoningCapabilities(
    val request: Boolean = false,
    val output: Boolean = false,
)

enum class ModelRuntime(
    val configValue: String,
    val displayName: String,
) {
    LlamaCpp("llama_cpp", "llama.cpp"),
    LiteRtLm("litert_lm", "LiteRT-LM"),
    WhisperCpp("whisper_cpp", "whisper.cpp"),
    ;

    companion object {
        fun fromConfigValue(value: String): ModelRuntime = entries.firstOrNull { it.configValue == value }
            ?: throw IllegalArgumentException("Unsupported model runtime: $value")
    }
}

enum class ModelArtifactFormat(
    val configValue: String,
    val displayName: String,
) {
    Gguf("gguf", "GGUF"),
    LiteRtLmBundle("litert_lm_bundle", "LiteRT-LM bundle"),
    WhisperGgml("whisper_ggml", "Whisper GGML"),
    ;

    companion object {
        fun fromConfigValue(value: String): ModelArtifactFormat = entries.firstOrNull { it.configValue == value }
            ?: throw IllegalArgumentException("Unsupported model artifact format: $value")
    }
}

enum class ModelPurpose(
    val configValue: String,
    val displayName: String,
) {
    Chat("chat", "Chat"),
    Reasoning("reasoning", "Reasoning"),
    Utility("utility", "Utility"),
    ;

    companion object {
        fun fromConfigValue(value: String): ModelPurpose = entries.firstOrNull { it.configValue == value }
            ?: throw IllegalArgumentException("Unsupported model purpose: $value")
    }
}

enum class ModelTask(
    val configValue: String,
    val displayName: String,
) {
    Chat("chat", "Chat"),
    Reasoning("reasoning", "Reasoning"),
    Transcription("transcription", "Audio transcription"),
    ;

    companion object {
        fun fromConfigValue(value: String): ModelTask = entries.firstOrNull { it.configValue == value }
            ?: throw IllegalArgumentException("Unsupported model task: $value")
    }
}

enum class ModelMaturity(
    val configValue: String,
    val displayName: String,
) {
    Stable("stable", "Stable"),
    Experimental("experimental", "Experimental"),
    ;

    companion object {
        fun fromConfigValue(value: String): ModelMaturity = entries.firstOrNull { it.configValue == value }
            ?: throw IllegalArgumentException("Unsupported model maturity: $value")
    }
}

enum class ModelAccelerationPolicy(
    val configValue: String,
    val displayName: String,
) {
    GpuPreferred("gpu_preferred", "GPU preferred"),
    CpuOnly("cpu_only", "CPU only"),
    ;

    companion object {
        fun fromConfigValue(value: String): ModelAccelerationPolicy = entries.firstOrNull { it.configValue == value }
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
    val fallbackUrls: List<String> = emptyList(),
    val fileName: String,
    val relativePath: String,
    val sha256: String,
    val expectedBytes: Long?,
    val recommendedFreeRamBytes: Long? = null,
    val purposes: Set<ModelPurpose> = setOf(ModelPurpose.Chat),
    val tasks: Set<ModelTask> = setOf(ModelTask.Chat),
    val maturity: ModelMaturity = ModelMaturity.Stable,
    val variant: String? = null,
    val inference: InferenceConfig? = null,
    val inputCapabilities: ModelInputCapabilities = ModelInputCapabilities(),
    val reasoningCapabilities: ModelReasoningCapabilities = ModelReasoningCapabilities(),
)

fun ModelConfig.supportsPurpose(purpose: ModelPurpose): Boolean = purpose in purposes

fun ModelConfig.supportsTask(task: ModelTask): Boolean = task in tasks

fun ModelConfig.requireInference(): InferenceConfig = requireNotNull(inference) {
    "Model $id does not define LLM inference settings"
}

data class ModelCatalog(
    val defaultModelId: String,
    val models: List<ModelConfig>,
    val chat: ChatConfig = ChatConfig(DEFAULT_SYSTEM_PROMPT),
) {
    init {
        require(models.isNotEmpty()) { "Model catalog must contain at least one model" }
        require(models.map { it.id }.toSet().size == models.size) {
            "Model catalog model IDs must be unique"
        }
        require(models.any { it.id == defaultModelId }) {
            "Model catalog default model must exist"
        }
        require(models.first { it.id == defaultModelId }.supportsPurpose(ModelPurpose.Chat)) {
            "Model catalog default model must support Chat"
        }
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT: String =
            "You are ArarAI, a concise local assistant. Answer directly and ask for clarification when needed."
    }
}

data class LocalModel(
    val id: String,
    val name: String,
    val filePath: String,
    val runtime: ModelRuntime = ModelRuntime.LlamaCpp,
    val artifactFormat: ModelArtifactFormat = ModelArtifactFormat.Gguf,
    val acceleration: ModelAccelerationPolicy = ModelAccelerationPolicy.GpuPreferred,
    val inputCapabilities: ModelInputCapabilities = ModelInputCapabilities(),
    val reasoningCapabilities: ModelReasoningCapabilities = ModelReasoningCapabilities(),
)
