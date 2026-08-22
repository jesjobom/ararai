## Context

The application-scoped model catalog currently resolves every configured model
and automatically requests the default download when no chat model is locally
available. Home always treats both conversation cards as available, while
normal Chat already derives send eligibility from model startup state. See
`proposal.md` for the product motivation and the delta specs for observable
behavior.

## Goals / Non-Goals

**Goals:**

- Separate passive startup model resolution from user-authorized download.
- Keep the one-time prompt state testable and independent of composable lifetime.
- Derive all unavailable UI behavior from whether any valid local chat model is
  usable, and react without restarting the application.
- Reuse the existing foreground download flow after consent.

**Non-Goals:**

- Adding Wi-Fi-only constraints, network-type confirmation, or download scheduling.
- Changing model integrity, resume, progress, cancellation, or notification behavior.
- Repeating onboarding after the prompt is handled or after the last model is deleted.
- Treating a downloaded transcription-only model as sufficient for Chat or Voice Chat.

## Decisions

### Persist a dedicated prompt-handled flag

Store a local boolean through an injectable preference boundary and mark it when
the user chooses any dialog outcome. This models the agreed one-time behavior
directly and supports deterministic tests. Inferring onboarding from model files
was rejected because a user may intentionally dismiss the prompt or later delete
their last model.

If a valid local chat model already exists on first startup, the dialog is
skipped. The flag may remain unset because later absence must not convert model
deletion into first-launch onboarding; implementation shall either mark the
prompt handled when skipping or otherwise preserve equivalent one-time semantics.

### Make startup model resolution side-effect free

Remove the catalog controller's bootstrap download request. Resolution continues
to choose an available chat model when possible and publish missing/invalid state
otherwise. Explicit prompt, Model Management, retry, and redownload actions keep
using the current download gateway.

Keeping an automatic request followed by UI cancellation was rejected because
the transfer can begin before Compose renders and would violate consent.

### Keep unavailable surfaces interactive for explanation, not navigation

Normal Chat remains navigable because it owns useful local history. Its composer
uses disabled submission semantics while a containing interaction target emits
brief localized guidance. Voice Chat remains visually present with disabled gray
styling, but a surrounding click target emits guidance instead of navigating.
This preserves discoverability and accessibility while preventing entry into a
destination that cannot perform its core loop.

The feedback mechanism may use the app's standard transient message surface,
provided repeated taps do not queue an unbounded number of messages and the text
is exposed to accessibility services.

### Use chat-purpose availability as the single gate

The gate is the presence of a valid configured model supporting Chat, not merely
the selected item's state and not a Whisper-only artifact. This avoids disabling
conversation features when a non-selected local chat model can be selected and
prevents transcription assets from falsely satisfying inference readiness.

## Risks / Trade-offs

- [A disabled-looking card can appear non-interactive] → Preserve an accessible
  action description and transient explanation when activated.
- [Composer controls implemented as disabled may consume no click events] → Put
  explanatory interaction handling at a stable parent boundary without enabling
  submission.
- [Existing installs with no model have no prompt preference] → Treat them as
  eligible for the one-time prompt; no download starts until consent.
- [The default model's catalog size may be absent] → Require a deterministic
  localized fallback rather than hiding the data implication.

## Migration Plan

Ship the new preference with no stored value. Existing installations that have
a local chat model continue normally. Existing installations without one see the
one-time prompt and no automatic download. Rollback restores the old startup
policy but leaves the unknown preference key harmless.
