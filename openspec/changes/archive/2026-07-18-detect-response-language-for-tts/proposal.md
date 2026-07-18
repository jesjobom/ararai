## Why

Chat responses can be generated in a language different from the device's
default language, causing native text-to-speech playback to use incorrect
pronunciation. Chat needs to identify each completed response locally before
offering playback so the Android TTS engine can select an appropriate language.

## What Changes

- Detect the language of each completed, non-blank assistant response locally.
- Keep the response sound action visible but disabled while language detection
  is in progress.
- Configure native TTS with the detected language before speaking the response.
- Fall back safely to the device's default TTS language when detection is
  uncertain or the detected language is unavailable.
- Include the bundled ML Kit language-identification model so detection is
  available offline without a first-use download.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `local-llm-hub`: Native assistant-response playback becomes language-aware
  and exposes a disabled preparation state before playback is ready.

## Impact

- Chat response lifecycle and message presentation state.
- Native Android TTS service contract and locale selection.
- Gradle dependencies and packaged application size (approximately 900 KB).
- Unit and Android-boundary tests for detection readiness, fallback, and TTS
  language selection.
