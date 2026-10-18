# Design

## Context

See `proposal.md` for motivation and the two spec deltas for behavioral
contracts. This is one OpenSpec change with four independently reviewable code
slices. ArarAI has a single application-scoped LiteRT-LM engine; widget
authorship, Chat, Voice Chat, and diagnostics can use it. The foreground
authoring service reads a durable revisioned session, while model downloads
use a checked-in catalog with expected byte counts and SHA-256 digests.

The current service rejects an old action for workflow mutation but may still
stop itself. The downloader copies until EOF before checking length. The engine
serializes generations separately from load/unload/reload transitions, and both
its native-callback and outer Flow layers ignore unsuccessful `trySend` calls.
The existing quality gate covers JVM tests and instrumentation **assembly**;
actual Android foreground/native behavior is a separate device boundary.

## Goals / Non-Goals

**Goals:**

- Preserve foreground ownership and cancellation controls for the latest
  active authoring attempt despite delayed or duplicate notification actions.
- Make expected model byte size an active disk-write limit while retaining
  resumability, fallback isolation, SHA-256 verification, and atomic promotion.
- Define one native-session ownership order across generation and transitions.
- Make all reported successful generation streams complete and ordered; turn
  bounded transport overflow into a controlled failure, not partial success.
- Add deterministic regression tests and record the device checks that the
  host quality gate cannot perform.

**Non-Goals:**

- Change widget-authoring stage protocols, schedules, permissions, or consent.
- Replace the LiteRT-LM runtime, add another engine instance, introduce an
  unbounded event queue, or block an unknown native callback thread.
- Add remote model catalog entries, change model artifact hashes, or change
  Chat/Voice persistence schemas.
- Treat JVM fakes or an assembled instrumentation APK as proof of physical
  native/GPU or Android foreground-service correctness.

## Decisions

### D1. Reconcile actions against current service ownership

`WidgetAuthoringService` will use one post-action disposition path based on
the **latest** workflow snapshot and its current service start ID. A stale
Cancel/Continue/Retry action will not mutate the current session and will not
unconditionally call `stopForeground` or `stopSelf`. A matching Cancel requests
cancellation; the committed terminal-state refresh owns foreground teardown.
Asynchronous Continue/Retry completion will also re-evaluate the latest state
before stopping, and a newer start ID prevents an older callback from ending
the service. Queued foreground starts must still be promoted before mutable
state is inspected, as the existing refresh path does.

The notification's captured session revision is also passed into the
coordinator for Continue/Retry and checked before mutation; an on-start check
alone would race with coroutine dispatch. Cancel uses the captured active
attempt ID under the controller's submit/cancel mutex, so it cannot cancel a
newer attempt if the notification becomes stale after receipt.

Alternative rejected: simply replace every `stopSelf()` with
`stopSelfResult(startId)`. That protects service stopping after a newer start
but does not by itself protect the foreground notification from an unconditional
`stopForeground` call, nor keep a matching Cancel foreground until its outcome
is committed. Legacy authoring actions must retain their existing semantics.

### D2. Bound the disk copy with the catalog's exact size

`ModelFileDownloader` will reject a missing/non-positive `expectedBytes` before
opening the byte source. It will remove an already oversized `.part` file before
requesting a range. During a copy, it will compare each read count to the
remaining allowance **before writing**, using subtraction to avoid integer
overflow. An excess response closes resources through existing `use` scopes,
deletes the invalid partial, and takes the existing controlled failure/fallback
route. An exact-length candidate still requires SHA-256 and atomic promotion;
the previous valid final artifact stays untouched.

Alternative rejected: rely on `Content-Length` or post-copy validation.
Neither is an enforceable disk limit for streamed/chunked or incorrect HTTP
responses. All four checked-in artifacts already declare expected sizes; the
nullable model type can remain for unrelated resolution tests, while download
fails explicitly without a bound.

### D3. Give generation one lease over native transitions

Use the existing `generationMutex` as a session-ownership gate. Every operation
that can replace, unload, or reconfigure `loadedSession` acquires it before
`transitionMutex`; generation already holds it through collection and briefly
uses `transitionMutex` for profile preparation. This establishes one lock order:
**generation/ownership gate → transition gate**. No Java/Kotlin monitor is held
across suspension. Native load publication and cleanup remain protected by the
existing non-cancellable ownership-transfer sections, not by making an entire
user operation non-cancellable. Cancellation must release the generation lease
after native cleanup so a queued transition can proceed.

