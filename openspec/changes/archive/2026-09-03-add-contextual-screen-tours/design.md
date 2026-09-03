## Context

The application already owns persisted preferences and Compose screens for
normal Chat, Voice Chat, Model Management, Assistant configuration, and general
Settings. The proposed guidance crosses those presentation surfaces but must not
duplicate their feature logic or fabricate UI for capabilities unavailable to
the selected model. See `proposal.md` and the delta specification for the
observable contract.

## Goals / Non-Goals

**Goals:**

- Explain only interior-screen concepts whose behavior or consequences are not
  evident from labels alone.
- Present one anchored explanation at a time and remain usable across supported
  screen sizes, font scales, and localized copy.
- Make each tour optional, locally persisted, capability-aware, and testable.
- Let the user close and permanently dismiss each screen's tour independently.
- Provide an explicit options action that restores all screen tours on demand.
- Reuse one presentation and state model across all participating screens.

**Non-Goals:**

- Adding a Home feature tour or replacing the existing first-model consent dialog.
- Demonstrating features by starting inference, downloads, recording, camera
  capture, transcription, tool calls, or navigation automatically.
- Explaining every visible control or restating self-explanatory labels.
- Adding remote analytics, accounts, cloud synchronization, or server-controlled
  onboarding content.
- Changing the behavior of the features described by the tours.

## Decisions

### Use sequential anchored coach marks

Each step targets one registered layout anchor, dims the remaining surface, and
presents a short title and explanation with a compact current/total progress
indicator and simple navigation actions.
This is preferred over a simultaneous map because it limits cognitive load and
avoids overlapping annotations on compact screens or at larger font scales.

The presentation layer measures the anchor and places the explanatory surface
inside safe bounds without covering the target when space permits. If no safe
adjacent placement exists, it may use a bounded bottom surface while retaining
the spotlight relationship. Transitions respect the platform reduced-motion
setting.

### Persist completion or dismissal independently per screen

Persist one terminal state keyed by stable screen-tour ID and content version.
`Next` and `Back` move only inside the active sequence. `Complete` on the last
step records that screen tour as complete. A close icon in the tour surface
records that screen tour as dismissed, including its unseen steps, and closes it.
Neither outcome changes the eligibility of tours on other screens.

System Back behaves like the visible close action so dismissal is predictable
and the same tour does not immediately recur on the next visit. Stable IDs and
versions allow materially revised guidance to be offered again without replaying
unrelated screen tours after every application update.

The application options expose `Restore tours`. After confirmation, the action
clears every locally stored tour completion and dismissal record. It does not
open a tour immediately; each restored tour becomes eligible again on the next
visit to its screen. This is a reset action, not a persistent global enable or
disable preference.

### Resolve conditional steps from current capabilities

The coordinator receives presentation facts from each screen rather than
querying model, transcription, camera, or tool infrastructure directly. A step
whose anchor or prerequisite is unavailable is omitted from that screen tour.
Completion or dismissal applies to the resolved screen-tour version, so an
omitted step does not appear later unless the screen tour receives a materially
revised content version. This keeps the promise that closing a tour ends
guidance for that screen.

The coordinator waits until the screen and target anchor are laid out before
showing a step. It shall not guess coordinates or display an unanchored fallback
for a feature-specific explanation.

### Keep tour actions side-effect free

Advancing a tour changes only tour state. Spotlight targets do not invoke the
underlying control while the tour owns interaction, and the tour never requests
permissions or performs feature operations. This avoids accidental downloads,
network tool calls, inference, microphone capture, or camera capture.

### Treat accessibility as part of the component contract

The active step exposes its title, explanation, progress, target description,
and actions in a deterministic reading order. Focus moves into the tour when it
opens and returns to a meaningful screen element when it closes. The dimmed
background is not exposed as independently actionable while the modal tour is
active.

## Tour Content Boundaries

- **Normal Chat:** explain that history remains browsable without a Chat model
  while new messages require a downloaded and selected Chat model; distinguish requesting model reasoning from displaying
  returned reasoning; explain that additional reasoning can increase time before
  the final answer; explain transcript visibility only when local
  transcription is applicable, including that hiding a transcript does not
  remove the persisted transcript or its use as context.
- **Voice Chat:** explain that reasoning may be retained for shared history but
  is not spoken and can increase response time; for image-capable models,
  explain that opening the in-app camera keeps the current voice turn active and
  that a valid pause can capture the current frame automatically unless a manual
  photo is already pending.
- **Model Management:** explain workload tabs and independent single-active-model
  selection for Chat and Transcription. Do not imply that selecting a model
  downloads it or that a transcription model can satisfy the Chat-model
  prerequisite. Explain that, when local transcription is used for a voice
  prompt, its text supports later context reconstruction and automatic session
  title generation while the original audio remains app-owned conversation data.
- **Assistant configuration:** explain tool enablement and network boundaries,
  per-model total input-plus-output context capacity, temperature effects and
  profiles, capability restrictions, restoration of catalog defaults, and the
  lack of an unsupported independent response-token control.

## Risks / Trade-offs

- [Tours become stale as layouts change] -> Anchor steps by stable semantic IDs,
  keep copy near the screen integration, and cover anchor availability in UI
  tests.
- [Conditional guidance is omitted when a capability is unavailable] -> Resolve
  the tour from current screen capabilities and add a new content version only
  when later presentation is important enough to justify renewed guidance.
- [Closing one tour accidentally suppresses unrelated guidance] -> Persist the
  terminal state by screen-tour ID; never use a global disable flag.
- [Accidental reset surprises the user] -> Label the action as restoring all
  tours, request confirmation, and state that guidance will reappear on later
  visits.
- [Spotlights conflict with scrolling or compact layouts] -> Scroll a registered
  target into view before measuring it and fall back to a bounded explanatory
  surface when adjacent placement cannot fit.
- [Modal guidance blocks accessibility] -> Define focus movement, traversal,
  target description, dismissal, and background action suppression as tested
  component behavior.

## Migration Plan

Ship new local preference keys with tours enabled and no completion records.
Existing users may therefore receive contextual guidance on their first visit
after upgrade. Completing or closing a tour records only that screen and version.
Restoring tours deletes only these terminal records. Rollback leaves unknown
local preference keys harmless.
