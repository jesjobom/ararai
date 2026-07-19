# Change: Query persisted media references efficiently

## Why

The current media-reference operation lists every session and loads and decodes
every message for each session. Cleanup and draft operations pay 1+N queries and
materialize the full history when they need only owned media URIs.

## What Changes

- Add a dedicated persisted-media-reference operation to the store boundary.
- Implement SQLite retrieval without loading complete sessions and messages.
- Preserve in-memory behavior and media ownership safety.
- Add query-shape, migration if required, and deletion-safety tests.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: Chat session store/schema or projection, media cleanup callers, tests
- Message content and visible session behavior: unchanged
