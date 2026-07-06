package com.jesjobom.ararai.model

sealed interface ModelDownloadState {
    data object NotNeeded : ModelDownloadState
    data class Needed(val config: ModelConfig, val reason: String) : ModelDownloadState
    data class Queued(val config: ModelConfig) : ModelDownloadState
    data class Downloading(val config: ModelConfig, val bytesDownloaded: Long, val totalBytes: Long?) :
        ModelDownloadState
    data class Failed(val config: ModelConfig, val message: String) : ModelDownloadState
}

class ModelDownloadPlanner {
    fun plan(resolution: ModelResolutionState): ModelDownloadState =
        when (resolution) {
            is ModelResolutionState.Available -> ModelDownloadState.NotNeeded
            is ModelResolutionState.Missing -> ModelDownloadState.Needed(
                config = resolution.config,
                reason = "Configured model is missing",
            )
            is ModelResolutionState.IntegrityFailed -> ModelDownloadState.Needed(
                config = resolution.config,
                reason = resolution.reason,
            )
        }
}
