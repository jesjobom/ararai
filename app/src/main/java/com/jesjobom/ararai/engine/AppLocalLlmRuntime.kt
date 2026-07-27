package com.jesjobom.ararai.engine

/**
 * Application-scoped owner of the native local inference runtime.
 *
 * Chat and Benchmark must share this owner so only one potentially
 * multi-gigabyte native runtime tree can exist in the process.
 */
class AppLocalLlmRuntime(
    engineFactory: () -> LocalLlmEngine = { LiteRtLmLocalLlmEngine() },
) {
    val engine: LocalLlmEngine = engineFactory()
}
