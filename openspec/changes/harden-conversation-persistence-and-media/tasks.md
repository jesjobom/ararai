## 1. Specification

- [x] 1.1 Define atomic media ownership, bounded history, and off-main persistence requirements.

## 2. Persistence boundary

- [x] 2.1 Add bounded recent-message, count, and session-media queries to every store implementation.
- [x] 2.2 Add store tests for ordering, boundaries, counts, and media references.

## 3. Chat history and threading

- [x] 3.1 Load a bounded recent message window and expose whether older messages exist.
- [x] 3.2 Add explicit incremental loading of older messages without truncating canonical history.
- [x] 3.3 Dispatch interactive SQLite reads and writes off the Android main thread with deterministic tests.

## 4. Voice Chat media atomicity

- [x] 4.1 Reject a turn when app-owned audio copy fails instead of persisting the temporary path.
- [x] 4.2 Test success, copy failure, cleanup, and absence of broken canonical references.

## 5. Validation

- [x] 5.1 Run focused persistence, Chat, and Voice Chat tests.
- [x] 5.2 Run the full project quality gate and strict OpenSpec validation.
- [x] 5.3 Record remaining physical-device validation boundaries.
