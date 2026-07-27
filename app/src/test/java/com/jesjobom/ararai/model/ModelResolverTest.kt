package com.jesjobom.ararai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelResolverTest {
    private val config =
        ModelConfig(
            id = "test-model",
            name = "Test Model",
            url = "https://example.com/test.gguf",
            fileName = "test.gguf",
            relativePath = "models/test.gguf",
            sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            expectedBytes = 5,
            inference = InferenceConfig(contextTokens = 128, temperature = 0.7f, topP = 0.9f),
        )

    @Test
    fun `reports available when configured file exists and passes integrity`() {
        val root = Files.createTempDirectory("ararai-model").toFile()
        val modelFile = File(root, config.relativePath)
        modelFile.parentFile.mkdirs()
        modelFile.writeText("hello")

        val state = ModelResolver(root).resolve(config)

        assertTrue(state is ModelResolutionState.Available)
        state as ModelResolutionState.Available
        assertEquals(modelFile.absolutePath, state.file.absolutePath)
    }

    @Test
    fun `reports missing when configured file does not exist`() {
        val root = Files.createTempDirectory("ararai-model").toFile()

        val state = ModelResolver(root).resolve(config)

        assertEquals(ModelResolutionState.Missing(config), state)
    }

    @Test
    fun `reports integrity failure for wrong bytes`() {
        val root = Files.createTempDirectory("ararai-model").toFile()
        val modelFile = File(root, config.relativePath)
        modelFile.parentFile.mkdirs()
        modelFile.writeText("wrong")

        val state = ModelResolver(root).resolve(config)

        assertTrue(state is ModelResolutionState.IntegrityFailed)
        assertEquals(modelFile.absolutePath, (state as ModelResolutionState.IntegrityFailed).file.absolutePath)
    }
}
