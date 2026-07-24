package com.jesjobom.ararai.ui

import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelStartupState

data class ModelStatusUiState(
    val modelName: String,
    val title: String,
    val detail: String,
    val capabilities: List<String>,
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
                    capabilities = config.capabilityLabels(),
                    progressPercent = null,
                    canRetry = false,
                )
            is ModelStartupState.Invalid ->
                ModelStatusUiState(
                    modelName = config.name,
                    title = "Model invalid",
                    detail = startupState.reason,
                    capabilities = config.capabilityLabels(),
                    progressPercent = null,
                    canRetry = false,
                )
            is ModelStartupState.Downloading -> startupState.toUiState(config)
            is ModelStartupState.Available ->
                ModelStatusUiState(
                    modelName = startupState.model.name,
                    title = "Model ready",
                    detail = "Available locally",
                    capabilities = config.capabilityLabels(),
                    progressPercent = null,
                    canRetry = false,
                )
            is ModelStartupState.Failed ->
                ModelStatusUiState(
                    modelName = config.name,
                    title = "Download failed",
                    detail = startupState.message,
                    capabilities = config.capabilityLabels(),
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
                capabilities = config.capabilityLabels(),
                progressPercent = percent,
                canRetry = false,
            )
        }
    }
}

internal fun ModelConfig.capabilityLabels(): List<String> = buildList {
    add(maturity.displayName)
    variant?.let(::add)
    purposes.forEach { add(it.displayName) }
    tasks.filterNot { task -> task.displayName in this }.forEach { add(it.displayName) }
    if (inputCapabilities.text) add("Text")
    if (inputCapabilities.audio) add("Voice")
    if (inputCapabilities.image) add("Image")
    if (reasoningCapabilities.request || reasoningCapabilities.output) add("Reasoning")
    add(if (acceleration == ModelAccelerationPolicy.CpuOnly) "CPU" else "GPU")
}

private fun Long.toByteText(): String = if (this < 1024L) {
    "$this B"
} else {
    val mb = this.toDouble() / (1024.0 * 1024.0)
    "%.1f MB".format(mb)
}
