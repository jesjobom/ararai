# Change: Manage Chat Media File Lifecycle

## Why

Chat sessions persist paths to app-owned image and audio files, but deleting a
session, clearing all sessions, or removing a draft attachment does not provide
a unified ownership and cleanup policy. Orphaned media can accumulate without
limit and consume user storage.

## What Changes

- Introduce a testable repository responsible for app-owned Chat media.
- Track which persisted messages reference each media file.
- Delete unreferenced draft media promptly and persisted media only after its
  owning messages are removed.
- Reconcile orphaned files left by crashes or prior app versions.
- Keep cleanup confined to the canonical Chat media directory and fail safely.

## Impact

- Touches media import/recording, Chat persistence deletion, and startup or
  maintenance cleanup.
- Requires `melhoria-1-limitar-processamento-de-imagens` to define the import
  boundary first or be coordinated with it.
- Must not delete media still referenced by another message.
