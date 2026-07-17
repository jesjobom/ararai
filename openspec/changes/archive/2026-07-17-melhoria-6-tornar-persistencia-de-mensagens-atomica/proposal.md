# Change: Make Chat Message Persistence Atomic

## Why

Appending a message and updating its session timestamp are separate SQLite
operations. A failure between them can leave session ordering inconsistent,
and a missing session is not detected as part of the append contract.

## What Changes

- Wrap message insertion and session timestamp update in one transaction.
- Require the target session to exist and exactly one session row to be updated.
- Roll back the entire append when either operation fails.
- Apply equivalent contract behavior to test and in-memory stores.
- Add failure-path and ordering tests.

## Impact

- Touches Chat session-store persistence only.
- Does not change the database schema or stored message encoding.
- Can be implemented independently, but media deletion in improvement 4 should
  build on the same transactional deletion discipline.
