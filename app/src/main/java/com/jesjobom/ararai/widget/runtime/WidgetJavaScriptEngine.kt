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
    const val MAX_WRAPPER_BYTES = 8 * 1024

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
  const __araraiAlias = /^[a-z][a-z0-9_]{0,31}$/;
  const __araraiPath = /^[A-Za-z][A-Za-z0-9_]{0,63}(?:\.(?:[A-Za-z][A-Za-z0-9_]{0,63}|[0-9]{1,2})){0,4}$/;
  function __araraiOutcome(__araraiOutcomes, __araraiName) {
    if (!__araraiAlias.test(__araraiName) || !__araraiOutcomes ||
        typeof __araraiOutcomes !== 'object' ||
        !Object.prototype.hasOwnProperty.call(__araraiOutcomes, __araraiName)) return null;
    return __araraiOutcomes[__araraiName];
  }
  function __araraiSuccessful(__araraiResult) {
    return !!(__araraiResult && __araraiResult.status === 'success' &&
      __araraiResult.payload && typeof __araraiResult.payload === 'object' &&
      __araraiResult.payload.kind !== 'failure');
  }
  function __araraiRead(__araraiOutcomes, __araraiName, __araraiField) {
    const __araraiResult = __araraiOutcome(__araraiOutcomes, __araraiName);
    if (!__araraiSuccessful(__araraiResult) ||
        typeof __araraiField !== 'string' || !__araraiPath.test(__araraiField)) {
      throw new TypeError('invalid tool result access');
    }
    let __araraiValue = __araraiResult.payload;
    for (const __araraiSegment of __araraiField.split('.')) {
      if (__araraiValue === null || typeof __araraiValue !== 'object' ||
          !Object.prototype.hasOwnProperty.call(__araraiValue, __araraiSegment)) {
        throw new TypeError('missing tool result field');
      }
      __araraiValue = __araraiValue[__araraiSegment];
    }
    return __araraiValue;
  }
  __araraiRuntime.isSuccess = function(__araraiOutcomes, __araraiName) {
    return __araraiSuccessful(__araraiOutcome(__araraiOutcomes, __araraiName));
  };
  __araraiRuntime.get = function(__araraiOutcomes, __araraiName, __araraiField) {
    return __araraiRead(__araraiOutcomes, __araraiName, __araraiField);
  };
  __araraiRuntime.getArray = function(__araraiOutcomes, __araraiName, __araraiField) {
    const __araraiValue = __araraiRead(__araraiOutcomes, __araraiName, __araraiField);
    if (!Array.isArray(__araraiValue)) throw new TypeError('tool result field is not an array');
    return __araraiValue;
  };
  __araraiRuntime.pickArrayItem = function(__araraiOutcomes, __araraiName, __araraiField, __araraiSelection) {
    const __araraiMode = __araraiSelection === undefined ? 'seeded_random' : __araraiSelection;
    if (__araraiMode !== 'first' && __araraiMode !== 'seeded_random') throw new TypeError('invalid selection');
    if (!__araraiRuntime.isSuccess(__araraiOutcomes, __araraiName)) return null;
    const __araraiItems = __araraiRuntime.getArray(__araraiOutcomes, __araraiName, __araraiField);
    if (__araraiItems.length === 0) return null;
    if (__araraiMode === 'seeded_random' && typeof __araraiRuntime.seededIndex !== 'function') {
      throw new TypeError('seed unavailable');
    }
    const __araraiIndex = __araraiMode === 'first' ? 0 : __araraiRuntime.seededIndex(__araraiItems.length);
    return Object.freeze({item:__araraiItems[__araraiIndex],path:__araraiField+'.'+__araraiIndex});
  };
  __araraiRuntime.card = function(__araraiContent) {
    const __araraiValues = Array.isArray(__araraiContent) ? __araraiContent : [__araraiContent];
    if (__araraiValues.length === 0) throw new TypeError('empty card');
    const __araraiChildren = __araraiValues.map(function(__araraiValue) {
      if (typeof __araraiValue === 'string') return {type:'text',text:__araraiValue,tone:'neutral'};
      if (__araraiValue && typeof __araraiValue === 'object' && !Array.isArray(__araraiValue)) return __araraiValue;
      throw new TypeError('invalid card content');
    });
    return {type:'card',child:{type:'column',children:__araraiChildren}};
  };
  __araraiRuntime.linkFor = function(__araraiOutcomes, __araraiName, __araraiField, __araraiLabel) {
    const __araraiUrl = __araraiRead(__araraiOutcomes, __araraiName, __araraiField);
    if (typeof __araraiUrl !== 'string' || !__araraiUrl.startsWith('https://')) {
      throw new TypeError('tool result field is not an HTTPS URL');
    }
    return {type:'https_link',label:__araraiLabel,url:__araraiUrl,
      sourceAlias:__araraiName,sourceField:__araraiField};
  };
  __araraiRuntime.collectionCard = function(__araraiOutcomes, __araraiName, __araraiConfig) {
    if (!__araraiConfig || typeof __araraiConfig !== 'object' ||
        typeof __araraiConfig.items !== 'string' ||
        typeof __araraiConfig.title !== 'string' ||
        typeof __araraiConfig.fallback !== 'string') throw new TypeError('invalid card configuration');
    const __araraiPicked = __araraiRuntime.pickArrayItem(
      __araraiOutcomes, __araraiName, __araraiConfig.items, __araraiConfig.selection);
    if (!__araraiPicked) return __araraiRuntime.card(__araraiConfig.fallback);
    const __araraiTitle = __araraiRuntime.get(__araraiOutcomes, __araraiName,
      __araraiPicked.path+'.'+__araraiConfig.title);
    if (typeof __araraiTitle !== 'string') throw new TypeError('card title is not text');
    const __araraiContent = [__araraiTitle];
    const __araraiResult = __araraiOutcome(__araraiOutcomes, __araraiName);
    const __araraiDefaultBody = __araraiResult && __araraiResult.payload &&
      __araraiResult.payload.defaultBodyField === 'extract' ? 'extract' : undefined;
    const __araraiBodyField = Object.prototype.hasOwnProperty.call(__araraiConfig, 'body')
      ? __araraiConfig.body : __araraiDefaultBody;
    if (__araraiBodyField !== undefined && __araraiBodyField !== null) {
      if (typeof __araraiBodyField !== 'string') throw new TypeError('invalid body field');
      const __araraiBody = __araraiRuntime.get(__araraiOutcomes, __araraiName,
        __araraiPicked.path+'.'+__araraiBodyField);
      if (typeof __araraiBody !== 'string') throw new TypeError('card body is not text');
      __araraiContent.push(__araraiBody);
    }
    const __araraiUrlField = __araraiConfig.url === undefined &&
      __araraiPicked.item && typeof __araraiPicked.item.canonicalUrl === 'string' &&
      __araraiPicked.item.canonicalUrl.startsWith('https://')
      ? 'canonicalUrl' : __araraiConfig.url;
    if (__araraiUrlField !== undefined) {
      if (typeof __araraiUrlField !== 'string') throw new TypeError('invalid URL field');
      const __araraiLabel = __araraiConfig.linkLabel === undefined ? __araraiTitle : __araraiConfig.linkLabel;
      if (typeof __araraiLabel !== 'string') throw new TypeError('invalid link label');
      __araraiContent.push(__araraiRuntime.linkFor(__araraiOutcomes, __araraiName,
        __araraiPicked.path+'.'+__araraiUrlField, __araraiLabel));
    }
    return __araraiRuntime.card(__araraiContent);
  };
  return Object.freeze(__araraiRuntime);
}"""
}
