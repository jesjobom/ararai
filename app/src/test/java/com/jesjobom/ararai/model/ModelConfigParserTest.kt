package com.jesjobom.ararai.model

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
        assertEquals("models/smollm2-135m-q4.gguf", config.relativePath)
        assertEquals(1234L, config.expectedBytes)
        assertEquals(2048, config.inference.contextTokens)
        assertEquals(512, config.inference.maxTokens)
        assertEquals(0.7f, config.inference.temperature)
        assertEquals(0.9f, config.inference.topP)
    }

    @Test
    fun `parses catalog with multiple configured models`() {
        val catalog = ModelConfigParser.parseCatalog(
            """
            models.count=2
            models.defaultId=tiny
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
        assertEquals(2, catalog.models.size)
        assertEquals("Tiny Model", catalog.models[0].name)
        assertEquals("Small Model", catalog.models[1].name)
        assertEquals(256, catalog.models[1].inference.maxTokens)
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
            ModelConfigParser.parse(validRawConfig().replace("model.relativePath=models/smollm2-135m-q4.gguf", "model.relativePath=other/path.gguf"))
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
}
