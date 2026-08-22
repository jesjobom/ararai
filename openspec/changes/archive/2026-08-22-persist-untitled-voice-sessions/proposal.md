## Why

Voice Chat relies on Whisper transcription to replace the transient `New chat`
title and persist the session. With a direct-audio model and no local Whisper
artifact, the session remains transient indefinitely. Creating another Chat or
Voice Chat session then reuses the same identity and can make old history
disappear temporarily and reappear on the next message.

## What Changes

- Promote the first direct-audio turn immediately when Whisper is unavailable,
  using a localized timestamp-based title in both regular Chat and Voice Chat.
- Preserve transcript-derived titles whenever local transcription is available.
- Ensure a subsequent new session has a distinct identity and empty history.

## Capabilities

### Modified Capabilities

- `local-llm-hub`: voice conversations remain durable and separable without a
  downloaded Whisper model.

## Impact

- Affected code: regular Chat and Voice Chat session titling, application composition, localized
  strings, and session/ViewModel regression tests.
- Privacy/networking: unchanged; fallback titles are generated locally and do
  not inspect or transmit audio.
