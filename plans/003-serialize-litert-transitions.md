# Plan 003: Prevent LiteRT session transitions during an active generation

> **Executor:** Read the plan and run
> `git diff --stat 38acb8b..HEAD -- app/src/main/java/com/jesjobom/ararai/engine/LiteRtLmLocalLlmEngine.kt app/src/test/java/com/jesjobom/ararai/engine/LiteRtLmGenerationSerializationTest.kt app/src/test/java/com/jesjobom/ararai/engine/LiteRtLmReloadTest.kt openspec/changes`.
> Recheck the excerpts on drift; stop on mismatch. Do not commit, push, or
> archive without separate authorization.

## Status

- Priority P1; effort M; fix risk MEDIUM; category correctness/native lifecycle.
- Depends on: none. Planned at `38acb8b`, 2026-10-17.

## Why this matters

ArarAI has one application-owned local model engine shared by Chat, benchmarks,
and widget authorship. The engine serializes two generations, but its load,
unload, reload, and workload-prepare paths can replace or close the native
session while a generation is still collecting from it. Device-specific native
failure is not proven here, but closing the same session object during use is a
source-level ownership race. A clear lock order must protect the whole session
lease without trapping cancellation.

## Current state and conventions

- `LiteRtLmLocalLlmEngine.kt:63-65` defines separate `transitionMutex` and
  `generationMutex`.
- `:195-231` holds `generationMutex`, briefly acquires `transitionMutex` for
  `ensureProfile`, then releases it before `session.generate(...).collect`.
- `:97-128,135-185,262-293` perform load/reload/recovery/prepare/unload under
  `transitionMutex` only. `unloadLocked` nulls the owner then closes the prior
  session (`:273-293`).
- `ArarAiApplication.kt:273,283` injects the same `localLlmRuntime.engine`
  into legacy and resumable widget authorship; `AppLocalLlmRuntime.kt:20-33`
  owns one engine and unloads on close.
- `LiteRtLmGenerationSerializationTest.kt` already uses
  `CompletableDeferred`, a fake `LiteRtLmSession`, and `StandardTestDispatcher`
  to characterize interleavings. `LiteRtLmReloadTest.kt` checks replacement
  and recovery. Match those deterministic fake-based conventions. Preserve
  generation event order, native cancellation, and one engine instance.

## Commands

Run Gradle with JDK 17; `scripts/quality-gate.sh` additionally requires a
JDK 21 installation at `FIREBASE_JAVA_HOME` for Firebase emulator tests. See
`docs/quality-gates.md` for the pinned toolchain.

| Purpose | Command | Expected |
| --- | --- | --- |
| Focused concurrency tests | `./gradlew :app:testDebugUnitTest --tests 'com.jesjobom.ararai.engine.LiteRtLmGenerationSerializationTest' --tests 'com.jesjobom.ararai.engine.LiteRtLmReloadTest'` | exit 0 |
| Full local/CI gate | `scripts/quality-gate.sh` | exit 0 |
| Spec validation | `openspec validate --all --strict` | exit 0 |

## Scope

In scope: `LiteRtLmLocalLlmEngine.kt`, the two named JVM test files, and a new
active `openspec/changes/serialize-litert-transitions/` change. Do not change
the JNI bridge, LiteRT dependency, widget pipeline, Chat/Voice ViewModels,
thermal admission policy, or native model artifacts. Follow existing
coroutine/`Mutex.withLock` style and the project's one-engine architecture.
Commit/push only if the operator separately authorizes it.

## Steps

1. Add an OpenSpec requirement that no load/reload/prepare/unload may close or
   replace a session while a generation uses it, including cross-feature use
   of the application engine. Preserve bounded cancellation and recovery.
   Verify strict OpenSpec validation exits 0.
2. Define one lock order: **generation/ownership mutex before transition
   mutex**. Apply it consistently to session-replacing transitions. A
   generation must retain ownership through its entire native flow collection,
   not just `ensureProfile`. Do not hold a monitor (`synchronized(lock)`)
   across suspension. Preserve `NonCancellable` only where required to finish
   native ownership transfer; do not make a queued cancellation wait for an
   unrelated ten-minute recovery window without a documented reason.
   Verify focused tests.
3. Extend `LiteRtLmGenerationSerializationTest` with a fake generation paused
   mid-stream. Start unload, reload, and a differing-profile load/prepare in
   separate deterministic cases; assert no `close()` until the generation
   finishes or is cancelled, and no overlapping native sessions. Add a
   cancelled-collector case proving `cancel()` releases waiting transitions.
   Extend `LiteRtLmReloadTest` only if its existing recovery contract needs a
   new assertion. Verify focused tests, then the full gate.

## Test plan

- Concurrent generation/transition tests fail against the original race and
  pass with the ownership lock. Existing serialization/recovery tests pass.

## Done criteria

- No lock-order inversion exists among `load`, `reload`, `reloadWhenReady`,
  `prepare`, `unload`, and `generate`; native session close waits for generation
  ownership release. Cancellation still completes in bounded test time.
- `scripts/quality-gate.sh` and strict OpenSpec validation exit 0; only
  in-scope files change. Physical GPU/native validation remains a separate
  release boundary and must not be inferred from JVM fakes.

## STOP conditions

Stop if a native callback synchronously calls back into a transition method,
if the proposed lock order deadlocks under a deterministic test, or if a
physical-device behavior requires changing the project-wide single-engine
policy.

## Maintenance notes

Future additions that replace or close `loadedSession` must acquire the same
ownership gate; reviewers should look for a released mutex before
`session.generate(...).collect` completes.
