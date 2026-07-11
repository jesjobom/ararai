package com.jesjobom.ararai.model

import java.io.StringReader
import java.util.Properties

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
            return ModelCatalog(defaultModelId = model.id, models = listOf(model))
        }

        require(count > 0) { "models.count must be positive" }

        val models = (0 until count).map { index ->
            properties.parseModel(
                modelPrefix = "models.$index.",
                inferencePrefix = "models.$index.inference.",
            )
        }
        val defaultModelId = properties.getProperty("models.defaultId")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: models.first().id

        return ModelCatalog(defaultModelId = defaultModelId, models = models)
    }

    private fun Properties.parseModel(modelPrefix: String, inferencePrefix: String): ModelConfig =
        ModelConfig(
            id = required("${modelPrefix}id"),
            name = required("${modelPrefix}name"),
            runtime = ModelRuntime.fromConfigValue(
                optional("${modelPrefix}runtime", ModelRuntime.LlamaCpp.configValue),
            ),
            artifactFormat = ModelArtifactFormat.fromConfigValue(
                optional("${modelPrefix}artifactFormat", ModelArtifactFormat.Gguf.configValue),
            ),
            acceleration = ModelAccelerationPolicy.fromConfigValue(
                optional("${modelPrefix}acceleration", ModelAccelerationPolicy.GpuPreferred.configValue),
            ),
            url = required("${modelPrefix}url"),
            fileName = required("${modelPrefix}fileName"),
            relativePath = required("${modelPrefix}relativePath"),
            sha256 = required("${modelPrefix}sha256").lowercase(),
            expectedBytes = getProperty("${modelPrefix}expectedBytes")?.toLong(),
            inference = InferenceConfig(
                contextTokens = required("${inferencePrefix}contextTokens").toInt(),
                maxTokens = required("${inferencePrefix}maxTokens").toInt(),
                temperature = required("${inferencePrefix}temperature").toFloat(),
                topP = required("${inferencePrefix}topP").toFloat(),
            ),
        ).also { it.validate() }

    private fun Properties.required(key: String): String =
        getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing required model config field: $key")

    private fun Properties.optional(key: String, defaultValue: String): String =
        getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultValue

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
                (runtime == ModelRuntime.LiteRtLm && artifactFormat == ModelArtifactFormat.LiteRtLmBundle),
        ) {
            "model.runtime and model.artifactFormat must be compatible"
        }
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "model.sha256 must be a lowercase SHA-256 hex digest"
        }
        require(expectedBytes == null || expectedBytes > 0) {
            "model.expectedBytes must be positive when present"
        }
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
    }
}
