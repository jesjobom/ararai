package com.jesjobom.ararai.model

import java.io.StringReader
import java.util.Properties

@Suppress("TooManyFunctions")
object ModelConfigParser {
    fun parse(raw: String): ModelConfig {
        val properties = Properties().apply { load(StringReader(raw)) }

        return properties.parseModel(modelPrefix = "model.", inferencePrefix = "inference.")
    }

    fun parseCatalog(raw: String): ModelCatalog {
        val properties = Properties().apply { load(StringReader(raw)) }
        val count = properties.getProperty("models.count")?.trim()?.toInt()

        if (count == null) {
            val model = properties.parseModel(modelPrefix = "model.", inferencePrefix = "inference.")
            return ModelCatalog(
                defaultModelId = model.id,
                models = listOf(model),
                chat = properties.parseChatConfig(),
            )
        }

        require(count > 0) { "models.count must be positive" }

        val models =
            (0 until count).map { index ->
                properties.parseModel(
                    modelPrefix = "models.$index.",
                    inferencePrefix = "models.$index.inference.",
                )
            }
        val defaultModelId =
            properties
                .getProperty("models.defaultId")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: models.first().id

        return ModelCatalog(
            defaultModelId = defaultModelId,
            models = models,
            chat = properties.parseChatConfig(),
        )
    }

    private fun Properties.parseChatConfig(): ChatConfig = ChatConfig(
        systemPrompt = optional("chat.systemPrompt", ModelCatalog.DEFAULT_SYSTEM_PROMPT),
    )

    private fun Properties.parseModel(
        modelPrefix: String,
        inferencePrefix: String,
    ): ModelConfig = ModelConfig(
        id = required("${modelPrefix}id"),
        name = required("${modelPrefix}name"),
        runtime =
        ModelRuntime.fromConfigValue(
            optional("${modelPrefix}runtime", ModelRuntime.LlamaCpp.configValue),
        ),
        artifactFormat =
        ModelArtifactFormat.fromConfigValue(
            optional("${modelPrefix}artifactFormat", ModelArtifactFormat.Gguf.configValue),
        ),
        acceleration =
        ModelAccelerationPolicy.fromConfigValue(
            optional("${modelPrefix}acceleration", ModelAccelerationPolicy.GpuPreferred.configValue),
        ),
        gpuLayerCount = getProperty("${modelPrefix}gpuLayerCount")?.trim()?.toInt(),
        url = required("${modelPrefix}url"),
        fallbackUrls = optionalList("${modelPrefix}fallbackUrls"),
        fileName = required("${modelPrefix}fileName"),
        relativePath = required("${modelPrefix}relativePath"),
        sha256 = required("${modelPrefix}sha256").lowercase(),
        expectedBytes = getProperty("${modelPrefix}expectedBytes")?.toLong(),
        recommendedFreeRamBytes = getProperty("${modelPrefix}recommendedFreeRamBytes")?.toLong(),
        purposes = optionalEnumSet("${modelPrefix}purposes", setOf(ModelPurpose.Chat), ModelPurpose::fromConfigValue),
        tasks = optionalEnumSet("${modelPrefix}tasks", setOf(ModelTask.Chat), ModelTask::fromConfigValue),
        maturity = ModelMaturity.fromConfigValue(
            optional("${modelPrefix}maturity", ModelMaturity.Stable.configValue),
        ),
        variant = getProperty("${modelPrefix}variant")?.trim()?.takeIf(String::isNotEmpty),
        inference = parseInference(inferencePrefix),
        inputCapabilities =
        ModelInputCapabilities(
            text = optionalBoolean("${modelPrefix}capabilities.input.text", defaultValue = true),
            image = optionalBoolean("${modelPrefix}capabilities.input.image", defaultValue = false),
            audio = optionalBoolean("${modelPrefix}capabilities.input.audio", defaultValue = false),
        ),
        reasoningCapabilities =
        ModelReasoningCapabilities(
            request = optionalBoolean("${modelPrefix}capabilities.reasoning.request", defaultValue = false),
            output = optionalBoolean("${modelPrefix}capabilities.reasoning.output", defaultValue = false),
        ),
    ).also { it.validate() }

