## 1. Local Language Identification

- [x] 1.1 Add the bundled ML Kit Language ID dependency.
- [x] 1.2 Add a lifecycle-safe language identifier abstraction and Android ML
  Kit implementation with explicit uncertain-result handling.

## 2. Language-aware Speech Playback

- [x] 2.1 Extend the Chat speech controller to prepare completed assistant
  messages and track per-message language readiness.
- [x] 2.2 Configure and validate the detected locale before native TTS playback,
  with device-default voice fallback.
- [x] 2.3 Render the completed-response sound action disabled while language
  preparation is pending and enable it when preparation finishes.

## 3. Verification and Documentation

- [x] 3.1 Add or update unit tests for asynchronous preparation, stale results,
  fallback, playback, and disposal behavior.
- [x] 3.2 Add Android-boundary coverage for bundled language identification and
  native TTS locale availability where the environment supports it.
- [x] 3.3 Run OpenSpec validation and the repository's relevant unit, lint,
  build, and Android-boundary quality gates.
- [x] 3.4 Review README and OpenSpec project context for documentation impact.
