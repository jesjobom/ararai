package com.jesjobom.ararai.widget.runtime

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRuntimeStandardLibraryInstrumentedTest {
    private val engine = QuickJsWidgetJavaScriptEngine()

    @Test
    fun currentLocalDateTimeReadsOneImmutableExecutionSnapshot() = runBlocking {
        val result = engine.call(
            source = """function inspect(runtime) {
  const first = runtime.currentLocalDateTime();
  const second = runtime.currentLocalDateTime();
  let mutationBlocked = false;
  try { first.day = 99; } catch (_) { mutationBlocked = true; }
  return {first:first,second:second,language:runtime.language,index:runtime.seededIndex(5),mutationBlocked:mutationBlocked};
}""",
            entrypoint = "inspect",
            argumentsJson = listOf(
                WidgetRuntimeContext(
                    "pt-BR",
                    "America/Sao_Paulo",
                    "2026-09-10T13:25:04-03:00",
                    42,
                ).toJson(WidgetRuntimeValue.entries.toSet()),
            ),
            limits = LIMITS,
        )

        assertEquals(
            WidgetScriptResult.Success(
                """{"first":{"day":1E+1,"hour":13,"iso":"2026-09-10T13:25:04-03:00","minute":25,"month":9,"second":4,"timezone":"America/Sao_Paulo","year":2026},"index":2,"language":"pt","mutationBlocked":true,"second":{"day":1E+1,"hour":13,"iso":"2026-09-10T13:25:04-03:00","minute":25,"month":9,"second":4,"timezone":"America/Sao_Paulo","year":2026}}""",
            ),
            result,
        )
    }

    @Test
    fun identicalSnapshotsReplayAndLaterExecutionReceivesFreshTime() = runBlocking {
        val source = "function inspect(runtime) { return runtime.currentLocalDateTime(); }"
        val firstContext = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-10T12:00:00-04:00", null)
            .toJson(setOf(WidgetRuntimeValue.Locale, WidgetRuntimeValue.LocalTime, WidgetRuntimeValue.Timezone))
        val laterContext = WidgetRuntimeContext("en-CA", "America/Toronto", "2026-09-11T12:00:00-04:00", null)
            .toJson(setOf(WidgetRuntimeValue.Locale, WidgetRuntimeValue.LocalTime, WidgetRuntimeValue.Timezone))

        val first = engine.call(source, "inspect", listOf(firstContext), LIMITS)
        val replay = engine.call(source, "inspect", listOf(firstContext), LIMITS)
        val later = engine.call(source, "inspect", listOf(laterContext), LIMITS)

        assertEquals(first, replay)
        assertTrue(first != later)
    }

    companion object {
        private val LIMITS = WidgetRequestedLimits(
            memoryBytes = WidgetRuntimePolicy.MAX_MEMORY_BYTES,
            stackBytes = WidgetRuntimePolicy.MAX_STACK_BYTES,
            executionMillis = WidgetRuntimePolicy.MAX_EXECUTION_MILLIS,
            maxToolCalls = WidgetRuntimePolicy.MAX_TOOL_CALLS,
            maxOutputBytes = WidgetRuntimePolicy.MAX_OUTPUT_BYTES,
        )
    }
}
