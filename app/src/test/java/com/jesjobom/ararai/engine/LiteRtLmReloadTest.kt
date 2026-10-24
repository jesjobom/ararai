package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelInputCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmReloadTest {
    @Test
    fun `explicit reload replaces an otherwise compatible native engine`() = runTest {
        val bridge = ReloadRecordingBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))

        engine.load(MODEL, CONFIG)
        engine.reload(MODEL, CONFIG)

        assertEquals(2, bridge.loadCalls)
        assertTrue(bridge.sessions.first().closed)
        assertTrue(!bridge.sessions.last().closed)
    }

    @Test
    fun `bounded recovery keeps the engine unloaded while waiting`() = runTest {
        val bridge = ReloadRecordingBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)

        val ready = engine.reloadWhenReady(MODEL, CONFIG) {
            assertTrue(bridge.sessions.single().closed)
            false
        }

        assertFalse(ready)
        assertEquals(1, bridge.loadCalls)
        assertTrue(bridge.sessions.single().closed)
    }

    @Test
    fun `bounded recovery reloads only after the readiness barrier passes`() = runTest {
        val bridge = ReloadRecordingBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)

        val ready = engine.reloadWhenReady(MODEL, CONFIG) { true }

        assertTrue(ready)
        assertEquals(2, bridge.loadCalls)
        assertTrue(bridge.sessions.first().closed)
        assertFalse(bridge.sessions.last().closed)
    }

    private class ReloadRecordingBridge : LiteRtLmBridge {
        val sessions = mutableListOf<ReloadRecordingSession>()
        var loadCalls = 0

        override suspend fun load(
            modelPath: String,
            config: InferenceConfig,
            useGpu: Boolean,
            inputCapabilities: ModelInputCapabilities,
            toolNames: Set<String>,
            profile: LiteRtLmWorkloadProfile,
        ): LiteRtLmSession {
            loadCalls++
            return ReloadRecordingSession().also(sessions::add)
        }
    }

    private class ReloadRecordingSession : LiteRtLmSession {
        var closed = false

        override fun generate(
            request: PromptRequest,
            config: InferenceConfig,
        ): Flow<LiteRtLmChunk> = emptyFlow()

        override fun cancel() = Unit

        override fun close() {
            closed = true
        }
    }

    companion object {
        private val MODEL = LocalModel(
            id = "reload",
            name = "Reload",
            filePath = "/tmp/reload.litertlm",
        )
        private val CONFIG = InferenceConfig(
            contextTokens = 128,
            promptReserveTokens = 8,
            temperature = 0.2f,
            topP = 0.9f,
        )
    }
}
