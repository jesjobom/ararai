## Context

`ChatViewModel` receives incremental text and reasoning events from the local
engine. Before this change, every event built a new full response string,
updated the UI, and immediately called `ChatSessionStore.updateMessage`. For a
long generation this coupled rendering cadence to SQLite write cadence and
performed repeated full-string concatenation on the inference hot path.

The UI must continue showing each delta as it arrives. Persisted partial output
must remain recoverable after completion, failure, explicit cancellation, model
replacement, or leaving Chat. The existing session schema and engine event
contract must remain compatible.

## Goals / Non-Goals

**Goals:**

- Keep immediate streamed UI updates.
- Bound SQLite persistence delay to 250 milliseconds while generation is active.
- Flush the latest visible content synchronously at every controlled terminal path.
- Reduce repeated immutable-string concatenation by accumulating text and
  reasoning in mutable buffers.
- Keep timing deterministic under coroutine-test virtual time.

**Non-Goals:**

- Changing engine output chunking or Compose rendering cadence.
- Changing the stored message schema or session-store API.
- Providing crash consistency for content generated after the most recent
  bounded flush but before an abrupt process kill.
- Moving all Chat persistence to a new database abstraction.

## Decisions

### Maintain one accumulator for the active assistant message

The ViewModel owns a single `AssistantMessageBuffer` containing the active
message ID plus separate text and reasoning `StringBuilder`s. Each engine delta
updates the buffer and publishes an immutable snapshot to UI state.

This is preferred over deriving the next response from the existing UI string
because the buffer avoids `current + delta` concatenation and keeps persistence
state independent from rendering state. A single buffer matches the existing
invariant that only one generation may run at a time.

### Use leading-edge scheduled persistence with a 250 ms bound

The first dirty delta schedules one coroutine. Further deltas reuse that pending
schedule instead of restarting it. At 250 ms the latest complete buffer snapshot
is written once, and a later delta schedules the next interval.

This is preferred over persisting every N chunks because chunk size and cadence
vary by runtime. It is also preferred over a trailing-edge debounce that resets
on every token, which could postpone persistence indefinitely during continuous
generation.

### Flush synchronously before clearing active state

Completion, `GenerationEvent.Failed`, thrown failures, explicit cancellation,
model-state cancellation, and leaving Chat call the same flush/finalize path.
The pending scheduled job is cancelled, the latest snapshot is written through
the existing synchronous store API, and only then are buffer references cleared.

Idempotent finalization allows both caller-driven cancellation and the generation
coroutine's `finally` block to use the same path without duplicate writes.

### Keep synchronization local to buffer ownership

A private lock protects the buffer, dirty flag, and scheduled persistence job.
Database writes occur after releasing the lock so store latency cannot block a
concurrent buffer snapshot while holding ViewModel-internal synchronization.

## Risks / Trade-offs

- **[An abrupt process kill can lose up to 250 ms of output]** → Keep the bound
  short and flush every controlled terminal path.
- **[UI snapshots still allocate full immutable strings]** → Retain immediate UI
  semantics; mutable accumulation removes concatenation overhead, but Compose
  state still requires immutable snapshots.
- **[Cancellation and generation completion can race]** → Make flush/finalize
  idempotent and protect ownership state with one lock.
- **[Store failure during a terminal flush can surface from a synchronous user
  action]** → Preserve existing store error semantics; a future persistence
  reliability change may introduce explicit store failure state.

## Migration Plan

1. Introduce the active assistant accumulator without changing UI output.
2. Replace per-delta store updates with the scheduled 250 ms flush.
3. Route all controlled terminal paths through the shared finalizer.
4. Add deterministic tests for batching, completion, failure, cancellation, and
   leaving Chat.
5. Run unit tests, lint, debug assembly, and strict OpenSpec validation.

Rollback is code-only: restore per-delta `updateMessage` calls. No persisted data
or schema migration is required.

## Open Questions

- Whether a future asynchronous store API should move terminal disk writes off
  the caller thread while still providing an acknowledged durability boundary.
