package com.jesjobom.ararai.widget.managed

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Android implementation of the device-state gate used before starting a
 * background widget authoring job. Severe-or-worse thermal status or memory
 * pressure defers the job instead of forcing on-device generation.
 */
internal class AndroidWidgetAuthoringDeviceState(
    context: Context,
    private val minimumAvailableMemoryFraction: Double = MINIMUM_AVAILABLE_MEMORY_FRACTION,
) : WidgetAuthoringDeviceState {
    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)
    private val powerManager = appContext.getSystemService(android.os.PowerManager::class.java)

    override fun evaluate(): WidgetAuthoringDeviceEvaluation {
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.currentThermalStatus
        } else {
            null
        }
        if (thermalStatus != null && thermalStatus >= THERMAL_STATUS_SEVERE) {
            return WidgetAuthoringDeviceEvaluation(
                acceptable = false,
                deferralReason = WidgetAuthoringDeferralReason.ThermalState,
            )
        }
        val memory = ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager?.getMemoryInfo(memoryInfo)
        }
        val minimumAvailableMemory = memory.totalMem * minimumAvailableMemoryFraction
        if (memory.lowMemory || memory.availMem < minimumAvailableMemory) {
            return WidgetAuthoringDeviceEvaluation(
                acceptable = false,
                deferralReason = WidgetAuthoringDeferralReason.LowMemory,
            )
        }
        return WidgetAuthoringDeviceEvaluation.Acceptable
    }

    companion object {
        private const val THERMAL_STATUS_SEVERE = android.os.PowerManager.THERMAL_STATUS_SEVERE
        private const val MINIMUM_AVAILABLE_MEMORY_FRACTION = 0.25
    }
}