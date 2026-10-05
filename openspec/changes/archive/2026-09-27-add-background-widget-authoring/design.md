## Context

Widget authorship runs today as `ManagedWidgetsController.generateDraft`
invoked from a `rememberCoroutineScope` job inside
`ManagedWidgetAuthoringScreen`. Cancelling the screen navigation cancels the
job; the LiteRT-LM engine instance is owned by `AppLocalLlmRuntime` created
inside the composition (`ArarAiAppControllers`), and the recovery gate is
remembered in the composition as well.

Physical validation (SM-S901E, change `add-managed-llm-authored-widgets`
tasks 11.24–11.27) established:

- Android terminates the app under low-memory pressure during E2B authoring
  even after the atomic reload and the serialized unload/recovery/reload
  barrier.
- The recovery gate (`LocalLlmRecoveryGate` /
  `androidLocalLlmRecoveryGate`) already samples thermal status, battery
  temperature, process PSS, available memory, total memory, and system
  low-memory, and can wait for a bounded stable acceptable window.
- The engine holds retained native resources and a reload barrier; two
  concurrent engine instances would double native memory and repeat the
  low-memory terminations.

## Goals / Non-Goals

**Goals**

- One shared LiteRT-LM engine at application scope.
- Authoring jobs survive leaving the authoring screen while the process lives.
- Single-flight authoring: at most one active authoring job.
- Device-state gating before generation with bounded deferral.
- Visible state in the app UI and in a persistent notification with cancel.

**Non-Goals**

- Surviving process death of an in-flight generation (the model, its
  conversation state, and retained resources do not survive; a killed job is
  reported as failed/abandoned and can be re-requested).
- Changing the authoring pipeline stages, protocol, validation, consent, or
  confirmation flow.
- Scheduling automatic background authoring without a user request.
- Physical-device validation (explicitly deferred by JJ for this change).

## Decisions

### D1. Engine ownership moves to application scope

`AppLocalLlmRuntime` (single `LocalLlmEngine`) is created lazily in
`ArarAiApplication` with the same factory inputs the composition computed, and
`ArarAiAppControllers` receives the shared engine instead of building its own.
The recovery gate also moves to application scope (it is a cheap wrapper) so
background and foreground observe the same signals.

Rationale: duplicating the engine for background authoring would double native
heap pressure, which physical evidence already proves is fatal. One engine
serializes native generation anyway (concurrent-generation detection exists in
telemetry), so a single instance is the safe shape.

### D2. Job controller owns single-flight and state, not the pipeline

A new application-scoped `WidgetAuthoringJobController` owns:

- `MutableStateFlow<WidgetAuthoringJobState?>` with
  `Queued / Running(stage, progress) / Deferred(reason, attempt) /
  Succeeded(draft) / Failed(error) / Cancelled`.
- `submit(request)` returning accepted/refused-active-exists; the active
  request identity (instruction, widgetId, model, inference) is retained.
- `cancel()` cancelling the active coroutine and marking `Cancelled`.

The controller calls `ManagedWidgetsController.generateDraft` in an
application-scope coroutine. The controller instance it uses is created at
application scope with the shared engine and services; the composition-scoped
controller for list/detail/confirm remains, but both wrap the same engine, so
no double inference path exists. The job controller takes precedence: the
authoring screen reads job state instead of owning the coroutine.

### D3. Foreground service, not WorkManager worker, for the run phase

The generation runs in an application-scope coroutine hosted by
`WidgetAuthoringService` (foreground, `dataSync` type, matching the existing
`ModelDownloadService` pattern). Deferral is handled in-process: when the
device-state check refuses to start, the controller transitions to
`Deferred`, waits with capped backoff, and re-evaluates — no WorkManager
retry worker. A WorkManager retry would only help after process death, and
the in-memory job state (draft result included) does not survive process
death anyway, so the extra worker would be dead weight.

### D4. Deferral is bounded and explicit

- Before starting a job, evaluate device state via the recovery-gate snapshot
  source: severe thermal status (`SEVERE`+ or critical battery temperature) or
  system low memory defers the job.
- Deferred jobs retry with capped attempts (small checked-in constant) and
  backoff; when the budget is exhausted the job transitions to `Failed` with a
  deferral-exhausted reason, and the notification says so.
- The bounded recovery-gate wait already inside the pipeline remains in place
  for transient unacceptable windows after the job has started.

### D5. UI contract

The authoring screen observes the job-state flow:

- `generate` submits to the controller instead of launching its own coroutine;
  it is refused (disabled) while a job is active or queued.
- The screen shows the live stage/deferral reason and offers `Cancel`.
- Navigating away does not cancel; returning shows the same state. Only an
  explicit cancel action or the notification action cancels.
- A `Succeeded` result keeps the draft preview/diff/confirm flow exactly as
  today, keyed by the request that submitted it.

### D6. Localization and notification

New user-visible strings (progress, deferred reason, cancel, single-flight
refusal, failure) are added to `values/` and `values-pt-rBR/` in both
languages. The notification reuses the app notification channel for model
operations if one exists, otherwise a dedicated `widget_authoring` channel.

## Risks / Trade-offs

- Moving the engine to application scope changes initialization timing
  (engine creation becomes lazy at first use instead of at first composition);
  chat and diagnostics must tolerate the lazy acquisition.
- In-process coroutine + foreground service keeps the process alive during
  generation; this is intended, but makes the process a bigger LMK target —
  mitigated by the deferral gating not starting generation under memory
  pressure.
- Single-flight means the user cannot queue a second widget while one is
  authoring; the UI states this explicitly per JJ's requirement.
- Draft results are held in memory only; if the process dies, the job is lost
  and re-requestable. This is stated in the UI copy.

## Migration Plan

1. Land OpenSpec change with design/spec/tasks.
2. Move engine runtime + recovery gate to application scope; run local gates.
3. Add job controller, service, notification, worker with unit tests.
4. Rewire the authoring screen to the job controller; run local gates.
5. Physical validation is a later, separate task (excluded here by request).

## Open Questions

- None blocking. Physical-device acceptance thresholds (thermal level,
  available-memory floor) start with the same bounds already used by
  `BoundedLocalLlmRecoveryGate` and may be tuned after physical validation.