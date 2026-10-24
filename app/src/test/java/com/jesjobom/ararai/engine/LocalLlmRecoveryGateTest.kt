package com.jesjobom.ararai.engine

import com.jesjobom.ararai.model.LocalModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

@OptIn(ExperimentalCoroutinesApi::class)
class LocalLlmRecoveryGateTest {
    @Test
    fun `requires consecutive cool low-memory-pressure samples`() = runTest {
        val samples = ArrayDeque(
            listOf(
                snapshot(thermal = 1, battery = 35f, pss = 2_000_000),
                snapshot(),
                snapshot(),
                snapshot(),
            ),
        )
        val gate = BoundedLocalLlmRecoveryGate(
            snapshot = { samples.removeFirst() },
            sampleIntervalMillis = 10,
            maximumWaitMillis = 100,
            stableSamplesRequired = 3,
        )

        val result = gate.awaitReady()

        assertTrue(result)
    }

    @Test
    fun `times out while memory pressure remains high`() = runTest {
        val gate = BoundedLocalLlmRecoveryGate(
            snapshot = { snapshot(lowMemory = true) },
            sampleIntervalMillis = 10,
            maximumWaitMillis = 30,
            stableSamplesRequired = 2,
        )

        assertFalse(gate.awaitReady())
    }

    @Test
    fun `model-specific memory headroom blocks an otherwise healthy sample`() = runTest {
        val gate = BoundedLocalLlmRecoveryGate(
            snapshot = { snapshot(availableMemoryKiB = 3_000_000) },
            sampleIntervalMillis = 10,
            maximumWaitMillis = 20,
            stableSamplesRequired = 1,
        )

        assertFalse(gate.awaitReady(LocalLlmRecoveryRequirement(minimumAvailableMemoryKiB = 3_500_000)))
    }

    @Test
    fun `model artifact size contributes bounded resident estimate and load headroom`() {
        val artifact = File.createTempFile("recovery-model", ".litertlm")
        RandomAccessFile(artifact, "rw").use { it.setLength(2L * 1_024L * 1_024L) }
        try {
            val requirement = LocalLlmRecoveryRequirement.forModel(
                LocalModel("fixture", "Fixture", artifact.absolutePath),
            )

            assertEquals(525_312L, requirement.minimumAvailableMemoryKiB)
        } finally {
            artifact.delete()
        }
    }

    @Test
    fun `normal battery temperature does not block when Android reports no thermal pressure`() = runTest {
        val gate = BoundedLocalLlmRecoveryGate(
            snapshot = { snapshot(battery = 32.4f) },
            sampleIntervalMillis = 10,
            maximumWaitMillis = 20,
            stableSamplesRequired = 1,
        )

        assertTrue(gate.awaitReady())
    }

    @Test
    fun `default recovery window remains active beyond three minutes and times out at ten`() = runTest {
        val gate = BoundedLocalLlmRecoveryGate(
            snapshot = { snapshot(lowMemory = true) },
        )
        val result = async { gate.awaitReady() }

        advanceTimeBy(3 * 60_000L)
        runCurrent()
        assertFalse(result.isCompleted)

        advanceTimeBy(7 * 60_000L)
        runCurrent()
        assertTrue(result.isCompleted)
        assertFalse(result.await())
    }

    @Test
    fun `cancellation interrupts the cooling wait`() = runTest {
        val gate = BoundedLocalLlmRecoveryGate(
            snapshot = { snapshot(battery = 40f) },
            sampleIntervalMillis = 10,
            maximumWaitMillis = 100,
            stableSamplesRequired = 2,
        )
        val job = backgroundScope.launch { gate.awaitReady() }
        runCurrent()

        job.cancel()
        advanceTimeBy(10)

        assertTrue(job.isCancelled)
    }

    private fun snapshot(
        thermal: Int = 0,
        battery: Float = 30f,
        pss: Int = 500_000,
        lowMemory: Boolean = false,
        availableMemoryKiB: Long = 4_000_000,
    ) = LocalLlmRecoverySnapshot(
        thermalStatus = thermal,
        batteryTemperatureCelsius = battery,
        processPssKiB = pss,
        availableMemoryKiB = availableMemoryKiB,
        totalMemoryKiB = 8_000_000,
        systemLowMemory = lowMemory,
    )
}
