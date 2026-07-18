## Context

The Chat currently exposes speech only after generation completes, but its TTS
service receives text alone and leaves the Android engine on its default voice.
Consequently, a response generated in another language is pronounced using the
wrong locale. Language readiness also needs to be represented independently
from generation and playback state.

The app is local-first and must not require Google Play Services or a network
download before identifying a response. Language identification is derived from
the final response text and does not need to become part of the conversational
context.

## Goals / Non-Goals

**Goals:**

- Identify completed assistant-response language locally and asynchronously.
- Represent detection readiness per message so the existing sound action can
  remain visible but disabled until detection finishes.
- Select a compatible Android TTS locale per utterance and restore/fall back to
  the device's default voice when no usable language is available.
- Keep detection and TTS integrations behind testable interfaces.

**Non-Goals:**

- Detect or switch languages inside different segments of one response.
- Guarantee a regional variant such as `pt-BR` versus `pt-PT` when the detector
  returns only `pt`.
- Install missing TTS voices or engines.
- Persist derived language metadata in the chat database.

## Decisions

### Bundle ML Kit Language ID

Use `com.google.mlkit:language-id:17.0.6`, whose model is packaged with the app.
This increases packaged size by approximately 900 KB but makes detection
immediately available offline and avoids a Google Play Services dependency.
The unbundled artifact was rejected because its smaller APK contribution comes
with a first-use model download and weaker availability guarantees.

### Detect final response text in a Chat-scoped controller

Extend the Chat TTS controller with a language-identifier abstraction and a
per-message preparation state. Chat requests detection only for completed,
non-blank assistant text. Requests are idempotent for the same message text,
and stale asynchronous callbacks are ignored.

Detection is intentionally not persisted. It is small, derived data; detecting
visible eligible history again when Chat is recreated avoids a database schema
migration and prevents stale metadata after future text transformations.

### Treat uncertainty or detector failure as ready with default-language fallback

ML Kit's `und` result and operational failures resolve preparation rather than
permanently disabling speech. The message becomes playable with no language
tag, which tells the TTS service to use the captured device-default voice. This
keeps speech available while avoiding an unsupported locale guess.

### Select and validate locale immediately before playback

The speech service accepts an optional BCP-47 language tag. Before each
utterance, it calls `setLanguage(Locale.forLanguageTag(tag))` and accepts only a
non-negative availability result. If the tag is absent or unavailable, it
restores the voice captured when the engine initialized. Playback remains
single-utterance, so engine-global locale selection cannot race with another
active response.

## Risks / Trade-offs

- **Short or mixed-language responses may be undetermined** → Use the default
  voice rather than guessing, and keep the confidence threshold explicit.
- **Language-only tags do not specify a regional accent** → Let the installed
  TTS engine select its best compatible voice; regional preference remains a
  future setting.
- **Re-entering Chat repeats detection for history** → Detection is lightweight
  and asynchronous; idempotence prevents repeated work during one controller
  lifetime.
- **A detected language may have no installed TTS voice** → Validate
  `setLanguage` and fall back to the captured default voice.
- **ML Kit increases app size** → Accept the approximately 900 KB cost in return
  for deterministic offline availability.

## Migration Plan

Add the bundled dependency and new controller/service contracts without a data
migration. Existing sessions are detected when rendered after upgrade. Rollback
removes the dependency and returns the TTS service to its text-only contract;
stored chat data remains unchanged.

## Open Questions

None.
