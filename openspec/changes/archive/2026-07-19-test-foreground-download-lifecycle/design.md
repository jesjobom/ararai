# Design: Foreground download lifecycle test harness

## Context

`ModelDownloadService` enters foreground state in `onCreate`, observes the
application-scoped controller, tracks service-owned model IDs, handles redelivered
commands, and cancels owned work in `onDestroy`. Current tests cover downloader
and controller logic but do not execute this orchestration.

## Decisions

Prefer Robolectric service tests for deterministic lifecycle and notification
inspection. Add narrow injectable boundaries for controller access and elapsed
time only where the platform harness cannot control them. Avoid introducing a
general DI framework solely for tests.

Use fake controller state to assert ownership transitions and cancellation calls.
Retain instrumentation/physical checks for real foreground-service restrictions,
notification permission, process pressure, and large network transfers.

## Validation

- Tests cover create-before-command, valid and null starts, redelivered download,
  cancel, completion, multiple owned transfers, and destruction.
- Manifest tests remain in place and the full quality gate passes.
