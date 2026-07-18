## Context

The current implementation passes a language-only locale to
`TextToSpeech.setLanguage`. Device testing showed that some engines report the
locale as available while retaining their previously active default voice.

## Goals / Non-Goals

**Goals:**

- Make the selected voice's locale match the detected language when a
  compatible installed voice exists.
- Prefer offline voices and rank multiple candidates deterministically.
- Log enough non-sensitive metadata to distinguish detection, selection, and
  fallback on a physical device.

**Non-Goals:**

- Download or install missing voices.
- Choose a user-specific regional accent.
- Log response text.

## Decisions

Enumerate `TextToSpeech.voices`, discard voices whose locale language does not
match the detected BCP-47 tag or whose data is marked not installed, then rank
the remaining candidates by: no network requirement, current matching voice,
higher quality, lower latency, stable name. Call `setVoice` explicitly for the
winner. Only restore the captured default voice when detection is absent or no
candidate can be activated.

Keep the selection function separate and deterministic so unit tests can
construct Android `Voice` values without initializing an actual engine.

Log only the language tag, selected voice name/locale, and fallback reason under
the Chat TTS tag. Response content remains private.

## Risks / Trade-offs

- **An engine reports no voice list** → Fall back to `setLanguage`, verify the
  resulting active voice language, then use the default if it still mismatches.
- **A voice is listed but fails activation** → Try the next ranked compatible
  voice before default fallback.
- **Regional accent differs from user preference** → Deterministic ranking is a
  safe baseline; explicit accent selection remains future work.

## Migration Plan

No persisted data changes. Replace voice configuration in place and retain the
existing default-voice fallback.

## Open Questions

None.
