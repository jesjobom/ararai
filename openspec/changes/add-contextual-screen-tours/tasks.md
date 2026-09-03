## 1. Establish reusable tour state and presentation

- [x] 1.1 Add stable screen-tour/version definitions and an injectable local
  terminal-state store; verify fresh, completed, dismissed, independently
  dismissed screens, restored-all, upgraded-version, and process-recreation
  states with unit tests.
- [x] 1.2 Build the reusable Compose spotlight component with one anchored target,
  safe placement, current/total progress, back/next/complete, close icon,
  system-Back, font-scale, reduced-motion, and accessibility behavior; verify it
  with focused Compose tests.
- [x] 1.3 Add capability-aware sequencing that waits for a laid-out semantic
  anchor, scrolls registered targets into view when needed, omits unavailable
  conditional steps, and never invokes target actions; verify deterministic
  sequencing and side-effect isolation.

## 2. Integrate interior-screen tours

- [x] 2.1 Add the normal-Chat tour for reasoning enablement versus reasoning
  visibility, its possible response-time cost, and conditionally applicable
  transcript presentation; verify copy, anchors, completion, capability changes,
  and absence of generation or transcription side effects.
- [x] 2.2 Add the Voice Chat tour for reasoning/history behavior and conditional
  reasoning response-time cost and manual/automatic photo capture; verify it
  appears only when the destination and relevant capabilities are available and
  does not start capture or request permissions.
- [x] 2.3 Add the Model Management tour explaining workload tabs and independent
  single-active Chat and Transcription selection, plus how local transcripts
  support reconstructible history and automatic voice-session titles; verify it
  neither downloads nor selects a model while advancing.
- [x] 2.4 Add the Assistant configuration tour for tools, network boundaries,
  context capacity, temperature, capability restrictions, and restoration of
  defaults; verify tab/anchor navigation and that no preference changes until the
  user operates the real controls outside the tour.

## 3. Complete controls, localization, and validation

- [x] 3.1 Keep Home free of a feature tour and preserve the first-model consent
  dialog unchanged; verify closing or completing one interior-screen tour does
  not suppress any other screen's tour.
- [x] 3.2 Add a confirmed `Restore tours` action to the appropriate application
  options surface; verify it clears only tour terminal states and makes every
  screen tour eligible on its next visit without opening one immediately.
- [x] 3.3 Add concise English and Portuguese tour copy and content descriptions;
  verify representative compact-screen, large-font, RTL-safe placement, TalkBack
  traversal, and system-Back behavior in Compose tests where automation is
  practical.
- [x] 3.4 Update README and project context if implementation materially changes
  documented onboarding or architecture, run targeted unit/Compose tests, then
  run `scripts/quality-gate.sh` and record physical-device accessibility checks
  as not executed unless actually performed.
- [x] 3.5 Add always-applicable normal-Chat history guidance that explains the
  downloaded-model prerequisite for sending new messages; version the revised
  tour and verify the no-model state.
- [x] 3.6 Adapt the history guidance to acknowledge a ready Chat model without
  incorrectly instructing the user to download another model; verify both states.
