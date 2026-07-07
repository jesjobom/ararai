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
