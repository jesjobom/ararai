package com.jesjobom.ararai.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.util.Log

internal fun androidLocalLlmRecoveryGate(
    context: Context,
    telemetryEnabled: Boolean,
): LocalLlmRecoveryGate {
    val appContext = context.applicationContext
    val activityManager = appContext.getSystemService(ActivityManager::class.java)
    val powerManager = appContext.getSystemService(PowerManager::class.java)
    return BoundedLocalLlmRecoveryGate(
        snapshot = {
            val memory = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
            val batteryTemperature = appContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, UNKNOWN_BATTERY_TEMPERATURE)
                ?.takeUnless { it == UNKNOWN_BATTERY_TEMPERATURE }
                ?.div(BATTERY_TEMPERATURE_SCALE)
            LocalLlmRecoverySnapshot(
                thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    powerManager?.currentThermalStatus
                } else {
                    null
                },
                batteryTemperatureCelsius = batteryTemperature,
                processPssKiB = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss,
                availableMemoryKiB = memory.availMem / BYTES_PER_KIBIBYTE,
                totalMemoryKiB = memory.totalMem / BYTES_PER_KIBIBYTE,
                systemLowMemory = memory.lowMemory,
            )
        },
        onSample = { snapshot, ready ->
            if (telemetryEnabled) {
                Log.d(LOG_TAG, snapshot.toSanitizedLog("recovery_sample", ready))
            }
        },
        onFinished = { ready ->
            if (telemetryEnabled) {
                Log.d(LOG_TAG, "recovery event=recovery_finished ready=$ready")
            }
        },
    )
}

private fun LocalLlmRecoverySnapshot.toSanitizedLog(event: String, ready: Boolean): String = buildString {
    append("recovery event=")
    append(event)
    append(" ready=")
    append(ready)
    append(" thermalStatus=")
    append(thermalStatus ?: "none")
    append(" batteryTemperatureCelsius=")
    append(batteryTemperatureCelsius ?: "none")
    append(" processPssKiB=")
    append(processPssKiB ?: "none")
    append(" availableMemoryKiB=")
    append(availableMemoryKiB ?: "none")
    append(" totalMemoryKiB=")
    append(totalMemoryKiB ?: "none")
    append(" systemLowMemory=")
    append(systemLowMemory ?: "none")
}

private const val BYTES_PER_KIBIBYTE = 1_024L
private const val UNKNOWN_BATTERY_TEMPERATURE = Int.MIN_VALUE
private const val BATTERY_TEMPERATURE_SCALE = 10f
private const val LOG_TAG = "ArarAI.Recovery"
