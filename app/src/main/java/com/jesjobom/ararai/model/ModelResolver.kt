package com.jesjobom.ararai.model

import java.io.File

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

        when (val validation = ModelFileIntegrity.validate(file, config)) {
            ModelFileValidation.Valid -> Unit
            is ModelFileValidation.Invalid -> {
                return ModelResolutionState.IntegrityFailed(config, file, validation.reason)
            }
        }

        return ModelResolutionState.Available(
            config = config,
            file = file,
            model = LocalModel(
                id = config.id,
                name = config.name,
                runtime = config.runtime,
                artifactFormat = config.artifactFormat,
                acceleration = config.acceleration,
                filePath = file.absolutePath,
            ),
        )
    }
}
