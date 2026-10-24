# Plan 001: Keep the active authoring service alive across stale notification actions

> **Executor:** Read this entire plan. First run
> `git diff --stat 38acb8b..HEAD -- app/src/main/java/com/jesjobom/ararai/WidgetAuthoringService.kt app/src/test/java/com/jesjobom/ararai/WidgetAuthoringServiceIntentTest.kt app/src/androidTest/java/com/jesjobom/ararai openspec/changes`.
> If relevant code changed, compare the excerpts below with the live file and
> stop on mismatch. Do not commit, push, archive a change, or use a retained
> Android device without separate authorization.

## Status

- Priority P1; effort M; fix risk MEDIUM; category correctness/lifecycle.
- Depends on: none. Planned at `38acb8b`, 2026-10-17.

## Why this matters

A notification action carries the authoring session revision. Delayed actions
for an older revision are rejected as state transitions, but the service still
stops itself or removes its foreground notification. A newer active attempt can
therefore lose its foreground lifetime and cancellation control. The
consolidated `llm-widget-authoring` spec requires a foreground notification
throughout an active attempt; device validation previously caught a related
foreground-service timing crash.

## Current state and conventions

- `WidgetAuthoringService.kt:77-83` checks whether Cancel matches the current
  snapshot, then **unconditionally** calls `stopForeground` and `stopSelf`.
- `WidgetAuthoringService.kt:139-159` calls `stopSelf()` on rejected Continue
  and Retry; successful asynchronous handlers also stop without checking
  whether a new start arrived (`:152`, `:182`).
- `WidgetAuthoringService.kt:109-131` is the existing safe refresh pattern:
  acknowledge a queued foreground start, inspect the latest state, then apply
  `widgetAuthoringForegroundDisposition` with `stopSelfResult(startId)`.
- `WidgetAuthoringService.kt:229-231,256-260` binds actions to session ID and
  revision. `WidgetAuthoringServiceIntentTest.kt:30-105` already characterizes
  revision matching and the refresh disposition; extend that style.
- `openspec/specs/llm-widget-authoring/spec.md` requires an ongoing notification
  while queued/deferred/running and idempotent stale action delivery. Preserve
  existing user-facing action labels, cancellation semantics, and the separate
  legacy authoring-controller path.

## Commands

Run Gradle with JDK 17; `scripts/quality-gate.sh` additionally requires a
JDK 21 installation at `FIREBASE_JAVA_HOME` for Firebase emulator tests. See
`docs/quality-gates.md` for the pinned toolchain.

| Purpose | Command | Expected |
| --- | --- | --- |
| Focused JVM tests | `./gradlew :app:testDebugUnitTest --tests 'com.jesjobom.ararai.WidgetAuthoringServiceIntentTest'` | exit 0, new stale-action cases pass |
| Android test APK | `./gradlew :app:assembleDebugAndroidTest` | exit 0 |
| Full local/CI gate | `scripts/quality-gate.sh` | exit 0 (JDK 17 + Firebase JDK 21 configured) |
| Spec validation | `openspec validate --all --strict` | exit 0 |

## Scope and delivery

In scope: `WidgetAuthoringService.kt`, `WidgetAuthoringServiceIntentTest.kt`, a
focused service-lifecycle test under `app/src/androidTest/java/com/jesjobom/ararai/`,
`docs/device-validation.md`, and a new active
`openspec/changes/preserve-active-authoring-service/` change. Keep changes
within those paths. Do not alter model pipeline stages, the widget repository,
notification action labels, or Chat. Follow conventional commit style seen in
`git log` (`fix: ...`) **only if the operator later authorizes a commit**.

## Steps

1. Add an active OpenSpec change stating that stale/duplicate action delivery
   cannot end foreground service for a newer queued/deferred/running attempt;
   preserve existing cancellation behavior. Verify with
   `openspec validate --all --strict` (exit 0).
2. Centralize post-action service disposition around the **latest** snapshot
   and `startId`; reject stale actions without mutating workflow state or
   stopping a currently active service. For a matching Cancel, request
   cancellation but leave foreground teardown to the committed terminal-state
   refresh. For asynchronous Continue/Retry, do not use unconditional
   `stopSelf()` after a newer start; use the latest disposition and
   `stopSelfResult(startId)` only when no attempt is active. Preserve the
   `ACTION_REFRESH` promotion-before-inspection rule. Verify focused JVM tests.
3. Add deterministic JVM cases for stale Cancel/Continue/Retry with a newer
   running revision, matching Cancel, and a completed session; assert both
   workflow calls and service disposition. Add a real Android instrumentation
   case for rapid queued-to-terminal and stale-action-to-new-running transitions,
   using a disposable emulator/device and synthetic state only. Build its APK
   with the command above; document the exact connected-test command and
   retained-device prohibition in `docs/device-validation.md`.
4. Run the full quality gate. Then run focused instrumentation on a
   **disposable arm64 target only** using the repository's explicit
   `-Pararai.allowDestructiveConnectedTests=true` opt-in. If none is available,
   record device execution as **not run**, not passed; do not substitute the
   retained SM-S901E.

## Test plan

- Follow `WidgetAuthoringServiceIntentTest` for state/revision fixtures and
  `AndroidBoundaryTest` for AndroidJUnit4 setup. Cover stale Cancel, Continue,
  Retry, matching Cancel, rapid terminal transition, and a newer active start.

## Done criteria

- New JVM cases pass, Android instrumentation APK assembles, and device result
  is recorded truthfully. `scripts/quality-gate.sh` exits 0.
- Stale actions cannot call `stopForeground`/`stopSelf` while the latest
  authoring snapshot is ongoing; an active attempt retains cancellation UI.
- Only in-scope files changed; no commit, push, or archive was performed.

## STOP conditions

Stop if the actual service-start contract differs from the described
`ACTION_REFRESH` path, if Android cannot run the lifecycle test without
uninstalling a retained app, or if correct handling requires changing the
repository's single-flight workflow state machine.

## Maintenance notes

Review future notification actions against the same latest-state disposition;
do not reintroduce a direct `stopSelf()` in an action branch.
