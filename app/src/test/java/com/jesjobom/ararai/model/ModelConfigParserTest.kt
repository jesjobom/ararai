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
            inference.temperature=0.7
            inference.topP=0.9
            """.trimIndent(),
        )

        assertEquals("smollm2-135m-q4", config.id)
        assertEquals("SmolLM2 135M Q4", config.name)
        assertEquals("models/smollm2-135m-q4.gguf", config.relativePath)
        assertEquals(1234L, config.expectedBytes)
        assertEquals(2048, config.inference.contextTokens)
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
}
