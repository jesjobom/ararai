package com.jesjobom.ararai.ui

import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelStartupState

data class ModelStatusUiState(
    val modelName: String,
    val title: String,
    val detail: String,
    val progressPercent: Int?,
    val canRetry: Boolean,
) {
    companion object {
        fun from(
            config: ModelConfig,
            startupState: ModelStartupState,
        ): ModelStatusUiState = when (startupState) {
            ModelStartupState.Missing ->
                ModelStatusUiState(
                    modelName = config.name,
                    title = "Not downloaded",
                    detail = "Download this model to use it locally",
                    progressPercent = null,
                    canRetry = false,
                )
            is ModelStartupState.Invalid ->
                ModelStatusUiState(
                    modelName = config.name,
                    title = "Model invalid",
                    detail = startupState.reason,
                    progressPercent = null,
                    canRetry = false,
                )
            is ModelStartupState.Downloading -> startupState.toUiState(config)
            is ModelStartupState.Available ->
                ModelStatusUiState(
                    modelName = startupState.model.name,
                    title = "Model ready",
                    detail = startupState.model.filePath,
                    progressPercent = null,
                    canRetry = false,
                )
            is ModelStartupState.Failed ->
                ModelStatusUiState(
                    modelName = config.name,
                    title = "Download failed",
                    detail = startupState.message,
                    progressPercent = null,
                    canRetry = true,
                )
        }

        private fun ModelStartupState.Downloading.toUiState(config: ModelConfig): ModelStatusUiState {
            val total = totalBytes
            val percent =
                if (total != null && total > 0L) {
                    ((bytesDownloaded * 100) / total).toInt().coerceIn(0, 100)
                } else {
                    null
                }

            return ModelStatusUiState(
                modelName = config.name,
                title = "Downloading model",
                detail =
                if (total != null && total > 0L && bytesDownloaded > 0L) {
                    "${bytesDownloaded.toByteText()} / ${total.toByteText()}"
                } else {
                    "Waiting for download progress"
                },
                progressPercent = percent,
                canRetry = false,
            )
        }
    }
}

private fun Long.toByteText(): String = if (this < 1024L) {
    "$this B"
} else {
    val mb = this.toDouble() / (1024.0 * 1024.0)
    "%.1f MB".format(mb)
}
