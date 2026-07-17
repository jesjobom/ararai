## Context

Chat images and recordings are copied into `filesDir/chat_media`, while persisted
messages store their paths. Draft state and SQLite therefore form two independent
sources of ownership that must be considered before deleting a file.

## Goals / Non-Goals

- Delete app-owned media promptly after its last draft or message reference disappears.
- Preserve shared media and every path outside the canonical Chat media directory.
- Recover bounded orphaned files left by crashes or older versions.
- Do not add a media reference table or database migration in this change.
- Do not make message/session deletion transactional; that is covered by improvement 6.

## Decisions

### Canonical repository boundary

`FileChatMediaRepository` is the only component that creates or deletes Chat media.
It accepts only direct children whose canonical parent equals the canonical
`chat_media` directory. `content://` URIs, nested paths, symlink escapes, and other
filesystem locations are never deletion candidates.

### Ownership lookup

Draft paths live in `ChatUiState`. Persisted ownership is derived from structured
message content through `ChatSessionStore.referencedMediaUris()`. Cleanup receives
the current persisted reference set and deletes a candidate only when its canonical
file is absent from that set. This preserves a file shared by multiple messages
without introducing duplicate ownership state.

### Delete after persistence

Session messages are read as cleanup candidates, the store deletion runs, and only
then does file cleanup compare candidates with remaining persisted references. A
store failure therefore cannot remove media that still belongs to an undeleted
message. File deletion is best-effort because a missing or locked file must not
invalidate the already completed user action.

### Bounded reconciliation

Startup reconciliation examines at most 256 direct regular files. It never recurses
or traverses outside the canonical directory. Additional orphans can be handled on
a later startup, keeping maintenance work bounded.

## Risks / Trade-offs

- Deriving references scans persisted sessions and messages. Chat histories are
  currently small, and cleanup is infrequent; a reference table can be introduced
  later if profiling shows this scan is material.
- A process crash between database deletion and file cleanup can leave an orphan.
  Startup reconciliation intentionally repairs that direction of inconsistency.
- Reconciliation has no persisted draft after process death, so any unreferenced
  file is correctly treated as abandoned.
