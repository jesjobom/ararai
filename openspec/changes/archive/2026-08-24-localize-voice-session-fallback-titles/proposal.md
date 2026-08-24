# Change: Localize fallback titles for voice sessions

## Why

When local Whisper transcription is unavailable, Chat and Voice Chat create a
dated fallback session title. Those titles are currently resolved through the
application context, so they remain in Portuguese even when the configured app
language is English.

## What Changes

- Resolve audio-message and Voice Chat fallback titles from the context carrying
  the configured application locale.
- Format the appended date and time with that same application locale and the
  device's local time zone.
- Keep persisted titles stable after creation.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: application/controller composition and fallback-title formatting
- Persistence: no schema migration
