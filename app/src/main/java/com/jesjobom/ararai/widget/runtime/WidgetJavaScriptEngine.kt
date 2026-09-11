package com.jesjobom.ararai.widget.runtime

import com.jesjobom.ararai.quickjs.QuickJsCallResult
import com.jesjobom.ararai.quickjs.QuickJsFailureCode
import com.jesjobom.ararai.quickjs.QuickJsSandbox
import com.jesjobom.ararai.quickjs.QuickJsSandboxLimits

internal sealed interface WidgetScriptResult {
    data class Success(val outputJson: String) : WidgetScriptResult
    data class Failure(val code: WidgetRuntimeFailureCode) : WidgetScriptResult
}

internal fun interface WidgetJavaScriptEngine {
    suspend fun call(
        source: String,
        entrypoint: String,
        argumentsJson: List<String>,
        limits: WidgetRequestedLimits,
    ): WidgetScriptResult
}

internal class QuickJsWidgetJavaScriptEngine(
    private val sandbox: QuickJsSandbox = QuickJsSandbox(),
) : WidgetJavaScriptEngine {
    override suspend fun call(
        source: String,
        entrypoint: String,
        argumentsJson: List<String>,
        limits: WidgetRequestedLimits,
    ): WidgetScriptResult = when (
        val result = sandbox.callWithArguments(
            source = WidgetRuntimeStandardLibrary.wrap(source, entrypoint),
            entrypoint = WidgetRuntimeStandardLibrary.WRAPPER_ENTRYPOINT,
            argumentsJson = argumentsJson,
            limits = QuickJsSandboxLimits(
                memoryBytes = limits.memoryBytes,
                stackBytes = limits.stackBytes,
                executionMillis = limits.executionMillis,
                maxSourceBytes = WidgetRuntimePolicy.MAX_SOURCE_BYTES + WidgetRuntimeStandardLibrary.MAX_WRAPPER_BYTES,
                maxInputBytes = WidgetRuntimePolicy.MAX_JSON_BYTES,
                maxOutputBytes = limits.maxOutputBytes,
            ),
        )
    ) {
        is QuickJsCallResult.Success -> runCatching {
            WidgetScriptResult.Success(StrictJson.canonical(StrictJson.parse(result.outputJson)))
        }.getOrElse { WidgetScriptResult.Failure(WidgetRuntimeFailureCode.ScriptError) }
        is QuickJsCallResult.Failure -> WidgetScriptResult.Failure(result.code.toWidgetFailure())
    }

    private fun QuickJsFailureCode.toWidgetFailure(): WidgetRuntimeFailureCode = when (this) {
        QuickJsFailureCode.InvalidRequest,
        QuickJsFailureCode.ScriptRejected,
        -> WidgetRuntimeFailureCode.ScriptError
        QuickJsFailureCode.ResourceLimit,
        QuickJsFailureCode.TimedOut,
        -> WidgetRuntimeFailureCode.ResourceLimit
        QuickJsFailureCode.Cancelled -> WidgetRuntimeFailureCode.Cancelled
        QuickJsFailureCode.RuntimeUnavailable -> WidgetRuntimeFailureCode.RuntimeUnavailable
    }
}

internal object WidgetRuntimeStandardLibrary {
    const val WRAPPER_ENTRYPOINT = "__araraiInvokeWithRuntime"
    const val MAX_WRAPPER_BYTES = 4 * 1024

    fun wrap(source: String, entrypoint: String): String {
        require(ENTRYPOINT_PATTERN.matches(entrypoint))
        require(!RESERVED_IDENTIFIER.containsMatchIn(source))
        return buildString(source.length + STDLIB.length + 256) {
            append(STDLIB)
            append('\n')
            append(source)
            append('\n')
            append("function ")
            append(WRAPPER_ENTRYPOINT)
            append("(__araraiSnapshot) {\n")
            append("  const __araraiArgs = Array.prototype.slice.call(arguments, 1);\n")
            append("  __araraiArgs.unshift(__araraiCreateRuntime(__araraiSnapshot));\n")
            append("  return ")
            append(entrypoint)
            append(".apply(undefined, __araraiArgs);\n")
            append("}\n")
        }.also { require(it.utf8Size() <= source.utf8Size() + MAX_WRAPPER_BYTES) }
    }

    private val RESERVED_IDENTIFIER = Regex("\\b__ararai[A-Za-z0-9_$]*")

    private const val STDLIB = """function __araraiCreateRuntime(__araraiSnapshot) {
  'use strict';
  const __araraiRuntime = {};
  for (const __araraiKey of Object.keys(__araraiSnapshot)) {
    __araraiRuntime[__araraiKey] = __araraiSnapshot[__araraiKey];
  }
  if (typeof __araraiSnapshot.localTime === 'string') {
    const __araraiDateTime = Object.freeze({
      iso: __araraiSnapshot.localTime,
      year: __araraiSnapshot.localYear,
      month: __araraiSnapshot.localMonth,
      day: __araraiSnapshot.localDay,
      hour: __araraiSnapshot.localHour,
      minute: __araraiSnapshot.localMinute,
      second: __araraiSnapshot.localSecond,
      timezone: __araraiSnapshot.timezone
    });
    __araraiRuntime.currentLocalDateTime = function() { return __araraiDateTime; };
  }
  if (typeof __araraiSnapshot.seed === 'number') {
    __araraiRuntime.seededIndex = function(__araraiLength) {
      if (!Number.isInteger(__araraiLength) || __araraiLength <= 0) throw new RangeError('invalid length');
      return Math.abs(__araraiSnapshot.seed) % __araraiLength;
    };
  }
  return Object.freeze(__araraiRuntime);
}"""
}
