package com.jesjobom.ararai.quickjs

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

@RunWith(AndroidJUnit4::class)
class QuickJsSandboxInstrumentedTest {
    private val sandbox = QuickJsSandbox()

    @Test
    fun executesControlFlowCollectionsAndUnicodeDataOnlyArguments() = runBlocking {
        val result = sandbox.callWithArguments(
            source = """
                function compute(context, values) {
                  let total = 0;
                  for (const value of values) {
                    if (value > context.minimum) total += value;
                  }
                  return { label: context.label, total, doubled: values.map(x => x * 2) };
                }
            """.trimIndent(),
            entrypoint = "compute",
            argumentsJson = listOf("{\"minimum\":2,\"label\":\"São Paulo 🦜\"}", "[1,3,5]"),
        )

        assertEquals(
            QuickJsCallResult.Success("{\"label\":\"São Paulo 🦜\",\"total\":8,\"doubled\":[2,6,10]}"),
            result,
        )
    }

    @Test
    fun removesHostClockRandomDynamicEvaluationAndPlatformGlobals() = runBlocking {
        val result = sandbox.call(
            source = """
                function inspect(input) {
                  return {
                    eval: typeof eval,
                    functionGlobal: typeof Function,
                    constructor: typeof (() => {}).constructor,
                    date: typeof Date,
                    random: typeof Math.random,
                    wasm: typeof WebAssembly,
                    fetch: typeof fetch,
                    require: typeof require,
                    process: typeof process,
                    java: typeof Java,
                    packages: typeof Packages,
                    document: typeof document,
                    storage: typeof localStorage
                  };
                }
            """.trimIndent(),
            entrypoint = "inspect",
            inputJson = "{}",
        )

        val output = (result as QuickJsCallResult.Success).outputJson
        assertEquals(13, Regex("undefined").findAll(output).count())
    }

    @Test
    fun blocksPrototypeTraversalDynamicEvaluationAndDirectHostAccessAttempts() = runBlocking {
        val result = sandbox.call(
            source = """
                function inspect(input) {
                  const attempts = [
                    () => eval('1'),
                    () => Function('return 1')(),
                    () => (() => {}).constructor('return globalThis')(),
                    () => ({}).constructor.constructor('return globalThis')(),
                    () => Object.getPrototypeOf(() => {}).constructor('return globalThis')(),
                    () => fetch('https://forbidden.invalid'),
                    () => new XMLHttpRequest(),
                    () => new WebSocket('wss://forbidden.invalid'),
                    () => require('fs').readFileSync('/etc/passwd'),
                    () => process.env,
                    () => Deno.readTextFile('/etc/passwd'),
                    () => Bun.file('/etc/passwd'),
                    () => Java.type('java.lang.System').getenv(),
                    () => Packages.java.lang.System.getenv(),
                    () => localStorage.getItem('credential'),
                    () => sessionStorage.getItem('credential'),
                    () => document.cookie,
                  ];
                  let blocked = 0;
                  for (const attempt of attempts) {
                    try {
                      if (attempt() === undefined) blocked += 1;
                    } catch (_) {
                      blocked += 1;
                    }
                  }
                  return {blocked, total: attempts.length};
                }
            """.trimIndent(),
            entrypoint = "inspect",
            inputJson = "{}",
        )

        assertEquals(QuickJsCallResult.Success("{\"blocked\":17,\"total\":17}"), result)
    }

    @Test
    fun rejectsStaticAndDynamicImportsWithoutModuleLoader() = runBlocking {
        val staticImport = sandbox.call("import value from 'host'; function run(x){ return x; }", "run", "{}")
        val dynamicImport = sandbox.call("function run(x){ return import('host'); }", "run", "{}")

        assertTrue(staticImport is QuickJsCallResult.Failure)
        assertTrue(dynamicImport is QuickJsCallResult.Failure)
    }

    @Test
    fun deepFreezesInputsAndCreatesFreshIsolateForEveryCall() = runBlocking {
        val source = """
            var counter = 0;
            function run(input) {
              counter += 1;
              let mutationBlocked = false;
              try { input.value = 99; } catch (_) { mutationBlocked = true; }
              return { counter, value: input.value, mutationBlocked };
            }
        """.trimIndent()

        val first = sandbox.call(source, "run", "{\"value\":1}")
        val second = sandbox.call(source, "run", "{\"value\":1}")

        val expected = QuickJsCallResult.Success("{\"counter\":1,\"value\":1,\"mutationBlocked\":true}")
        assertEquals(expected, first)
        assertEquals(expected, second)
    }

