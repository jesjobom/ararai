# Design: Bounded asynchronous conversation persistence

## Context

`ChatSessionStore` is intentionally a small synchronous persistence boundary.
SQLite implementations can query efficiently, but ViewModels currently call the
boundary directly from user actions and repeatedly materialize every message.
The prompt builder later discards older history according to the model context
budget, so reading an unbounded session first provides no inference benefit.

Voice Chat captures into a temporary cache directory, then copies the file to the
shared Chat media directory. The persisted message must reference only the latter.
Treating copy failure as a recoverable fallback violates that ownership contract.

## Decisions

### Preserve the store boundary and add explicit bounded operations

Keep existing full-history access for maintenance, tests, export, and operations
that genuinely require it. Add recent-window and count operations with an
efficient SQLite implementation (`ORDER BY ... DESC LIMIT`, reversed before
return) and equivalent in-memory behavior. Add a direct session-media query so
deletion does not decode full message payloads.

The Chat UI starts with a fixed recent window and increases it in fixed pages when
the user requests older messages. Canonical storage is never truncated.

### Dispatch at the ViewModel boundary

Interactive persistence work SHALL run on an injected I/O dispatcher while state
publication remains safe through `StateFlow`. The dispatcher is injectable for
deterministic tests. Generation and media cleanup preserve existing ownership and
cancellation ordering.

Do not introduce Room or a general DI framework in this change. Those would
expand migration and review risk without being required for bounded queries.

### Make media ownership atomic

Voice Chat SHALL copy the temporary recording into app-owned Chat media before
starting the canonical user turn. If directory creation or copying fails, the
turn enters a controlled error state, the temporary capture is removed, and no
message/media reference is stored. Never persist the temporary path as fallback.

## Validation

- Store tests prove bounded ordering, counts, session-media lookup, and empty
  boundaries for SQLite, deferred, and in-memory implementations.
- ViewModel tests prove recent-window loading, explicit older-page loading, and
  off-main persistence execution.
- Voice Chat tests prove copy failure stores no broken user message.
- The full quality gate and strict OpenSpec validation pass.
- Very large real-device databases and filesystem failure behavior remain useful
  physical-device checks; automated tests provide deterministic contract evidence.

