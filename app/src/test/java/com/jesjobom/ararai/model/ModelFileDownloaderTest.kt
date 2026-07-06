package com.jesjobom.ararai.model

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelFileDownloaderTest {
    @Test
    fun `downloads to temp file validates and promotes to final path`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val progress = mutableListOf<ModelDownloadProgress>()
        val downloader = ModelFileDownloader(
            appFilesRoot = root,
            byteSource = StaticModelByteSource("hello".toByteArray()),
        )

        val result = downloader.download(helloConfig(), progress::add)

        assertTrue(result is ModelResolutionState.Available)
        val finalFile = File(root, helloConfig().relativePath)
        assertEquals("hello", finalFile.readText())
        assertFalse(File(finalFile.parentFile, "${finalFile.name}.part").exists())
        assertEquals(ModelDownloadProgress(bytesDownloaded = 5, totalBytes = 5), progress.last())
    }

    @Test
    fun `validation failure does not promote temp file`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val downloader = ModelFileDownloader(
            appFilesRoot = root,
            byteSource = StaticModelByteSource("wrong".toByteArray()),
        )

        try {
            downloader.download(helloConfig())
            fail("Expected validation failure")
        } catch (_: ModelDownloadException) {
            val finalFile = File(root, helloConfig().relativePath)
            assertFalse(finalFile.exists())
            assertFalse(File(finalFile.parentFile, "${finalFile.name}.part").exists())
        }
    }

    @Test
    fun `validation failure keeps an existing final file unchanged`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val finalFile = File(root, helloConfig().relativePath)
        finalFile.parentFile.mkdirs()
        finalFile.writeText("old-valid")
        val downloader = ModelFileDownloader(
            appFilesRoot = root,
            byteSource = StaticModelByteSource("wrong".toByteArray()),
        )

        try {
            downloader.download(helloConfig())
            fail("Expected validation failure")
        } catch (_: ModelDownloadException) {
            assertEquals("old-valid", finalFile.readText())
        }
    }

    private fun helloConfig(): ModelConfig =
        ModelConfig(
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

private class StaticModelByteSource(
    private val bytes: ByteArray,
) : ModelByteSource {
    override fun open(config: ModelConfig): ByteArrayInputStream = ByteArrayInputStream(bytes)
}
