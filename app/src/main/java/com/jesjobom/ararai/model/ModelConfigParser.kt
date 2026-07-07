package com.jesjobom.ararai.model

import java.io.StringReader
import java.util.Properties

object ModelConfigParser {
    fun parse(raw: String): ModelConfig {
        val properties = Properties().apply { load(StringReader(raw)) }

        return ModelConfig(
            id = properties.required("model.id"),
            name = properties.required("model.name"),
            url = properties.required("model.url"),
            fileName = properties.required("model.fileName"),
            relativePath = properties.required("model.relativePath"),
            sha256 = properties.required("model.sha256").lowercase(),
            expectedBytes = properties.getProperty("model.expectedBytes")?.toLong(),
            inference = InferenceConfig(
                contextTokens = properties.required("inference.contextTokens").toInt(),
                maxTokens = properties.required("inference.maxTokens").toInt(),
                temperature = properties.required("inference.temperature").toFloat(),
                topP = properties.required("inference.topP").toFloat(),
            ),
        ).also { it.validate() }
    }

    private fun Properties.required(key: String): String =
        getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing required model config field: $key")

    private fun ModelConfig.validate() {
        require(relativePath == "models/$fileName") {
            "model.relativePath must match models/<model.fileName>"
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
