# Change: Characterize foreground download lifecycle behavior

## Why

Foreground model downloads are a critical path with ownership, intent redelivery,
cancellation, completion, notification, and service-destruction behavior, but the
service lifecycle is not exercised by the automated test suite.

## What Changes

- Add deterministic lifecycle tests for foreground model downloads.
- Cover start, progress, cancel, completion, redelivery, null intents, and destruction.
- Introduce only the minimal injection seams required for stable tests.
- Keep physical-device validation for Android behavior that Robolectric cannot prove.

## Impact

- Affected spec: `local-llm-hub`
- Affected code: download service testability and test suites
- User-visible download behavior: unchanged unless characterization reveals a defect
