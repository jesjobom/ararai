package com.jesjobom.ararai.engine

import app.cash.turbine.test
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeLocalLlmEngineTest {
    @Test
    fun `fails generation when model has not been loaded`() = runTest {
        val engine = FakeLocalLlmEngine(chunks = listOf("ignored"))

        engine.generate(PromptRequest("hello")).test {
            assertEquals(
                GenerationEvent.Failed(
                    message = "Model is not loaded",
                    kind = GenerationFailureKind.Expected,
                ),
                awaitItem(),
            )
            awaitComplete()
        }
    }

    @Test
    fun `emits configured chunks and completion`() = runTest {
        val engine = FakeLocalLlmEngine(chunks = listOf("a", "b"))
        engine.load(
            model = LocalModel("test", "Test", "/tmp/test.gguf"),
            config = InferenceConfig(contextTokens = 128, temperature = 0.7f, topP = 0.9f),
        )

        engine.generate(PromptRequest("hello")).test {
            assertEquals(GenerationEvent.Token("a"), awaitItem())
            assertEquals(GenerationEvent.Token("b"), awaitItem())
            assertEquals(GenerationEvent.Completed, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `unload returns engine to unloaded state`() = runTest {
        val engine = FakeLocalLlmEngine(chunks = listOf("a"))
        engine.load(
            model = LocalModel("test", "Test", "/tmp/test.gguf"),
            config = InferenceConfig(contextTokens = 128, temperature = 0.7f, topP = 0.9f),
        )

        engine.unload()

        engine.generate(PromptRequest("hello")).test {
            assertEquals(
                GenerationEvent.Failed(
                    message = "Model is not loaded",
                    kind = GenerationFailureKind.Expected,
                ),
                awaitItem(),
            )
            awaitComplete()
        }
    }
}
