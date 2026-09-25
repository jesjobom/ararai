package com.jesjobom.ararai.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    ) = LocalLlmRecoverySnapshot(
        thermalStatus = thermal,
        batteryTemperatureCelsius = battery,
        processPssKiB = pss,
        availableMemoryKiB = 4_000_000,
        totalMemoryKiB = 8_000_000,
        systemLowMemory = lowMemory,
    )
}
