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

data class ManagedModelItem(
    val config: ModelConfig,
    val state: ModelStartupState,
)

data class ModelCatalogState(
    val models: List<ManagedModelItem>,
    val selectedModelId: String,
) {
    val selectedItem: ManagedModelItem
        get() = models.first { it.config.id == selectedModelId }

    val selectedStartupState: ModelStartupState
        get() = selectedItem.state

    val selectedConfig: ModelConfig
        get() = selectedItem.config
}

class ModelCatalogController(
    private val catalog: ModelCatalog,
    private val appFilesRoot: File,
    private val downloader: ModelDownloader = ModelFileDownloader(appFilesRoot),
    private val resolver: ModelResolver = ModelResolver(appFilesRoot),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val downloadJobs = mutableMapOf<String, Job>()
    private val initialModels = catalog.models.map { ManagedModelItem(it, resolve(it)) }
    private val _state = MutableStateFlow(
        ModelCatalogState(
            models = initialModels,
            selectedModelId = initialModels.firstOrNull { it.state is ModelStartupState.Available }?.config?.id
                ?: catalog.defaultModelId,
        ),
    )
    val state: StateFlow<ModelCatalogState> = _state.asStateFlow()

    init {
        ensureBootstrapModelDownload()
    }

    fun select(modelId: String) {
        require(catalog.models.any { it.id == modelId }) { "Unknown model: $modelId" }
        _state.update { it.copy(selectedModelId = modelId) }
    }

    fun retry(modelId: String) {
        download(modelId)
    }

    fun download(modelId: String) {
        val item = state.value.item(modelId)
        if (item.state is ModelStartupState.Downloading) return
        startDownload(item.config)
    }

    fun cancelDownload(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
    }

    fun redownload(modelId: String) {
        val item = state.value.item(modelId)
        if (item.state is ModelStartupState.Downloading) return
        deleteModelFiles(item.config)
        startDownload(item.config)
    }

    fun delete(modelId: String) {
        val item = state.value.item(modelId)
        if (item.state is ModelStartupState.Downloading) return
        deleteModelFiles(item.config)
        updateModelState(modelId, ModelStartupState.Missing)
    }

    private fun ensureBootstrapModelDownload() {
        val current = state.value
        if (current.models.any { it.state is ModelStartupState.Available }) return

        val defaultModel = current.models.first { it.config.id == catalog.defaultModelId }
        if (defaultModel.state is ModelStartupState.Missing || defaultModel.state is ModelStartupState.Invalid) {
            startDownload(defaultModel.config)
        }
    }

    private fun resolve(config: ModelConfig): ModelStartupState =
        when (val resolution = resolver.resolve(config)) {
            is ModelResolutionState.Available -> {
                ModelStartupState.Available(resolution.model, config.inference)
            }
            is ModelResolutionState.Missing -> ModelStartupState.Missing
            is ModelResolutionState.IntegrityFailed -> ModelStartupState.Invalid(resolution.reason)
        }

    private fun startDownload(config: ModelConfig) {
        val job = scope.launch {
            updateModelState(config.id, ModelStartupState.Downloading())
            try {
                val available = downloader.download(config) { progress ->
                    updateModelState(
                        config.id,
                        ModelStartupState.Downloading(
                            bytesDownloaded = progress.bytesDownloaded,
                            totalBytes = progress.totalBytes,
                        ),
                    )
                }
                updateModelState(config.id, ModelStartupState.Available(available.model, config.inference))
            } catch (_: CancellationException) {
                updateModelState(config.id, resolve(config))
            } catch (error: ModelDownloadException) {
                updateModelState(
                    config.id,
                    ModelStartupState.Failed(error.message ?: "Configured model download failed"),
                )
            } finally {
                downloadJobs.remove(config.id)
            }
        }
        downloadJobs[config.id] = job
    }

    private fun updateModelState(modelId: String, modelState: ModelStartupState) {
        _state.update { current ->
            current.copy(
                models = current.models.map { item ->
                    if (item.config.id == modelId) item.copy(state = modelState) else item
                },
            )
        }
    }

    private fun deleteModelFiles(config: ModelConfig) {
        val finalFile = File(appFilesRoot, config.relativePath)
        val tempFile = File(finalFile.parentFile, "${finalFile.name}.part")
        if (finalFile.exists()) finalFile.delete()
        if (tempFile.exists()) tempFile.delete()
    }

    private fun ModelCatalogState.item(modelId: String): ManagedModelItem =
        models.firstOrNull { it.config.id == modelId }
            ?: throw IllegalArgumentException("Unknown model: $modelId")
}
