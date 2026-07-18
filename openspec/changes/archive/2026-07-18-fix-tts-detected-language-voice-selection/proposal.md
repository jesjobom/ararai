## Why

Device testing showed that passing the detected language through
`TextToSpeech.setLanguage` can leave the engine's default voice active even when
the method reports support. The implementation therefore does not reliably
satisfy the existing language-aware playback requirement across Android TTS
engines.

## What Changes

- Select an installed TTS voice whose locale matches the detected language
  instead of relying solely on the engine-global `setLanguage` side effect.
- Prefer an installed local voice and use deterministic ranking when several
  regional voices match a language-only tag.
- Add diagnostic logging for detected language, selected voice, and fallback.
- Add deterministic tests for compatible voice selection and fallback.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `local-llm-hub`: Clarify that language-aware playback selects an installed
  compatible voice rather than merely requesting a language from the engine.

## Impact

- Android native TTS voice selection.
- Language-identification and playback diagnostics.
- Unit and Android-boundary tests.
