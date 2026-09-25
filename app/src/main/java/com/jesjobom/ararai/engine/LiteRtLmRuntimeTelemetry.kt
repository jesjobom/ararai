@file:Suppress("TooGenericExceptionCaught")

package com.jesjobom.ararai.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Sanitized debug telemetry for the native LiteRT-LM lifecycle.
 *
 * The payload intentionally excludes model identifiers, prompts, generated content, tool
 * arguments, exception messages, and stack traces. Request and resource identifiers are
 * process-local counters/identity hashes used only to correlate lifecycle events.
 */
class LiteRtLmRuntimeTelemetry private constructor(
    private val enabled: Boolean,
    private val snapshotProvider: () -> RuntimeSnapshot,
) {
    private val nextRequestId = AtomicLong()
    private val activeRequests = AtomicInteger()

    internal fun beginRequest(): Long {
        val requestId = nextRequestId.incrementAndGet()
        val active = activeRequests.incrementAndGet()
        record(event = "generation_started", requestId = requestId, active = active, elapsedMillis = 0)
        if (active > 1) {
            record(
                event = "concurrent_generation_detected",
                requestId = requestId,
                active = active,
                elapsedMillis = 0,
                outcome = "warning",
            )
        }
        return requestId
    }

    internal fun finishRequest(
        requestId: Long,
        elapsedMillis: Long,
        outcome: String,
    ) {
        val active = activeRequests.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        record(
            event = "generation_finished",
            requestId = requestId,
            active = active,
            elapsedMillis = elapsedMillis,
            outcome = outcome,
        )
    }

    internal fun record(
        event: String,
        requestId: Long? = null,
        resourceId: Int? = null,
        elapsedMillis: Long? = null,
        outcome: String? = null,
        active: Int = activeRequests.get(),
    ) {
        if (!enabled) return
        val snapshot = runCatching(snapshotProvider).getOrDefault(RuntimeSnapshot())
        Log.d(
            LOG_TAG,
            buildString {
                append("runtime event=")
                append(event)
                append(" request=")
                append(requestId.orNone())
                append(" resource=")
                append(resourceId.orNone())
                append(" active=")
                append(active)
                append(" elapsedMillis=")
                append(elapsedMillis.orNone())
                append(" outcome=")
                append(outcome.orNone())
                append(" threads=")
                append(snapshot.threadCount.orNone())
                append(" rssKiB=")
                append(snapshot.rssKiB.orNone())
                append(" swapKiB=")
                append(snapshot.swapKiB.orNone())
                append(" pssKiB=")
                append(snapshot.pssKiB.orNone())
                append(" nativePssKiB=")
                append(snapshot.nativePssKiB.orNone())
                append(" nativeHeapKiB=")
                append(snapshot.nativeHeapKiB.orNone())
                append(" javaHeapKiB=")
                append(snapshot.javaHeapKiB.orNone())
                append(" availableMemoryKiB=")
                append(snapshot.availableMemoryKiB.orNone())
                append(" systemLowMemory=")
                append(snapshot.systemLowMemory.orNone())
                append(" thermalStatus=")
                append(snapshot.thermalStatus.orNone())
            },
        )
    }

    internal fun recordResourceOperation(
        eventPrefix: String,
        resource: Any,
        block: () -> Unit,
    ) {
        if (!enabled) {
            block()
            return
        }
        val resourceId = System.identityHashCode(resource)
        val startedAt = System.nanoTime()
        record(event = "${eventPrefix}_started", resourceId = resourceId, elapsedMillis = 0)
        try {
            block()
            record(
                event = "${eventPrefix}_finished",
                resourceId = resourceId,
                elapsedMillis = startedAt.elapsedMillis(),
                outcome = "success",
            )
        } catch (error: Throwable) {
            record(
                event = "${eventPrefix}_finished",
                resourceId = resourceId,
                elapsedMillis = startedAt.elapsedMillis(),
                outcome = "failed",
            )
            throw error
        }
    }

    private data class RuntimeSnapshot(
        val threadCount: Int? = null,
        val rssKiB: Long? = null,
        val swapKiB: Long? = null,
        val pssKiB: Int? = null,
        val nativePssKiB: Int? = null,
        val nativeHeapKiB: Long? = null,
        val javaHeapKiB: Long? = null,
        val availableMemoryKiB: Long? = null,
        val systemLowMemory: Boolean? = null,
        val thermalStatus: Int? = null,
    )

    companion object {
        fun disabled(): LiteRtLmRuntimeTelemetry = LiteRtLmRuntimeTelemetry(
            enabled = false,
            snapshotProvider = { RuntimeSnapshot() },
        )

        fun android(
            context: Context,
            enabled: Boolean,
        ): LiteRtLmRuntimeTelemetry {
            val appContext = context.applicationContext
            return LiteRtLmRuntimeTelemetry(enabled = enabled) {
                val status = readProcessStatus()
                val processMemory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                val systemMemory = ActivityManager.MemoryInfo().also { memoryInfo ->
                    appContext.getSystemService(ActivityManager::class.java)?.getMemoryInfo(memoryInfo)
                }
                val runtime = Runtime.getRuntime()
                RuntimeSnapshot(
                    threadCount = status["Threads"]?.toIntOrNull(),
                    rssKiB = status["VmRSS"]?.toLongOrNull(),
                    swapKiB = status["VmSwap"]?.toLongOrNull(),
                    pssKiB = processMemory.totalPss,
                    nativePssKiB = processMemory.nativePss,
                    nativeHeapKiB = Debug.getNativeHeapAllocatedSize() / BYTES_PER_KIBIBYTE,
                    javaHeapKiB = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_KIBIBYTE,
                    availableMemoryKiB = systemMemory.availMem / BYTES_PER_KIBIBYTE,
                    systemLowMemory = systemMemory.lowMemory,
                    thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appContext.getSystemService(PowerManager::class.java)?.currentThermalStatus
                    } else {
                        null
                    },
                )
            }
        }

        private fun readProcessStatus(): Map<String, String> = runCatching {
            File("/proc/self/status").useLines { lines ->
                lines.mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) return@mapNotNull null
                    val key = line.substring(0, separator)
                    if (key !in STATUS_KEYS) return@mapNotNull null
                    val value = line.substring(separator + 1).trim().substringBefore(' ')
                    key to value
                }.toMap()
            }
        }.getOrDefault(emptyMap())

        private val STATUS_KEYS = setOf("Threads", "VmRSS", "VmSwap")
        private const val BYTES_PER_KIBIBYTE = 1_024L
        private const val LOG_TAG = "ArarAI.LiteRtLm"
    }
}

private fun Long.elapsedMillis(): Long = (System.nanoTime() - this) / 1_000_000

private fun Any?.orNone(): Any = this ?: "none"
