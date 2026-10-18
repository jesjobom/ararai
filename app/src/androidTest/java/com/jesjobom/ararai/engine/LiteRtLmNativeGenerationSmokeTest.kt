package com.jesjobom.ararai.engine

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jesjobom.ararai.ArarAiApplication
import com.jesjobom.ararai.model.ModelStartupState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in physical smoke using only synthetic text and an already installed E2B artifact. */
@RunWith(AndroidJUnit4::class)
class LiteRtLmNativeGenerationSmokeTest {
    @Test
    fun textCancellationAndReloadKeepTheNativeSessionUsable() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ArarAiApplication>()
        val item = app.modelController.state.value.models.single { it.config.id == E2B_MODEL_ID }
        assumeTrue("E2B artifact is not installed", item.state is ModelStartupState.Available)
        val available = item.state as ModelStartupState.Available
        val config = requireNotNull(available.inference).copy(contextTokens = 2_048)
        val telemetry = LiteRtLmRuntimeTelemetry.android(app, enabled = true)
        val engine = LiteRtLmLocalLlmEngine(
            bridge = AndroidLiteRtLmBridge(cacheDir = app.cacheDir.absolutePath, runtimeTelemetry = telemetry),
        )

        try {
            withTimeout(GENERATION_TIMEOUT_MILLIS) { engine.load(available.model, config) }
            val first = withTimeout(GENERATION_TIMEOUT_MILLIS) {
                engine.generate(PromptRequest("Reply with one short sentence about the color blue.")).toList()
            }
            assertEquals(1, first.count { it == GenerationEvent.Completed })
            assertTrue(first.filterIsInstance<GenerationEvent.Token>().any { it.text.isNotBlank() })

            withTimeout(GENERATION_TIMEOUT_MILLIS) {
                engine.generate(PromptRequest("Count from one to ten, separated by commas."))
                    .first { it is GenerationEvent.Token }
            }
            withTimeout(GENERATION_TIMEOUT_MILLIS) { engine.reload(available.model, config) }

            val afterReload = withTimeout(GENERATION_TIMEOUT_MILLIS) {
                engine.generate(PromptRequest("Reply with one short sentence about the color green.")).toList()
            }
            assertEquals(1, afterReload.count { it == GenerationEvent.Completed })
            assertTrue(afterReload.filterIsInstance<GenerationEvent.Token>().any { it.text.isNotBlank() })
        } finally {
            withContext(NonCancellable) { engine.unload() }
        }
    }

    private companion object {
        const val E2B_MODEL_ID = "gemma-4-e2b-it-litert-lm"
        const val GENERATION_TIMEOUT_MILLIS = 240_000L
    }
}
