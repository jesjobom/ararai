## 1. Specification

- [x] Define in-place audio recording behavior for supported audio models.

## 2. Implementation

- [x] Add microphone permission declaration and runtime request flow.
- [x] Add Chat composer recording UI that opens recording immediately and
  supports recording, review/playback/use, and cancel states.
- [x] Save recorded audio into app-owned chat media storage.
- [x] Convert recorded files into existing `AudioPrompt` submissions.
- [x] Remove the audio file picker fallback from the Chat composer.
- [x] Add playback for audio prompt messages in chat history.

## 3. Validation

- [x] Add or update focused tests for recorded audio prompt metadata where
  practical.
- [x] Run OpenSpec validation.
- [x] Run relevant Android unit/build validation.
