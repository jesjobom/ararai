package com.jesjobom.ararai.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.jesjobom.ararai.R
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelConfig
import com.jesjobom.ararai.model.ModelMaturity
import com.jesjobom.ararai.model.ModelPurpose
import com.jesjobom.ararai.model.ModelStartupState
import com.jesjobom.ararai.model.ModelTask

data class ModelStatusUiState(
    val modelName: String,
    val title: String,
    val detail: String,
    val capabilities: List<String>,
    val progressPercent: Int?,
    val canRetry: Boolean,
    val titleKind: ModelStatusTitle? = null,
    val detailKind: ModelStatusDetail? = null,
    val detailArguments: List<String> = emptyList(),
    val capabilityDescriptors: List<ModelCapabilityDescriptor> = emptyList(),
) {
    val isReady: Boolean get() = titleKind == ModelStatusTitle.Ready

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
                    titleKind = ModelStatusTitle.NotDownloaded,
                    detailKind = ModelStatusDetail.DownloadPrompt,
                    capabilityDescriptors = config.capabilityDescriptors(),
                )
            is ModelStartupState.Invalid ->
                ModelStatusUiState(
                    modelName = config.name,
                    title = "Model invalid",
                    detail = startupState.reason,
                    capabilities = config.capabilityLabels(),
                    progressPercent = null,
                    canRetry = false,
                    titleKind = ModelStatusTitle.Invalid,
                    capabilityDescriptors = config.capabilityDescriptors(),
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
                    titleKind = ModelStatusTitle.Ready,
                    detailKind = ModelStatusDetail.AvailableLocally,
                    capabilityDescriptors = config.capabilityDescriptors(),
                )
            is ModelStartupState.Failed ->
                ModelStatusUiState(
                    modelName = config.name,
                    title = "Download failed",
                    detail = startupState.message,
                    capabilities = config.capabilityLabels(),
                    progressPercent = null,
                    canRetry = true,
                    titleKind = ModelStatusTitle.DownloadFailed,
                    capabilityDescriptors = config.capabilityDescriptors(),
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
                titleKind = ModelStatusTitle.Downloading,
                detailKind =
                if (total != null && total > 0L && bytesDownloaded > 0L) {
                    ModelStatusDetail.DownloadProgress
                } else {
                    ModelStatusDetail.WaitingForProgress
                },
                detailArguments =
                if (total != null && total > 0L && bytesDownloaded > 0L) {
                    listOf(bytesDownloaded.toByteText(), total.toByteText())
                } else {
                    emptyList()
                },
                capabilityDescriptors = config.capabilityDescriptors(),
            )
        }
    }
}

enum class ModelStatusTitle { NotDownloaded, Invalid, Ready, DownloadFailed, Downloading }

enum class ModelStatusDetail { DownloadPrompt, AvailableLocally, WaitingForProgress, DownloadProgress }

enum class ModelCapabilityKind {
    Stable,
    Experimental,
    Chat,
    Reasoning,
    Utility,
    Transcription,
    Text,
    Voice,
    Image,
    Cpu,
    Gpu,
}

data class ModelCapabilityDescriptor(
    val kind: ModelCapabilityKind? = null,
    val literal: String? = null,
)

@Composable
internal fun ModelStatusUiState.localizedTitle(): String = when (titleKind) {
    ModelStatusTitle.NotDownloaded -> stringResource(R.string.model_status_not_downloaded)
    ModelStatusTitle.Invalid -> stringResource(R.string.model_status_invalid)
    ModelStatusTitle.Ready -> stringResource(R.string.model_status_ready)
    ModelStatusTitle.DownloadFailed -> stringResource(R.string.model_status_download_failed)
    ModelStatusTitle.Downloading -> stringResource(R.string.model_status_downloading)
    null -> title
}

@Composable
internal fun ModelStatusUiState.localizedDetail(): String = when (detailKind) {
    ModelStatusDetail.DownloadPrompt -> stringResource(R.string.model_status_download_prompt)
    ModelStatusDetail.AvailableLocally -> stringResource(R.string.model_status_available_locally)
    ModelStatusDetail.WaitingForProgress -> stringResource(R.string.model_status_waiting_progress)
    ModelStatusDetail.DownloadProgress ->
        stringResource(R.string.model_status_download_progress, detailArguments[0], detailArguments[1])
    null -> detail
}

@Composable
internal fun ModelStatusUiState.localizedCapabilities(): List<String> = capabilityDescriptors.takeIf(List<*>::isNotEmpty)?.map { it.localized() } ?: capabilities

@Composable
private fun ModelCapabilityDescriptor.localized(): String = when (kind) {
    ModelCapabilityKind.Stable -> stringResource(R.string.model_capability_stable)
    ModelCapabilityKind.Experimental -> stringResource(R.string.model_capability_experimental)
    ModelCapabilityKind.Chat -> stringResource(R.string.model_capability_chat)
    ModelCapabilityKind.Reasoning -> stringResource(R.string.model_capability_reasoning)
    ModelCapabilityKind.Utility -> stringResource(R.string.model_capability_utility)
    ModelCapabilityKind.Transcription -> stringResource(R.string.model_capability_transcription)
    ModelCapabilityKind.Text -> stringResource(R.string.model_capability_text)
    ModelCapabilityKind.Voice -> stringResource(R.string.model_capability_voice)
    ModelCapabilityKind.Image -> stringResource(R.string.model_capability_image)
    ModelCapabilityKind.Cpu -> stringResource(R.string.model_capability_cpu)
    ModelCapabilityKind.Gpu -> stringResource(R.string.model_capability_gpu)
    null -> checkNotNull(literal)
}

private fun ModelConfig.capabilityDescriptors(): List<ModelCapabilityDescriptor> = buildList {
    add(ModelCapabilityDescriptor(kind = if (maturity == ModelMaturity.Stable) ModelCapabilityKind.Stable else ModelCapabilityKind.Experimental))
    variant?.let { add(ModelCapabilityDescriptor(literal = it)) }
    purposes.forEach { purpose ->
        add(
            ModelCapabilityDescriptor(
                kind = when (purpose) {
                    ModelPurpose.Chat -> ModelCapabilityKind.Chat
                    ModelPurpose.Reasoning -> ModelCapabilityKind.Reasoning
                    ModelPurpose.Utility -> ModelCapabilityKind.Utility
                },
            ),
        )
    }
    tasks.forEach { task ->
        val kind = when (task) {
            ModelTask.Chat -> ModelCapabilityKind.Chat
            ModelTask.Reasoning -> ModelCapabilityKind.Reasoning
            ModelTask.Transcription -> ModelCapabilityKind.Transcription
        }
        if (none { it.kind == kind }) add(ModelCapabilityDescriptor(kind = kind))
    }
    if (inputCapabilities.text) add(ModelCapabilityDescriptor(kind = ModelCapabilityKind.Text))
    if (inputCapabilities.audio) add(ModelCapabilityDescriptor(kind = ModelCapabilityKind.Voice))
    if (inputCapabilities.image) add(ModelCapabilityDescriptor(kind = ModelCapabilityKind.Image))
    if (reasoningCapabilities.request || reasoningCapabilities.output) {
        if (none { it.kind == ModelCapabilityKind.Reasoning }) {
            add(ModelCapabilityDescriptor(kind = ModelCapabilityKind.Reasoning))
        }
    }
    add(
        ModelCapabilityDescriptor(
            kind = if (acceleration == ModelAccelerationPolicy.CpuOnly) ModelCapabilityKind.Cpu else ModelCapabilityKind.Gpu,
        ),
    )
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
