## 1. Application-scoped engine runtime

- [x] 1.1 Move `AppLocalLlmRuntime` creation to `ArarAiApplication` with the
  same factory inputs the composition computed today (bridge dependencies,
  telemetry, cache dir, tool wiring), keeping engine creation lazy.
- [x] 1.2 Move the `androidLocalLlmRecoveryGate` instance to application scope
  and share it between foreground chat controllers and the authoring job
  controller.
- [x] 1.3 Rewire `ArarAiAppControllers` to receive the shared engine instead of
  constructing `AppLocalLlmRuntime` in composition; keep constructor semantics
  and existing diagnostics intact.
- [x] 1.4 Run the focused engine and UI local gates and verify no behavioral
  change to chat/model status flows.

## 2. Authoring job controller

- [x] 2.1 Add `WidgetAuthoringJobState` (queued, running with stage/progress,
  deferred with reason and attempt, succeeded with draft result, failed with
  reason, cancelled) and a request record capturing model, inference,
  instruction, widget id, and request identity.
- [x] 2.2 Add the application-scoped `WidgetAuthoringJobController` with
  single-flight `submit`, cooperative `cancel`, and a `StateFlow` of job state
  that runs `generateDraft` in an application-scope coroutine.
- [x] 2.3 Add device-state gating at job start using the recovery-gate
  snapshot source (thermal severe+, system low memory) with a bounded retry
  budget and explicit failure on exhaustion.
- [x] 2.4 Add unit tests covering state transitions, single-flight refusal,
  cancel semantics, and deferral/failure decisions with a fake gate and fake
  engine.

## 3. Foreground service and notification

- [x] 3.1 Add `WidgetAuthoringService` (foreground, `dataSync` type) that owns
  the progress notification and binds its lifetime to the active job.
- [x] 3.2 Add notification content: current stage, deferral reason, cancel
  action, and terminal-state cleanup; reuse or create the notification channel
  per design.
- [x] 3.3 Implement the bounded deferral loop (capped attempts with backoff,
  in-process re-evaluation of device state) in the job controller.
- [x] 3.4 Add user-visible strings (en + pt-rBR) for progress, deferral,
  cancel, single-flight refusal, and failure states.

## 4. Authoring UI rewiring

- [x] 4.1 Rewire `ManagedWidgetAuthoringScreen` to submit requests to the job
  controller, observe job state instead of owning the coroutine, disable new
  requests while a job is active, and keep navigate-away running.
- [x] 4.2 Wire cancel in the screen to the job controller cancel path and keep
  the draft preview/diff/confirm flow working for succeeded results.
- [x] 4.3 Update user-facing copy for the single-flight refusal and deferral
  states in both languages.
- [x] 4.4 Run the focused UI and authoring local gates.

## 5. Validation and documentation

- [x] 5.1 Run the full local unit-test gate and lint for the touched modules.
- [x] 5.2 Update `docs/` with the background authoring lifecycle and its
  in-memory-only job persistence limitation.
- [x] 5.3 Record that physical-device validation (deferred by request) is the
  remaining acceptance evidence for this change.