# Change: Harden conversation persistence and media

## Why

Voice Chat currently falls back to persisting a temporary capture path when the
copy into app-owned Chat media fails. Normal cleanup later deletes that temporary
file, leaving a canonical conversation message with missing audio. Conversation
screens also read and decode an entire session synchronously for common actions,
so increasingly long histories can turn SQLite work into visible UI stalls.

## What Changes

- Make Voice Chat audio persistence fail atomically before a user message is
  appended when the app-owned copy cannot be created.
- Add bounded recent-message queries and message counts to the conversation store.
- Display a bounded recent window and let the user explicitly load older history.
- Keep context projection bounded and avoid loading media solely to discover files
  owned by a deleted session.
- Dispatch interactive SQLite reads and writes away from the Android main thread.
- Preserve canonical full history, ordering, media reference safety, and shared
  Chat/Voice Chat session semantics.

## Impact

- Affected specs: `local-llm-hub`, `voice-chat`
- Affected code: conversation persistence, Chat state/UI, Voice Chat media flow,
  tests
- Data migration: none; existing schema remains readable

