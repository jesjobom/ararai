# Design: Background model downloads

## Context

`ModelCatalogController` currently owns download jobs in a private coroutine
scope created by `MainActivity`. `ModelFileDownloader` uses an atomic `.part`
file but deletes it before every attempt and on cancellation. This supports a
simple foreground UI but cannot provide resilient background transfer.

## Decisions

Create an application-scoped model controller shared by the activity and a
`dataSync` foreground service. UI download commands start the service; the
service invokes the controller's execution boundary and remains foreground
until its requested downloads reach a terminal state. Recreated activities
observe the same process-level state.

The service creates a low-importance notification channel, publishes determinate
progress when size is known, provides Open and Cancel actions, and stops itself
when no owned transfer remains. A redelivered start intent can reattach after a
service/process restart. The controller retains an injectable direct execution
path for deterministic unit tests.

Cancellation is cooperative inside the blocking stream-copy loop, not only at
coroutine suspension boundaries. The service does not classify a requested
model as terminal until it has first observed that model downloading. Progress
notification updates are paced to avoid platform notification rate limiting,
and tapping the notification opens the model-management destination even when
an existing activity receives the intent.

ArarAI's only activity uses `singleTask` launch mode. Notification and launcher
intents therefore return to the existing application task through `onNewIntent`
instead of stacking duplicate `MainActivity` instances.

Make byte sources offset-aware. Preserve `.part` on cancellation and transient
network failure. Request an HTTP byte range for its current length; append only
when the server confirms the range, otherwise truncate and restart safely.
Integrity failure deletes the unusable partial file, and final promotion remains
atomic.

Request `POST_NOTIFICATIONS` while a download is active on Android 13+. Denial
does not block the transfer because Android foreground-service disclosure still
applies through system surfaces.

## Validation

- Unit tests cover partial preservation, confirmed resume, safe restart when a
  range is ignored, controller delegation, and manifest declarations.
- The project quality gate covers tests, lint, APK builds and strict OpenSpec.
- Physical validation must cover backgrounding, activity recreation,
  notification progress/actions, permission denial, process pressure and HTTP
  resume against the configured hosts.
