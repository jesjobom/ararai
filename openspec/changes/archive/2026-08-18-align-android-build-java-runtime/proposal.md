## Why

The Android quality workflow labels its runtime as JDK 17 but configures Java
21, while repository documentation declares JDK 17 as the supported Gradle
baseline. Local and CI builds therefore do not exercise the same claimed runtime.

## What Changes

- Select one supported Java runtime for Gradle based on the current AGP/Gradle
  compatibility boundary and exercise it consistently in CI and documentation.
- Keep any Java 21-only Firebase emulator invocation isolated from the Android
  Gradle runtime when two runtimes remain necessary.
- Add an inexpensive assertion that prevents workflow labels, configured Java,
  and documented prerequisites from drifting again.

## Capabilities

### Modified Capabilities

- `build-delivery`: The canonical Android quality gate uses and documents one
  reproducible Java runtime.

## Impact

- Affected files: GitHub Actions workflow, quality scripts, README, project and
  quality-gate documentation.
- Runtime behavior: unchanged.
