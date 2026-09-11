# Widget script runtime foundation

ArarAI contains a runtime-only foundation for executing an in-memory,
integrity-checked widget program. It does not generate, persist, schedule, list,
or render user-facing widgets. Those lifecycle concerns belong to a separate
change.

## Trust boundary and protocol

A package consists of separately stored UTF-8 JavaScript and a strict JSON
manifest. The manifest schema declares JavaScript/API version 1, distinct
`plan` and `render` entrypoints, exact tool/runtime/presentation capabilities,
requested resource limits, and the lowercase SHA-256 digest of the source.
Unknown or duplicate JSON fields, unsupported versions/capabilities, excessive
limits, malformed values, and digest mismatches fail before QuickJS is created.

Each phase receives a new QuickJS runtime and context. A project-owned
platform-neutral C++ sandbox core owns QuickJS setup, bootstrap, evaluation,
limits, cancellation, and cleanup; both the Android JNI adapter and Linux host
tests call that exact core. JNI accepts and returns
only UTF-8 bytes containing source, identifiers, and JSON; no Java, Kotlin,
Android, registry, gateway, credential, callback, or transport object enters
the engine. Inputs are deeply frozen. QuickJS's host library, module loader,
filesystem and process helpers are not compiled into the application.

Execution has one bounded planning round:

1. `plan(runtime)` returns a finite array of aliased, versioned semantic tool
   requests.
2. Kotlin validates the complete plan against the manifest and caller-owned
   grant before dispatching any request through `WidgetToolExecutionGateway`.
3. `render(runtime, outcomes, state)` runs in a fresh isolate and returns an
   allowlisted renderer-neutral presentation tree.

Links are HTTPS-only and must exactly match a declared field in a successful
outcome. HTML, Compose code, callbacks, arbitrary intents, transport fields,
and unknown presentation properties are rejected.

## Fixed version-1 bounds

- manifest: 16 KiB; source: 32 KiB;
- heap: 8 MiB; native stack request: 512 KiB;
- one phase: 250 ms; serialized input/output: 64 KiB;
- JSON: depth 16, 512 values, 4,096 characters per string;
- plan: at most four tool calls;
- presentation: depth 8, 128 nodes, and 32 children per collection.

Manifest values can lower these execution bounds but cannot raise them.
QuickJS uses a monotonic deadline, atomic cancellation flag, heap ceiling, and
stack ceiling. The Kotlin boundary discards late results after coroutine
cancellation. Stable public failure codes contain no script text, stack trace,
native address, exception message, or credential data.

## Threat and feasibility matrix

- **Ambient Android/JVM authority:** no host-object or callback bridge exists;
  instrumentation inspects forbidden globals and constructor traversal.
- **Network/filesystem/process/credentials:** `quickjs-libc` and module loading
  are absent; browser, Node, Java, timer, console, and storage globals are
  unavailable. Physical release-candidate execution remains required evidence.
- **Dynamic code/import/WebAssembly:** the internal Eval and Promise intrinsics
  exist only so the host can evaluate source, disable constructor paths, and
  identify asynchronous results. Before widget source runs, `eval`, `Function`,
  `Promise`, and constructor paths are unavailable. No module loader or
  WebAssembly support is installed, and any returned Promise is rejected.
  Host-native and instrumentation corpora cover static/dynamic imports.
- **Clock/randomness:** Date, timers, performance, and `Math.random` are absent.
  Before each plan/render/preview/dry-run isolate, Kotlin captures one immutable
  context snapshot. The frozen `runtime.currentLocalDateTime()` returns its ISO
  local timestamp, date/time components, and timezone;
  `runtime.seededIndex(length)` derives a deterministic bounded index from the
  granted seed. Language is exposed as `runtime.language`. Replaying the same
  snapshot is stable, while a later execution receives a newly captured clock.
- **CPU/non-cooperation:** the QuickJS interrupt handler checks a monotonic
  250 ms maximum deadline and cancellation flag. Host-native tests execute
  infinite-loop, cross-thread cancellation, and recovery cases; device timing
  remains required.
- **Memory/stack/output bombs:** QuickJS limits heap and stack; Kotlin/JNI bound
  source, input, output, JSON traversal, plans, and presentation. Host-native
  tests execute allocation, recursion, malformed UTF-8/JSON, invalid native
  limits, and oversized-output cases under ASan/UBSan.
- **Cross-run state:** every call owns and unconditionally destroys a fresh
  runtime/context. Host-native tests execute repeated creation, failure,
  one-shot-session, replay, and global-state cases with leak detection enabled.
- **ABI/platform:** only `arm64-v8a` is packaged; minimum SDK remains 28. Debug
  and optimized release-candidate assembly plus APK ABI inspection are part of
  the quality gate.
- **R8/JNI:** the Kotlin native entrypoints are statically reachable through the
  runtime adapter. The release `.so` exports only the four data-only JNI
  operations (`create`, `evaluate`, `cancel`, and `close`). Release artifact
  verification and on-device invocation are mandatory before the feasibility
  gate is considered complete.
- **Supply chain/license:** QuickJS `2026-06-04` is vendored from its official
  MIT archive. Exact origin, archive/source checksums, excluded host helpers,
  and update procedure are recorded in `quickjs-runtime/ORIGIN.md`.
- **Size/startup:** the stripped arm64 release `.so` is 1,071,696 bytes. The
  release-candidate APK is 54,514,571 bytes, 1,203,920 bytes above the prior
  recorded 53,310,651-byte candidate. Cold-call timing must still be recorded
  on a physical device.

The remaining material risk is a memory-safety vulnerability in the in-process
native engine. Data-only isolation and strict bounds reduce authority and blast
radius but are not an OS process sandbox. Any QuickJS update requires checksum,
license, ABI, adversarial, R8, size, and physical-device review.

## Automated evidence without a device

Run `scripts/run-quickjs-host-tests.sh` to compile the same `sandbox_core.cpp`
and vendored QuickJS C sources used by the Android library into a Linux test
executable. The suite executes real JavaScript, not a fake engine, under both a
sanitized debug build and an optimized release build. It is also a required
step in `scripts/quality-gate.sh` and runs in CI. The Android quality gate then
compiles the same corpus into the debug instrumentation APK and verifies the
optimized release-candidate APK and native ABI; executing those APKs remains a
device gate.

JVM tests independently exercise the coordinator and real widget gateway with
widget-only success, model-only rejection, disabled and unconfigured tools,
invalid arguments, provider failure, timeout, tool cancellation, caller
cancellation during every phase, late adapter results, adapter exceptions, and
recovery. They also verify that rendering cannot start another tool round and
that combined canonical outcomes are bounded before entering the render
isolate.

This layer deliberately stops at the platform-neutral boundary. It cannot
validate JNI/ART behavior, the packaged arm64 library, Android process and
lifecycle behavior, or real-device resource/timing characteristics.

## Device evidence still required

Run either the release-candidate-only self-service validator or the debug and
optimized instrumentation commands in `docs/device-validation.md` on a
supported arm64 API 28+ device. Record device, Android version, artifact hash,
cold execution timing, process PSS, timeout and cancellation latency,
repeated-isolate result, APK delta, and native ABI. Until that evidence passes,
OpenSpec tasks 1.2, 1.3, 6.3, and final change completion remain intentionally
open. Building the manual validator completes preparation only.