    private fun Properties.parseInference(prefix: String): InferenceConfig? {
        if (getProperty("${prefix}contextTokens").isNullOrBlank()) return null
        return InferenceConfig(
            contextTokens = required("${prefix}contextTokens").toInt(),
            maxTokens = required("${prefix}maxTokens").toInt(),
            temperature = required("${prefix}temperature").toFloat(),
            topP = required("${prefix}topP").toFloat(),
            topK = optional("${prefix}topK", "40").toInt(),
            minP = optional("${prefix}minP", "0.05").toFloat(),
            repeatPenalty = optional("${prefix}repeatPenalty", "1.10").toFloat(),
        )
    }

    private fun Properties.required(key: String): String = getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Missing required model config field: $key")

    private fun Properties.optional(
        key: String,
        defaultValue: String,
    ): String = getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultValue

    private fun Properties.optionalBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean {
        val raw = getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: return defaultValue
        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("$key must be true or false")
        }
    }

    private fun Properties.optionalList(key: String): List<String> = getProperty(key)
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

    private fun <T> Properties.optionalEnumSet(
        key: String,
        defaultValue: Set<T>,
        parser: (String) -> T,
    ): Set<T> {
        val values = optionalList(key)
        return if (values.isEmpty()) defaultValue else values.map { parser(it.lowercase()) }.toSet()
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun ModelConfig.validate() {
        require(relativePath.startsWith("models/")) {
            "model.relativePath must be under models/"
        }
        if (artifactFormat == ModelArtifactFormat.Gguf) {
            require(relativePath == "models/$fileName") {
                "model.relativePath must match models/<model.fileName>"
            }
        }
        require(
            (runtime == ModelRuntime.LlamaCpp && artifactFormat == ModelArtifactFormat.Gguf) ||
                (runtime == ModelRuntime.LiteRtLm && artifactFormat == ModelArtifactFormat.LiteRtLmBundle) ||
                (runtime == ModelRuntime.WhisperCpp && artifactFormat == ModelArtifactFormat.WhisperGgml),
        ) {
            "model.runtime and model.artifactFormat must be compatible"
        }
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "model.sha256 must be a lowercase SHA-256 hex digest"
        }
        require(expectedBytes == null || expectedBytes > 0) {
            "model.expectedBytes must be positive when present"
        }
        require(recommendedFreeRamBytes == null || recommendedFreeRamBytes > 0) {
            "model.recommendedFreeRamBytes must be positive when present"
        }
        require(gpuLayerCount == null || gpuLayerCount > 0) {
            "model.gpuLayerCount must be positive when present"
        }
        require(
            gpuLayerCount == null ||
                (runtime == ModelRuntime.LlamaCpp && acceleration == ModelAccelerationPolicy.GpuPreferred),
        ) {
            "model.gpuLayerCount requires llama_cpp with gpu_preferred acceleration"
        }
        require((listOf(url) + fallbackUrls).all { it.startsWith("https://") }) {
            "model download URLs must use https"
        }
        require(purposes.isNotEmpty()) { "model.purposes must not be empty" }
        require(tasks.isNotEmpty()) { "model.tasks must not be empty" }
        require(ModelPurpose.Utility in purposes || ModelTask.Transcription !in tasks) {
            "transcription task requires utility purpose"
        }
        require(ModelPurpose.Utility !in purposes || ModelTask.Transcription in tasks) {
            "utility purpose requires a supported utility task"
        }
        require(ModelPurpose.Chat !in purposes || ModelTask.Chat in tasks) {
            "chat purpose requires chat task"
        }
        require(ModelPurpose.Chat !in purposes || inference != null) {
            "chat models require inference settings"
        }
        if (inference == null) return
        require(inference.contextTokens > 0) {
            "inference.contextTokens must be positive"
        }
        require(inference.maxTokens > 0) {
            "inference.maxTokens must be positive"
        }
        require(inference.temperature >= 0f) {
            "inference.temperature must be non-negative"
        }
        require(inference.topP in 0f..1f) {
            "inference.topP must be between 0 and 1"
        }
        require(inference.topK > 0) {
            "inference.topK must be positive"
        }
        require(inference.minP in 0f..1f) {
            "inference.minP must be between 0 and 1"
        }
        require(inference.repeatPenalty >= 0f) {
            "inference.repeatPenalty must be non-negative"
        }
        val hasSupportedInput = inputCapabilities.text || inputCapabilities.image || inputCapabilities.audio
        require(ModelPurpose.Chat !in purposes || hasSupportedInput) {
            "model capabilities must enable at least one input modality"
        }
        require(!inputCapabilities.image || inputCapabilities.text) {
            "image input requires text input support"
        }
    }
}
