package com.jesjobom.ararai.engine

import com.jesjobom.ararai.chat.ImageAttachment
import com.jesjobom.ararai.chat.MessageContent
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelInputCapabilities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtLmWorkloadLoadingTest {
    @Test
    fun `loads requested audio profile directly without intermediate text engine`() = runTest {
        val bridge = WorkloadRecordingBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        val model =
            LocalModel(
                id = "audio-model",
                name = "Audio model",
                filePath = "/tmp/audio-model.litertlm",
                inputCapabilities = ModelInputCapabilities(audio = true),
            )

        engine.loadForWorkload(
            model,
            InferenceConfig(contextTokens = 128, temperature = 0.2f, topP = 0.9f),
            LocalLlmWorkload.Audio,
        )

        assertEquals(listOf(LiteRtLmWorkloadProfile(image = false, audio = true)), bridge.loadedProfiles)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `publishes native session when requesting caller is cancelled during load`() = runTest {
        val loadStarted = CompletableDeferred<Unit>()
        val allowLoadToFinish = CompletableDeferred<Unit>()
        val bridge = WorkloadRecordingBridge(loadStarted, allowLoadToFinish)
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        val model = audioModel()
        val config = InferenceConfig(contextTokens = 128, temperature = 0.2f, topP = 0.9f)

        val abandonedEntry = launch { engine.loadForWorkload(model, config, LocalLlmWorkload.Audio) }
        runCurrent()
        loadStarted.await()
        abandonedEntry.cancel()
        allowLoadToFinish.complete(Unit)
        advanceUntilIdle()

        engine.loadForWorkload(model, config, LocalLlmWorkload.Audio)

        assertEquals(1, bridge.loadCalls)
        assertEquals(listOf(LiteRtLmWorkloadProfile(image = false, audio = true)), bridge.loadedProfiles)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancelled generation still owns a native session created during profile switch`() = runTest {
        val bridge = PausedProfileLoadBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        val model = audioModel().copy(inputCapabilities = ModelInputCapabilities(image = true))
        val config = InferenceConfig(contextTokens = 128, temperature = 0.2f, topP = 0.9f)
        engine.load(model, config)

        val generation = launch {
            engine.generate(
                PromptRequest(
                    MessageContent.TextPrompt(
                        "describe",
                        imageAttachments = listOf(ImageAttachment("file:///tmp/image.png", "image/png")),
                    ),
                ),
            ).collect()
        }
        runCurrent()
        bridge.secondLoadStarted.await()
        generation.cancel()
        bridge.releaseSecondLoad.complete(Unit)
        advanceUntilIdle()

        engine.unload()
        assertEquals(2, bridge.sessions.size)
        assertEquals(listOf(1, 1), bridge.sessions.map { it.closeCount })
    }

    private fun audioModel() = LocalModel(
        id = "audio-model",
        name = "Audio model",
        filePath = "/tmp/audio-model.litertlm",
        inputCapabilities = ModelInputCapabilities(audio = true),
    )
}

private class WorkloadRecordingBridge(
    private val loadStarted: CompletableDeferred<Unit>? = null,
    private val allowLoadToFinish: CompletableDeferred<Unit>? = null,
) : LiteRtLmBridge {
    val loadedProfiles = mutableListOf<LiteRtLmWorkloadProfile>()
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
        loadStarted?.complete(Unit)
        allowLoadToFinish?.await()
        loadedProfiles += profile
        return EmptyLiteRtLmSession
    }
}

private object EmptyLiteRtLmSession : LiteRtLmSession {
    override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = emptyFlow()

    override fun cancel() = Unit

    override fun close() = Unit
}

private class PausedProfileLoadBridge : LiteRtLmBridge {
    val secondLoadStarted = CompletableDeferred<Unit>()
    val releaseSecondLoad = CompletableDeferred<Unit>()
    val sessions = mutableListOf<ClosableLiteRtLmSession>()

    override suspend fun load(
        modelPath: String,
        config: InferenceConfig,
        useGpu: Boolean,
        inputCapabilities: ModelInputCapabilities,
        toolNames: Set<String>,
        profile: LiteRtLmWorkloadProfile,
    ): LiteRtLmSession {
        val session = ClosableLiteRtLmSession().also(sessions::add)
        if (sessions.size == 2) {
            secondLoadStarted.complete(Unit)
            withContext(NonCancellable) { releaseSecondLoad.await() }
        }
        return session
    }
}

private class ClosableLiteRtLmSession : LiteRtLmSession {
    var closeCount = 0
        private set

    override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = emptyFlow()
    override fun cancel() = Unit
    override fun close() {
        closeCount++
    }
}
