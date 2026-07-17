## 1. Speech service boundary

- [x] 1.1 Define a testable Chat TTS service contract and playback result/state model.
- [x] 1.2 Implement the Android `TextToSpeech` adapter using the device defaults.
- [x] 1.3 Correlate utterance callbacks and ignore stale completion/error callbacks.
- [x] 1.4 Stop and release the native TTS engine deterministically on disposal.

## 2. Chat UI

- [x] 2.1 Show a sound action only for completed assistant messages with non-blank response text.
- [x] 2.2 Speak only response text and exclude reasoning content.
- [x] 2.3 Replace sound with stop for the active message.
- [x] 2.4 Stop current speech before starting a different response.
- [x] 2.5 Surface TTS initialization, availability, and playback errors safely.
- [x] 2.6 Add distinct accessibility descriptions for sound and stop actions.

## 3. Verification

- [x] 3.1 Test eligibility rules for assistant, user, blank, and streaming messages.
- [x] 3.2 Test start, stop, replacement, completion, error, and stale callbacks with a fake service.
- [x] 3.3 Test that reasoning is never passed to the speech service.
- [x] 3.4 Test lifecycle disposal stops playback and releases the service.
- [x] 3.5 Add an Android instrumentation smoke test for native TTS initialization when available without requiring voice installation.
- [x] 3.6 Run the complete Android quality gate.
- [x] 3.7 Validate this OpenSpec change strictly.
- [x] 3.8 Validate sound/stop behavior and device-default voice on a physical device.
