package com.jesjobom.ararai.model

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class ModelStartupControllerTest {
    @Test
    fun `downloads missing model and publishes available state`() = runTest {
        val root = Files.createTempDirectory("ararai-startup").toFile()
        val controller =
            ModelStartupController(
                config = helloConfig(),
                appFilesRoot = root,
                downloader =
                ModelFileDownloader(
                    appFilesRoot = root,
                    byteSource = StaticStartupByteSource("hello".toByteArray()),
                ),
                scope = this,
            )

        controller.state.test {
            assertEquals(ModelStartupState.Missing, awaitItem())
            assertEquals(ModelStartupState.Downloading(), awaitItem())
            assertEquals(ModelStartupState.Downloading(bytesDownloaded = 5, totalBytes = 5), awaitItem())
            assertTrue(awaitItem() is ModelStartupState.Available)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `failed download can be retried`() = runTest {
        val root = Files.createTempDirectory("ararai-startup").toFile()
        val byteSource =
            RetryStartupByteSource(
                first = "wrong".toByteArray(),
                second = "hello".toByteArray(),
            )
        val controller =
            ModelStartupController(
                config = helloConfig(),
                appFilesRoot = root,
                downloader = ModelFileDownloader(appFilesRoot = root, byteSource = byteSource),
                scope = this,
            )

        controller.state.test {
            assertEquals(ModelStartupState.Missing, awaitItem())
            assertTrue(awaitItem() is ModelStartupState.Downloading)
            assertTrue(awaitItem() is ModelStartupState.Downloading)
            assertTrue(awaitItem() is ModelStartupState.Failed)

            controller.retry()

            assertTrue(awaitItem() is ModelStartupState.Downloading)
            assertTrue(awaitItem() is ModelStartupState.Downloading)
            assertTrue(awaitItem() is ModelStartupState.Available)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun helloConfig(): ModelConfig = ModelConfig(
        id = "hello",
        name = "Hello Model",
        url = "https://example.com/hello.gguf",
        fileName = "hello.gguf",
        relativePath = "models/hello.gguf",
        sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
        expectedBytes = 5,
        inference = InferenceConfig(contextTokens = 128, temperature = 0.7f, topP = 0.9f),
    )
}

private class StaticStartupByteSource(
    private val bytes: ByteArray,
) : ModelByteSource {
    override fun open(config: ModelConfig): ByteArrayInputStream = ByteArrayInputStream(bytes)
}

private class RetryStartupByteSource(
    private val first: ByteArray,
    private val second: ByteArray,
) : ModelByteSource {
    private var calls = 0

    override fun open(config: ModelConfig): ByteArrayInputStream {
        calls += 1
        return ByteArrayInputStream(if (calls == 1) first else second)
    }
}
