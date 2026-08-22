## Why

Whisper transcription currently inherits the application UI locale. This is
incorrect when the spoken language differs from the interface language and
gives users no way to request Whisper's own language detection.

## What Changes

- Add an Audio tab to Assistant configuration for global audio behavior.
- Persist a transcription-language preference with Automatic as the default.
- Offer system, interface, English, and Portuguese language choices in addition
  to automatic detection.
- Resolve the preference immediately before each new Chat or Voice Chat
  transcription without changing already completed transcripts.

## Capabilities

### Modified Capabilities

- `local-llm-hub`: users control the language hint supplied to local Whisper
  transcription independently from the application UI language.

## Impact

- Affected code: Assistant configuration UI, local preferences, MainActivity
  composition, Whisper transcription tests, and Compose journeys.
- Privacy/networking: unchanged; language selection and transcription remain
  local.
