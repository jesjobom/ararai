## 1. Specification

- [x] Define the session-list bulk-clear action and confirmation behavior.
- [x] Define atomic persistence cleanup and post-clear Chat state.

## 2. Implementation

- [x] Add bulk session deletion to the session-store contract and both storage
  implementations.
- [x] Add a ChatViewModel action that clears history and selects a replacement
  empty session.
- [x] Add `Clear all` to the Chat session list with a destructive-action
  confirmation dialog.
- [x] Clear draft text and pending attachments after confirmation.
- [x] Guard the operation while generation is active.

## 3. Validation

- [x] Add focused SQLite cleanup coverage.
- [x] Add focused ChatViewModel post-clear state coverage.
- [x] Run OpenSpec strict validation.
- [x] Run Android unit tests and debug APK assembly.
