package com.jesjobom.ararai.model

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFileIntegrityTest {
    @Test
    fun `rejects missing model files`() {
        val root = Files.createTempDirectory("ararai-integrity").toFile()
        val missing = File(root, "models/hello.gguf")

        val validation = ModelFileIntegrity.validate(missing, helloConfig(expectedBytes = null))

        assertEquals(
            ModelFileValidation.Invalid("Configured model file does not exist"),
            validation,
        )
    }

    @Test
    fun `rejects directories at configured model path`() {
        val root = Files.createTempDirectory("ararai-integrity").toFile()
        val directory = File(root, "models/hello.gguf")
        directory.mkdirs()

        val validation = ModelFileIntegrity.validate(directory, helloConfig(expectedBytes = null))

        assertEquals(
            ModelFileValidation.Invalid("Configured model path is not a file"),
            validation,
        )
    }

    @Test
    fun `reports byte size mismatch before hashing`() {
        val root = Files.createTempDirectory("ararai-integrity").toFile()
        val file = File(root, "models/hello.gguf")
        file.parentFile!!.mkdirs()
        file.writeText("hello")

        val validation = ModelFileIntegrity.validate(file, helloConfig(expectedBytes = 6))

        assertEquals(
            ModelFileValidation.Invalid("Expected 6 bytes but found 5"),
            validation,
        )
    }

    @Test
    fun `reports sha mismatch when size is unknown`() {
        val root = Files.createTempDirectory("ararai-integrity").toFile()
        val file = File(root, "models/hello.gguf")
        file.parentFile!!.mkdirs()
        file.writeText("hello")

        val validation = ModelFileIntegrity.validate(
            file = file,
            config = helloConfig(
                sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                expectedBytes = null,
            ),
        )

        assertTrue(validation is ModelFileValidation.Invalid)
        assertTrue((validation as ModelFileValidation.Invalid).reason.startsWith("Expected SHA-256"))
    }

    private fun helloConfig(
        sha256: String = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
        expectedBytes: Long?,
    ): ModelConfig =
        ModelConfig(
            id = "hello",
            name = "Hello Model",
            url = "https://example.com/hello.gguf",
            fileName = "hello.gguf",
            relativePath = "models/hello.gguf",
            sha256 = sha256,
            expectedBytes = expectedBytes,
            inference = InferenceConfig(contextTokens = 128, temperature = 0.7f, topP = 0.9f),
        )
}
