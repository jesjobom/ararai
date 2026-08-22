## 1. Characterize the defect

- [x] 1.1 Specify durable fallback titling and distinct-session behavior without Whisper.
- [x] 1.2 Add a regression test covering a direct-audio turn followed by a new session.

## 2. Implement fallback promotion

- [x] 2.1 Generate a localized timestamp-based Voice Chat title at the application boundary.
- [x] 2.2 Promote the first direct-audio message immediately only when transcription is unavailable.
- [x] 2.3 Preserve transcript-derived titles and transient empty-session behavior.
- [x] 2.4 Apply equivalent fallback promotion to audio sent from regular Chat.

## 3. Validate

- [x] 3.1 Run focused session and Voice Chat tests.
- [x] 3.2 Run the complete quality gate and strict OpenSpec validation.
