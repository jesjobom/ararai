## Context

LiteRT-LM retains a successful `Conversation` so a compatible next Chat turn can
reuse native conversation state. The same conversation can be referenced as
both active and retained. Previously, `cancel()` cancelled the active object and
cleared retained metadata without closing it. The flow's `awaitClose` and engine
`close()` also had independent cleanup logic, creating both leak and double-close
risks when callbacks and cancellation raced.

Native conversation ownership must remain correct across asynchronous callbacks,
flow cancellation, reuse, profile replacement, model replacement, and unload.

## Goals / Non-Goals

**Goals:**

- Close every invalidated conversation exactly once by object identity.
- Cancel active processing before closing on cancellation paths.
- Clear active and retained ownership before invoking native cleanup.
- Reject late attempts to retain a conversation already disposed by cancellation.
- Preserve reuse of successfully completed compatible conversations.

**Non-Goals:**

- Changing transcript-compatibility or sampler-reuse rules.
- Retaining more than one reusable conversation.
- Changing the generic `LocalLlmEngine` API.
- Recovering a partially generated native conversation after cancellation.

## Decisions

### Centralize ownership in an identity-based resource owner

`RetainedResourceOwner` owns the active resource, optional retained metadata,
and an identity map of disposed resources. `AndroidLiteRtLmSession` delegates
activation, retention, invalidation, cancellation, and close-all operations to
this owner instead of mutating independent references.

Identity comparison is required because native wrapper equality must not imply
shared ownership. Marking a resource disposed and clearing references occurs
under one lock before native methods are invoked.

### Separate cancellation disposal from normal disposal

An incomplete or explicitly cancelled generation invokes `cancelProcess()` and
then `close()`. A successfully completed non-reusable conversation, incompatible
retained conversation, or normal unload only invokes `close()`.

Both paths claim the resource through the same disposed-identity set, so later
`awaitClose`, callback, cancel, or unload calls become no-ops for that resource.

### Reject callback retention after disposal

`retain` returns false when cancellation has already claimed the resource. This
prevents a late `onDone` callback from restoring ownership of closed native state.
Successful callbacks that win the race retain the conversation normally; a
subsequent cancel still claims and closes it once.

### Keep the owner generic and directly testable

The Google LiteRT `Conversation` is created by a concrete native engine and is
not practical to construct in JVM unit tests. The generic owner is tested with
recording resources, while existing engine tests continue covering compatible
reuse predicates and session unload behavior.

## Risks / Trade-offs

- **[Native cancel or close throws]** → `close` runs in `finally` after cancel;
  ownership is already cleared so the failed resource cannot be reused.
- **[Late callback races with cancellation]** → Disposed identity is checked by
  both `activate` and `retain` under the ownership lock.
- **[Disposed identity entries remain for the session lifetime]** → The set is
  bounded by conversations created during one loaded LiteRT engine session and
  is released when that session is closed.
- **[Generic lifecycle code adds abstraction]** → Keep its surface limited to
  active/retained ownership and native disposal; compatibility remains outside.

## Migration Plan

1. Introduce the identity-based owner with fake-resource lifecycle tests.
2. Route conversation activation and successful retention through the owner.
3. Replace mismatch, flow cleanup, cancellation, and unload cleanup with owner operations.
4. Run engine tests, the full JVM suite, lint, debug assembly, and strict OpenSpec validation.

Rollback is code-only and requires no data migration.

## Open Questions

- Whether future LiteRT versions provide an explicit idempotent closed-state API
  that could supplement the app-owned identity guard.
