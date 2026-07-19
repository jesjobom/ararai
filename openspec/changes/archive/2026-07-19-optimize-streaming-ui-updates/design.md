# Design: Paced streaming presentation

## Context

Persistence is already paced, but presentation state is rebuilt per token.
Compose effects key on growing message content, and Markdown parsing operates on
the complete current response during recomposition.

## Decisions

Keep the authoritative incremental assistant buffer off the structural UI state.
Publish immutable display snapshots no more than once per configured short
interval or frame. Always flush synchronously at completion, failure,
cancellation, session/model transitions, and lifecycle cleanup.

Make scroll follow a monotonic display revision or targeted message signal. Make
TTS preparation react only to completed eligible messages. Memoize Markdown
parsing by the displayed text and preserve safe partial-Markdown fallback.

Do not drop tokens or delay terminal state behind the pacing interval. Preserve
the existing periodic durable persistence boundary independently.

## Validation

- Coroutine tests use a controllable scheduler to prove update coalescing,
  ordering, terminal flushes, and cancellation durability.
- Compose tests assert follow-latest behavior and completed-message TTS preparation.
- A physical-device long-response check records jank/memory before and after.
