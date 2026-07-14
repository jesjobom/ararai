package com.jesjobom.ararai.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ModelConfigParserTest {
    @Test
    fun `parses the fixed model configuration`() {
        val config = ModelConfigParser.parse(
            """
            model.id=smollm2-135m-q4
            model.name=SmolLM2 135M Q4
            model.url=https://example.com/smollm2-135m-q4.gguf
            model.fileName=smollm2-135m-q4.gguf
            model.relativePath=models/smollm2-135m-q4.gguf
            model.sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            model.expectedBytes=1234
            inference.contextTokens=2048
            inference.maxTokens=512
            inference.temperature=0.7
            inference.topP=0.9
            """.trimIndent(),
        )

        assertEquals("smollm2-135m-q4", config.id)
        assertEquals("SmolLM2 135M Q4", config.name)
        assertEquals(ModelRuntime.LlamaCpp, config.runtime)
        assertEquals(ModelArtifactFormat.Gguf, config.artifactFormat)
        assertEquals(ModelAccelerationPolicy.GpuPreferred, config.acceleration)
        assertEquals(emptyList<String>(), config.fallbackUrls)
        assertEquals(true, config.inputCapabilities.text)
        assertEquals(false, config.inputCapabilities.image)
        assertEquals(false, config.inputCapabilities.audio)
        assertEquals("models/smollm2-135m-q4.gguf", config.relativePath)
        assertEquals(1234L, config.expectedBytes)
        assertEquals(2048, config.inference.contextTokens)
        assertEquals(512, config.inference.maxTokens)
        assertEquals(0.7f, config.inference.temperature)
        assertEquals(0.9f, config.inference.topP)
        assertEquals(40, config.inference.topK)
        assertEquals(0.05f, config.inference.minP)
        assertEquals(1.10f, config.inference.repeatPenalty)
    }

    @Test
    fun `parses optional fallback download urls`() {
        val config = ModelConfigParser.parse(
            validRawConfig() + """

            model.fallbackUrls=https://mirror.example.com/a.gguf, https://mirror.example.com/b.gguf
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "https://mirror.example.com/a.gguf",
                "https://mirror.example.com/b.gguf",
            ),
            config.fallbackUrls,
        )
    }

    @Test
    fun `parses explicit multimodal input capabilities`() {
        val config = ModelConfigParser.parse(
            validRawConfig() + """

            model.capabilities.input.image=true
            model.capabilities.input.audio=true
            """.trimIndent(),
        )

        assertEquals(true, config.inputCapabilities.text)
        assertEquals(true, config.inputCapabilities.image)
        assertEquals(true, config.inputCapabilities.audio)
    }

    @Test
    fun `parses configured runtime metadata`() {
        val config = ModelConfigParser.parse(
            """
            model.id=gemma-litert
            model.name=Gemma LiteRT
            model.runtime=litert_lm
            model.artifactFormat=litert_lm_bundle
            model.acceleration=cpu_only
            model.url=https://example.com/gemma-litert.task
            model.fileName=gemma-litert.task
            model.relativePath=models/litert-lm/gemma-litert.task
            model.sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            model.expectedBytes=1234
            inference.contextTokens=2048
            inference.maxTokens=512
            inference.temperature=0.7
            inference.topP=0.9
            """.trimIndent(),
        )

        assertEquals(ModelRuntime.LiteRtLm, config.runtime)
        assertEquals(ModelArtifactFormat.LiteRtLmBundle, config.artifactFormat)
        assertEquals(ModelAccelerationPolicy.CpuOnly, config.acceleration)
        assertEquals("models/litert-lm/gemma-litert.task", config.relativePath)
    }

    @Test
    fun `parses catalog with multiple configured models`() {
        val catalog = ModelConfigParser.parseCatalog(
            """
            models.count=2
            models.defaultId=tiny
            chat.systemPrompt=Be brief.
            models.0.id=tiny
            models.0.name=Tiny Model
            models.0.url=https://example.com/tiny.gguf
            models.0.fileName=tiny.gguf
            models.0.relativePath=models/tiny.gguf
            models.0.sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
            models.0.expectedBytes=1234
            models.0.inference.contextTokens=1024
            models.0.inference.maxTokens=128
            models.0.inference.temperature=0.7
            models.0.inference.topP=0.9
            models.1.id=small
            models.1.name=Small Model
            models.1.url=https://example.com/small.gguf
            models.1.fileName=small.gguf
            models.1.relativePath=models/small.gguf
            models.1.sha256=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
            models.1.expectedBytes=5678
            models.1.inference.contextTokens=2048
            models.1.inference.maxTokens=256
            models.1.inference.temperature=0.6
            models.1.inference.topP=0.8
            """.trimIndent(),
        )

        assertEquals("tiny", catalog.defaultModelId)
        assertEquals("Be brief.", catalog.chat.systemPrompt)
        assertEquals(2, catalog.models.size)
        assertEquals("Tiny Model", catalog.models[0].name)
        assertEquals("Small Model", catalog.models[1].name)
        assertEquals(256, catalog.models[1].inference.maxTokens)
    }

    @Test
    fun `parses checked in fixed model catalog`() {
        val raw = fixedModelCatalogFile().readText()

        val catalog = ModelConfigParser.parseCatalog(raw)

        assertEquals("smollm2-135m-instruct-q4-k-m", catalog.defaultModelId)
        assertEquals(7, catalog.models.size)
        assertEquals("Qwen3.5 0.8B Q4_K_M", catalog.models[3].name)
        assertEquals("Qwen3.5 2B Q4_K_M", catalog.models[4].name)
        assertEquals("Qwen3.5 4B Q4_K_M", catalog.models[5].name)
        assertEquals(ModelAccelerationPolicy.CpuOnly, catalog.models[3].acceleration)
        assertEquals(ModelAccelerationPolicy.CpuOnly, catalog.models[4].acceleration)
        assertEquals(ModelAccelerationPolicy.CpuOnly, catalog.models[5].acceleration)
        catalog.models.slice(3..5).forEach { qwen ->
            assertEquals(8192, qwen.inference.contextTokens)
            assertEquals(20, qwen.inference.topK)
            assertEquals(0.0f, qwen.inference.minP)
            assertEquals(1.0f, qwen.inference.repeatPenalty)
        }
        assertEquals("Gemma 4 E2B IT LiteRT-LM", catalog.models[6].name)
    }

    @Test
    fun `wraps legacy single model configuration as catalog`() {
        val catalog = ModelConfigParser.parseCatalog(validRawConfig())

        assertEquals("smollm2-135m-q4", catalog.defaultModelId)
        assertEquals(1, catalog.models.size)
    }

    @Test
    fun `rejects duplicate catalog model ids`() {
        try {
            ModelConfigParser.parseCatalog(
                """
                models.count=2
                models.0.id=dup
                models.0.name=First
                models.0.url=https://example.com/first.gguf
                models.0.fileName=first.gguf
                models.0.relativePath=models/first.gguf
                models.0.sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                models.0.expectedBytes=1
                models.0.inference.contextTokens=1024
                models.0.inference.maxTokens=128
                models.0.inference.temperature=0.7
                models.0.inference.topP=0.9
                models.1.id=dup
                models.1.name=Second
                models.1.url=https://example.com/second.gguf
                models.1.fileName=second.gguf
                models.1.relativePath=models/second.gguf
                models.1.sha256=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
                models.1.expectedBytes=1
                models.1.inference.contextTokens=1024
                models.1.inference.maxTokens=128
                models.1.inference.temperature=0.7
                models.1.inference.topP=0.9
                """.trimIndent(),
            )
            fail("Expected duplicate IDs to throw")
        } catch (error: IllegalArgumentException) {
            assertEquals("Model catalog model IDs must be unique", error.message)
        }
    }

    @Test
    fun `rejects missing required fields`() {
        try {
            ModelConfigParser.parse("model.id=missing-fields")
            fail("Expected missing fields to throw")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun `rejects relative path that does not match configured file name`() {
        try {
            ModelConfigParser.parse(validRawConfig().replace("model.relativePath=models/smollm2-135m-q4.gguf", "model.relativePath=models/other.gguf"))
            fail("Expected relative path mismatch to throw")
        } catch (error: IllegalArgumentException) {
            assertEquals("model.relativePath must match models/<model.fileName>", error.message)
        }
    }

    @Test
    fun `rejects invalid integrity and inference bounds`() {
        assertInvalid(
            raw = validRawConfig().replace(
                "model.sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "model.sha256=not-a-sha",
            ),
            expectedMessage = "model.sha256 must be a lowercase SHA-256 hex digest",
        )
        assertInvalid(
            raw = validRawConfig().replace("model.expectedBytes=1234", "model.expectedBytes=0"),
            expectedMessage = "model.expectedBytes must be positive when present",
        )
        assertInvalid(
            raw = validRawConfig().replace("inference.topP=0.9", "inference.topP=1.1"),
            expectedMessage = "inference.topP must be between 0 and 1",
        )
        assertInvalid(
            raw = validRawConfig().replace("inference.maxTokens=512", "inference.maxTokens=0"),
            expectedMessage = "inference.maxTokens must be positive",
        )
        assertInvalid(
            raw = validRawConfig().replace(
                "model.id=smollm2-135m-q4",
                "model.id=smollm2-135m-q4\nmodel.runtime=litert_lm",
            ),
            expectedMessage = "model.runtime and model.artifactFormat must be compatible",
        )
        assertInvalid(
            raw = validRawConfig() + "\nmodel.capabilities.input.text=false\n",
            expectedMessage = "model capabilities must enable at least one input modality",
        )
        assertInvalid(
            raw = validRawConfig() + "\nmodel.capabilities.input.text=false\nmodel.capabilities.input.image=true\n",
            expectedMessage = "image input requires text input support",
        )
    }

    private fun assertInvalid(raw: String, expectedMessage: String) {
        try {
            ModelConfigParser.parse(raw)
            fail("Expected invalid config to throw")
        } catch (error: IllegalArgumentException) {
            assertEquals(expectedMessage, error.message)
        }
    }

    private fun validRawConfig(): String =
        """
        model.id=smollm2-135m-q4
        model.name=SmolLM2 135M Q4
        model.url=https://example.com/smollm2-135m-q4.gguf
        model.fileName=smollm2-135m-q4.gguf
        model.relativePath=models/smollm2-135m-q4.gguf
        model.sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        model.expectedBytes=1234
        inference.contextTokens=2048
        inference.maxTokens=512
        inference.temperature=0.7
        inference.topP=0.9
        """.trimIndent()

    private fun fixedModelCatalogFile(): File {
        val candidates = listOf(
            File("app/src/main/res/raw/fixed_model.properties"),
            File("src/main/res/raw/fixed_model.properties"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate fixed_model.properties from ${File(".").absolutePath}")
    }
}