Alternative rejected: a second engine instance or a session reference-count
layer. The first conflicts with memory-pressure evidence and application-wide
single-engine ownership; the second adds an ownership abstraction when the
existing two mutexes can express the required order. Recovery waiting may
serialize other work as it already does under `transitionMutex`; tests must
check cancellation rather than assume the wait is harmless.

The generating coroutine cancels its captured session, if needed, in a
`finally` block before releasing the generation lease. It does not look up
`loadedSession` later from `awaitClose`, which could otherwise cancel a
replacement session after an asynchronous close.

### D4. Make both event boundaries report delivery failure

The outer engine coroutine can suspend, so it will deliver mapped chunks and
terminal events with backpressure-aware `send` rather than unchecked
`trySend`. A successful `Completed` follows all mapped events. The native
`MessageCallback` cannot suspend: a bounded, nonblocking delivery adapter
checks every enqueue result. If capacity is exhausted, an atomic terminal
state marks the round invalid, closes the flow with a **controlled** failure,
and arranges native cancellation/cleanup without synchronously blocking or
re-entering an unsafe callback. A subsequent `onDone` cannot retain a partial
transcript or convert overflow to success. A channel closed by normal caller
cancellation remains cancellation, not overflow.

Alternative rejected: unchecked `trySend` (silent truncation), an unbounded
channel (memory growth under a slow UI collector), or `runBlocking`/
`trySendBlocking` on the native callback (possible deadlock without a proven
threading contract). The executor must verify the callback cancellation
threading contract before choosing the exact safe cancellation dispatch; if
that contract cannot be established, stop and report rather than guessing.
Error messages and telemetry remain bounded/sanitized and contain no prompt,
token, tool argument, or raw native exception text.

Implementation decision: the LiteRT `MessageCallback` only performs a
nonblocking `trySend` through a bounded adapter. Overflow atomically closes
the callback flow with a fixed-message exception; it does **not** call
`conversation.cancelProcess()` or `conversation.close()` from the native
callback. `awaitClose` runs the existing invalidation/cancellation path in the
producer coroutine that collects on the engine dispatcher. The adapter's
terminal gate prevents a later `onDone` from retaining the conversation after
overflow. The adapter has host-side full/closed-channel tests; physical native
callback behavior remains a separate arm64 validation requirement.
Widget authoring accepts a captured tool artifact only after a `Completed`
terminal event. A later `Failed` event (including delivery overflow), or a
stream that ends without a terminal event, invalidates that round even if the
tool arguments were already captured.

## Risks / Trade-offs

- **Service stop ordering races with a new start** → cover stale action,
  matching Cancel, newer start ID, and rapid queued-to-terminal transitions in
  JVM/Robolectric plus real Android lifecycle tests.
- **Native lock ordering increases wait time or cancellation latency** → use
  deterministic paused-generation fakes and cancelled-collector cases; retain
  the device recovery/thermal gate and verify no overlapping native sessions.
- **Fail-fast overflow can surface a user-visible failure on a very slow
  consumer** → preferable to a falsely successful truncated response; test
  event ordering and controlled retryability, and keep the queue bounded.
- **Callback cancellation may be re-entrant or thread-sensitive** → validate
  the LiteRT callback contract or dispatch cleanup safely; stop if neither can
  be proven with tests.
- **A download-size error may advance to a fallback source** → delete excess
  partial bytes before fallback so different URLs never share unverified data.

## Migration Plan

No durable schema, data, or API migration is needed. Implement the slices in
this order: (1) service actions, (2) download bound, (3) native ownership,
(4) stream integrity after (3). Each slice gets targeted tests and the complete
quality gate before delivery. Run the Android foreground lifecycle test only on
a disposable arm64 target with the repository's destructive-test opt-in; do
not run connected tests on JJ's retained SM-S901E. Record physical native
inference and foreground results separately from build/test success. A failed
slice can be reverted independently before release; no stored model, widget,
or conversation format changes require data rollback.
