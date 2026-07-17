## Context

`SqliteChatSessionStore.appendMessage` previously inserted a `chat_messages` row
and then updated `chat_sessions.updated_at_millis` as independent operations. A
failure or missing session between those operations could commit an orphan
message or leave session ordering stale. SQLite foreign-key enforcement alone
is not a sufficient contract because it may vary by connection configuration.

## Goals / Non-Goals

- Commit the message and owning-session timestamp as one unit.
- Reject appends unless exactly one target session exists.
- Keep message and session timestamps aligned for deterministic ordering.
- Give the in-memory implementation the same missing-session behavior.
- Do not change schema or structured-message encoding.

## Decisions

### Transaction boundary

The SQLite transaction contains the next-message timestamp query, message insert,
and session update. Moving timestamp selection inside the transaction ensures a
second store/connection cannot invalidate the append assumptions between the
read and writes.

### Exact update count

After insertion, the session update must affect exactly one row. Zero rows raises
`ChatPersistenceException`, and any other count is also treated as a contract
violation. The exception exits before `setTransactionSuccessful`, so SQLite rolls
back the inserted message.

### Timestamp guarantee

The committed message `created_at_millis` and session `updated_at_millis` use the
same monotonic value. This makes the owning session move to the correct position
after a successful append. The in-memory store applies the identical value and
checks session existence before mutating either map.

## Failure behavior

- Missing session: controlled `ChatPersistenceException`, no message committed.
- SQLite insert/update failure: original database exception, transaction rolled back.
- Concurrent deletion through another connection: serialization or a zero-row
  update causes the append to fail without partial state.
