package com.jesjobom.ararai.model

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

sealed interface ModelFileValidation {
    data object Valid : ModelFileValidation

    data class Invalid(
        val reason: String,
    ) : ModelFileValidation
}

object ModelFileIntegrity {
    fun validate(
        file: File,
        config: ModelConfig,
    ): ModelFileValidation {
        if (!file.exists()) {
            return ModelFileValidation.Invalid("Configured model file does not exist")
        }
        if (!file.isFile) {
            return ModelFileValidation.Invalid("Configured model path is not a file")
        }
        if (config.expectedBytes != null && file.length() != config.expectedBytes) {
            return ModelFileValidation.Invalid(
                "Expected ${config.expectedBytes} bytes but found ${file.length()}",
            )
        }

        val verificationFile = file.verificationFile()
        val verification = verificationFile.takeIf(File::isFile)?.readVerification()
        if (verification == ModelVerification.forFile(file, config)) {
            return ModelFileValidation.Valid
        }

        verificationFile.delete()
        val actualSha256 = file.sha256()
        if (actualSha256 != config.sha256) {
            return ModelFileValidation.Invalid(
                "Expected SHA-256 ${config.sha256} but found $actualSha256",
            )
        }

        verificationFile.writeVerification(ModelVerification.forFile(file, config))
        return ModelFileValidation.Valid
    }

    fun invalidate(file: File) {
        file.verificationFile().delete()
    }

    fun promoteVerification(source: File, destination: File) {
        val sourceVerification = source.verificationFile()
        if (!sourceVerification.isFile) return
        runCatching {
            Files.move(
                sourceVerification.toPath(),
                destination.verificationFile().toPath(),
                REPLACE_EXISTING,
            )
        }
    }
}

private data class ModelVerification(
    val sha256: String,
    val bytes: Long,
    val lastModifiedMillis: Long,
) {
    companion object {
        fun forFile(file: File, config: ModelConfig): ModelVerification = ModelVerification(
            sha256 = config.sha256,
            bytes = file.length(),
            lastModifiedMillis = file.lastModified(),
        )
    }
}

private fun File.verificationFile() = File(parentFile, ".$name.verified")

private fun File.readVerification(): ModelVerification? = runCatching {
    val values = readLines().associate { line ->
        val separator = line.indexOf('=')
        require(separator > 0)
        line.substring(0, separator) to line.substring(separator + 1)
    }
    require(values["version"] == "1")
    ModelVerification(
        sha256 = checkNotNull(values["sha256"]),
        bytes = checkNotNull(values["bytes"]).toLong(),
        lastModifiedMillis = checkNotNull(values["lastModifiedMillis"]).toLong(),
    )
}.getOrNull()

private fun File.writeVerification(verification: ModelVerification) {
    runCatching {
        parentFile?.mkdirs()
        writeText(
            "version=1\n" +
                "sha256=${verification.sha256}\n" +
                "bytes=${verification.bytes}\n" +
                "lastModifiedMillis=${verification.lastModifiedMillis}\n",
        )
    }
}

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { "%02x".format(it) }
}
