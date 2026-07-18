package com.jesjobom.ararai.model

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

        val finalFile = File(root, helloConfig().relativePath)
        assertEquals("hello", finalFile.readText())
        assertEquals(finalFile.absolutePath, result.file.absolutePath)
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
        finalFile.parentFile!!.mkdirs()
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

    @Test
    fun `byte source failure preserves partial file and keeps existing final file`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val finalFile = File(root, helloConfig().relativePath)
        finalFile.parentFile!!.mkdirs()
        finalFile.writeText("old-valid")
        val tempFile = File(finalFile.parentFile, "${finalFile.name}.part")
        tempFile.writeText("stale")
        val downloader = ModelFileDownloader(
            appFilesRoot = root,
            byteSource = FailingModelByteSource,
        )

        try {
            downloader.download(helloConfig())
            fail("Expected byte source failure")
        } catch (error: ModelDownloadException) {
            assertEquals(
                "All configured download URLs failed for hello: Configured model download failed: network down",
                error.message,
            )
            assertEquals("old-valid", finalFile.readText())
            assertEquals("stale", tempFile.readText())
        }
    }

    @Test
    fun `tries fallback urls after primary url fails`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val byteSource = UrlSwitchingModelByteSource(
            bytesByUrl = mapOf("https://example.com/fallback.gguf" to "hello".toByteArray()),
        )
        val downloader = ModelFileDownloader(
            appFilesRoot = root,
            byteSource = byteSource,
        )
        val config = helloConfig().copy(
            url = "https://example.com/primary.gguf",
            fallbackUrls = listOf("https://example.com/fallback.gguf"),
        )

        downloader.download(config)

        val finalFile = File(root, config.relativePath)
        assertEquals("hello", finalFile.readText())
        assertEquals(
            listOf("https://example.com/primary.gguf", "https://example.com/fallback.gguf"),
            byteSource.openedUrls,
        )
    }

    @Test
    fun `cancellation preserves temp file and is not wrapped as download failure`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val downloader = ModelFileDownloader(
            appFilesRoot = root,
            byteSource = CancelingModelByteSource,
        )

        try {
            downloader.download(helloConfig())
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            val finalFile = File(root, helloConfig().relativePath)
            assertFalse(finalFile.exists())
            assertTrue(File(finalFile.parentFile, "${finalFile.name}.part").exists())
        }
    }

    @Test
    fun `job cancellation interrupts an active stream copy`() = runBlocking {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val config = helloConfig().copy(expectedBytes = 8L * 1024L * 1024L)
        val downloader = ModelFileDownloader(root, SlowModelByteSource(config.expectedBytes!!))
        val finalFile = File(root, config.relativePath)
        val tempFile = File(finalFile.parentFile, "${finalFile.name}.part")

        val job = launch { downloader.download(config) }
        while (!tempFile.exists() || tempFile.length() == 0L) delay(5)
        job.cancel()
        job.join()

        assertFalse(finalFile.exists())
        assertTrue(tempFile.exists())
        assertTrue(tempFile.length() < config.expectedBytes)
    }

    @Test
    fun `resumes from confirmed partial offset`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val finalFile = File(root, helloConfig().relativePath)
        finalFile.parentFile!!.mkdirs()
        File(finalFile.parentFile, "${finalFile.name}.part").writeText("he")
        val source = RangeModelByteSource(acceptRange = true)

        ModelFileDownloader(root, source).download(helloConfig())

        assertEquals(2L, source.requestedOffset)
        assertEquals("hello", finalFile.readText())
    }

    @Test
    fun `restarts safely when source ignores partial offset`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val finalFile = File(root, helloConfig().relativePath)
        finalFile.parentFile!!.mkdirs()
        File(finalFile.parentFile, "${finalFile.name}.part").writeText("he")
        val source = RangeModelByteSource(acceptRange = false)

        ModelFileDownloader(root, source).download(helloConfig())

        assertEquals(2L, source.requestedOffset)
        assertEquals("hello", finalFile.readText())
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

private data object FailingModelByteSource : ModelByteSource {
    override fun open(config: ModelConfig): ByteArrayInputStream {
        throw IOException("network down")
    }
}

private data object CancelingModelByteSource : ModelByteSource {
    override fun open(config: ModelConfig): InputStream =
        object : InputStream() {
            private var emitted = false

            override fun read(): Int {
                if (!emitted) {
                    emitted = true
                    return 'h'.code
                }
                throw CancellationException("cancelled")
            }
        }
}

private class UrlSwitchingModelByteSource(
    private val bytesByUrl: Map<String, ByteArray>,
) : ModelByteSource {
    val openedUrls = mutableListOf<String>()

    override fun open(config: ModelConfig): ByteArrayInputStream {
        openedUrls += config.url
        return bytesByUrl[config.url]?.let(::ByteArrayInputStream)
            ?: throw IOException("unavailable")
    }
}

private class RangeModelByteSource(
    private val acceptRange: Boolean,
) : ModelByteSource {
    var requestedOffset: Long = -1L

    override fun open(config: ModelConfig): InputStream = ByteArrayInputStream("hello".toByteArray())

    override fun open(config: ModelConfig, requestedOffset: Long): ModelByteResponse {
        this.requestedOffset = requestedOffset
        val bytes = "hello".toByteArray()
        return if (acceptRange) {
            ModelByteResponse(ByteArrayInputStream(bytes.copyOfRange(requestedOffset.toInt(), bytes.size)), requestedOffset)
        } else {
            ModelByteResponse(ByteArrayInputStream(bytes), acceptedOffset = 0L)
        }
    }
}

private class SlowModelByteSource(
    private val byteCount: Long,
) : ModelByteSource {
    override fun open(config: ModelConfig): InputStream = object : InputStream() {
        private var remaining = byteCount

        override fun read(): Int {
            if (remaining <= 0L) return -1
            remaining -= 1
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0L) return -1
            Thread.sleep(1)
            val count = minOf(length.toLong(), remaining).toInt()
            java.util.Arrays.fill(buffer, offset, offset + count, 0.toByte())
            remaining -= count
            return count
        }
    }
}
