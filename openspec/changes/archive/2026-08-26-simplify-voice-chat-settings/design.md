## Context

The Voice Chat settings dialog currently renders reasoning, speech rate, pause,
minimum response words, VAD provider and sensitivity, speech confirmation,
pre-roll, minimum speech, capture source, noise suppression, and an experimental
restart note in one scrollable column. Settings already persist immediately and
Reset restores the complete `VoiceChatSettings` default value.

## Goals / Non-Goals

**Goals:**

- Prioritize the two controls relevant to ordinary use.
- Keep specialist controls discoverable without removing or changing them.
- Preserve immediate persistence and full reset behavior.
- Keep disclosure state deterministic and independent from product settings.

**Non-Goals:**

- Changing setting defaults, ranges, labels, validation, or runtime application.
- Removing experimental controls or moving them to another destination.
- Persisting whether the advanced section was expanded.
- Changing reasoning capability gating or TTS speech-rate behavior.

## Decisions

### Use one collapsed advanced disclosure

Render the reasoning row and reading-speed slider first. Follow them with one
full-width, accessible `Advanced` disclosure control. Expanding it renders all
remaining controls in their current logical order. Collapsing hides only their
presentation and does not reset or modify stored values.

One disclosure is preferred over multiple categories because this is a small
hierarchy change and the hidden controls collectively represent tuning beyond
the two ordinary-use choices.

### Keep disclosure state local to the dialog instance

The advanced section starts collapsed every time the settings dialog opens.
Expansion survives recomposition while that dialog instance remains open but is
not written to preferences. Closing and reopening returns to the compact view.

### Reset every product setting regardless of visibility

Reset continues to restore all Voice Chat defaults, including hidden advanced
values. It does not need to expand the section and does not change the current
expanded/collapsed presentation state.

## Risks / Trade-offs

- [Users overlook advanced tuning] -> Use a clear localized `Advanced` label and
  expansion affordance with correct accessibility state.
- [Hidden values appear inactive] -> Keep immediate persistence and runtime
  application unchanged; hiding is presentation only.
- [Tests find hidden controls absent] -> Update tests to expand the disclosure
  before interacting with advanced controls and separately verify collapsed
  defaults.

## Migration Plan

No data migration is needed. Existing stored values remain authoritative and
continue to apply even while their controls are collapsed. Rollback restores the
flat layout without changing preferences.
