## 1. Specification

- [x] Define system-prompt and textual-history inclusion for current audio
  prompts.
- [x] Define context-budget and role-preservation behavior.
- [x] Exclude historical media re-sending from this change.

## 2. Implementation

- [x] Extend prompt-context construction to represent a current audio turn
  alongside bounded textual context.
- [x] Pass the resulting textual context and current audio file through the
  generation request boundary.
- [x] Update the LiteRT-LM adapter to send both context text and audio content.
- [x] Preserve capability validation and existing audio persistence/playback.

## 3. Validation

- [x] Add focused tests for system prompt and recent textual history on audio
  turns.
- [x] Add focused tests for LiteRT-LM audio plus text content construction.
- [x] Add regression coverage proving historical media files are not re-sent.
- [x] Run OpenSpec strict validation.
- [x] Run Android unit tests and debug APK assembly.
