package com.jesjobom.ararai.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class ModelFileDownloaderTest {
    @Test
    fun `url response closes stream and disconnects exactly once after success`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val input = CloseRecordingInputStream("hello".toByteArray())
        val connection = FakeHttpConnection(input = input)
        val downloader =
            ModelFileDownloader(
                root,
                UrlModelByteSource(QueueConnectionFactory(connection)),
            )

        downloader.download(helloConfig())

        assertEquals(1, input.closeCount)
        assertEquals(1, connection.disconnectCount)
    }

    @Test
    fun `url response cleanup is idempotent`() {
        val input = CloseRecordingInputStream("hello".toByteArray())
        val connection = FakeHttpConnection(input = input)
        val response =
            UrlModelByteSource(QueueConnectionFactory(connection))
                .open(helloConfig(), requestedOffset = 0L)

        response.close()
        response.close()

        assertEquals(1, input.closeCount)
        assertEquals(1, connection.disconnectCount)
    }

    @Test
    fun `url source disconnects exactly once when http response is rejected`() {
        val connection = FakeHttpConnection(statusCode = HttpURLConnection.HTTP_NOT_FOUND)
        val source = UrlModelByteSource(QueueConnectionFactory(connection))

        assertThrows(ModelDownloadException::class.java) {
            source.open(helloConfig(), requestedOffset = 0L)
        }

        assertEquals(1, connection.disconnectCount)
    }

    @Test
    fun `url source disconnects exactly once when opening response stream fails`() {
        val connection = FakeHttpConnection(inputError = IOException("stream unavailable"))
        val source = UrlModelByteSource(QueueConnectionFactory(connection))

        assertThrows(IOException::class.java) {
            source.open(helloConfig(), requestedOffset = 0L)
        }

        assertEquals(1, connection.disconnectCount)
    }

    @Test
    fun `url response closes stream and disconnects exactly once on cancellation`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val input = CancelingCloseRecordingInputStream()
        val connection = FakeHttpConnection(input = input)
        val downloader =
            ModelFileDownloader(
                root,
                UrlModelByteSource(QueueConnectionFactory(connection)),
            )

        assertThrows(CancellationException::class.java) {
            runBlocking { downloader.download(helloConfig()) }
        }

        assertEquals(1, input.closeCount)
        assertEquals(1, connection.disconnectCount)
    }

    @Test
    fun `failed url is disconnected before fallback succeeds`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val primary = FakeHttpConnection(statusCode = HttpURLConnection.HTTP_UNAVAILABLE)
        val fallbackInput = CloseRecordingInputStream("hello".toByteArray())
        val fallback = FakeHttpConnection(input = fallbackInput)
        val source = UrlModelByteSource(QueueConnectionFactory(primary, fallback))
        val config =
            helloConfig().copy(
                url = "https://example.com/primary.gguf",
                fallbackUrls = listOf("https://example.com/fallback.gguf"),
            )

        ModelFileDownloader(root, source).download(config)

        assertEquals(1, primary.disconnectCount)
        assertEquals(1, fallback.disconnectCount)
        assertEquals(1, fallbackInput.closeCount)
    }

    @Test
    fun `downloads to temp file validates and promotes to final path`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val progress = mutableListOf<ModelDownloadProgress>()
        val downloader =
            ModelFileDownloader(
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
        val downloader =
            ModelFileDownloader(
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
        val downloader =
            ModelFileDownloader(
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
        val downloader =
            ModelFileDownloader(
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
        val byteSource =
            UrlSwitchingModelByteSource(
                bytesByUrl = mapOf("https://example.com/fallback.gguf" to "hello".toByteArray()),
            )
        val downloader =
            ModelFileDownloader(
                appFilesRoot = root,
                byteSource = byteSource,
            )
        val config =
            helloConfig().copy(
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
        val downloader =
            ModelFileDownloader(
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

    @Test
    fun `restarts from zero when switching from a partial primary to fallback`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val source = PartialPrimaryThenFallbackModelByteSource()
        val config =
            helloConfig().copy(
                url = "https://example.com/primary.gguf",
                fallbackUrls = listOf("https://example.com/fallback.gguf"),
            )

        ModelFileDownloader(root, source).download(config)

        assertEquals(
            listOf(
                "https://example.com/primary.gguf" to 0L,
                "https://example.com/fallback.gguf" to 0L,
            ),
            source.requests,
        )
        assertEquals("hello", File(root, config.relativePath).readText())
    }

    @Test
    fun `retries resumed content once from zero after integrity failure`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val finalFile = File(root, helloConfig().relativePath)
        finalFile.parentFile!!.mkdirs()
        File(finalFile.parentFile, "${finalFile.name}.part").writeText("he")
        val source = InvalidResumeThenCleanModelByteSource(cleanRetryBytes = "hello")

        ModelFileDownloader(root, source).download(helloConfig())

        assertEquals(listOf(2L, 0L), source.requestedOffsets)
        assertEquals("hello", finalFile.readText())
    }

    @Test
    fun `bounds clean retry after resumed content remains invalid`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val finalFile = File(root, helloConfig().relativePath)
        finalFile.parentFile!!.mkdirs()
        File(finalFile.parentFile, "${finalFile.name}.part").writeText("he")
        val source = InvalidResumeThenCleanModelByteSource(cleanRetryBytes = "wrong")

        try {
            ModelFileDownloader(root, source).download(helloConfig())
            fail("Expected bounded validation failure")
        } catch (_: ModelDownloadException) {
            assertEquals(listOf(2L, 0L), source.requestedOffsets)
            assertFalse(finalFile.exists())
            assertFalse(File(finalFile.parentFile, "${finalFile.name}.part").exists())
        }
    }

    @Test
    fun `rejects escaped download path before opening source or creating files`() = runTest {
        val root = Files.createTempDirectory("ararai-download").toFile()
        val outside = File(root.parentFile, "escaped-download.gguf")
        val source = RecordingModelByteSource()
        val config = helloConfig().copy(relativePath = "models/../../${outside.name}")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ModelFileDownloader(root, source).download(config) }
        }

        assertEquals(0, source.openCount)
        assertFalse(outside.exists())
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

private class QueueConnectionFactory(
    vararg connections: FakeHttpConnection,
) : ModelHttpConnectionFactory {
    private val connections = ArrayDeque(connections.toList())

    override fun open(url: URL): HttpURLConnection = connections.removeFirst()
}

private class FakeHttpConnection(
    private val statusCode: Int = HttpURLConnection.HTTP_OK,
    private val input: InputStream = ByteArrayInputStream(ByteArray(0)),
    private val inputError: IOException? = null,
) : HttpURLConnection(URL("https://example.com/model.gguf")) {
    var disconnectCount = 0

    override fun connect() = Unit

    override fun disconnect() {
        disconnectCount += 1
    }

    override fun usingProxy(): Boolean = false

    override fun getResponseCode(): Int = statusCode

    override fun getInputStream(): InputStream = inputError?.let { throw it } ?: input
}

private open class CloseRecordingInputStream(
    bytes: ByteArray,
) : ByteArrayInputStream(bytes) {
    var closeCount = 0

    override fun close() {
        closeCount += 1
        super.close()
    }
}

private class CancelingCloseRecordingInputStream : InputStream() {
    var closeCount = 0
    private var emitted = false

    override fun read(): Int {
        if (!emitted) {
            emitted = true
            return 'h'.code
        }
        throw CancellationException("cancelled")
    }

    override fun close() {
        closeCount += 1
    }
}

private class RecordingModelByteSource : ModelByteSource {
    var openCount = 0

    override fun open(config: ModelConfig): InputStream {
        openCount += 1
        return ByteArrayInputStream("hello".toByteArray())
    }
}

private class StaticModelByteSource(
    private val bytes: ByteArray,
) : ModelByteSource {
    override fun open(config: ModelConfig): ByteArrayInputStream = ByteArrayInputStream(bytes)
}

private data object FailingModelByteSource : ModelByteSource {
    override fun open(config: ModelConfig): ByteArrayInputStream = throw IOException("network down")
}

private data object CancelingModelByteSource : ModelByteSource {
    override fun open(config: ModelConfig): InputStream = object : InputStream() {
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

    override fun open(
        config: ModelConfig,
        requestedOffset: Long,
    ): ModelByteResponse {
        this.requestedOffset = requestedOffset
        val bytes = "hello".toByteArray()
        return if (acceptRange) {
            ModelByteResponse(ByteArrayInputStream(bytes.copyOfRange(requestedOffset.toInt(), bytes.size)), requestedOffset)
        } else {
            ModelByteResponse(ByteArrayInputStream(bytes), acceptedOffset = 0L)
        }
    }
}

private class PartialPrimaryThenFallbackModelByteSource : ModelByteSource {
    val requests = mutableListOf<Pair<String, Long>>()

    override fun open(config: ModelConfig): InputStream = error("Offset-aware open expected")

    override fun open(
        config: ModelConfig,
        requestedOffset: Long,
    ): ModelByteResponse {
        requests += config.url to requestedOffset
        return if (config.url.endsWith("primary.gguf")) {
            ModelByteResponse(PartialThenFailingInputStream("xx".toByteArray()), acceptedOffset = 0L)
        } else {
            ModelByteResponse(ByteArrayInputStream("hello".toByteArray()), acceptedOffset = 0L)
        }
    }
}

private class InvalidResumeThenCleanModelByteSource(
    private val cleanRetryBytes: String,
) : ModelByteSource {
    val requestedOffsets = mutableListOf<Long>()

    override fun open(config: ModelConfig): InputStream = error("Offset-aware open expected")

    override fun open(
        config: ModelConfig,
        requestedOffset: Long,
    ): ModelByteResponse {
        requestedOffsets += requestedOffset
        return if (requestedOffset > 0L) {
            ModelByteResponse(ByteArrayInputStream("xxx".toByteArray()), acceptedOffset = requestedOffset)
        } else {
            ModelByteResponse(ByteArrayInputStream(cleanRetryBytes.toByteArray()), acceptedOffset = 0L)
        }
    }
}

private class PartialThenFailingInputStream(
    private val bytes: ByteArray,
) : InputStream() {
    private var index = 0

    override fun read(): Int {
        if (index < bytes.size) return bytes[index++].toInt() and 0xff
        throw IOException("primary interrupted")
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

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            if (remaining <= 0L) return -1
            Thread.sleep(1)
            val count = minOf(length.toLong(), remaining).toInt()
            java.util.Arrays.fill(buffer, offset, offset + count, 0.toByte())
            remaining -= count
            return count
        }
    }
}
