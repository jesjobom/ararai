package com.jesjobom.ararai.quickjs

import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

data class QuickJsSandboxLimits(
    val memoryBytes: Long = DEFAULT_MEMORY_BYTES,
    val stackBytes: Long = DEFAULT_STACK_BYTES,
    val executionMillis: Long = DEFAULT_EXECUTION_MILLIS,
    val maxSourceBytes: Int = DEFAULT_MAX_SOURCE_BYTES,
    val maxInputBytes: Int = DEFAULT_MAX_INPUT_BYTES,
    val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
) {
    init {
        require(memoryBytes in MIN_MEMORY_BYTES..MAX_MEMORY_BYTES) { "Invalid QuickJS memory limit" }
        require(stackBytes in MIN_STACK_BYTES..MAX_STACK_BYTES) { "Invalid QuickJS stack limit" }
        require(executionMillis in 1..MAX_EXECUTION_MILLIS) { "Invalid QuickJS execution limit" }
        require(maxSourceBytes in 1..MAX_SOURCE_BYTES) { "Invalid QuickJS source limit" }
        require(maxInputBytes in 1..MAX_INPUT_BYTES) { "Invalid QuickJS input limit" }
        require(maxOutputBytes in 1..MAX_OUTPUT_BYTES) { "Invalid QuickJS output limit" }
    }

    companion object {
        const val DEFAULT_MEMORY_BYTES = 8L * 1024 * 1024
        const val DEFAULT_STACK_BYTES = 512L * 1024
        const val DEFAULT_EXECUTION_MILLIS = 250L
        const val DEFAULT_MAX_SOURCE_BYTES = 32 * 1024
        const val DEFAULT_MAX_INPUT_BYTES = 64 * 1024
        const val DEFAULT_MAX_OUTPUT_BYTES = 64 * 1024

        const val MIN_MEMORY_BYTES = 1024L * 1024
        const val MAX_MEMORY_BYTES = 32L * 1024 * 1024
        const val MIN_STACK_BYTES = 64L * 1024
        const val MAX_STACK_BYTES = 1024L * 1024
        const val MAX_EXECUTION_MILLIS = 1_000L
        const val MAX_SOURCE_BYTES = 64 * 1024
        const val MAX_INPUT_BYTES = 256 * 1024
        const val MAX_OUTPUT_BYTES = 256 * 1024
    }
}

enum class QuickJsFailureCode {
    InvalidRequest,
    ScriptRejected,
    ResourceLimit,
    TimedOut,
    Cancelled,
    RuntimeUnavailable,
}

sealed interface QuickJsCallResult {
    data class Success(val outputJson: String) : QuickJsCallResult

    data class Failure(val code: QuickJsFailureCode) : QuickJsCallResult
}

