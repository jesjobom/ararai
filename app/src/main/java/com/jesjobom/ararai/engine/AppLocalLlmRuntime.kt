package com.jesjobom.ararai.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application-scoped owner of the native local inference runtime.
 *
 * Chat and Benchmark must share this owner so only one potentially
 * multi-gigabyte native runtime tree can exist in the process.
 */
class AppLocalLlmRuntime(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    engineFactory: () -> LocalLlmEngine = { LiteRtLmLocalLlmEngine() },
) : Closeable {
    val engine: LocalLlmEngine = engineFactory()
    private val lifecycleJob = SupervisorJob()
    private val scope = CoroutineScope(scope.coroutineContext + lifecycleJob)
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.launch {
            try {
                engine.unload()
            } finally {
                lifecycleJob.cancel()
            }
        }
    }
}
