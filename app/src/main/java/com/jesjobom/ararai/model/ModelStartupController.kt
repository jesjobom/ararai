package com.jesjobom.ararai.model

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ModelStartupState {
    data object Missing : ModelStartupState
    data class Invalid(val reason: String) : ModelStartupState
    data class Downloading(
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long? = null,
    ) : ModelStartupState

    data class Available(val model: LocalModel, val inference: InferenceConfig) : ModelStartupState
    data class Failed(val message: String) : ModelStartupState
}

class ModelStartupController(
    private val config: ModelConfig,
    appFilesRoot: File,
    private val downloader: ModelDownloader = ModelFileDownloader(appFilesRoot),
    private val resolver: ModelResolver = ModelResolver(appFilesRoot),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var downloadJob: Job? = null
    private val _state = MutableStateFlow<ModelStartupState>(ModelStartupState.Missing)
    val state: StateFlow<ModelStartupState> = _state.asStateFlow()

    init {
        resolveAndMaybeDownload()
    }

    fun retry() {
        if (_state.value is ModelStartupState.Downloading) return
        startDownload()
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    private fun resolveAndMaybeDownload() {
        when (val resolution = resolver.resolve(config)) {
            is ModelResolutionState.Available -> {
                _state.value = ModelStartupState.Available(resolution.model, config.inference)
            }
            is ModelResolutionState.Missing -> {
                _state.value = ModelStartupState.Missing
                startDownload()
            }
            is ModelResolutionState.IntegrityFailed -> {
                _state.value = ModelStartupState.Invalid(resolution.reason)
                startDownload()
            }
        }
    }

    private fun startDownload() {
        downloadJob = scope.launch {
            _state.update { ModelStartupState.Downloading() }
            try {
                val available = downloader.download(config) { progress ->
                    _state.value = ModelStartupState.Downloading(
                        bytesDownloaded = progress.bytesDownloaded,
                        totalBytes = progress.totalBytes,
                    )
                }
                _state.value = ModelStartupState.Available(available.model, config.inference)
            } catch (_: CancellationException) {
                _state.value = when (val resolution = resolver.resolve(config)) {
                    is ModelResolutionState.Available -> ModelStartupState.Available(resolution.model, config.inference)
                    is ModelResolutionState.Missing -> ModelStartupState.Missing
                    is ModelResolutionState.IntegrityFailed -> ModelStartupState.Invalid(resolution.reason)
                }
            } catch (error: ModelDownloadException) {
                _state.value = ModelStartupState.Failed(error.message ?: "Configured model download failed")
            } finally {
                downloadJob = null
            }
        }
    }
}
