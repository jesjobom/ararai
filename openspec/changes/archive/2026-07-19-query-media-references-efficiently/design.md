# Design: Dedicated media-reference projection

## Context

`ChatSessionStore.referencedMediaUris` composes `listSessions` with one
`getMessages` call per session. SQLite then decodes all structured content even
though cleanup needs only attachment URIs.

## Decisions

Add an explicit store operation for referenced media. Prefer a normalized
attachment-reference table keyed by message/session/URI if it can be migrated
atomically and maintained in the same message transaction. This provides a
single indexed query and avoids parsing opaque payloads.

If migration risk is disproportionate, use a single SQLite projection and
centralized payload decoding as an interim implementation, but document that it
still scans message payloads. In either design, append/update/delete/clear must
keep references transactionally consistent before cleanup can consume them.

## Validation

- Migration tests preserve existing text, image, audio, and reasoning messages.
- Store contract tests compare in-memory and SQLite reference results.
- Media deletion tests prove shared references are never removed prematurely.
