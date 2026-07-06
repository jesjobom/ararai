package com.jesjobom.ararai.model

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelByteSource {
    fun open(config: ModelConfig): InputStream
}

class UrlModelByteSource : ModelByteSource {
    override fun open(config: ModelConfig): InputStream {
        val connection = URL(config.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        if (connection.responseCode !in 200..299) {
            throw ModelDownloadException("HTTP ${connection.responseCode} while downloading ${config.id}")
        }
        return connection.inputStream
    }
}

class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ModelFileDownloader(
    private val appFilesRoot: File,
    private val byteSource: ModelByteSource = UrlModelByteSource(),
    private val resolver: ModelResolver = ModelResolver(appFilesRoot),
) {
    suspend fun download(config: ModelConfig): ModelResolutionState.Available =
        withContext(Dispatchers.IO) {
            val finalFile = File(appFilesRoot, config.relativePath)
            val parent = finalFile.parentFile
                ?: throw ModelDownloadException("Configured model path has no parent directory")
            val tempFile = File(parent, "${finalFile.name}.part")

            parent.mkdirs()
            if (tempFile.exists() && !tempFile.delete()) {
                throw ModelDownloadException("Could not delete stale temporary model file")
            }

            try {
                byteSource.open(config).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
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

                when (val resolution = resolver.resolve(config)) {
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
                throw error
            } catch (error: Exception) {
                tempFile.delete()
                throw ModelDownloadException("Configured model download failed: ${error.message}", error)
            }
        }
}
