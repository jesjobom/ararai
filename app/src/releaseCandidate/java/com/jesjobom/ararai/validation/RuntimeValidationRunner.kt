package com.jesjobom.ararai.validation

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import com.google.gson.JsonObject
import com.jesjobom.ararai.BuildConfig
import com.jesjobom.ararai.quickjs.QuickJsCallResult
import com.jesjobom.ararai.quickjs.QuickJsFailureCode
import com.jesjobom.ararai.quickjs.QuickJsSandbox
import com.jesjobom.ararai.quickjs.QuickJsSandboxLimits
import com.jesjobom.ararai.widget.runtime.QuickJsWidgetJavaScriptEngine
import com.jesjobom.ararai.widget.runtime.WidgetExecutionGrant
import com.jesjobom.ararai.widget.runtime.WidgetExecutionResult
import com.jesjobom.ararai.widget.runtime.WidgetPresentationCapability
import com.jesjobom.ararai.widget.runtime.WidgetPresentationNode
import com.jesjobom.ararai.widget.runtime.WidgetProgram
import com.jesjobom.ararai.widget.runtime.WidgetProgramParser
import com.jesjobom.ararai.widget.runtime.WidgetProgramValidationResult
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeContext
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeCoordinator
import com.jesjobom.ararai.widget.runtime.WidgetRuntimeValue
import com.jesjobom.ararai.widget.runtime.WidgetToolCapability
import com.jesjobom.ararai.widget.runtime.WidgetToolOutcome
import com.jesjobom.ararai.widget.runtime.sha256
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile

