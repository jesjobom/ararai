## Why

Voice Chat settings currently present every tuning and experimental audio control
at the same level. Most users primarily need reasoning enablement and reading
speed, while pause detection, response segmentation, VAD, capture timing, audio
source, and noise suppression are specialist controls. Showing everything at
once makes the dialog harder to scan and gives experimental tuning undue visual
weight.

## What Changes

- Keep only the reasoning toggle and TTS reading-speed control expanded when
  Voice Chat settings opens.
- Place every other existing Voice Chat control behind an `Advanced` disclosure
  that is collapsed by default.
- Preserve immediate persistence, validation, reset behavior, capability gating,
  defaults, ranges, and runtime effects for every setting.
- Treat expanded/collapsed presentation as transient dialog state rather than a
  persisted product preference.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `voice-chat`: simplify the settings hierarchy without changing any setting's
  functional semantics.

## Impact

- Affected code: Voice Chat settings Compose layout, localized labels and
  descriptions, and focused Compose tests.
- No migration of existing Voice Chat preferences is required.
- No inference, audio, VAD, capture, TTS, or persistence contract changes.
