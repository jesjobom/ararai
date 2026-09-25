package com.jesjobom.ararai.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal fun interface LocalLlmRecoveryGate {
    suspend fun awaitReady(): Boolean
}

internal data class LocalLlmRecoverySnapshot(
    val thermalStatus: Int?,
    val batteryTemperatureCelsius: Float?,
    val processPssKiB: Int?,
    val availableMemoryKiB: Long?,
    val totalMemoryKiB: Long?,
    val systemLowMemory: Boolean?,
)

internal class BoundedLocalLlmRecoveryGate(
    private val snapshot: () -> LocalLlmRecoverySnapshot,
    private val onSample: (LocalLlmRecoverySnapshot, Boolean) -> Unit = { _, _ -> },
    private val onFinished: (Boolean) -> Unit = {},
    private val sampleIntervalMillis: Long = SAMPLE_INTERVAL_MILLIS,
    private val maximumWaitMillis: Long = MAXIMUM_WAIT_MILLIS,
    private val stableSamplesRequired: Int = STABLE_SAMPLES_REQUIRED,
    private val maximumBatteryTemperatureCelsius: Float = MAXIMUM_BATTERY_TEMPERATURE_CELSIUS,
    private val maximumProcessPssKiB: Int = MAXIMUM_PROCESS_PSS_KIB,
    private val minimumAvailableMemoryFraction: Double = MINIMUM_AVAILABLE_MEMORY_FRACTION,
) : LocalLlmRecoveryGate {
    init {
        require(sampleIntervalMillis > 0)
        require(maximumWaitMillis >= sampleIntervalMillis)
        require(stableSamplesRequired > 0)
        require(maximumBatteryTemperatureCelsius > 0)
        require(maximumProcessPssKiB > 0)
        require(minimumAvailableMemoryFraction in 0.0..1.0)
    }

    override suspend fun awaitReady(): Boolean {
        val ready = withTimeoutOrNull(maximumWaitMillis) {
            var stableSamples = 0
            while (true) {
                val current = snapshot()
                val sampleReady = current.isReady()
                onSample(current, sampleReady)
                stableSamples = if (sampleReady) stableSamples + 1 else 0
                if (stableSamples >= stableSamplesRequired) return@withTimeoutOrNull true
                delay(sampleIntervalMillis)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } ?: false
        onFinished(ready)
        return ready
    }

    private fun LocalLlmRecoverySnapshot.isReady(): Boolean {
        val thermalReady = thermalStatus == null || thermalStatus <= THERMAL_STATUS_NONE
        val batteryReady = batteryTemperatureCelsius == null ||
            batteryTemperatureCelsius <= maximumBatteryTemperatureCelsius
        val processReady = processPssKiB == null || processPssKiB <= maximumProcessPssKiB
        val availableReady = if (availableMemoryKiB == null || totalMemoryKiB == null || totalMemoryKiB == 0L) {
            true
        } else {
            availableMemoryKiB.toDouble() / totalMemoryKiB >= minimumAvailableMemoryFraction
        }
        return thermalReady &&
            batteryReady &&
            processReady &&
            availableReady &&
            systemLowMemory != true
    }

    companion object {
        const val SAMPLE_INTERVAL_MILLIS = 5_000L
        const val MAXIMUM_WAIT_MILLIS = 3 * 60_000L
        const val STABLE_SAMPLES_REQUIRED = 3
        const val MAXIMUM_BATTERY_TEMPERATURE_CELSIUS = 32f
        const val MAXIMUM_PROCESS_PSS_KIB = 1_048_576
        const val MINIMUM_AVAILABLE_MEMORY_FRACTION = 0.20
        private const val THERMAL_STATUS_NONE = 0
    }
}

internal val ImmediateLocalLlmRecoveryGate = LocalLlmRecoveryGate { true }
