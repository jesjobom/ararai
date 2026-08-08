# Design: Agent guidance and Voice Chat characterization

## Context

Important project rules currently live across `openspec/project.md`, the
consolidated specs, `README.md`, and `docs/quality-gates.md`. Agents do not have a
repository-local entry point that states their precedence or prevents automated
build results from being mistaken for physical-device evidence.

`VoiceChatViewModel` coordinates model preparation, capture, app-owned media,
optional transcription, conversation persistence, streamed generation, speech
queueing, cancellation, diagnostics, and restart of the listening loop. Existing
tests cover its state model and web-search parity, but not the controller's core
lifecycle as a focused deterministic unit.

## Decisions

### Keep `AGENTS.md` short and authoritative

The guide SHALL reference canonical documents instead of duplicating their full
contents. It SHALL state the required quality gate, OpenSpec workflow, source
boundaries, and physical-device validation boundary. If guidance conflicts, the
OpenSpec precedence defined in `openspec/project.md` remains authoritative.

### Characterize through public controller behavior

Tests SHALL drive the same public methods used by Compose and observe state,
conversation persistence, fake engine requests, fake capture ownership, fake
speech segments, and file cleanup. They SHALL not reach into private fields or
use reflection.

Use Robolectric only to provide the Android main looper required by
`viewModelScope`. Use in-memory stores and temporary directories for all data.
Avoid adding a general dependency-injection framework.

### Separate characterization from bug fixes

This change records current intended contracts and adds the seams/fakes needed to
verify them. If a test exposes the known fallback-copy media defect or another
production defect, document it and address it in a dedicated follow-up change so
the behavior change remains reviewable.

## Validation

- Focused Voice Chat controller tests pass repeatedly.
- Existing unit tests remain green.
- Spotless, Detekt, Android lint, app build, instrumentation APK build, and strict
  OpenSpec validation pass through `scripts/quality-gate.sh`.
- Real capture, LiteRT-LM, Whisper, TTS, GPU, lifecycle under load, memory, and
  thermal behavior remain physical-device checks.