class QuickJsSandbox(
    private val executor: Executor = runtimeExecutor,
) {
    suspend fun call(
        source: String,
        entrypoint: String,
        inputJson: String,
        limits: QuickJsSandboxLimits = QuickJsSandboxLimits(),
    ): QuickJsCallResult = callEncoded(source, entrypoint, inputJson, false, limits)

    suspend fun callWithArguments(
        source: String,
        entrypoint: String,
        argumentsJson: List<String>,
        limits: QuickJsSandboxLimits = QuickJsSandboxLimits(),
    ): QuickJsCallResult {
        if (argumentsJson.size !in 1..MAX_ARGUMENTS) {
            return QuickJsCallResult.Failure(QuickJsFailureCode.InvalidRequest)
        }
        return callEncoded(source, entrypoint, argumentsJson.joinToString(",", "[", "]"), true, limits)
    }

    private suspend fun callEncoded(
        source: String,
        entrypoint: String,
        inputJson: String,
        expandArguments: Boolean,
        limits: QuickJsSandboxLimits,
    ): QuickJsCallResult {
        val sourceBytes = source.toByteArray(StandardCharsets.UTF_8)
        val inputBytes = inputJson.toByteArray(StandardCharsets.UTF_8)
        if (
            sourceBytes.size > limits.maxSourceBytes ||
            inputBytes.size > limits.maxInputBytes ||
            !ENTRYPOINT_PATTERN.matches(entrypoint)
        ) {
            return QuickJsCallResult.Failure(QuickJsFailureCode.InvalidRequest)
        }

        val handle = runCatching {
            NativeQuickJsBridge.create(
                memoryBytes = limits.memoryBytes,
                stackBytes = limits.stackBytes,
                executionMillis = limits.executionMillis,
                maxOutputBytes = limits.maxOutputBytes,
            )
        }.getOrDefault(0L)
        if (handle == 0L) {
            return QuickJsCallResult.Failure(QuickJsFailureCode.RuntimeUnavailable)
        }

        return suspendCancellableCoroutine { continuation ->
            val session = NativeSession(handle)
            continuation.invokeOnCancellation { session.cancel() }
            try {
                executor.execute {
                    val result = try {
                        decodeResult(
                            NativeQuickJsBridge.evaluate(
                                handle = handle,
                                source = sourceBytes,
                                entrypoint = entrypoint.toByteArray(StandardCharsets.US_ASCII),
                                inputJson = inputBytes,
                                expandArguments = expandArguments,
                            ),
                        )
                    } catch (_: RuntimeException) {
                        QuickJsCallResult.Failure(QuickJsFailureCode.RuntimeUnavailable)
                    } finally {
                        session.close()
                    }
                    continuation.resume(result)
                }
            } catch (_: RejectedExecutionException) {
                session.close()
                continuation.resume(QuickJsCallResult.Failure(QuickJsFailureCode.RuntimeUnavailable))
            }
        }
    }

    private class NativeSession(handle: Long) {
        private val handle = AtomicLong(handle)

        fun cancel() {
            synchronized(this) {
                handle.get().takeIf { it != 0L }?.let(NativeQuickJsBridge::cancel)
            }
        }

        fun close() {
            synchronized(this) {
                handle.getAndSet(0L).takeIf { it != 0L }?.let(NativeQuickJsBridge::close)
            }
        }
    }

    private companion object {
        val ENTRYPOINT_PATTERN = Regex("[A-Za-z_$][A-Za-z0-9_$]{0,63}")
        const val MAX_ARGUMENTS = 4
        val runtimeExecutor: Executor = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "ararai-quickjs").apply { isDaemon = true }
        }

        fun decodeResult(encoded: ByteArray?): QuickJsCallResult {
            if (encoded == null) return QuickJsCallResult.Failure(QuickJsFailureCode.RuntimeUnavailable)
            val value = encoded.toString(StandardCharsets.UTF_8)
            val separator = value.indexOf('\n')
            val status = if (separator >= 0) value.substring(0, separator) else value
            val payload = if (separator >= 0) value.substring(separator + 1) else ""
            return when (status) {
                "ok" -> QuickJsCallResult.Success(payload)
                "invalid" -> QuickJsCallResult.Failure(QuickJsFailureCode.InvalidRequest)
                "script" -> QuickJsCallResult.Failure(QuickJsFailureCode.ScriptRejected)
                "resource" -> QuickJsCallResult.Failure(QuickJsFailureCode.ResourceLimit)
                "timeout" -> QuickJsCallResult.Failure(QuickJsFailureCode.TimedOut)
                "cancelled" -> QuickJsCallResult.Failure(QuickJsFailureCode.Cancelled)
                else -> QuickJsCallResult.Failure(QuickJsFailureCode.RuntimeUnavailable)
            }
        }
    }
}

private object NativeQuickJsBridge {
    init {
        System.loadLibrary("ararai_quickjs")
    }

    external fun create(
        memoryBytes: Long,
        stackBytes: Long,
        executionMillis: Long,
        maxOutputBytes: Int,
    ): Long

    external fun evaluate(
        handle: Long,
        source: ByteArray,
        entrypoint: ByteArray,
        inputJson: ByteArray,
        expandArguments: Boolean,
    ): ByteArray?

    external fun cancel(handle: Long)

    external fun close(handle: Long)
}
