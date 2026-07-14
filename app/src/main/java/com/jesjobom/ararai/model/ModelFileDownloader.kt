package com.jesjobom.ararai.model

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelByteSource {
    fun open(config: ModelConfig): InputStream
}

data class ModelDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?,
)

class UrlModelByteSource : ModelByteSource {
    override fun open(config: ModelConfig): InputStream {
        val connection = URL(config.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        if (connection.responseCode !in 200..299) {
            val host = connection.url.host
            val hint = if (connection.responseCode == HttpURLConnection.HTTP_FORBIDDEN && host.contains("xethub.hf.co")) {
                " via Hugging Face Xet/CAS"
            } else {
                ""
            }
            throw ModelDownloadException("HTTP ${connection.responseCode}$hint while downloading ${config.id}")
        }
        return connection.inputStream
    }
}

class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
    suspend fun download(config: ModelConfig): ModelResolutionState.Available =
        download(config) {}

    override suspend fun download(
        config: ModelConfig,
        onProgress: (ModelDownloadProgress) -> Unit,
    ): ModelResolutionState.Available =
        withContext(Dispatchers.IO) {
            val downloadUrls = listOf(config.url) + config.fallbackUrls
            val failures = mutableListOf<String>()
            val finalFile = File(appFilesRoot, config.relativePath)
            val parent = finalFile.parentFile
                ?: throw ModelDownloadException("Configured model path has no parent directory")
            val tempFile = File(parent, "${finalFile.name}.part")

            parent.mkdirs()
            if (tempFile.exists() && !tempFile.delete()) {
                throw ModelDownloadException("Could not delete stale temporary model file")
            }

            for (url in downloadUrls) {
                val attemptConfig = config.copy(url = url)
                try {
                    byteSource.open(attemptConfig).use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyToWithProgress(
                                output = output,
                                totalBytes = config.expectedBytes,
                                onProgress = onProgress,
                            )
                        }
                    }

                    when (val validation = ModelFileIntegrity.validate(tempFile, config)) {
                        ModelFileValidation.Valid -> Unit
                        is ModelFileValidation.Invalid -> {
                            tempFile.delete()
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
                    tempFile.delete()
                    failures += error.message ?: error::class.java.simpleName
                } catch (error: CancellationException) {
                    tempFile.delete()
                    throw error
                } catch (error: Exception) {
                    tempFile.delete()
                    failures += "Configured model download failed: ${error.message}"
                }
            }

            throw ModelDownloadException("All configured download URLs failed for ${config.id}: ${failures.joinToString("; ")}")
        }
}

private fun InputStream.copyToWithProgress(
    output: java.io.OutputStream,
    totalBytes: Long?,
    onProgress: (ModelDownloadProgress) -> Unit,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytesCopied = 0L

    while (true) {
        val read = read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
        bytesCopied += read
        onProgress(ModelDownloadProgress(bytesCopied, totalBytes))
    }
}
