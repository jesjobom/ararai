package com.jesjobom.ararai.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

interface ModelByteSource {
    fun open(config: ModelConfig): InputStream

    fun open(
        config: ModelConfig,
        requestedOffset: Long,
    ): ModelByteResponse = ModelByteResponse(open(config), acceptedOffset = 0L)
}

data class ModelByteResponse(
    val input: InputStream,
    val acceptedOffset: Long,
)

data class ModelDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
)

class UrlModelByteSource : ModelByteSource {
    override fun open(config: ModelConfig): InputStream = open(config, requestedOffset = 0L).input

    override fun open(
        config: ModelConfig,
        requestedOffset: Long,
    ): ModelByteResponse {
        val connection = URL(config.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        if (requestedOffset > 0L) {
            connection.setRequestProperty("Range", "bytes=$requestedOffset-")
        }
        if (connection.responseCode !in 200..299) {
            val host = connection.url.host
            val hint =
                if (connection.responseCode == HttpURLConnection.HTTP_FORBIDDEN && host.contains("xethub.hf.co")) {
                    " via Hugging Face Xet/CAS"
                } else {
                    ""
                }
            throw ModelDownloadException("HTTP ${connection.responseCode}$hint while downloading ${config.id}")
        }
        val acceptedOffset =
            if (connection.responseCode == HttpURLConnection.HTTP_PARTIAL) {
                connection
                    .getHeaderField("Content-Range")
                    ?.substringAfter("bytes ")
                    ?.substringBefore('-')
                    ?.toLongOrNull()
                    ?.takeIf { it == requestedOffset }
                    ?: 0L
            } else {
                0L
            }
        return ModelByteResponse(connection.inputStream, acceptedOffset)
    }
}

class ModelDownloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

interface ModelDownloader {
    suspend fun download(
        config: ModelConfig,
        onProgress: (ModelDownloadProgress) -> Unit = {},
    ): ModelResolutionState.Available
}

class ModelFileDownloader(
    private val appFilesRoot: File,
    private val byteSource: ModelByteSource = UrlModelByteSource(),
    private val resolver: ModelResolver = ModelResolver(appFilesRoot),
) : ModelDownloader {
    suspend fun download(config: ModelConfig): ModelResolutionState.Available = download(config) {}

    override suspend fun download(
        config: ModelConfig,
        onProgress: (ModelDownloadProgress) -> Unit,
    ): ModelResolutionState.Available = withContext(Dispatchers.IO) {
        val downloadUrls = listOf(config.url) + config.fallbackUrls
        val failures = mutableListOf<String>()
        val finalFile = File(appFilesRoot, config.relativePath)
        val parent =
            finalFile.parentFile
                ?: throw ModelDownloadException("Configured model path has no parent directory")
        val tempFile = File(parent, "${finalFile.name}.part")

        parent.mkdirs()
        var previousUrl: String? = null
        for (url in downloadUrls) {
            if (previousUrl != null && previousUrl != url) {
                tempFile.delete()
            }
            previousUrl = url
            val attemptConfig = config.copy(url = url)
            var cleanRetryAvailable = true
            attempts@ while (true) {
                try {
                    val requestedOffset = tempFile.takeIf(File::isFile)?.length() ?: 0L
                    val response = byteSource.open(attemptConfig, requestedOffset)
                    var appendedToPartial = false
                    response.input.use { input ->
                        appendedToPartial = requestedOffset > 0L && response.acceptedOffset == requestedOffset
                        java.io.FileOutputStream(tempFile, appendedToPartial).use { output ->
                            input.copyToWithProgress(
                                output = output,
                                totalBytes = config.expectedBytes,
                                initialBytes = if (appendedToPartial) requestedOffset else 0L,
                                onProgress = onProgress,
                            )
                        }
                    }

                    when (val validation = ModelFileIntegrity.validate(tempFile, config)) {
                        ModelFileValidation.Valid -> Unit
                        is ModelFileValidation.Invalid -> {
                            tempFile.delete()
                            if (appendedToPartial && cleanRetryAvailable) {
                                cleanRetryAvailable = false
                                continue@attempts
                            }
                            throw ModelDownloadException(validation.reason)
                        }
                    }

                    try {
                        Files.move(tempFile.toPath(), finalFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                    } catch (error: AtomicMoveNotSupportedException) {
                        tempFile.delete()
                        throw ModelDownloadException("Atomic model promotion is not supported", error)
                    }

                    return@withContext when (val resolution = resolver.resolve(config)) {
                        is ModelResolutionState.Available -> resolution
                        is ModelResolutionState.Missing -> {
                            throw ModelDownloadException("Downloaded model was not found after promotion")
                        }
                        is ModelResolutionState.IntegrityFailed -> {
                            throw ModelDownloadException(resolution.reason)
                        }
                    }
                } catch (error: ModelDownloadException) {
                    failures += error.message ?: error::class.java.simpleName
                    break@attempts
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += "Configured model download failed: ${error.message}"
                    break@attempts
                }
            }
        }

        throw ModelDownloadException("All configured download URLs failed for ${config.id}: ${failures.joinToString("; ")}")
    }
}

private suspend fun InputStream.copyToWithProgress(
    output: java.io.OutputStream,
    totalBytes: Long?,
    initialBytes: Long = 0L,
    onProgress: (ModelDownloadProgress) -> Unit,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytesCopied = initialBytes

    while (true) {
        currentCoroutineContext().ensureActive()
        val read = read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
        bytesCopied += read
        onProgress(ModelDownloadProgress(bytesCopied, totalBytes))
    }
}
