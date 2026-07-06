package com.jesjobom.ararai.model

import java.io.File
import java.security.MessageDigest

sealed interface ModelFileValidation {
    data object Valid : ModelFileValidation
    data class Invalid(val reason: String) : ModelFileValidation
}

object ModelFileIntegrity {
    fun validate(file: File, config: ModelConfig): ModelFileValidation {
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

        val actualSha256 = file.sha256()
        if (actualSha256 != config.sha256) {
            return ModelFileValidation.Invalid(
                "Expected SHA-256 ${config.sha256} but found $actualSha256",
            )
        }

        return ModelFileValidation.Valid
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
