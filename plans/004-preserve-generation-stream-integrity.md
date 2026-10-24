# Plan 004: Never report a successful generation after dropping stream events

> **Executor:** Execute Plan 003 first. Then read this plan and run
> `git diff --stat 38acb8b..HEAD -- app/src/main/java/com/jesjobom/ararai/engine/LiteRtLmLocalLlmEngine.kt app/src/test/java/com/jesjobom/ararai/engine openspec/changes`.
> Plan 003 will intentionally change the engine; compare the current code
> against the excerpts and adjust only those ownership-lock lines. Stop for
> unrelated drift. Do not commit, push, or archive without authorization.

## Status

- Priority P1; effort M; fix risk MEDIUM; category correctness/streaming.
- Depends on: Plan 003. Planned at `38acb8b`, 2026-10-17.

## Why this matters

The LiteRT bridge emits incremental, cumulative-model deltas through a
`callbackFlow`. Both the native-callback channel and the outer engine flow
ignore failed `trySend` calls. When a consumer is slower than the producer,
the default buffered channel can fill and silently lose text, reasoning, tool
events, or `Completed`. Chat then persists a truncated answer as successful;
widget authoring may treat a missing terminal as `NoArtifact`. A channel-close
caused by normal caller cancellation is different from overflow and must not
be shown as a model failure.

## Current state and conventions

- `LiteRtLmLocalLlmEngine.kt:722-742`: native `MessageCallback.onMessage` computes
  `textDelta`/`reasoningDelta`, updates `previousText`, then ignores
  `trySend(LiteRtLmChunk(...))` failure. Tool and metrics callback sends at
  `:656-700,:764` also ignore delivery results.
- `LiteRtLmLocalLlmEngine.kt:231-235`: the outer coroutine calls `trySend` for
  every mapped event and `GenerationEvent.Completed`; rejection and failure
  paths at `:208-247` do the same.
- `WidgetAuthoringPipelineModel.kt:251-265` waits for a Completed/Failed event;
  `ChatViewModel.kt:641-643,952-982` and `VoiceChatViewModel.kt:486-497` consume
  deltas after delivery and cannot reconstruct ones lost upstream.
- Native callbacks are not suspend functions. Do **not** block their thread
  with `runBlocking`/`trySendBlocking` without a proven threading contract.
  Existing engine tests use fake bridges and `kotlinx-coroutines-test`; follow
  `LiteRtLmGenerationSerializationTest.kt` and `LiteRtLmLocalLlmEngineTest.kt`.
- Consolidated specs require streamed local Chat and bounded staged widget
  generation; preserve sanitized failures and no raw model text in logs.

## Commands

Run Gradle with JDK 17; `scripts/quality-gate.sh` additionally requires a
JDK 21 installation at `FIREBASE_JAVA_HOME` for Firebase emulator tests. See
`docs/quality-gates.md` for the pinned toolchain.

| Purpose | Command | Expected |
| --- | --- | --- |
| Focused engine tests | `./gradlew :app:testDebugUnitTest --tests 'com.jesjobom.ararai.engine.LiteRtLmLocalLlmEngineTest' --tests 'com.jesjobom.ararai.engine.LiteRtLmGenerationSerializationTest'` | exit 0 |
| Full local/CI gate | `scripts/quality-gate.sh` | exit 0 |
| Spec validation | `openspec validate --all --strict` | exit 0 |

## Scope

In scope: `LiteRtLmLocalLlmEngine.kt`, a new focused test under
`app/src/test/java/com/jesjobom/ararai/engine/`, the two existing engine tests
named above if needed, and a new active
`openspec/changes/preserve-generation-stream-integrity/` change. Do not alter
the JNI library, public `GenerationEvent` data shape, Chat persistence schema,
widget result classifier, or native model dependency. Use the existing
expected/unexpected failure mapping; keep error text controlled. Commit/push
only if separately authorized.

## Steps

1. Add an OpenSpec requirement: a completed generation has a complete ordered
   event stream; if delivery cannot keep up, it terminates with a controlled
   failure rather than returning incomplete success. Verify strict validation.
2. In the **outer coroutine**, replace ignored `trySend` with suspending `send`
   for mapped chunks and terminal events, respecting caller cancellation and
   the Plan 003 ownership gate. The producer must not mark success if a send
   fails. Verify a fake bridge emitting more than the default channel capacity
   with a slow collector receives all ordered deltas and one Completed.
3. At the **native callback boundary**, use a testable nonblocking delivery
   helper that checks each `trySend` result. On buffer-full failure, atomically
   mark the generation invalid, cancel the active native conversation, close
   the flow with a controlled overflow cause, and prevent `onDone` from
   retaining a partial transcript or reporting success. A closed channel due
   to normal caller cancellation must follow normal cleanup, not emit an
   overflow failure. Apply the same rule to tool/metrics events whose loss
   changes user-visible state. Never log a prompt, token, tool argument, or
   full exception text. Verify focused tests.
4. Add deterministic tests for hundreds of fast native-callback events with a held
   collector (overflow is explicit, no Completed), an outer-flow slow collector
   (all deltas and Completed), callback `onDone` after overflow (cannot turn it
   into success), and normal cancellation (no spurious overflow). Use a fake
   callback/conversation adapter, not a live model. Run focused tests and the
   full quality gate.

## Test plan

- A successful stream's concatenated deltas equal the fake model's complete
  output, in order, with one Completed. A saturated native-callback queue
  yields a controlled Failed outcome; it never yields Completed or retained
  partial conversation state.

## Done criteria

- Caller cancellation remains quiet and releases native resources. Existing
  engine and Chat/Voice tests remain green; `scripts/quality-gate.sh` and
  strict OpenSpec validation exit 0.
- Only in-scope files change. Physical inference behavior remains unverified
  until the repository's device matrix is run separately.

## STOP conditions

Stop if the LiteRT callback contract can deliver concurrent `onMessage` calls
in a way the nonblocking helper cannot serialize, if `cancelProcess()` from a
callback thread is not safe, or if an explicit overflow cannot reach the outer
failure mapping without touching out-of-scope interfaces. In that case, report
the required API boundary and propose a separate bounded adapter design; do
not substitute an unbounded channel or a blocking native callback as a quick
fix.

## Maintenance notes

Every future `trySend` in this path needs checked failure semantics.
