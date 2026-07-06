package com.jesjobom.ararai.model

import java.io.File
import java.security.MessageDigest

sealed interface ModelResolutionState {
    data class Available(
        val config: ModelConfig,
        val file: File,
        val model: LocalModel,
    ) : ModelResolutionState

    data class Missing(val config: ModelConfig) : ModelResolutionState

    data class IntegrityFailed(
        val config: ModelConfig,
        val file: File,
        val reason: String,
    ) : ModelResolutionState
}

class ModelResolver(
    private val appFilesRoot: File,
) {
    fun resolve(config: ModelConfig): ModelResolutionState {
        val file = File(appFilesRoot, config.relativePath)

        if (!file.exists()) {
            return ModelResolutionState.Missing(config)
        }

        if (!file.isFile) {
            return ModelResolutionState.IntegrityFailed(config, file, "Configured model path is not a file")
        }

        if (config.expectedBytes != null && file.length() != config.expectedBytes) {
            return ModelResolutionState.IntegrityFailed(
                config = config,
                file = file,
                reason = "Expected ${config.expectedBytes} bytes but found ${file.length()}",
            )
        }

        val actualSha256 = file.sha256()
        if (actualSha256 != config.sha256) {
            return ModelResolutionState.IntegrityFailed(
                config = config,
                file = file,
                reason = "Expected SHA-256 ${config.sha256} but found $actualSha256",
            )
        }

        return ModelResolutionState.Available(
            config = config,
            file = file,
            model = LocalModel(
                id = config.id,
                name = config.name,
                filePath = file.absolutePath,
            ),
        )
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}
