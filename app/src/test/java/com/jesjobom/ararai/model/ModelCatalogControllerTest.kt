package com.jesjobom.ararai.model

import app.cash.turbine.test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ModelCatalogControllerTest {
    @Test
    fun `delegates downloads and cancellation to background gateway`() = runTest {
        val root = Files.createTempDirectory("ararai-catalog").toFile()
        val gateway = RecordingDownloadGateway()
        val controller = ModelCatalogController(
            catalog = ModelCatalog(defaultModelId = "default", models = listOf(defaultConfig())),
            appFilesRoot = root,
            downloader = SuspendedDownloader(),
            downloadGateway = gateway,
            scope = this,
        )

        assertEquals(listOf("default" to false), gateway.started)

        controller.cancelDownload("default")

        assertEquals(listOf("default"), gateway.cancelled)
    }

    @Test
    fun `auto downloads the default missing model and leaves other models untouched`() = runTest {
        val root = Files.createTempDirectory("ararai-catalog").toFile()
        val controller = ModelCatalogController(
            catalog = catalog(),
            appFilesRoot = root,
            downloader = ModelFileDownloader(
                appFilesRoot = root,
                byteSource = CatalogByteSource(mapOf("default" to "hello".toByteArray())),
            ),
            scope = this,
        )

        controller.state.test {
            val initial = awaitItem()
            assertEquals("default", initial.selectedModelId)
            assertTrue(initial.models[0].state is ModelStartupState.Missing)
            assertTrue(initial.models[1].state is ModelStartupState.Missing)

            assertTrue(awaitItem().models[0].state is ModelStartupState.Downloading)
            assertTrue(awaitItem().models[0].state is ModelStartupState.Downloading)
            val available = awaitItem()
            assertTrue(available.models[0].state is ModelStartupState.Available)
            assertTrue(available.models[1].state is ModelStartupState.Missing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `does not auto download default model when another configured model is already available`() = runTest {
        val root = Files.createTempDirectory("ararai-catalog").toFile()
        val optionalPath = root.toPath().resolve("models/optional.gguf")
        optionalPath.parent.createDirectories()
        optionalPath.writeBytes("optional".toByteArray())
        val byteSource = CatalogByteSource(mapOf("default" to "hello".toByteArray()))

        val controller = ModelCatalogController(
            catalog = catalog(),
            appFilesRoot = root,
            downloader = ModelFileDownloader(appFilesRoot = root, byteSource = byteSource),
            scope = this,
        )

        assertEquals("optional", controller.state.value.selectedModelId)
        assertTrue(controller.state.value.selectedStartupState is ModelStartupState.Available)
        assertEquals(0, byteSource.callsFor("default"))
    }

    @Test
    fun `restores selected model from local selection store`() = runTest {
        val root = Files.createTempDirectory("ararai-catalog").toFile()
        val optionalPath = root.toPath().resolve("models/optional.gguf")
        optionalPath.parent.createDirectories()
        optionalPath.writeBytes("optional".toByteArray())
        val selectionStore = InMemoryModelSelectionStore("optional")

        val controller = ModelCatalogController(
            catalog = catalog(),
            appFilesRoot = root,
            downloader = ModelFileDownloader(appFilesRoot = root, byteSource = CatalogByteSource(emptyMap())),
            selectionStore = selectionStore,
            scope = this,
        )

        assertEquals("optional", controller.state.value.selectedModelId)

        controller.select("default")

        assertEquals("default", controller.state.value.selectedModelId)
        assertEquals("default", selectionStore.selectedModelId())
    }

    @Test
    fun `delete removes an available model file and marks it missing`() = runTest {
        val root = Files.createTempDirectory("ararai-catalog").toFile()
        val controller = ModelCatalogController(
            catalog = ModelCatalog(defaultModelId = "default", models = listOf(defaultConfig())),
            appFilesRoot = root,
            downloader = ModelFileDownloader(
                appFilesRoot = root,
                byteSource = CatalogByteSource(mapOf("default" to "hello".toByteArray())),
            ),
            scope = this,
        )

        controller.state.test {
            skipItems(3)
            assertTrue(awaitItem().selectedStartupState is ModelStartupState.Available)

            controller.delete("default")

            val deleted = awaitItem()
            assertTrue(deleted.selectedStartupState is ModelStartupState.Missing)
            assertFalse(root.resolve("models/default.gguf").exists())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `redownload replaces an available model`() = runTest {
        val root = Files.createTempDirectory("ararai-catalog").toFile()
        val byteSource = CatalogByteSource(mapOf("default" to "hello".toByteArray()))
        val controller = ModelCatalogController(
            catalog = ModelCatalog(defaultModelId = "default", models = listOf(defaultConfig())),
            appFilesRoot = root,
            downloader = ModelFileDownloader(appFilesRoot = root, byteSource = byteSource),
            scope = this,
        )

        controller.state.test {
            skipItems(3)
            assertTrue(awaitItem().selectedStartupState is ModelStartupState.Available)

            controller.redownload("default")

            assertTrue(awaitItem().selectedStartupState is ModelStartupState.Downloading)
            assertTrue(awaitItem().selectedStartupState is ModelStartupState.Downloading)
            assertTrue(awaitItem().selectedStartupState is ModelStartupState.Available)
            assertEquals(2, byteSource.callsFor("default"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancel download stops active job and returns model to missing state`() = runTest {
        val root = Files.createTempDirectory("ararai-catalog").toFile()
        val downloader = SuspendedDownloader()
        val controller = ModelCatalogController(
            catalog = ModelCatalog(defaultModelId = "default", models = listOf(defaultConfig())),
            appFilesRoot = root,
            downloader = downloader,
            scope = this,
        )

        runCurrent()
        assertTrue(controller.state.value.selectedStartupState is ModelStartupState.Downloading)

        controller.cancelDownload("default")
        runCurrent()

        assertTrue(downloader.wasCancelled)
        assertTrue(controller.state.value.selectedStartupState is ModelStartupState.Missing)
    }


    private fun catalog(): ModelCatalog =
        ModelCatalog(
            defaultModelId = "default",
            models = listOf(defaultConfig(), optionalConfig()),
        )

    private fun defaultConfig(): ModelConfig =
        ModelConfig(
            id = "default",
            name = "Default Model",
            url = "https://example.com/default.gguf",
            fileName = "default.gguf",
            relativePath = "models/default.gguf",
            sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            expectedBytes = 5,
            inference = InferenceConfig(contextTokens = 128, maxTokens = 32, temperature = 0.7f, topP = 0.9f),
        )

    private fun optionalConfig(): ModelConfig =
        ModelConfig(
            id = "optional",
            name = "Optional Model",
            url = "https://example.com/optional.gguf",
            fileName = "optional.gguf",
            relativePath = "models/optional.gguf",
            sha256 = "ec91fdd9256cb75ae611249b50cb7eb16533f0fa91b86239ec1d439a1ea033b8",
            expectedBytes = 8,
            inference = InferenceConfig(contextTokens = 128, maxTokens = 32, temperature = 0.7f, topP = 0.9f),
        )
}

private class CatalogByteSource(
    private val bytesByModelId: Map<String, ByteArray>,
) : ModelByteSource {
    private val calls = mutableMapOf<String, Int>()

    override fun open(config: ModelConfig): ByteArrayInputStream {
        calls[config.id] = callsFor(config.id) + 1
        return ByteArrayInputStream(bytesByModelId.getValue(config.id))
    }

    fun callsFor(modelId: String): Int = calls[modelId] ?: 0
}

private class SuspendedDownloader : ModelDownloader {
    var wasCancelled = false
        private set

    override suspend fun download(
        config: ModelConfig,
        onProgress: (ModelDownloadProgress) -> Unit,
    ): ModelResolutionState.Available =
        suspendCancellableCoroutine { continuation: CancellableContinuation<ModelResolutionState.Available> ->
            continuation.invokeOnCancellation {
                wasCancelled = true
            }
        }
}

private class RecordingDownloadGateway : ModelDownloadCommandGateway {
    val started = mutableListOf<Pair<String, Boolean>>()
    val cancelled = mutableListOf<String>()

    override fun start(modelId: String, replaceExisting: Boolean) {
        started += modelId to replaceExisting
    }

    override fun cancel(modelId: String) {
        cancelled += modelId
    }
}
