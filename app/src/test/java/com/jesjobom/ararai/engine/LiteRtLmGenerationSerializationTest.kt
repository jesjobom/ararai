package com.jesjobom.ararai.engine

import com.jesjobom.ararai.knowledge.ApplicationToolExecutionEvent
import com.jesjobom.ararai.model.InferenceConfig
import com.jesjobom.ararai.model.LocalModel
import com.jesjobom.ararai.model.ModelAccelerationPolicy
import com.jesjobom.ararai.model.ModelInputCapabilities
import com.jesjobom.ararai.model.ModelRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmGenerationSerializationTest {
    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `shared engine serializes concurrent generations`() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var calls = 0
        var active = 0
        var maximumActive = 0
        val session = object : LiteRtLmSession {
            override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = flow {
                calls += 1
                active += 1
                maximumActive = maxOf(maximumActive, active)
                try {
                    if (calls == 1) {
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                    }
                    emit(LiteRtLmChunk(text = request.textPrompt.orEmpty()))
                } finally {
                    active -= 1
                }
            }

            override fun cancel() = Unit

            override fun close() = Unit
        }
        val bridge = object : LiteRtLmBridge {
            override suspend fun load(
                modelPath: String,
                config: InferenceConfig,
                useGpu: Boolean,
                inputCapabilities: ModelInputCapabilities,
                toolNames: Set<String>,
                profile: LiteRtLmWorkloadProfile,
            ): LiteRtLmSession = session
        }
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)

        val first = async { engine.generate(PromptRequest("first")).toList() }
        runCurrent()
        firstStarted.await()
        val second = async { engine.generate(PromptRequest("second")).toList() }
        runCurrent()

        assertEquals(1, calls)
        assertEquals(1, maximumActive)
        releaseFirst.complete(Unit)
        advanceUntilIdle()
        first.await()
        second.await()
        assertEquals(2, calls)
        assertEquals(1, maximumActive)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `unload waits for active generation before closing its session`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var active = false
        var closed = false
        var closedWhileActive = false
        val session = object : LiteRtLmSession {
            override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = flow {
                active = true
                started.complete(Unit)
                try {
                    release.await()
                    emit(LiteRtLmChunk(text = "complete"))
                } finally {
                    active = false
                }
            }
            override fun cancel() = Unit
            override fun close() {
                closedWhileActive = active
                closed = true
            }
        }
        val bridge = object : LiteRtLmBridge {
            override suspend fun load(
                modelPath: String,
                config: InferenceConfig,
                useGpu: Boolean,
                inputCapabilities: ModelInputCapabilities,
                toolNames: Set<String>,
                profile: LiteRtLmWorkloadProfile,
            ): LiteRtLmSession = session
        }
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)
        val generation = async { engine.generate(PromptRequest("first")).toList() }
        runCurrent()
        started.await()
        val unload = async { engine.unload() }
        runCurrent()
        assertFalse(closed)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(GenerationEvent.Completed, generation.await().last())
        unload.await()
        assertTrue(closed)
        assertFalse(closedWhileActive)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `reload waits for active generation before replacing its session`() = runTest {
        val bridge = PausedGenerationBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)

        val generation = async { engine.generate(PromptRequest("first")).toList() }
        runCurrent()
        bridge.firstStarted.await()
        val reload = async { engine.reload(MODEL, CONFIG) }
        runCurrent()

        assertEquals(1, bridge.sessions.size)
        assertEquals(0, bridge.sessions.single().closeCount)
        bridge.releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(GenerationEvent.Completed, generation.await().last())
        reload.await()
        assertEquals(2, bridge.sessions.size)
        assertEquals(1, bridge.sessions.first().closeCount)
        assertFalse(bridge.sessions.first().closedWhileActive)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `different workload waits for active generation before replacing its session`() = runTest {
        val bridge = PausedGenerationBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL.copy(inputCapabilities = ModelInputCapabilities(image = true)), CONFIG)

        val generation = async { engine.generate(PromptRequest("first")).toList() }
        runCurrent()
        bridge.firstStarted.await()
        val prepare = async { engine.prepare(LocalLlmWorkload(image = true)) }
        runCurrent()

        assertEquals(listOf(LiteRtLmWorkloadProfile.TextOnly), bridge.profiles)
        assertEquals(0, bridge.sessions.single().closeCount)
        bridge.releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(GenerationEvent.Completed, generation.await().last())
        prepare.await()
        assertEquals(
            listOf(LiteRtLmWorkloadProfile.TextOnly, LiteRtLmWorkloadProfile(image = true, audio = false)),
            bridge.profiles,
        )
        assertEquals(1, bridge.sessions.first().closeCount)
        assertFalse(bridge.sessions.first().closedWhileActive)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `recovery waits for cancellation then blocks new generation until reload`() = runTest {
        val bridge = PausedGenerationBridge()
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)

        val generation = async { engine.generate(PromptRequest("first")).toList() }
        runCurrent()
        bridge.firstStarted.await()
        val recoveryEntered = CompletableDeferred<Unit>()
        val allowRecovery = CompletableDeferred<Unit>()
        val recovery = async {
            engine.reloadWhenReady(MODEL, CONFIG) {
                recoveryEntered.complete(Unit)
                allowRecovery.await()
                true
            }
        }
        runCurrent()
        assertEquals(0, bridge.sessions.single().closeCount)

        generation.cancel()
        runCurrent()
        recoveryEntered.await()
        assertEquals(1, bridge.sessions.first().closeCount)
        assertFalse(bridge.sessions.first().closedWhileActive)
        val next = async { engine.generate(PromptRequest("next")).toList() }
        runCurrent()
        assertEquals(1, bridge.sessions.size)

        allowRecovery.complete(Unit)
        advanceUntilIdle()
        assertTrue(recovery.await())
        assertEquals(2, bridge.sessions.size)
        assertEquals(GenerationEvent.Completed, next.await().last())
        assertEquals(0, bridge.sessions.last().closeCount)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `cancelled generation releases a waiting reload without closing an active session`() = runTest {
        val started = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()
        var active = false
        var closedWhileActive = false
        var loads = 0
        val bridge = object : LiteRtLmBridge {
            override suspend fun load(
                modelPath: String,
                config: InferenceConfig,
                useGpu: Boolean,
                inputCapabilities: ModelInputCapabilities,
                toolNames: Set<String>,
                profile: LiteRtLmWorkloadProfile,
            ): LiteRtLmSession {
                loads++
                return object : LiteRtLmSession {
                    override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = flow {
                        active = true
                        started.complete(Unit)
                        try {
                            hold.await()
                        } finally {
                            active = false
                        }
                    }
                    override fun cancel() = Unit
                    override fun close() {
                        closedWhileActive = active
                    }
                }
            }
        }
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)
        val generation = async { engine.generate(PromptRequest("first")).toList() }
        runCurrent()
        started.await()
        val reload = async { engine.reload(MODEL, CONFIG) }
        runCurrent()
        assertEquals(1, loads)
        generation.cancel()
        advanceUntilIdle()
        reload.await()
        assertFalse(active)
        assertFalse(closedWhileActive)
        assertEquals(2, loads)
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `slow collector receives ordered chunks before completion`() = runTest {
        val bridge = object : LiteRtLmBridge {
            override suspend fun load(
                modelPath: String,
                config: InferenceConfig,
                useGpu: Boolean,
                inputCapabilities: ModelInputCapabilities,
                toolNames: Set<String>,
                profile: LiteRtLmWorkloadProfile,
            ): LiteRtLmSession = object : LiteRtLmSession {
                override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = flow {
                    repeat(300) { emit(LiteRtLmChunk(text = "$it,")) }
                }
                override fun cancel() = Unit
                override fun close() = Unit
            }
        }
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)
        val events = mutableListOf<GenerationEvent>()
        engine.generate(PromptRequest("first")).collect {
            events += it
            delay(1)
        }
        assertEquals(
            (0 until 300).joinToString("") { "$it," },
            events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text },
        )
        assertEquals(1, events.count { it == GenerationEvent.Completed })
        assertEquals(GenerationEvent.Completed, events.last())
    }

    @Test
    fun `slow collector preserves tool state around streamed text`() = runTest {
        val bridge = object : LiteRtLmBridge {
            override suspend fun load(
                modelPath: String,
                config: InferenceConfig,
                useGpu: Boolean,
                inputCapabilities: ModelInputCapabilities,
                toolNames: Set<String>,
                profile: LiteRtLmWorkloadProfile,
            ): LiteRtLmSession = object : LiteRtLmSession {
                override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = flow {
                    emit(LiteRtLmChunk(toolEvent = ApplicationToolExecutionEvent.Started, toolName = "calculator"))
                    repeat(100) { emit(LiteRtLmChunk(text = "$it,")) }
                    emit(
                        LiteRtLmChunk(
                            toolEvent = ApplicationToolExecutionEvent.Succeeded(emptyList()),
                            toolName = "calculator",
                        ),
                    )
                }
                override fun cancel() = Unit
                override fun close() = Unit
            }
        }
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)
        val events = mutableListOf<GenerationEvent>()

        engine.generate(PromptRequest("calculate")).collect {
            events += it
            delay(1)
        }

        assertEquals(GenerationEvent.ToolStarted("calculator", "calculator"), events.first())
        assertEquals(
            (0 until 100).joinToString("") { "$it," },
            events.filterIsInstance<GenerationEvent.Token>().joinToString("") { it.text },
        )
        assertEquals(GenerationEvent.ToolFinished("calculator", sources = emptyList()), events[101])
        assertEquals(GenerationEvent.Completed, events.last())
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `caller cancellation stops generation without emitting completion`() = runTest {
        val started = CompletableDeferred<Unit>()
        var cancellations = 0
        val bridge = object : LiteRtLmBridge {
            override suspend fun load(
                modelPath: String,
                config: InferenceConfig,
                useGpu: Boolean,
                inputCapabilities: ModelInputCapabilities,
                toolNames: Set<String>,
                profile: LiteRtLmWorkloadProfile,
            ): LiteRtLmSession = object : LiteRtLmSession {
                override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = flow {
                    emit(LiteRtLmChunk(text = "partial"))
                    started.complete(Unit)
                    awaitCancellation()
                }
                override fun cancel() {
                    cancellations++
                }
                override fun close() = Unit
            }
        }
        val engine = LiteRtLmLocalLlmEngine(bridge, StandardTestDispatcher(testScheduler))
        engine.load(MODEL, CONFIG)
        val events = mutableListOf<GenerationEvent>()
        val collector = launch { engine.generate(PromptRequest("cancel")).collect { events += it } }
        runCurrent()
        started.await()

        collector.cancel()
        advanceUntilIdle()

        assertEquals(listOf(GenerationEvent.Token("partial")), events)
        assertEquals(1, cancellations)
    }

    private companion object {
        val CONFIG = InferenceConfig(128, 8, 0.7f, 0.9f)
        val MODEL = LocalModel(
            id = "gemma-litert",
            name = "Gemma LiteRT",
            filePath = "/tmp/gemma.litertlm",
            runtime = ModelRuntime.LiteRtLm,
            acceleration = ModelAccelerationPolicy.GpuPreferred,
        )
    }
}

