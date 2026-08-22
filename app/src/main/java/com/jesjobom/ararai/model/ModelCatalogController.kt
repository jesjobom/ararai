package com.jesjobom.ararai.model

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
import java.io.File

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

interface ModelDownloadCommandGateway {
    fun start(
        modelId: String,
        replaceExisting: Boolean = false,
    )

    fun cancel(modelId: String)
}

interface ModelDownloadServiceController {
    val state: StateFlow<ModelCatalogState>

    fun executeBackgroundDownload(
        modelId: String,
        replaceExisting: Boolean = false,
    )

    fun executeBackgroundCancel(modelId: String)
}

class ModelCatalogController(
    private val catalog: ModelCatalog,
    private val appFilesRoot: File,
    private val downloader: ModelDownloader = ModelFileDownloader(appFilesRoot),
    private val resolver: ModelResolver = ModelResolver(appFilesRoot),
    private val selectionStore: ModelSelectionStore = InMemoryModelSelectionStore(),
    private val downloadGateway: ModelDownloadCommandGateway? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ModelDownloadServiceController {
    private val downloadJobs = mutableMapOf<String, Job>()
    private val initialModels = catalog.models.map { ManagedModelItem(it, resolve(it)) }
    private val _state =
        MutableStateFlow(
            ModelCatalogState(
                models = initialModels,
                selectedModelId = restoredSelectedModelId(initialModels),
            ),
        )
    override val state: StateFlow<ModelCatalogState> = _state.asStateFlow()

    val defaultModelConfig: ModelConfig
        get() = catalog.models.first { it.id == catalog.defaultModelId }

    fun select(modelId: String) {
        val config = catalog.models.firstOrNull { it.id == modelId }
            ?: throw IllegalArgumentException("Unknown model: $modelId")
        require(config.supportsPurpose(ModelPurpose.Chat)) { "Model $modelId is not selectable for Chat" }
        selectionStore.saveSelectedModelId(modelId)
        _state.update { it.copy(selectedModelId = modelId) }
    }

    fun retry(modelId: String) {
        download(modelId)
    }

    fun download(modelId: String) {
        val item = state.value.item(modelId)
        if (item.state is ModelStartupState.Downloading) return
        downloadGateway?.start(modelId) ?: startDownload(item.config)
    }

    fun cancelDownload(modelId: String) {
        downloadGateway?.cancel(modelId)
        downloadJobs.remove(modelId)?.cancel()
    }

    fun redownload(modelId: String) {
        val item = state.value.item(modelId)
        if (item.state is ModelStartupState.Downloading) return
        if (downloadGateway != null) {
            downloadGateway.start(modelId, replaceExisting = true)
        } else {
            deleteModelFiles(item.config)
            startDownload(item.config)
        }
    }

    fun delete(modelId: String) {
        val item = state.value.item(modelId)
        if (item.state is ModelStartupState.Downloading) return
        deleteModelFiles(item.config)
        updateModelState(modelId, ModelStartupState.Missing)
    }

    private fun restoredSelectedModelId(models: List<ManagedModelItem>): String {
        val stored =
            selectionStore
                .selectedModelId()
                ?.takeIf { modelId ->
                    models.any { it.config.id == modelId && it.config.supportsPurpose(ModelPurpose.Chat) }
                }
        return stored
            ?: models.firstOrNull {
                it.config.supportsPurpose(ModelPurpose.Chat) && it.state is ModelStartupState.Available
            }?.config?.id
            ?: catalog.defaultModelId
    }

    private fun resolve(config: ModelConfig): ModelStartupState = when (val resolution = resolver.resolve(config)) {
        is ModelResolutionState.Available -> {
            ModelStartupState.Available(resolution.model, config.inference)
        }
        is ModelResolutionState.Missing -> ModelStartupState.Missing
        is ModelResolutionState.IntegrityFailed -> ModelStartupState.Invalid(resolution.reason)
    }

    private fun startDownload(config: ModelConfig) {
        val job =
            scope.launch {
                updateModelState(config.id, ModelStartupState.Downloading())
                try {
                    val available =
                        downloader.download(config) { progress ->
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

    override fun executeBackgroundDownload(
        modelId: String,
        replaceExisting: Boolean,
    ) {
        val item = state.value.item(modelId)
        if (item.state is ModelStartupState.Downloading) return
        if (replaceExisting) deleteModelFiles(item.config)
        startDownload(item.config)
    }

    override fun executeBackgroundCancel(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
    }

    private fun updateModelState(
        modelId: String,
        modelState: ModelStartupState,
    ) {
        _state.update { current ->
            current.copy(
                models =
                current.models.map { item ->
                    if (item.config.id == modelId) item.copy(state = modelState) else item
                },
            )
        }
    }

    private fun deleteModelFiles(config: ModelConfig) {
        val finalFile = ModelPathPolicy.resolveContained(appFilesRoot, config.relativePath)
        val tempFile = File(finalFile.parentFile, "${finalFile.name}.part")
        ModelFileIntegrity.invalidate(finalFile)
        if (finalFile.exists()) finalFile.delete()
        if (tempFile.exists()) tempFile.delete()
    }

    private fun ModelCatalogState.item(modelId: String): ManagedModelItem = models.firstOrNull { it.config.id == modelId }
        ?: throw IllegalArgumentException("Unknown model: $modelId")
}