    @Test
    fun identicalExplicitInputsReplayIdentically() = runBlocking {
        val source = "function run(context) { return {localTime: context.localTime, seed: context.seed}; }"

        val first = sandbox.call(source, "run", "{\"localTime\":\"2026-09-04T06:00:00\",\"seed\":42}")
        val replay = sandbox.call(source, "run", "{\"localTime\":\"2026-09-04T06:00:00\",\"seed\":42}")
        val changed = sandbox.call(source, "run", "{\"localTime\":\"2026-09-04T07:00:00\",\"seed\":43}")

        assertEquals(first, replay)
        assertTrue(first != changed)
    }

    @Test
    fun rejectsMalformedJsonSourceEntrypointAndOversizedOutput() = runBlocking {
        assertEquals(
            QuickJsCallResult.Failure(QuickJsFailureCode.InvalidRequest),
            sandbox.call("function run(x){return x}", "../run", "{}"),
        )
        assertEquals(
            QuickJsCallResult.Failure(QuickJsFailureCode.InvalidRequest),
            sandbox.call("function run(x){return x}", "run", "{"),
        )
        val outputBomb = sandbox.call(
            "function run(x){ return 'x'.repeat(2048); }",
            "run",
            "{}",
            QuickJsSandboxLimits(maxOutputBytes = 128),
        )
        assertEquals(QuickJsCallResult.Failure(QuickJsFailureCode.ResourceLimit), outputBomb)
    }

    @Test
    fun interruptsInfiniteLoopWithinOuterBoundAndRecovers() = runBlocking {
        val elapsed = measureTime {
            val result = withTimeout(2.seconds) {
                sandbox.call(
                    "function run(input) { while (true) {} }",
                    "run",
                    "{}",
                    QuickJsSandboxLimits(executionMillis = 50),
                )
            }
            assertEquals(QuickJsCallResult.Failure(QuickJsFailureCode.TimedOut), result)
        }
        assertTrue(elapsed < 2.seconds)
        assertEquals(
            QuickJsCallResult.Success("2"),
            sandbox.call("function run(x){return x + 1}", "run", "1"),
        )
    }

    @Test
    fun boundsRecursionAndAllocationPressureWithoutCrashingHost() = runBlocking {
        val recursion = sandbox.call("function run(x){ return run(x); }", "run", "{}")
        val allocation = sandbox.call(
            "function run(x){ const a=[]; while(true) a.push('x'.repeat(4096)); }",
            "run",
            "{}",
            QuickJsSandboxLimits(memoryBytes = QuickJsSandboxLimits.MIN_MEMORY_BYTES),
        )

        assertEquals(QuickJsCallResult.Failure(QuickJsFailureCode.ResourceLimit), recursion)
        assertTrue(
            allocation == QuickJsCallResult.Failure(QuickJsFailureCode.ResourceLimit) ||
                allocation == QuickJsCallResult.Failure(QuickJsFailureCode.TimedOut),
        )
    }

    @Test
    fun cancellationInterruptsNativeExecutionAndDiscardsLateResult() = runBlocking {
        withTimeout(2.seconds) {
            val call = async {
                sandbox.call("function run(input) { while (true) {} }", "run", "{}")
            }
            delay(25)
            call.cancelAndJoin()
        }
        assertEquals(
            QuickJsCallResult.Success("true"),
            sandbox.call("function run(x){return true}", "run", "{}"),
        )
    }

    @Test
    fun repeatedCreationAndFailureDoesNotRetainGlobalState() = runBlocking {
        repeat(50) { index ->
            val source = if (index % 2 == 0) {
                "var retained = $index; function run(x){return retained}"
            } else {
                "function run(x){throw new Error('credential=secret')}"
            }
            val result = sandbox.call(source, "run", "{}")
            if (index % 2 == 0) {
                assertEquals(QuickJsCallResult.Success(index.toString()), result)
            } else {
                assertEquals(QuickJsCallResult.Failure(QuickJsFailureCode.ScriptRejected), result)
                assertTrue(result.toString().contains("credential").not())
            }
        }
    }
}