private class PausedGenerationBridge : LiteRtLmBridge {
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val sessions = mutableListOf<PausedGenerationSession>()
    val profiles = mutableListOf<LiteRtLmWorkloadProfile>()

    override suspend fun load(
        modelPath: String,
        config: InferenceConfig,
        useGpu: Boolean,
        inputCapabilities: ModelInputCapabilities,
        toolNames: Set<String>,
        profile: LiteRtLmWorkloadProfile,
    ): LiteRtLmSession {
        profiles += profile
        return PausedGenerationSession(sessions.isEmpty(), firstStarted, releaseFirst).also(sessions::add)
    }
}

private class PausedGenerationSession(
    private val pause: Boolean,
    private val started: CompletableDeferred<Unit>,
    private val release: CompletableDeferred<Unit>,
) : LiteRtLmSession {
    private var active = false
    var closeCount = 0
        private set
    var closedWhileActive = false
        private set

    override fun generate(request: PromptRequest, config: InferenceConfig): Flow<LiteRtLmChunk> = flow {
        active = true
        try {
            if (pause) {
                started.complete(Unit)
                release.await()
            }
            emit(LiteRtLmChunk(text = request.textPrompt.orEmpty()))
        } finally {
            active = false
        }
    }

    override fun cancel() = Unit

    override fun close() {
        closeCount++
        closedWhileActive = active
    }
}