@Suppress("LargeClass", "LongMethod", "MagicNumber", "TooManyFunctions")
internal class RuntimeValidationRunner(
    private val context: Context,
    private val sandbox: QuickJsSandbox = QuickJsSandbox(),
) {
    suspend fun run(): RuntimeValidationReport {
        val validationCases = cases()
        val results = mutableListOf<RuntimeValidationCaseResult>()
        for (validationCase in validationCases) {
            currentCoroutineContext().ensureActive()
            results += runCase(validationCase)
        }
        return RuntimeValidationReport(environment(), results)
    }

    private fun cases(): List<ValidationCase> = listOf(
        ValidationCase("data_only_fixture", ::dataOnlyFixture),
        ValidationCase("forbidden_globals", ::forbiddenGlobals),
        ValidationCase("prototype_host_access", ::prototypeHostAccess),
        ValidationCase("imports_rejected", ::importsRejected),
        ValidationCase("fresh_isolates_immutable_inputs", ::freshIsolatesAndImmutableInputs),
        ValidationCase("deterministic_explicit_inputs", ::deterministicExplicitInputs),
        ValidationCase("malformed_requests_bounded_output", ::malformedRequestsAndBoundedOutput),
        ValidationCase("infinite_loop_timeout_recovery", ::infiniteLoopTimeoutAndRecovery),
        ValidationCase("recursion_allocation_bounds", ::recursionAndAllocationBounds),
        ValidationCase("caller_cancellation_recovery", ::callerCancellationAndRecovery),
        ValidationCase("repeated_isolate_recovery", ::repeatedIsolateRecovery),
        ValidationCase("two_phase_widget_runtime", ::twoPhaseWidgetRuntime),
    )

    private suspend fun runCase(validationCase: ValidationCase): RuntimeValidationCaseResult = coroutineScope {
        val pssBefore = Debug.getPss().coerceAtLeast(0)
        val peakPss = AtomicLong(pssBefore)
        val sampler = launch {
            while (isActive) {
                peakPss.accumulateAndGet(Debug.getPss().coerceAtLeast(0), ::maxOf)
                delay(MEMORY_SAMPLE_MILLIS)
            }
        }
        val started = SystemClock.elapsedRealtime()
        val outcome = try {
            withTimeout(CASE_TIMEOUT_MILLIS) { validationCase.execute() }
            OUTCOME_PASS
        } catch (_: ValidationAssertionFailure) {
            OUTCOME_UNEXPECTED
        } catch (_: TimeoutCancellationException) {
            OUTCOME_TIMEOUT
        } catch (_: LinkageError) {
            OUTCOME_RUNTIME_UNAVAILABLE
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            OUTCOME_INTERNAL_FAILURE
        } finally {
            sampler.cancelAndJoin()
        }
        val duration = SystemClock.elapsedRealtime() - started
        val pssAfter = Debug.getPss().coerceAtLeast(0)
        peakPss.accumulateAndGet(pssAfter, ::maxOf)
        RuntimeValidationCaseResult(
            id = validationCase.id,
            passed = outcome == OUTCOME_PASS,
            outcome = outcome,
            durationMillis = duration,
            pssBeforeKb = pssBefore,
            pssPeakKb = peakPss.get(),
            pssAfterKb = pssAfter,
        )
    }

    private suspend fun dataOnlyFixture() {
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
        expect(
            result == QuickJsCallResult.Success(
                "{\"label\":\"São Paulo 🦜\",\"total\":8,\"doubled\":[2,6,10]}",
            ),
        )
    }

    private suspend fun forbiddenGlobals() {
        val result = sandbox.call(
            source = """
                function inspect(input) {
                  return {
                    eval: typeof eval, functionGlobal: typeof Function,
                    constructor: typeof (() => {}).constructor, date: typeof Date,
                    random: typeof Math.random, wasm: typeof WebAssembly,
                    fetch: typeof fetch, require: typeof require, process: typeof process,
                    java: typeof Java, packages: typeof Packages,
                    document: typeof document, storage: typeof localStorage
                  };
                }
            """.trimIndent(),
            entrypoint = "inspect",
            inputJson = "{}",
        )
        val output = (result as? QuickJsCallResult.Success)?.outputJson.orEmpty()
        expect(Regex("undefined").findAll(output).count() == EXPECTED_FORBIDDEN_GLOBALS)
    }

    private suspend fun prototypeHostAccess() {
        val result = sandbox.call(
            source = """
                function inspect(input) {
                  const attempts = [
                    () => eval('1'), () => Function('return 1')(),
                    () => (() => {}).constructor('return globalThis')(),
                    () => ({}).constructor.constructor('return globalThis')(),
                    () => Object.getPrototypeOf(() => {}).constructor('return globalThis')(),
                    () => fetch('https://forbidden.invalid'), () => new XMLHttpRequest(),
                    () => new WebSocket('wss://forbidden.invalid'),
                    () => require('fs').readFileSync('/etc/passwd'), () => process.env,
                    () => Deno.readTextFile('/etc/passwd'), () => Bun.file('/etc/passwd'),
                    () => Java.type('java.lang.System').getenv(),
                    () => Packages.java.lang.System.getenv(),
                    () => localStorage.getItem('credential'),
                    () => sessionStorage.getItem('credential'), () => document.cookie,
                  ];
                  let blocked = 0;
                  for (const attempt of attempts) {
                    try { if (attempt() === undefined) blocked += 1; } catch (_) { blocked += 1; }
                  }
                  return {blocked, total: attempts.length};
                }
            """.trimIndent(),
            entrypoint = "inspect",
            inputJson = "{}",
        )
        expect(result == QuickJsCallResult.Success("{\"blocked\":17,\"total\":17}"))
    }

    private suspend fun importsRejected() {
        val staticImport = sandbox.call("import value from 'host'; function run(x){ return x; }", "run", "{}")
        val dynamicImport = sandbox.call("function run(x){ return import('host'); }", "run", "{}")
        expect(staticImport is QuickJsCallResult.Failure && dynamicImport is QuickJsCallResult.Failure)
    }

    private suspend fun freshIsolatesAndImmutableInputs() {
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
        expect(first == expected && second == expected)
    }

    private suspend fun deterministicExplicitInputs() {
        val source = "function run(context) { return {localTime: context.localTime, seed: context.seed}; }"
        val fixed = "{\"localTime\":\"2026-09-04T06:00:00\",\"seed\":42}"
        val changed = "{\"localTime\":\"2026-09-04T07:00:00\",\"seed\":43}"
        val first = sandbox.call(source, "run", fixed)
        val replay = sandbox.call(source, "run", fixed)
        val different = sandbox.call(source, "run", changed)
        expect(first == replay && first != different)
    }

    private suspend fun malformedRequestsAndBoundedOutput() {
        val invalidEntrypoint = sandbox.call("function run(x){return x}", "../run", "{}")
        val malformedJson = sandbox.call("function run(x){return x}", "run", "{")
        val outputBomb = sandbox.call(
            "function run(x){ return 'x'.repeat(2048); }",
            "run",
            "{}",
            QuickJsSandboxLimits(maxOutputBytes = 128),
        )
        expect(invalidEntrypoint == QuickJsCallResult.Failure(QuickJsFailureCode.InvalidRequest))
        expect(malformedJson == QuickJsCallResult.Failure(QuickJsFailureCode.InvalidRequest))
        expect(outputBomb == QuickJsCallResult.Failure(QuickJsFailureCode.ResourceLimit))
    }

    private suspend fun infiniteLoopTimeoutAndRecovery() {
        val started = SystemClock.elapsedRealtime()
        val timedOut = sandbox.call(
            "function run(input) { while (true) {} }",
            "run",
            "{}",
            QuickJsSandboxLimits(executionMillis = QUICKJS_TIMEOUT_MILLIS),
        )
        val elapsed = SystemClock.elapsedRealtime() - started
        expect(timedOut == QuickJsCallResult.Failure(QuickJsFailureCode.TimedOut))
        expect(elapsed <= MAX_TERMINATION_MILLIS)
        expect(sandbox.call("function run(x){return x + 1}", "run", "1") == QuickJsCallResult.Success("2"))
    }

    private suspend fun recursionAndAllocationBounds() {
        val recursion = sandbox.call("function run(x){ return run(x); }", "run", "{}")
        val allocation = sandbox.call(
            "function run(x){ const a=[]; while(true) a.push('x'.repeat(4096)); }",
            "run",
            "{}",
            QuickJsSandboxLimits(memoryBytes = QuickJsSandboxLimits.MIN_MEMORY_BYTES),
        )
        expect(recursion == QuickJsCallResult.Failure(QuickJsFailureCode.ResourceLimit))
        expect(
            allocation == QuickJsCallResult.Failure(QuickJsFailureCode.ResourceLimit) ||
                allocation == QuickJsCallResult.Failure(QuickJsFailureCode.TimedOut),
        )
    }

    private suspend fun callerCancellationAndRecovery() {
        val started = SystemClock.elapsedRealtime()
        coroutineScope {
            val call = async {
                sandbox.call("function run(input) { while (true) {} }", "run", "{}")
            }
            delay(CANCELLATION_DELAY_MILLIS)
            call.cancelAndJoin()
        }
        expect(SystemClock.elapsedRealtime() - started <= MAX_TERMINATION_MILLIS)
        expect(
            sandbox.call("function run(x){return true}", "run", "{}") == QuickJsCallResult.Success("true"),
        )
    }

    private suspend fun repeatedIsolateRecovery() {
        repeat(REPEATED_ISOLATES) { index ->
            val source = if (index % 2 == 0) {
                "var retained = $index; function run(x){return retained}"
            } else {
                "function run(x){throw new Error('credential=secret')}"
            }
            val result = sandbox.call(source, "run", "{}")
            if (index % 2 == 0) {
                expect(result == QuickJsCallResult.Success(index.toString()))
            } else {
                expect(result == QuickJsCallResult.Failure(QuickJsFailureCode.ScriptRejected))
                expect(!result.toString().contains("credential"))
            }
        }
    }

    private suspend fun twoPhaseWidgetRuntime() {
        val source = """
            var phaseState = 0;
            function plan(context) {
              phaseState = 99;
              return [{alias:'today',toolId:'fixture_data',contractVersion:1,
                arguments:{locale:context.locale}}];
            }
            function render(context, outcomes, state) {
              let outcomeBlocked = false;
              let stateBlocked = false;
              try { outcomes.today.payload.value = 'changed'; } catch (_) { outcomeBlocked = true; }
              try { state.suffix = 'changed'; } catch (_) { stateBlocked = true; }
              return {type:'text',text:[String(phaseState),outcomes.today.payload.value,state.suffix,
                String(outcomeBlocked),String(stateBlocked)].join(':'),tone:'neutral'};
            }
        """.trimIndent()
        val calls = mutableListOf<String>()
        val result = WidgetRuntimeCoordinator(QuickJsWidgetJavaScriptEngine()) { call ->
            calls += call.argumentsJson
            WidgetToolOutcome(
                alias = call.alias,
                payload = JsonObject().apply { addProperty("value", "ok") },
                rejection = null,
            )
        }.execute(
            program = parseProgram(source),
            grant = WidgetExecutionGrant(
                tools = setOf(WidgetToolCapability("fixture_data", 1)),
                runtimeValues = setOf(WidgetRuntimeValue.Locale),
                presentation = setOf(WidgetPresentationCapability.Text),
            ),
            context = WidgetRuntimeContext("pt-BR", "America/Toronto", "2026-09-04T06:00:00", null),
            stateJson = "{\"suffix\":\"done\"}",
        )
        val text = (result as? WidgetExecutionResult.Success)?.presentation as? WidgetPresentationNode.Text
        expect(calls == listOf("{\"locale\":\"pt-BR\"}"))
        expect(text?.text == "0:ok:done:true:true")
    }

    private fun parseProgram(source: String): WidgetProgram {
        val manifest = """{
          "schemaVersion":1,"language":"javascript","apiVersion":1,
          "entrypoints":{"plan":"plan","render":"render"},
          "capabilities":{"tools":[{"id":"fixture_data","version":1}],
            "runtime":["locale"],"presentation":["text"]},
          "limits":{"memoryBytes":8388608,"stackBytes":524288,"executionMillis":250,
            "maxToolCalls":1,"maxOutputBytes":65536},
          "sourceSha256":"${sha256(source)}"
        }
        """.trimIndent()
        val parsed = WidgetProgramParser.parse(manifest, source)
        expect(parsed is WidgetProgramValidationResult.Valid)
        return (parsed as WidgetProgramValidationResult.Valid).program
    }

    private fun environment(): RuntimeValidationEnvironment {
        val apk = File(context.applicationInfo.sourceDir)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val nativeArtifact = quickJsArtifact(apk)
        return RuntimeValidationEnvironment(
            generatedAtUtc = Instant.now().toString(),
            manufacturer = Build.MANUFACTURER.ifBlank { UNKNOWN },
            model = Build.MODEL.ifBlank { UNKNOWN },
            androidRelease = Build.VERSION.RELEASE.ifBlank { UNKNOWN },
            sdkInt = Build.VERSION.SDK_INT,
            buildDisplay = Build.DISPLAY.ifBlank { UNKNOWN },
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            appVersion = packageInfo.versionName ?: UNKNOWN,
            versionCode = packageInfo.longVersionCode,
            buildType = BuildConfig.BUILD_TYPE,
            apkSha256 = apk.inputStream().use(::sha256Stream),
            quickJsLibrarySha256 = nativeArtifact.sha256,
            quickJsLibraryBytes = nativeArtifact.bytes,
        )
    }

    private fun quickJsArtifact(apk: File): ArtifactDigest = runCatching {
        ZipFile(apk).use { zip ->
            val entry = checkNotNull(zip.getEntry(QUICKJS_APK_ENTRY))
            ArtifactDigest(entry.size, zip.getInputStream(entry).use(::sha256Stream))
        }
    }.getOrElse { ArtifactDigest(0, MISSING_SHA256) }

    private fun sha256Stream(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(HASH_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun expect(condition: Boolean) {
        if (!condition) throw ValidationAssertionFailure()
    }

    private data class ValidationCase(
        val id: String,
        val execute: suspend () -> Unit,
    )

    private data class ArtifactDigest(val bytes: Long, val sha256: String)

    private class ValidationAssertionFailure : IllegalStateException()

    private companion object {
        const val CASE_TIMEOUT_MILLIS = 10_000L
        const val MEMORY_SAMPLE_MILLIS = 10L
        const val QUICKJS_TIMEOUT_MILLIS = 50L
        const val MAX_TERMINATION_MILLIS = 2_000L
        const val CANCELLATION_DELAY_MILLIS = 25L
        const val REPEATED_ISOLATES = 50
        const val EXPECTED_FORBIDDEN_GLOBALS = 13
        const val HASH_BUFFER_BYTES = 64 * 1024
        const val QUICKJS_APK_ENTRY = "lib/arm64-v8a/libararai_quickjs.so"
        const val MISSING_SHA256 = "0000000000000000000000000000000000000000000000000000000000000000"
        const val UNKNOWN = "unknown"
        const val OUTCOME_PASS = "pass"
        const val OUTCOME_UNEXPECTED = "unexpected_result"
        const val OUTCOME_TIMEOUT = "case_timeout"
        const val OUTCOME_RUNTIME_UNAVAILABLE = "runtime_unavailable"
        const val OUTCOME_INTERNAL_FAILURE = "internal_failure"
    }
}
