## 1. Make startup downloads consent-driven

- [x] 1.1 Add a persisted, injectable first-launch model-prompt preference and verify handled, skipped-with-existing-model, and fresh-install states with unit tests.
- [x] 1.2 Remove automatic bootstrap downloading from model-catalog startup while preserving available-model selection, explicit download/retry behavior, and foreground gateway delegation; verify with controller tests.

## 2. Add the first-launch model prompt

- [x] 2.1 Wire the prompt preference into the application composition root and derive prompt eligibility from valid local chat-model availability; verify state transitions with deterministic tests.
- [x] 2.2 Add the localized first-launch dialog with the default model name, approximate size, download, model-list, and close actions; verify all actions, persistence, navigation, and lack of unrequested downloads in Compose tests.

## 3. Gate conversation surfaces without hiding local data

- [x] 3.1 Keep normal Chat history and session controls usable without a model, block every composer submission type, and emit bounded accessible transient guidance on composer interaction; verify with ViewModel and Compose tests.
- [x] 3.2 Render Voice Chat with a disabled gray treatment when no local chat model exists, intercept activation to show bounded accessible guidance, and restore navigation reactively when a model becomes available; verify with Home Compose tests.
- [x] 3.3 Ensure availability gates count only valid chat-purpose models, not transcription-only artifacts or only the selected missing model, and verify these catalog combinations with unit tests.

## 4. Documentation and validation

- [x] 4.1 Update consolidated requirements, project context, README behavior summaries, and localized strings as applicable; verify strict OpenSpec validation succeeds.
- [x] 4.2 Run targeted model-controller and Compose journey tests, then run `scripts/quality-gate.sh`; record physical-device validation as not executed unless it is actually performed.
