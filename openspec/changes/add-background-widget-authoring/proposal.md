## Why

Widget authoring currently runs as a UI-scoped coroutine: the LiteRT-LM engine
is owned by the composition runtime, the generation job dies when the user
leaves the authoring screen, and the process competes for memory/thermal
headroom exactly while the user is interacting with the app. Physical-device
evidence shows Android terminates the app under memory pressure during E2B
authoring, and the recovery gate already models the device-state signals
(thermal, battery temperature, PSS, available memory, low memory) that should
gate when generation is allowed to run.

## What Changes

- Move the shared local LLM engine runtime to application scope so a single
  LiteRT-LM instance serves foreground chat and background authoring without
  doubling native memory.
- Add an application-scoped authoring job controller with single-flight
  semantics: at most one widget authoring job may exist, and a new request is
  refused while one is active.
- Expose the job state (queued, running with stage progress, deferred with
  reason, succeeded, failed, cancelled) as an observable flow used by the
  authoring UI and the background notification.
- Run active authoring jobs under a foreground service (dataSync) with a
  progress notification exposing stage, deferral reason, and a cancel action.
- Defer job start when device state is unacceptable (severe thermal status,
  low available memory, system low-memory) with bounded in-process retries
  (capped attempt budget and backoff); continue immediately when the
  already-existing bounded recovery gate reaches an acceptable window.
- Keep the authoring screen usable while a background job runs: it shows the
  live stage, offers cancel, and blocks new generation requests until the job
  finishes. Resulting drafts remain previewable in the screen that submitted
  the request.
- Do not change the authoring pipeline stages, validation, consent, or
  confirmation flow; the background controller only re-homes where and when
  `generateDraft` executes.

## Impact

- Affected specs: `llm-widget-authoring` (new job orchestration requirements),
  `managed-widgets` (background execution presence).
- Affected code:
  `ArarAiApplication`, `ArarAiApp.kt`, `ArarAiAppControllers.kt`,
  `ManagedWidgetsController.kt`, `ManagedWidgetAuthoringScreen.kt`, new
  `widget/managed` job controller, worker, service, notification, and strings.
- New local unit tests for job state transitions, single-flight, cancel, and
  deferral decisions. No physical-device test in this change.